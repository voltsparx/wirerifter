# Rust Engine

The Rust crate lives at `app/src/main/rust` and compiles as both:

- `cdylib` for Android JNI loading
- `rlib` for native unit tests

## Module Layout

```text
src/lib.rs
  JNI entry points, report assembly, async persistence dispatch

src/analyzer/
  credential_leak.rs  Cleartext secret and token checks
  dns_intel.rs        DNS threat pattern checks
  entropy_eval.rs     Shannon entropy and density classification
  heuristics.rs       Header anomaly checks
  packet_parser.rs    Bounded IPv4/IPv6/TCP/UDP/ICMP parsing
  scan_detector.rs    Scan-signal scoring

src/sniffer/
  fault_isolation.rs  Panic isolation boundary
  thread_pool.rs      Panic-safe background worker pool

src/db/
  sqlite_persistence.rs Native analyzer side log
```

## Native Report

`analyzeRawPacketPayload` returns JSON consumed by `PacketAnalyzerJni.kt`.

Important fields:

- `suspicious`
- `protocol`
- `source_ip`
- `dest_ip`
- `source_port`
- `dest_port`
- `entropy`
- `density_classification`
- `leaks_found`
- `anomaly_detected`
- `heuristic_class`
- `dns_query`
- `risk_score`
- `scan_signals`
- `parse_warnings`
- `flow_id`
- `tcp_flags`
- `ttl_or_hop_limit`

## Robustness Rules

The Rust side follows these rules:

- Never index packet bytes without a prior length check.
- Return parse warnings for malformed but inspectable frames.
- Keep payload string conversion bounded.
- Catch panics across JNI and worker jobs.
- Treat native persistence as best-effort.
- Preserve Kotlin fallback behavior if native loading fails.

## Parser Coverage

The parser supports:

- IPv4
- IPv6 without extension-header walking
- TCP ports, flags, and payload offset
- UDP ports, length warning, DNS query extraction
- ICMP and ICMPv6 classification
- unknown IP protocol fallback

## Scan Scoring

The scan detector produces a `risk_score` and `scan_signals` from packet traits:

- TCP null probes
- FIN/PSH/URG probes
- SYN/FIN collisions
- SYN packets with payload
- invalid RST/SYN state
- UDP empty probes
- UDP discovery-service touches
- ICMP reachability probes

Scores at or above 60 mark a packet as suspicious.
