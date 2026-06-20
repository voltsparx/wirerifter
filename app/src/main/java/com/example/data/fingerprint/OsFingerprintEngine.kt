package com.example.data.fingerprint

import com.example.data.database.DeviceEntity

data class OsFingerprint(
    val family: String,
    val version: String,
    val deviceType: String,
    val label: String,
    val ttlSignature: String,
    val traits: String,
    val confidence: Int,
    val careState: String
)

object OsFingerprintEngine {
    private val profiles = listOf(
        Profile("Android", "14-16", "Smartphone Agent", listOf("android", "samsung", "pixel", "oneplus"), 64, "Linux mobile TCP stack; mDNS and QUIC common"),
        Profile("iOS", "17-19", "Mobile Device", listOf("iphone", "ipad", "apple mobile"), 64, "Darwin mobile stack; mDNS, AirDrop, APNS common"),
        Profile("macOS", "14-15", "Desktop Computer", listOf("macbook", "imac", "apple"), 64, "Darwin desktop stack; mDNS, SMB, AirPlay common"),
        Profile("Windows", "10-11", "Desktop Computer", listOf("windows", "microsoft", "surface", "hp", "dell", "lenovo"), 128, "Windows TCP defaults; LLMNR, SMB, NetBIOS common"),
        Profile("Ubuntu Linux", "22.04-24.04 LTS", "Host OS Server", listOf("ubuntu", "canonical"), 64, "Linux server stack; SSH, mDNS optional"),
        Profile("Debian Linux", "11-13", "Host OS Server", listOf("debian"), 64, "Linux server stack; conservative service profile"),
        Profile("Fedora Linux", "39-42", "Workstation", listOf("fedora", "red hat"), 64, "Linux workstation stack; mDNS and SSH common"),
        Profile("Kali Linux", "2023-2026", "Security Workstation", listOf("kali", "offensive", "probe"), 64, "Linux stack with bursty SYN patterns and tooling signatures"),
        Profile("OpenWrt", "22-24", "Router Gateway", listOf("openwrt", "lede"), 64, "Embedded Linux router; DNS, DHCP, NAT services"),
        Profile("Cisco IOS", "15.x / XE 17.x", "Router Gateway", listOf("cisco"), 255, "Network appliance TTL; routing control-plane behavior"),
        Profile("MikroTik RouterOS", "7.x", "Router Gateway", listOf("mikrotik", "routeros"), 64, "RouterOS services; Winbox and DHCP common"),
        Profile("Synology DSM", "7.x", "Storage Server (NAS)", listOf("synology", "nas", "dsm"), 64, "NAS services; SMB, AFP, NFS, web admin common"),
        Profile("QNAP QTS", "5.x", "Storage Server (NAS)", listOf("qnap", "qts"), 64, "NAS services; SMB, NFS, media services common"),
        Profile("FreeBSD", "13-14", "Server", listOf("freebsd", "pfsense", "opnsense"), 64, "BSD stack; firewall/router appliance signatures"),
        Profile("HP Printer Firmware", "FutureSmart 4-5", "Network Printer", listOf("hp", "officejet", "laserjet"), 64, "Printer service stack; IPP, mDNS, JetDirect common"),
        Profile("Canon Printer Firmware", "4.x", "Network Printer", listOf("canon", "pixma"), 64, "Printer service stack; IPP and discovery broadcasts"),
        Profile("ESPHome", "2024-2026", "Smart Automation Node", listOf("esphome", "esp8266", "esp32"), 128, "Microcontroller stack; mDNS and MQTT/Home Assistant common"),
        Profile("FreeRTOS", "10.x", "Smart IoT Sensor", listOf("freertos", "iot", "thermostat"), 128, "RTOS micro-stack; low-volume telemetry bursts"),
        Profile("Linux Embedded", "4.x-6.x", "Embedded Appliance", listOf("camera", "nvr", "tv", "chromecast"), 64, "Embedded Linux; UPnP, mDNS, vendor cloud beacons"),
    )

    fun infer(
        hostname: String,
        vendor: String,
        ttl: Int,
        openSignals: List<String> = emptyList(),
        suspiciousBurst: Boolean = false
    ): OsFingerprint {
        val haystack = (listOf(hostname, vendor) + openSignals).joinToString(" ").lowercase()
        val best = profiles
            .map { profile ->
                val keywordScore = profile.keywords.count { haystack.contains(it) } * 28
                val ttlScore = when {
                    ttl == profile.expectedTtl -> 28
                    kotlin.math.abs(ttl - profile.expectedTtl) <= 2 -> 18
                    ttl == 0 -> 0
                    else -> -8
                }
                profile to (keywordScore + ttlScore)
            }
            .maxByOrNull { it.second }

        val profile = best?.first
        val score = best?.second ?: 0
        val confidence = (45 + score).coerceIn(20, 98)
        val careState = when {
            suspiciousBurst -> "Stress: scanning signs"
            confidence < 45 -> "Observe gently"
            ttl >= 250 -> "Core network organ"
            else -> "Stable"
        }

        return if (profile != null && score > 0) {
            OsFingerprint(
                family = profile.family,
                version = profile.version,
                deviceType = profile.deviceType,
                label = "${profile.family} ${profile.version}",
                ttlSignature = "TTL=$ttl (${profile.family} passive metric)",
                traits = profile.traits,
                confidence = confidence,
                careState = careState
            )
        } else {
            val family = when {
                ttl >= 250 -> "Network Appliance"
                ttl >= 120 -> "Windows or Embedded RTOS"
                ttl in 50..70 -> "Unix-like Stack"
                else -> "Unknown Stack"
            }
            OsFingerprint(
                family = family,
                version = "Unresolved",
                deviceType = "Unclassified Network Node",
                label = "$family - version unresolved",
                ttlSignature = "TTL=$ttl (passive metric)",
                traits = "insufficient passive evidence; continue observing low-rate traffic",
                confidence = confidence,
                careState = careState
            )
        }
    }

    fun enrich(device: DeviceEntity, ttl: Int, openSignals: List<String> = emptyList(), suspiciousBurst: Boolean = false): DeviceEntity {
        val fp = infer(device.hostname, device.vendor, ttl, openSignals, suspiciousBurst)
        return device.copy(
            deviceType = fp.deviceType,
            osFingerprint = fp.label,
            ttlFingerprint = fp.ttlSignature,
            osFamily = fp.family,
            osVersion = fp.version,
            fingerprintConfidence = fp.confidence,
            stackTraits = fp.traits,
            careState = fp.careState
        )
    }

    private data class Profile(
        val family: String,
        val version: String,
        val deviceType: String,
        val keywords: List<String>,
        val expectedTtl: Int,
        val traits: String
    )
}
