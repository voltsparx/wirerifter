package com.example.utils

import android.content.Context
import android.util.Log
import org.json.JSONObject
import kotlin.math.log2

object PacketAnalyzerJni {
    private const val TAG = "PacketAnalyzerJni"
    var isNativeLibraryLoaded = false
        private set

    init {
        try {
            System.loadLibrary("packet_analyzer")
            isNativeLibraryLoaded = true
            Log.i(TAG, "Rust native JNI library packet_analyzer loaded successfully.")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Rust JNI packet_analyzer not found. Falling back to high-fidelity Kotlin analyzer engine.")
            isNativeLibraryLoaded = false
        }
    }

    // --- Native declaration matching Rust functions ---
    private external fun setNativeDatabasePath(dbPath: String): Boolean

    private external fun analyzeRawPacketPayload(payload: ByteArray): String

    private external fun checkMaliciousDnsNative(dnsQuery: String): Boolean

    private external fun inspectCleartextCredentialsNative(payload: String): String

    // --- Public accessors with graceful Kotlin fallbacks ---

    fun configureNativePersistence(context: Context): Boolean {
        if (!isNativeLibraryLoaded) return false
        return try {
            val dbPath = context.applicationContext
                .getDatabasePath("wirerifter_native.db")
                .absolutePath
            setNativeDatabasePath(dbPath)
        } catch (t: Throwable) {
            Log.e(TAG, "Could not configure Rust persistence database", t)
            false
        }
    }

    fun analyzePacket(packet: ByteArray): PacketAnalysisResult {
        if (isNativeLibraryLoaded) {
            try {
                val rawJson = analyzeRawPacketPayload(packet)
                return parseSimpleJson(rawJson)
            } catch (t: Throwable) {
                Log.e(TAG, "Error matching native analyzeRawPacketPayload, using fallback", t)
            }
        }
        return kotlinAnalyzeRawPacketPayload(packet)
    }

    fun checkMaliciousDns(dnsQuery: String): Boolean {
        if (isNativeLibraryLoaded) {
            try {
                return checkMaliciousDnsNative(dnsQuery)
            } catch (t: Throwable) {
                Log.e(TAG, "Error matching native checkMaliciousDns, using fallback", t)
            }
        }
        return kotlinCheckMaliciousDns(dnsQuery)
    }

    fun inspectCleartextCredentials(payload: String): String {
        if (isNativeLibraryLoaded) {
            try {
                return inspectCleartextCredentialsNative(payload)
            } catch (t: Throwable) {
                Log.e(TAG, "Error matching native inspectCleartextCredentials, using fallback", t)
            }
        }
        return kotlinInspectCleartextCredentials(payload)
    }

    // --- High-Fidelity Data Architecture matching Rust ---

    data class PacketAnalysisResult(
        val suspicious: Boolean,
        val protocol: String,
        val flags: List<String> = emptyList(),
        val entropy: Double = 0.0,
        val densityClassification: String = "Plain-Text Stream",
        val anomalyDetected: Boolean = false,
        val heuristicClass: String = "Normal State",
        val riskScore: Int = 0,
        val scanSignals: List<String> = emptyList(),
        val parseWarnings: List<String> = emptyList(),
        val flowId: String = "",
        val tcpFlags: String? = null,
        val rawJson: String = ""
    )

