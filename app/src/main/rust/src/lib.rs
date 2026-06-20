pub mod analyzer;
pub mod db;
pub mod sniffer;

use analyzer::credential_leak;
use analyzer::dns_intel;
use analyzer::entropy_eval;
use analyzer::heuristics;
use analyzer::packet_parser::{parse_packet, ParsedPacket};
use analyzer::scan_detector;
use db::sqlite_persistence::{NativePacketRecord, SQLitePersistenceEngine};
use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jboolean, jstring};
use jni::JNIEnv;
use once_cell::sync::Lazy;
use serde::Serialize;
use sniffer::fault_isolation::run_isolated_sandbox;
use sniffer::thread_pool::ThreadPool;
use std::sync::{Arc, RwLock};

static MULTITHREAD_POOL: Lazy<Arc<ThreadPool>> = Lazy::new(|| Arc::new(ThreadPool::new(4)));
static NATIVE_SQLITE: Lazy<RwLock<Option<Arc<SQLitePersistenceEngine>>>> =
    Lazy::new(|| RwLock::new(None));

#[derive(Serialize)]
struct RobustAnalysisReport {
    suspicious: bool,
    protocol: String,
    source_ip: String,
    dest_ip: String,
    source_port: Option<u16>,
    dest_port: Option<u16>,
    entropy: f64,
    density_classification: String,
    leaks_found: Vec<String>,
    anomaly_detected: bool,
    heuristic_class: String,
    dns_query: Option<String>,
    length: usize,
    risk_score: u8,
    scan_signals: Vec<String>,
    parse_warnings: Vec<String>,
    flow_id: String,
    tcp_flags: Option<String>,
    ttl_or_hop_limit: Option<u8>,
}

#[no_mangle]
pub extern "system" fn Java_com_example_utils_PacketAnalyzerJni_setNativeDatabasePath(
    mut env: JNIEnv,
    _class: JClass,
    db_path: JString,
) -> jboolean {
    run_isolated_sandbox(
        move || {
            let path: String = match env.get_string(&db_path) {
                Ok(value) => value.into(),
                Err(_) => return 0,
            };

            match SQLitePersistenceEngine::new(&path) {
                Ok(engine) => {
                    if let Ok(mut slot) = NATIVE_SQLITE.write() {
                        *slot = Some(Arc::new(engine));
                        1
                    } else {
                        0
                    }
                }
                Err(_) => 0,
            }
        },
        0,
    )
}

