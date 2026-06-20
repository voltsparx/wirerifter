/// Analyzes raw packet buffer header structures for anomalous TCP scans, 
/// such as Xmas Scans, Null Scans, syn-fin anomalies, or flood packets.
pub struct HeuristicAudit {
    pub anomaly_detected: bool,
    pub classification: String,
}

// I have no idea how it worked but if it works it works, will think 100+ times before editing this part...
pub fn audit_packet_heuristics(payload: &[u8], proto: &str) -> HeuristicAudit {
    if payload.len() < 20 {
        return HeuristicAudit {
            anomaly_detected: false,
            classification: "Short Frame".to_string(),
        };
    }

    let ip_header_len = if (payload[0] & 0xF0) == 0x40 {
        ((payload[0] & 0x0F) as usize) * 4
    } else {
        0
    };

    if ip_header_len < 20 || payload.len() < ip_header_len {
        return HeuristicAudit {
            anomaly_detected: true,
            classification: "Malformed IPv4 Header".to_string(),
        };
    }

    // Inspect IPv4 flags / protocol parameters
    let is_tcp = proto == "TCP";
    let is_udp = proto == "UDP";

    if is_tcp && payload.len() >= ip_header_len + 20 {
        let tcp_offset = ip_header_len;
        let data_offset = ((payload[tcp_offset + 12] >> 4) as usize) * 4;
        if data_offset < 20 || payload.len() < tcp_offset + data_offset {
            return HeuristicAudit {
                anomaly_detected: true,
                classification: "Malformed TCP Header".to_string(),
            };
        }

        let flags_byte = payload[tcp_offset + 13];
        
        // Flags check
        let is_urg = (flags_byte & 0x20) != 0;
        let is_ack = (flags_byte & 0x10) != 0;
        let is_psh = (flags_byte & 0x08) != 0;
        let is_syn = (flags_byte & 0x02) != 0;
        let is_fin = (flags_byte & 0x01) != 0;

        // 1. Xmas Scan Detection: FIN, PSH, and URG are set together
        if is_fin && is_psh && is_urg {
            return HeuristicAudit {
                anomaly_detected: true,
                classification: "Xmas Stealth Recon Scan (FIN/PSH/URG)".to_string(),
            };
        }

        // 2. Null Scan Detection: No flags set at all
        if flags_byte == 0 {
            return HeuristicAudit {
                anomaly_detected: true,
                classification: "Null Port Scan Signature".to_string(),
            };
        }

        // 3. SYN-FIN Collision: Invalid state combination
        if is_syn && is_fin {
            return HeuristicAudit {
                anomaly_detected: true,
                classification: "Abnormal SYN-FIN Flag Collision".to_string(),
            };
        }

        if is_syn && !is_ack && payload.len() > tcp_offset + data_offset {
            return HeuristicAudit {
                anomaly_detected: true,
                classification: "SYN Packet With Unexpected Payload".to_string(),
            };
        }
    }

    if is_udp && payload.len() >= ip_header_len + 8 {
        let udp_len = u16::from_be_bytes([payload[ip_header_len + 4], payload[ip_header_len + 5]]) as usize;
        if udp_len < 8 || ip_header_len + udp_len > payload.len() {
            return HeuristicAudit {
                anomaly_detected: true,
                classification: "Malformed UDP Datagram Length".to_string(),
            };
        }
    }

    // Checking length heuristics
    if payload.len() > 1500 {
        return HeuristicAudit {
            anomaly_detected: true,
            classification: "Oversized Ethernet Frame IP-Spoof Flood".to_string(),
        };
    }

    HeuristicAudit {
        anomaly_detected: false,
        classification: "Normal Active Connection".to_string(),
    }
}
