use std::net::{Ipv4Addr, Ipv6Addr};

#[derive(Clone, Debug, PartialEq, Eq)]
pub enum IpVersion {
    Ipv4,
    Ipv6,
    Raw,
}

#[derive(Clone, Debug)]
pub struct ParsedPacket {
    pub ip_version: IpVersion,
    pub protocol: String,
    pub source_ip: String,
    pub dest_ip: String,
    pub source_port: Option<u16>,
    pub dest_port: Option<u16>,
    pub payload_offset: usize,
    pub total_length: usize,
    pub dns_query: Option<String>,
    pub tcp_flags: Option<u8>,
    pub ttl_or_hop_limit: Option<u8>,
    pub warnings: Vec<String>,
}

impl ParsedPacket {
    pub fn payload<'a>(&self, bytes: &'a [u8]) -> &'a [u8] {
        bytes.get(self.payload_offset..self.total_length.min(bytes.len()))
            .unwrap_or(&[])
    }

    pub fn flow_id(&self) -> String {
        format!(
            "{}:{}>{}:{}:{}",
            self.source_ip,
            self.source_port
                .map(|value| value.to_string())
                .unwrap_or_else(|| "-".to_string()),
            self.dest_ip,
            self.dest_port
                .map(|value| value.to_string())
                .unwrap_or_else(|| "-".to_string()),
            self.protocol
        )
    }

    pub fn tcp_flag_labels(&self) -> Option<String> {
        self.tcp_flags.map(format_tcp_flags)
    }
}

pub fn parse_packet(bytes: &[u8]) -> Option<ParsedPacket> {
    let first = *bytes.first()?;
    match first >> 4 {
        4 => parse_ipv4_packet(bytes),
        6 => parse_ipv6_packet(bytes),
        _ => None,
    }
}

fn parse_ipv4_packet(bytes: &[u8]) -> Option<ParsedPacket> {
    if bytes.len() < 20 {
        return None;
    }

    let mut warnings = Vec::new();
    let ip_header_len = ((bytes[0] & 0x0F) as usize) * 4;
    if ip_header_len < 20 || bytes.len() < ip_header_len {
        return None;
    }

    let declared_total = u16::from_be_bytes([bytes[2], bytes[3]]) as usize;
    let total_length = if declared_total == 0 {
        warnings.push("ipv4-zero-total-length".to_string());
        bytes.len()
    } else if declared_total > bytes.len() {
        warnings.push("ipv4-truncated-frame".to_string());
        bytes.len()
    } else if declared_total < ip_header_len {
        warnings.push("ipv4-invalid-total-length".to_string());
        ip_header_len
    } else {
        declared_total
    };

    let source_ip = Ipv4Addr::new(bytes[12], bytes[13], bytes[14], bytes[15]).to_string();
    let dest_ip = Ipv4Addr::new(bytes[16], bytes[17], bytes[18], bytes[19]).to_string();
    let ttl = Some(bytes[8]);

    parse_transport(
        bytes,
        IpVersion::Ipv4,
        ip_header_len,
        total_length,
        bytes[9],
        source_ip,
        dest_ip,
        ttl,
        warnings,
    )
}

fn parse_ipv6_packet(bytes: &[u8]) -> Option<ParsedPacket> {
    if bytes.len() < 40 {
        return None;
    }

    let mut warnings = Vec::new();
    let payload_len = u16::from_be_bytes([bytes[4], bytes[5]]) as usize;
    let declared_total = 40usize.saturating_add(payload_len);
    let total_length = if declared_total > bytes.len() {
        warnings.push("ipv6-truncated-frame".to_string());
        bytes.len()
    } else {
        declared_total
    };

    let source_ip = Ipv6Addr::from(slice_to_16(&bytes[8..24])?).to_string();
    let dest_ip = Ipv6Addr::from(slice_to_16(&bytes[24..40])?).to_string();
    let hop_limit = Some(bytes[7]);

    parse_transport(
        bytes,
        IpVersion::Ipv6,
        40,
        total_length,
        bytes[6],
        source_ip,
        dest_ip,
        hop_limit,
        warnings,
    )
}

