/// Deeply inspects an ASCII string from raw packet data for unencrypted security credentials.
pub fn scan_for_leaks(payload: &str) -> Vec<&'static str> {
    let mut leaks = Vec::new();
    let payload_lower = payload.to_lowercase();

    if payload_lower.contains("password=") || payload_lower.contains("passwd=") || payload_lower.contains("\"password\":") {
        leaks.push("CLEAR_PASSWORD");
    }
    if payload_lower.contains("apikey=") || payload_lower.contains("api_key=") || payload_lower.contains("api-key") {
        leaks.push("API_KEY_LEAK");
    }
    if payload_lower.contains("token=") || payload_lower.contains("bearer ") || payload_lower.contains("sessiontoken") {
        leaks.push("UNENCRYPTED_TOKEN");
    }
    if payload_lower.contains("authorization: basic") || payload_lower.contains("auth=") {
        leaks.push("PLAIN_AUTH_HEADER");
    }
    if payload_lower.contains("secret_key") || payload_lower.contains("aws_access_key") {
        leaks.push("ENVIRONMENT_SECRET_LEAK");
    }
    
    leaks
}
