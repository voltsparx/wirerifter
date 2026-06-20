use super::packet_parser::{format_tcp_flags, ParsedPacket};

#[derive(Clone, Debug)]
pub struct ScanAssessment {
    pub suspicious: bool,
    pub score: u8,
    pub signals: Vec<String>,
}

impl ScanAssessment {
    pub fn clean() -> Self {
        Self {
            suspicious: false,
            score: 0,
            signals: Vec::new(),
        }
    }
}

pub fn assess_scan_behavior(packet: &ParsedPacket, payload_len: usize) -> ScanAssessment {
    let mut score: u16 = 0;
    let mut signals = Vec::new();

    if packet.protocol == "TCP" {
        if let Some(flags) = packet.tcp_flags {
            let syn = flags & 0x02 != 0;
            let fin = flags & 0x01 != 0;
            let psh = flags & 0x08 != 0;
            let urg = flags & 0x20 != 0;
            let ack = flags & 0x10 != 0;
            let rst = flags & 0x04 != 0;

            if flags == 0 {
                score += 85;
                signals.push("tcp-null-probe".to_string());
            }
            if fin && psh && urg {
                score += 90;
                signals.push("tcp-fin-psh-urg-probe".to_string());
            }
            if syn && fin {
                score += 95;
                signals.push("tcp-syn-fin-collision".to_string());
            }
            if syn && !ack && payload_len > 0 {
                score += 45;
                signals.push("tcp-syn-with-payload".to_string());
            }
            if rst && syn {
                score += 50;
                signals.push("tcp-rst-syn-invalid-state".to_string());
            }

            if signals.is_empty() && syn && !ack {
                score += 15;
                signals.push(format!("tcp-service-probe-flags={}", format_tcp_flags(flags)));
            }
        }
    }

    if packet.protocol == "UDP" {
        if matches!(packet.dest_port, Some(53 | 67 | 68 | 123 | 137 | 161 | 1900 | 5353)) {
            score += 20;
            signals.push("udp-discovery-service-touch".to_string());
        }
        if payload_len == 0 {
            score += 35;
            signals.push("udp-empty-probe".to_string());
        }
    }

    if packet.protocol == "ICMP" || packet.protocol == "ICMPV6" {
        score += 10;
        signals.push("icmp-reachability-probe".to_string());
    }

    let score = score.min(100) as u8;
    ScanAssessment {
        suspicious: score >= 60,
        score,
        signals,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::analyzer::packet_parser::{IpVersion, ParsedPacket};

    fn tcp_packet(flags: u8) -> ParsedPacket {
        ParsedPacket {
            ip_version: IpVersion::Ipv4,
            protocol: "TCP".to_string(),
            source_ip: "10.0.0.2".to_string(),
            dest_ip: "10.0.0.3".to_string(),
            source_port: Some(44444),
            dest_port: Some(80),
            payload_offset: 40,
            total_length: 40,
            dns_query: None,
            tcp_flags: Some(flags),
            ttl_or_hop_limit: Some(64),
            warnings: Vec::new(),
        }
    }

    #[test]
    fn detects_null_probe() {
        let assessment = assess_scan_behavior(&tcp_packet(0), 0);
        assert!(assessment.suspicious);
        assert!(assessment.signals.iter().any(|value| value == "tcp-null-probe"));
    }
}
