package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "packet_logs")
data class PacketEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val timestamp: Long,
    val timestampStr: String,
    val protocol: String,
    val sourceIp: String,
    val sourcePort: Int?,
    val destIp: String,
    val destPort: Int?,
    val length: Int,
    val summary: String,
    val payloadHex: String,
    val payloadAscii: String,
    val rawPacketHex: String = "",
    val headerSummary: String = "",
    val tcpFlags: String = "",
    val isSuspicious: Boolean,
    val alertMessage: String?,
    val entropy: Double = 0.0,
    val densityClassification: String = "Plain-Text Stream",
    val heuristicClass: String = "Normal Flow",
    val scanSignal: String = ""
)

@Entity(tableName = "network_devices")
data class DeviceEntity(
    @PrimaryKey val macAddress: String,
    val ipAddress: String,
    val hostname: String,
    val vendor: String,
    val isTrusted: Boolean, // User declared trust: true=Trusted, false=untrusted (Rogue warning state if not trusted)
    val isGateway: Boolean = false,
    val lastSeen: Long,
    val deviceType: String = "Generic IoT Core",
    val osFingerprint: String = "Unknown Net-Stack OS",
    val ttlFingerprint: String = "TTL=64 (Passive Metric)",
    val trustScore: Int = 80,
    val osFamily: String = "Unknown",
    val osVersion: String = "Unknown",
    val fingerprintConfidence: Int = 35,
    val stackTraits: String = "passive-observation",
    val careState: String = "Stable"
)

@Entity(tableName = "ids_rules")
data class IdsRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val protocolFilter: String, // "ALL", "TCP", "UDP", "DNS", "HTTP"
    val regexPattern: String,
    val severity: String, // "LOW", "MEDIUM", "HIGH"
    val isEnabled: Boolean = true
)

@Entity(tableName = "saved_sessions")
data class SessionEntity(
    @PrimaryKey val sessionId: String,
    val title: String,
    val timestamp: Long,
    val packetCount: Int,
    val averageSpeed: Double, // in MB/s
    val alertCount: Int
)