    private fun parseSimpleJson(json: String): PacketAnalysisResult {
        return try {
            val obj = JSONObject(json)
            val susp = obj.optBoolean("suspicious", false)
            val proto = obj.optString("protocol", "UNKNOWN")
            val entr = obj.optDouble("entropy", 0.0)
            val dense = obj.optString("density_classification", "Plain-Text Stream")
            val anomaly = obj.optBoolean("anomaly_detected", false)
            val heurClass = obj.optString("heuristic_class", "Normal State")
            val riskScore = obj.optInt("risk_score", if (susp) 75 else 0)
            val flowId = obj.optString("flow_id", "")
            val tcpFlags = obj.optString("tcp_flags", "").ifBlank { null }
            
            val leaksArr = obj.optJSONArray("leaks_found")
            val flags = mutableListOf<String>()
            if (leaksArr != null) {
                for (i in 0 until leaksArr.length()) {
                    flags.add(leaksArr.getString(i))
                }
            }
            if (anomaly) {
                flags.add("HEURISTIC_ANOMALY")
            }
            val scanSignals = readJsonStringArray(obj, "scan_signals")
            val parseWarnings = readJsonStringArray(obj, "parse_warnings")
            flags.addAll(scanSignals)

            PacketAnalysisResult(
                suspicious = susp,
                protocol = proto,
                flags = flags,
                entropy = entr,
                densityClassification = dense,
                anomalyDetected = anomaly,
                heuristicClass = heurClass,
                riskScore = riskScore,
                scanSignals = scanSignals,
                parseWarnings = parseWarnings,
                flowId = flowId,
                tcpFlags = tcpFlags,
                rawJson = json
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding Rust output JSON, returning safe fallback parse style", e)
            val susp = json.contains("\"suspicious\":true") || json.contains("\"suspicious\": true")
            val proto = if (json.contains("HTTP")) "HTTP" else if (json.contains("DNS")) "DNS" else "RAW"
            PacketAnalysisResult(suspicious = susp, protocol = proto, rawJson = json)
        }
    }

    private fun readJsonStringArray(obj: JSONObject, name: String): List<String> {
        val array = obj.optJSONArray(name) ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                array.optString(i).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun kotlinAnalyzeRawPacketPayload(payload: ByteArray): PacketAnalysisResult {
        if (payload.size < 20) {
            return PacketAnalysisResult(suspicious = false, protocol = "RAW", heuristicClass = "Short Frame Fragment")
        }

        val isIpv4 = (payload[0].toInt().and(0xF0)) == 0x40
        val protocol = if (isIpv4) {
            when (payload[9].toInt()) {
                6 -> "TCP"
                17 -> "UDP"
                1 -> "ICMP"
                else -> "IP"
            }
        } else {
            "IPV6"
        }

        // 1. Calculate Shannon Entropy in Kotlin Mirror Architecture
        val entropyVal = calculateKotlinEntropy(payload)
        val densityClass = when {
            entropyVal > 7.5 -> "High Compression / TLS Cipher"
            entropyVal > 5.0 -> "Structured Binary Stream"
            entropyVal > 2.0 -> "Plain-Text / Standard Text Protocol (HTTP/SMTP)"
            else -> "Repetitive Padding / Null Blocks"
        }

        // 2. Cleartext Password / Secrets audit
        var isSusp = false
        val flags = mutableListOf<String>()
        var payloadText = ""
        
        if (payload.size > 20) {
            payloadText = String(payload, 20, payload.size - 20, Charsets.UTF_8)
                .replace(Regex("[^\\x20-\\x7E]"), ".")
            val leakAlert = kotlinInspectCleartextCredentials(payloadText)
            if (leakAlert.isNotEmpty()) {
                isSusp = true
                flags.add("CREDS_LEAK")
            }

            // Simple DNS check if UDP destination port 53
            val isUdp = protocol == "UDP"
            val destPort = if (isUdp && payload.size >= 24) {
                ((payload[22].toInt() and 0xFF) shl 8) or (payload[23].toInt() and 0xFF)
            } else 0

            if (destPort == 53) {
                val query = extractDnsQuery(payload)
                if (query != null && kotlinCheckMaliciousDns(query)) {
                    isSusp = true
                    flags.add("MALICIOUS_DNS")
                }
            }
        }

        // 3. Scan for Heuristics and Invalid TCP Flag states (Null scan / Xmas Scan)
        var hasAnomaly = false
        var hClass = "Normal Active Connection"

        if (protocol == "TCP" && payload.size >= 40) {
            // TCP Flags byte is at offset 33 of raw packet (under 20-byte IP header)
            val flagsByte = payload[33].toInt() and 0xFF
            val isUrg = (flagsByte and 0x20) != 0
            val isPsh = (flagsByte and 0x08) != 0
            val isSyn = (flagsByte and 0x02) != 0
            val isFin = (flagsByte and 0x01) != 0

            if (isFin && isPsh && isUrg) {
                hasAnomaly = true
                isSusp = true
                hClass = "Xmas Stealth Recon Scan (FIN/PSH/URG)"
                flags.add("HEURISTIC_ANOMALY")
            } else if (flagsByte == 0) {
                hasAnomaly = true
                isSusp = true
                hClass = "Null Port Scan Signature"
                flags.add("HEURISTIC_ANOMALY")
            } else if (isSyn && isFin) {
                hasAnomaly = true
                isSusp = true
                hClass = "Abnormal SYN-FIN Flag Collision"
                flags.add("HEURISTIC_ANOMALY")
            }
        }

        if (payload.size > 1500) {
            hasAnomaly = true
            isSusp = true
            hClass = "Oversized Ethernet Frame IP-Spoof Flood"
            flags.add("HEURISTIC_ANOMALY")
        }

        val fallbackJson = """
            {
               "suspicious": $isSusp,
               "protocol": "$protocol",
               "entropy": $entropyVal,
               "density_classification": "$densityClass",
               "anomaly_detected": $hasAnomaly,
               "heuristic_class": "$hClass",
               "risk_score": ${if (isSusp) 75 else 0},
               "scan_signals": [],
               "parse_warnings": [],
               "length": ${payload.size}
            }
        """.trimIndent()

        return PacketAnalysisResult(
            suspicious = isSusp,
            protocol = protocol,
            flags = flags,
            entropy = entropyVal,
            densityClassification = densityClass,
            anomalyDetected = hasAnomaly,
            heuristicClass = hClass,
            riskScore = if (isSusp) 75 else 0,
            rawJson = fallbackJson
        )
    }

    private fun calculateKotlinEntropy(data: ByteArray): Double {
        if (data.isEmpty()) return 0.0
        val counts = IntArray(256)
        for (b in data) {
            counts[b.toInt() and 0xFF]++
        }
        val len = data.size.toDouble()
        var entropy = 0.0
        for (count in counts) {
            if (count > 0) {
                val p = count.toDouble() / len
                entropy -= p * log2(p)
            }
        }
        return entropy
    }

    private fun kotlinCheckMaliciousDns(dnsQuery: String): Boolean {
        val queryLower = dnsQuery.lowercase()
        return queryLower.contains("malware") ||
               queryLower.contains("c2-server") ||
               queryLower.contains("leak-detector") ||
               queryLower.contains("suspicious") ||
               queryLower.contains("onion") ||
               queryLower.contains("unknown.ru") ||
               queryLower.contains("ransomware") ||
               queryLower.contains("cryptominer")
    }

    private fun kotlinInspectCleartextCredentials(payload: String): String {
        val payloadLower = payload.lowercase()
        val mFlags = mutableListOf<String>()
        if (payloadLower.contains("password=") || payloadLower.contains("passwd=") || payloadLower.contains("\"password\":")) {
            mFlags.add("CLEAR_PASSWORD")
        }
        if (payloadLower.contains("apikey=") || payloadLower.contains("api_key=") || payloadLower.contains("api-key")) {
            mFlags.add("API_KEY_LEAK")
        }
        if (payloadLower.contains("token=") || payloadLower.contains("bearer ")) {
            mFlags.add("UNENCRYPTED_TOKEN")
        }
        if (payloadLower.contains("authorization: basic")) {
            mFlags.add("PLAIN_AUTH_HEADER")
        }
        if (mFlags.isNotEmpty()) {
            return "PCAP SECURITY ALERT: Plaintext sensitive payload detected (${mFlags.joinToString(", ")})"
        }
        return ""
    }

    private fun extractDnsQuery(payload: ByteArray): String? {
        try {
            if (payload.size <= 40) return null
            var idx = 40
            val sb = java.lang.StringBuilder()
            while (idx < payload.size) {
                val len = payload[idx].toInt() and 0xFF
                if (len == 0) break
                if (idx + len + 1 > payload.size) break
                if (sb.isNotEmpty()) sb.append(".")
                sb.append(String(payload, idx + 1, len, Charsets.UTF_8))
                idx += len + 1
            }
            return if (sb.isNotEmpty()) sb.toString() else null
        } catch (e: Exception) {
            return null
        }
    }
}
