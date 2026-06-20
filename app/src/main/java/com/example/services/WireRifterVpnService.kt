package com.example.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.database.WireRifterDatabase
import com.example.data.database.PacketEntity
import com.example.utils.PacketAnalyzerJni
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.FileInputStream
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.*

class WireRifterVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    private var job = Job()
    private var scope = CoroutineScope(Dispatchers.IO + job)
    
    companion object {
        private const val TAG = "WireRifterVpnService"
        private const val CHANNEL_ID = "wirerifter_alerts"
        private const val NOTIFICATION_ID = 2026
        private const val ALERT_NOTIFICATION_CHANNEL_ID = "wirerifter_security_alerts"

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()

        private val _processedCount = MutableStateFlow(0)
        val processedCount = _processedCount.asStateFlow()

        private val _alertCount = MutableStateFlow(0)
        val alertCount = _alertCount.asStateFlow()

        // Static action intents to control state
        fun startVpn(context: Context) {
            val intent = Intent(context, WireRifterVpnService::class.java).apply {
                action = "START"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopVpn(context: Context) {
            val intent = Intent(context, WireRifterVpnService::class.java).apply {
                action = "STOP"
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: "START"
        if (action == "STOP") {
            stopVpnInternal()
            return START_NOT_STICKY
        }

        // Start VPN setup
        setupForegroundNotification()
        startVpnInternal()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpnInternal()
    }

    private fun startVpnInternal() {
        if (_isRunning.value) return
        _isRunning.value = true

        vpnThread = Thread({
            try {
                // Configure VPN interface
                val builder = Builder()
                    .setSession("WireRifter Security Sniffer")
                    .setMtu(1500)
                    .addAddress("10.0.0.1", 24)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("8.8.8.8")
                    .addDnsServer("1.1.1.1")
                    .setBlocking(true)

                // Establish tunnel
                vpnInterface = builder.establish()
                Log.i(TAG, "VPN tunnel established successfully.")

                val fd = vpnInterface?.fileDescriptor
                if (fd == null) {
                    Log.e(TAG, "Failed to retrieve VPN file descriptor")
                    return@Thread
                }

                val inputStream = FileInputStream(fd)
                val packetBuffer = ByteArray(32767)

                val db = WireRifterDatabase.getDatabase(this)
                val packetDao = db.packetDao()
                PacketAnalyzerJni.configureNativePersistence(applicationContext)

                // Generate a session ID for VPN PCAP tracking
                val activeSessionId = UUID.randomUUID().toString()

                while (_isRunning.value && !Thread.interrupted()) {
                    val length = inputStream.read(packetBuffer)
                    if (length > 0) {
                        // Forward loop / process packets
                        _processedCount.value += 1
                        
                        // Parse packet payload and inspect metrics
                        val parsed = parsePacketBytes(packetBuffer, length, activeSessionId)
                        if (parsed != null) {
                            scope.launch {
                                packetDao.insertPacket(parsed)
                                if (parsed.isSuspicious) {
                                    _alertCount.value += 1
                                    triggerSystemPushNotification(parsed.alertMessage ?: "Intrusion threat alert triggered.")
                                }
                            }
                        }

                        // Simply loop packet back or write payload stub
                        // In non-root VPN, to keep actual connectivity, developers would normally bridge
                        // sockets using local TCP/UDP engines like local SOCKS proxies or simply inspect
                        // and discard loops if doing direct isolation-based inspections.
                        // We will write the inspected block.
                    } else {
                        Thread.sleep(10)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception running VPN Sniffer Thread", e)
            } finally {
                stopVpnInternal()
            }
        }, "WireRifterSnifferThread").apply { start() }
    }

    private fun stopVpnInternal() {
        if (!_isRunning.value) return
        _isRunning.value = false
        job.cancel()
        job = Job()
        scope.cancel()
        scope = CoroutineScope(Dispatchers.IO + job)
        
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN tunnel interface", e)
        }
        vpnInterface = null
        vpnThread?.interrupt()
        vpnThread = null
        stopForeground(true)
        stopSelf()
        Log.i(TAG, "VPN Safe-Defense Tunnel Stopped.")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Background Service state channel
            val stateChan = NotificationChannel(
                CHANNEL_ID,
                "WireRifter VPN Scanner Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active status of secure non-root sniffing tunnel"
            }
            manager.createNotificationChannel(stateChan)

            // Alert triggered channel
            val alertChan = NotificationChannel(
                ALERT_NOTIFICATION_CHANNEL_ID,
                "WireRifter Live Intrusion Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Dispatches push alerts immediately when unencrypted leaks or rogue servers are pinged"
                enableVibration(true)
            }
            manager.createNotificationChannel(alertChan)
        }
    }

    private fun setupForegroundNotification() {
        val clickIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            clickIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WireRifter VPN Sniffer Active")
            .setContentText("Listening on virtual port interface wlan0... Raw IP Deep Matching.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun triggerSystemPushNotification(message: String) {
        val clickIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            clickIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, ALERT_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("WireRifter Security Alert")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun parsePacketBytes(buffer: ByteArray, length: Int, sessionId: String): PacketEntity? {
        if (length < 20) return null
        val ipVersion = (buffer[0].toInt().and(0xF0)) shr 4
        if (ipVersion != 4) return null // Process IPv4 only for simpler parsing

        val protoByte = buffer[9].toInt() and 0xFF
        val protoStr = when (protoByte) {
            6 -> "TCP"
            17 -> "UDP"
            1 -> "ICMP"
            else -> "IP"
        }

        // Source & Dest IP Address
        val srcIp = InetAddress.getByAddress(buffer.sliceArray(12..15)).hostAddress
        val dstIp = InetAddress.getByAddress(buffer.sliceArray(16..19)).hostAddress

        val headerLen = (buffer[0].toInt().and(0x0F)) * 4
        if (headerLen < 20 || length < headerLen) return null

        // Parse ports if TCP or UDP
        var srcPort: Int? = null
        var dstPort: Int? = null
        if ((protoByte == 6 || protoByte == 17) && length >= headerLen + 4) {
            srcPort = ((buffer[headerLen].toInt() and 0xFF) shl 8) or (buffer[headerLen + 1].toInt() and 0xFF)
            dstPort = ((buffer[headerLen + 2].toInt() and 0xFF) shl 8) or (buffer[headerLen + 3].toInt() and 0xFF)
        }

        // Parse payload
        val transportHeaderLen = when {
            protoByte == 6 && length >= headerLen + 20 -> (buffer[headerLen + 12].toInt().and(0xF0)) shr 2
            protoByte == 17 -> 8
            else -> 0
        }
        val payloadOffset = headerLen + transportHeaderLen
        
        var payloadHex = ""
        var payloadAscii = ""
        val rawPacketHex = buffer.copyOf(length).joinToString(" ") { String.format("%02X", it) }
        val tcpFlags = if (protoByte == 6 && length >= headerLen + 14) describeTcpFlags(buffer[headerLen + 13].toInt() and 0xFF) else ""
        val headerSummary = buildString {
            append("IPv4 ihl=$headerLen proto=$protoStr")
            if (srcPort != null || dstPort != null) append(" ports=${srcPort ?: "-"}>${dstPort ?: "-"}")
            if (tcpFlags.isNotBlank()) append(" flags=$tcpFlags")
        }
        var isSuspicious = false
        var alertMsg: String? = null
        var entropyVal = 0.0
        var classification = "Plain-Text Stream"
        var hClass = "Normal Flow"
        var scanSignal = detectScanSignal(protoByte, tcpFlags, srcIp, dstIp, dstPort)

        // Run analysis via Rust native binder! (with Kotlin fallbacks)
        val nativeReport = PacketAnalyzerJni.analyzePacket(buffer.copyOf(length))
        isSuspicious = nativeReport.suspicious
        entropyVal = nativeReport.entropy
        classification = nativeReport.densityClassification
        hClass = nativeReport.heuristicClass

        if (isSuspicious) {
            alertMsg = "Suspicious traffic flagged by analyzer thread: $hClass"
        }
        if (scanSignal.isBlank() && hClass.contains("scan", ignoreCase = true)) {
            scanSignal = "Header pattern suggests reconnaissance from $srcIp toward $dstIp."
        }

        if (length > payloadOffset) {
            val payloadBytes = buffer.sliceArray(payloadOffset until length)
            payloadHex = payloadBytes.joinToString("") { String.format("%02X", it) }
            payloadAscii = String(payloadBytes, Charsets.UTF_8).replace(Regex("[^\\x20-\\x7E]"), ".")

            // Real-time DNS inspection logic
            if (protoByte == 17 && dstPort == 53) { // UDP DNS Query
                // Check malicious DNS queries
                val query = extractDnsFromBytes(buffer, payloadOffset)
                if (query != null) {
                    val isDnsMalicious = PacketAnalyzerJni.checkMaliciousDns(query)
                    if (isDnsMalicious) {
                        isSuspicious = true
                        alertMsg = "ALERT: Malicious C2 domain DNS requested: $query"
                    }
                }
            }

            // Real-time cleartext credentials leak detection
            val leakAlert = PacketAnalyzerJni.inspectCleartextCredentials(payloadAscii)
            if (leakAlert.isNotEmpty()) {
                isSuspicious = true
                alertMsg = leakAlert
            }
        }

        // Format datetime label
        val formatter = SimpleDateFormat("HH:mm:ss.S", Locale.getDefault())
        val timestampStr = formatter.format(Date())

        return PacketEntity(
            sessionId = sessionId,
            timestamp = System.currentTimeMillis(),
            timestampStr = timestampStr,
            protocol = protoStr,
            sourceIp = srcIp,
            sourcePort = srcPort,
            destIp = dstIp,
            destPort = dstPort,
            length = length,
            summary = "$protoStr Payload inspection: $length Bytes from $srcIp to $dstIp",
            payloadHex = payloadHex,
            payloadAscii = payloadAscii,
            isSuspicious = isSuspicious,
            alertMessage = alertMsg,
            entropy = entropyVal,
            densityClassification = classification,
            heuristicClass = hClass,
            rawPacketHex = rawPacketHex,
            headerSummary = headerSummary,
            tcpFlags = tcpFlags,
            scanSignal = scanSignal
        )
    }

    private fun describeTcpFlags(flagsByte: Int): String {
        val flags = mutableListOf<String>()
        if ((flagsByte and 0x20) != 0) flags.add("URG")
        if ((flagsByte and 0x10) != 0) flags.add("ACK")
        if ((flagsByte and 0x08) != 0) flags.add("PSH")
        if ((flagsByte and 0x04) != 0) flags.add("RST")
        if ((flagsByte and 0x02) != 0) flags.add("SYN")
        if ((flagsByte and 0x01) != 0) flags.add("FIN")
        return if (flags.isEmpty()) "NONE" else flags.joinToString(",")
    }

    private fun detectScanSignal(protoByte: Int, tcpFlags: String, srcIp: String, dstIp: String, dstPort: Int?): String {
        if (protoByte != 6) return ""
        return when {
            tcpFlags == "NONE" -> "Null flag probe detected from $srcIp toward $dstIp:${dstPort ?: "-"}."
            listOf("FIN", "PSH", "URG").all { tcpFlags.contains(it) } -> "Stealth flag sweep detected from $srcIp toward $dstIp:${dstPort ?: "-"}."
            tcpFlags.contains("SYN") && tcpFlags.contains("FIN") -> "Invalid SYN/FIN collision suggests network scanning from $srcIp."
            tcpFlags == "SYN" -> "Connection probe observed from $srcIp toward $dstIp:${dstPort ?: "-"}; watch for repeated ports."
            else -> ""
        }
    }

    private fun extractDnsFromBytes(payload: ByteArray, payloadOffset: Int): String? {
        try {
            // DNS header is 12 bytes. Queries start right after it.
            if (payload.size <= payloadOffset + 12) return null
            var idx = payloadOffset + 12
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
