pub mod analyzer;
pub mod db;
pub mod sniffer;

use analyzer::credential_leak;
use analyzer::dns_intel;
use analyzer::entropy_eval;
use analyzer::heuristics;
use db::sqlite_persistence::{NativePacketRecord, SQLitePersistenceEngine};
use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jboolean, jstring};
use jni::JNIEnv;
use once_cell::sync::Lazy;
use serde::Serialize;
use sniffer::fault_isolation::run_isolated_sandbox;
use sniffer::thread_pool::ThreadPool;
use std::net::Ipv4Addr;
use std::sync::{Arc, RwLock};

static MULTITHREAD_POOL: Lazy<Arc<ThreadPool>> = Lazy::new(|| Arc::new(ThreadPool::new(4)));
static NATIVE_SQLITE: Lazy<RwLock<Option<Arc<SQLitePersistenceEngine>>>> =
    Lazy::new(|| RwLock::new(None));

#[derive(Clone, Debug)]
struct PacketMetadata {
    protocol: String,
    source_ip: String,
    dest_ip: String,
    source_port: Option<u16>,
    dest_port: Option<u16>,
    payload_offset: usize,
    total_length: usize,
    dns_query: Option<String>,
}

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

            let metadata = match parse_ipv4_packet(&bytes) {
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
                    };
                    let json = serde_json::to_string(&report).unwrap_or_else(|_| default_err.to_string());
                    return new_jstring(&mut env, &json);
                }
            };

            let payload = bytes
                .get(metadata.payload_offset..metadata.total_length.min(bytes.len()))
                .unwrap_or(&[]);
            let entropy_val = entropy_eval::calculate_shannon_entropy(payload);
            let density = entropy_eval::classify_payload_density(entropy_val).to_string();
            let payload_str = String::from_utf8_lossy(payload).into_owned();
            let leaks = credential_leak::scan_for_leaks(&payload_str)
                .iter()
                .map(|&value| value.to_string())
                .collect::<Vec<_>>();
            let heuristics_report = heuristics::audit_packet_heuristics(&bytes, &metadata.protocol);
            let dns_suspicious = metadata
                .dns_query
                .as_ref()
                .map(|query| dns_intel::evaluate_dns_threat(query))
                .unwrap_or(false);
            let is_suspicious =
                !leaks.is_empty() || heuristics_report.anomaly_detected || dns_suspicious;

            let mut threat_tags = leaks.clone();
            if heuristics_report.anomaly_detected {
                threat_tags.push("HEURISTIC_ANOMALY".to_string());
            }
            if dns_suspicious {
                threat_tags.push("MALICIOUS_DNS".to_string());
            }

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

fn persist_packet_async(
    metadata: PacketMetadata,
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

fn parse_ipv4_packet(bytes: &[u8]) -> Option<PacketMetadata> {
    if bytes.len() < 20 || (bytes[0] >> 4) != 4 {
        return None;
    }

    let ip_header_len = ((bytes[0] & 0x0F) as usize) * 4;
    if ip_header_len < 20 || bytes.len() < ip_header_len {
        return None;
    }

    let total_length = u16::from_be_bytes([bytes[2], bytes[3]]) as usize;
    let total_length = total_length.clamp(ip_header_len, bytes.len());
    let source_ip = Ipv4Addr::new(bytes[12], bytes[13], bytes[14], bytes[15]).to_string();
    let dest_ip = Ipv4Addr::new(bytes[16], bytes[17], bytes[18], bytes[19]).to_string();

    match bytes[9] {
        6 => parse_transport_packet(bytes, ip_header_len, total_length, source_ip, dest_ip, "TCP"),
        17 => parse_transport_packet(bytes, ip_header_len, total_length, source_ip, dest_ip, "UDP"),
        1 => Some(PacketMetadata {
            protocol: "ICMP".to_string(),
            source_ip,
            dest_ip,
            source_port: None,
            dest_port: None,
            payload_offset: ip_header_len,
            total_length,
            dns_query: None,
        }),
        _ => Some(PacketMetadata {
            protocol: "IP".to_string(),
            source_ip,
            dest_ip,
            source_port: None,
            dest_port: None,
            payload_offset: ip_header_len,
            total_length,
            dns_query: None,
        }),
    }
}

fn parse_transport_packet(
    bytes: &[u8],
    ip_header_len: usize,
    total_length: usize,
    source_ip: String,
    dest_ip: String,
    protocol: &str,
) -> Option<PacketMetadata> {
    let header_len = if protocol == "TCP" {
        if total_length < ip_header_len + 20 {
            return None;
        }
        ((bytes[ip_header_len + 12] >> 4) as usize) * 4
    } else {
        if total_length < ip_header_len + 8 {
            return None;
        }
        8
    };

    if (protocol == "TCP" && header_len < 20)
        || (protocol == "UDP" && header_len != 8)
        || total_length < ip_header_len + header_len
    {
        return None;
    }

    let source_port = u16::from_be_bytes([bytes[ip_header_len], bytes[ip_header_len + 1]]);
    let dest_port = u16::from_be_bytes([bytes[ip_header_len + 2], bytes[ip_header_len + 3]]);
    let payload_offset = ip_header_len + header_len;
    let dns_query = if protocol == "UDP" && (source_port == 53 || dest_port == 53) {
        extract_dns_query(bytes.get(payload_offset..total_length).unwrap_or(&[]))
    } else {
        None
    };

    Some(PacketMetadata {
        protocol: protocol.to_string(),
        source_ip,
        dest_ip,
        source_port: Some(source_port),
        dest_port: Some(dest_port),
        payload_offset,
        total_length,
        dns_query,
    })
}

fn extract_dns_query(payload: &[u8]) -> Option<String> {
    if payload.len() <= 12 {
        return None;
    }

    let mut labels = Vec::new();
    let mut idx = 12usize;
    while idx < payload.len() {
        let len = payload[idx] as usize;
        if len == 0 {
            break;
        }
        if len & 0xC0 != 0 || idx + len + 1 > payload.len() {
            return None;
        }
        let label = std::str::from_utf8(&payload[idx + 1..idx + 1 + len]).ok()?;
        labels.push(label.to_string());
        idx += len + 1;
    }

    if labels.is_empty() {
        None
    } else {
        Some(labels.join("."))
    }
}

fn utc_now_ms() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as i64
}
