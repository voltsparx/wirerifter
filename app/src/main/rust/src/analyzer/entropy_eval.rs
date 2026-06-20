/// Computes Shannon Entropy of a raw byte slice.
/// Returns a value between 0.0 (completely uniform/fixed structure) and 8.0 (completely random/highly encrypted).
pub fn calculate_shannon_entropy(data: &[u8]) -> f64 {
    if data.is_empty() {
        return 0.0;
    }
    
    let mut counts = [0usize; 256];
    for &b in data {
        counts[b as usize] += 1;
    }
    
    let len = data.len() as f64;
    let mut entropy = 0.0;
    
    for &count in counts.iter() {
        if count > 0 {
            let p = count as f64 / len;
            entropy -= p * p.log2();
        }
    }
    
    entropy
}

/// Classifies payload encryption or compression based on entropy signatures.
pub fn classify_payload_density(entropy: f64) -> &'static str {
    if entropy > 7.5 {
        "High Compression / TLS Cipher"
    } else if entropy > 5.0 {
        "Structured Binary Stream"
    } else if entropy > 2.0 {
        "Plain-Text / Standard Text Protocol (HTTP/SMTP)"
    } else {
        "Repetitive Padding / Null Blocks"
    }
}