/// Deep-inspects a raw IPv4 packet from the VPN interface in Rust.
#[no_mangle]
pub extern "system" fn Java_com_example_utils_PacketAnalyzerJni_analyzeRawPacketPayload(
    mut env: JNIEnv,
    _class: JClass,
    packet: JByteArray,
) -> jstring {
    let fallback = env
        .new_string(r#"{"suspicious":false,"protocol":"ISOLATED_FAULT"}"#)
        .map(|value| value.into_raw())
        .unwrap_or(std::ptr::null_mut());

    run_isolated_sandbox(
        move || {
            let default_err = r#"{"suspicious":false,"protocol":"ERROR_FAULT"}"#;
            let bytes = match env.convert_byte_array(packet) {
                Ok(value) => value,
                Err(_) => return new_jstring(&mut env, default_err),
            };

            if bytes.is_empty() {
                return new_jstring(&mut env, r#"{"suspicious":false,"protocol":"EMPTY"}"#);
            }

            let metadata = match parse_packet(&bytes) {
                Some(value) => value,
                None => {
                    let entropy_val = entropy_eval::calculate_shannon_entropy(&bytes);
                    let report = RobustAnalysisReport {
                        suspicious: false,
                        protocol: "RAW".to_string(),
                        source_ip: "0.0.0.0".to_string(),
                        dest_ip: "0.0.0.0".to_string(),
                        source_port: None,
                        dest_port: None,
                        entropy: entropy_val,
                        density_classification: entropy_eval::classify_payload_density(entropy_val)
                            .to_string(),
                        leaks_found: Vec::new(),
                        anomaly_detected: false,
                        heuristic_class: "Unsupported or malformed frame".to_string(),
                        dns_query: None,
                        length: bytes.len(),
                        risk_score: 5,
                        scan_signals: Vec::new(),
                        parse_warnings: vec!["unsupported-network-frame".to_string()],
                        flow_id: "raw:-".to_string(),
                        tcp_flags: None,
                        ttl_or_hop_limit: None,
                    };
                    let json = serde_json::to_string(&report).unwrap_or_else(|_| default_err.to_string());
                    return new_jstring(&mut env, &json);
                }
            };

            let payload = metadata.payload(&bytes);
            let entropy_val = entropy_eval::calculate_shannon_entropy(payload);
            let density = entropy_eval::classify_payload_density(entropy_val).to_string();
            let payload_str = bounded_lossy_payload(payload, 4096);
            let leaks = credential_leak::scan_for_leaks(&payload_str)
                .iter()
                .map(|&value| value.to_string())
                .collect::<Vec<_>>();
            let heuristics_report = heuristics::audit_packet_heuristics(&bytes, &metadata.protocol);
            let scan_report = scan_detector::assess_scan_behavior(&metadata, payload.len());
            let dns_suspicious = metadata
                .dns_query
                .as_ref()
                .map(|query| dns_intel::evaluate_dns_threat(query))
                .unwrap_or(false);
            let warning_risk = if metadata.warnings.is_empty() { 0 } else { 15 };
            let leak_risk = if leaks.is_empty() { 0 } else { 85 };
            let dns_risk = if dns_suspicious { 80 } else { 0 };
            let heuristic_risk = if heuristics_report.anomaly_detected { 75 } else { 0 };
            let risk_score = [scan_report.score, warning_risk, leak_risk, dns_risk, heuristic_risk]
                .into_iter()
                .max()
                .unwrap_or(0);
            let is_suspicious = risk_score >= 60;

            let mut threat_tags = leaks.clone();
            if heuristics_report.anomaly_detected {
                threat_tags.push("HEURISTIC_ANOMALY".to_string());
            }
            if dns_suspicious {
                threat_tags.push("MALICIOUS_DNS".to_string());
            }
            threat_tags.extend(scan_report.signals.iter().cloned());

            let report = RobustAnalysisReport {
                suspicious: is_suspicious,
                protocol: metadata.protocol.clone(),
                source_ip: metadata.source_ip.clone(),
                dest_ip: metadata.dest_ip.clone(),
                source_port: metadata.source_port,
                dest_port: metadata.dest_port,
                entropy: entropy_val,
                density_classification: density.clone(),
                leaks_found: leaks,
                anomaly_detected: heuristics_report.anomaly_detected,
                heuristic_class: heuristics_report.classification.clone(),
                dns_query: metadata.dns_query.clone(),
                length: bytes.len(),
                risk_score,
                scan_signals: scan_report.signals.clone(),
                parse_warnings: metadata.warnings.clone(),
                flow_id: metadata.flow_id(),
                tcp_flags: metadata.tcp_flag_labels(),
                ttl_or_hop_limit: metadata.ttl_or_hop_limit,
            };

            let json = serde_json::to_string(&report).unwrap_or_else(|_| default_err.to_string());
            persist_packet_async(metadata, is_suspicious, threat_tags, entropy_val, heuristics_report.classification);
            new_jstring(&mut env, &json)
        },
        fallback,
    )
}

#[no_mangle]
pub extern "system" fn Java_com_example_utils_PacketAnalyzerJni_checkMaliciousDnsNative(
    mut env: JNIEnv,
    _class: JClass,
    dns_query: JString,
) -> jboolean {
    run_isolated_sandbox(
        move || {
            let query_str: String = match env.get_string(&dns_query) {
                Ok(s) => s.into(),
                Err(_) => return 0,
            };

            if dns_intel::evaluate_dns_threat(&query_str) {
                1
            } else {
                0
            }
        },
        0,
    )
}

#[no_mangle]
pub extern "system" fn Java_com_example_utils_PacketAnalyzerJni_inspectCleartextCredentialsNative(
    mut env: JNIEnv,
    _class: JClass,
    payload_str: JString,
) -> jstring {
    let fallback = env
        .new_string("")
        .map(|value| value.into_raw())
        .unwrap_or(std::ptr::null_mut());

    run_isolated_sandbox(
        move || {
            let payload: String = match env.get_string(&payload_str) {
                Ok(s) => s.into(),
                Err(_) => return new_jstring(&mut env, ""),
            };

            let leaks = credential_leak::scan_for_leaks(&payload);
            let alert_msg = if !leaks.is_empty() {
                format!(
                    "NATIVE THREAT INTEL ALERT: Plaintext sensitive leak detected ({})",
                    leaks.join(", ")
                )
            } else {
                String::new()
            };

            new_jstring(&mut env, &alert_msg)
        },
        fallback,
    )
}

fn new_jstring(env: &mut JNIEnv, value: &str) -> jstring {
    env.new_string(value)
        .map(|result| result.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

fn bounded_lossy_payload(payload: &[u8], max_len: usize) -> String {
    String::from_utf8_lossy(&payload[..payload.len().min(max_len)]).into_owned()
}

fn persist_packet_async(
    metadata: ParsedPacket,
    is_suspicious: bool,
    threat_tags: Vec<String>,
    entropy: f64,
    heuristic_class: String,
) {
    MULTITHREAD_POOL.execute(move || {
        let db = NATIVE_SQLITE
            .read()
            .ok()
            .and_then(|slot| slot.as_ref().map(Arc::clone));

        if let Some(db_engine) = db {
            let record = NativePacketRecord {
                timestamp: utc_now_ms(),
                source_ip: metadata.source_ip,
                dest_ip: metadata.dest_ip,
                protocol: metadata.protocol,
                source_port: metadata.source_port.map(|value| value as i32),
                dest_port: metadata.dest_port.map(|value| value as i32),
                length: metadata.total_length as i32,
                is_suspicious,
                threat_tags: threat_tags.join(","),
                entropy,
                heuristic_class,
            };
            let _ = db_engine.save_packet_record(&record);
        }
    });
}

fn utc_now_ms() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as i64
}
