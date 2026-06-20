use std::collections::HashSet;
use once_cell::sync::Lazy;

// High performance threat-intel list compiled into the native engine
static MALICIOUS_PATTERNS: Lazy<HashSet<&'static str>> = Lazy::new(|| {
    let mut s = HashSet::new();
    s.insert("malware");
    s.insert("c2-server");
    s.insert("leak-detector");
    s.insert("suspicious");
    s.insert("onion");
    s.insert("tor-exit");
    s.insert("unknown.ru");
    s.insert("ransomware");
    s.insert("phish-alert");
    s.insert("cryptominer");
    s.insert("coinhive");
    s.insert("miner.pool");
    s.insert("reverse-shell");
    s.insert("exfiltrate");
    s.insert("trojan");
    s
});

/// Evaluates if a given DNS query matches threat-intelligence signatures.
pub fn evaluate_dns_threat(query: &str) -> bool {
    let query_lower = query.to_lowercase();
    
    // Check direct pattern matching
    for pattern in MALICIOUS_PATTERNS.iter() {
        if query_lower.contains(pattern) {
            return true;
        }
    }
    
    // Double check specific top-level domain anomalies
    if query_lower.ends_with(".ru") || query_lower.ends_with(".su") || query_lower.ends_with(".cc") {
        // Flags high entropy subdomains on dynamic DNS providers often abused by attackers
        if query_lower.len() > 25 {
            return true;
        }
    }
    
    false
}