fn parse_transport(
    bytes: &[u8],
    ip_version: IpVersion,
    network_header_len: usize,
    total_length: usize,
    protocol_id: u8,
    source_ip: String,
    dest_ip: String,
    ttl_or_hop_limit: Option<u8>,
    mut warnings: Vec<String>,
) -> Option<ParsedPacket> {
    let protocol = match protocol_id {
        1 => "ICMP",
        6 => "TCP",
        17 => "UDP",
        58 => "ICMPV6",
        _ => "IP",
    };

    if protocol == "TCP" {
        if total_length < network_header_len + 20 {
            warnings.push("tcp-short-header".to_string());
            return Some(base_packet(
                ip_version,
                protocol,
                source_ip,
                dest_ip,
                network_header_len,
                total_length,
                ttl_or_hop_limit,
                warnings,
            ));
        }

        let tcp_offset = network_header_len;
        let tcp_header_len = ((bytes[tcp_offset + 12] >> 4) as usize) * 4;
        if tcp_header_len < 20 || total_length < tcp_offset + tcp_header_len {
            warnings.push("tcp-invalid-header-length".to_string());
            return Some(base_packet(
                ip_version,
                protocol,
                source_ip,
                dest_ip,
                network_header_len,
                total_length,
                ttl_or_hop_limit,
                warnings,
            ));
        }

        return Some(ParsedPacket {
            ip_version,
            protocol: protocol.to_string(),
            source_ip,
            dest_ip,
            source_port: Some(u16::from_be_bytes([bytes[tcp_offset], bytes[tcp_offset + 1]])),
            dest_port: Some(u16::from_be_bytes([bytes[tcp_offset + 2], bytes[tcp_offset + 3]])),
            payload_offset: tcp_offset + tcp_header_len,
            total_length,
            dns_query: None,
            tcp_flags: Some(bytes[tcp_offset + 13]),
            ttl_or_hop_limit,
            warnings,
        });
    }

    if protocol == "UDP" {
        if total_length < network_header_len + 8 {
            warnings.push("udp-short-header".to_string());
            return Some(base_packet(
                ip_version,
                protocol,
                source_ip,
                dest_ip,
                network_header_len,
                total_length,
                ttl_or_hop_limit,
                warnings,
            ));
        }

        let udp_offset = network_header_len;
        let source_port = u16::from_be_bytes([bytes[udp_offset], bytes[udp_offset + 1]]);
        let dest_port = u16::from_be_bytes([bytes[udp_offset + 2], bytes[udp_offset + 3]]);
        let udp_len = u16::from_be_bytes([bytes[udp_offset + 4], bytes[udp_offset + 5]]) as usize;
        if udp_len < 8 {
            warnings.push("udp-invalid-length".to_string());
        } else if udp_offset + udp_len > total_length {
            warnings.push("udp-length-exceeds-packet".to_string());
        }

        let payload_offset = udp_offset + 8;
        let dns_query = if source_port == 53 || dest_port == 53 {
            extract_dns_query(bytes.get(payload_offset..total_length).unwrap_or(&[]))
        } else {
            None
        };

        return Some(ParsedPacket {
            ip_version,
            protocol: protocol.to_string(),
            source_ip,
            dest_ip,
            source_port: Some(source_port),
            dest_port: Some(dest_port),
            payload_offset,
            total_length,
            dns_query,
            tcp_flags: None,
            ttl_or_hop_limit,
            warnings,
        });
    }

    Some(base_packet(
        ip_version,
        protocol,
        source_ip,
        dest_ip,
        network_header_len,
        total_length,
        ttl_or_hop_limit,
        warnings,
    ))
}

fn base_packet(
    ip_version: IpVersion,
    protocol: &str,
    source_ip: String,
    dest_ip: String,
    payload_offset: usize,
    total_length: usize,
    ttl_or_hop_limit: Option<u8>,
    warnings: Vec<String>,
) -> ParsedPacket {
    ParsedPacket {
        ip_version,
        protocol: protocol.to_string(),
        source_ip,
        dest_ip,
        source_port: None,
        dest_port: None,
        payload_offset,
        total_length,
        dns_query: None,
        tcp_flags: None,
        ttl_or_hop_limit,
        warnings,
    }
}

pub fn format_tcp_flags(flags: u8) -> String {
    let mut labels = Vec::new();
    if flags & 0x20 != 0 {
        labels.push("URG");
    }
    if flags & 0x10 != 0 {
        labels.push("ACK");
    }
    if flags & 0x08 != 0 {
        labels.push("PSH");
    }
    if flags & 0x04 != 0 {
        labels.push("RST");
    }
    if flags & 0x02 != 0 {
        labels.push("SYN");
    }
    if flags & 0x01 != 0 {
        labels.push("FIN");
    }
    if labels.is_empty() {
        "NONE".to_string()
    } else {
        labels.join(",")
    }
}

fn extract_dns_query(payload: &[u8]) -> Option<String> {
    if payload.len() <= 12 {
        return None;
    }

    let mut labels = Vec::new();
    let mut idx = 12usize;
    let mut jumps = 0usize;
    while idx < payload.len() && jumps < 32 {
        jumps += 1;
        let len = payload[idx] as usize;
        if len == 0 {
            break;
        }
        if len & 0xC0 != 0 || idx + len + 1 > payload.len() || len > 63 {
            return None;
        }
        let label = std::str::from_utf8(&payload[idx + 1..idx + 1 + len]).ok()?;
        if !label
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || byte == b'-' || byte == b'_')
        {
            return None;
        }
        labels.push(label.to_string());
        idx += len + 1;
    }

    if labels.is_empty() {
        None
    } else {
        Some(labels.join("."))
    }
}

fn slice_to_16(bytes: &[u8]) -> Option<[u8; 16]> {
    if bytes.len() != 16 {
        return None;
    }
    let mut out = [0u8; 16];
    out.copy_from_slice(bytes);
    Some(out)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_ipv4_tcp_syn() {
        let packet = [
            0x45, 0x00, 0x00, 0x28, 0x12, 0x34, 0x40, 0x00, 64, 6, 0, 0, 192, 168, 1, 10, 192,
            168, 1, 1, 0xC0, 0x01, 0x00, 0x50, 0, 0, 0, 1, 0, 0, 0, 0, 0x50, 0x02, 0x72, 0x10,
            0, 0, 0, 0,
        ];
        let parsed = parse_packet(&packet).expect("packet parses");
        assert_eq!(parsed.protocol, "TCP");
        assert_eq!(parsed.source_port, Some(49153));
        assert_eq!(parsed.dest_port, Some(80));
        assert_eq!(parsed.tcp_flag_labels().as_deref(), Some("SYN"));
    }

    #[test]
    fn rejects_short_buffers() {
        assert!(parse_packet(&[0x45, 0, 0]).is_none());
    }
}
