package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.*
import com.example.data.repository.NetworkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

enum class AppearanceMode {
    SYSTEM,
    DARK,
    LIGHT
}

enum class TerminalLineKind {
    COMMAND,
    OUTPUT,
    ERROR,
    SUCCESS,
    WARNING,
    INFO
}

data class TerminalLine(
    val kind: TerminalLineKind,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class CaptureInterfaceOption(
    val id: String,
    val name: String,
    val type: String,
    val ipAddress: String,
    val status: String,
    val activity: List<Int>,
    val selected: Boolean = false
)

class WireRifterViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = NetworkRepository(application)

    // --- Flows from Database ---
    val allDevices = repository.allDevicesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allRules = repository.allRulesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val savedSessions = repository.allSessionsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentSavedPackets = repository.recentPacketsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- UI/Runtime State Streams ---
    private val _currentSessionId = MutableStateFlow(UUID.randomUUID().toString())
    val currentSessionId: StateFlow<String> = _currentSessionId.asStateFlow()

    private val _isCaptureActive = MutableStateFlow(false)
    val isCaptureActive: StateFlow<Boolean> = _isCaptureActive.asStateFlow()

    private val _showInterfaceSelection = MutableStateFlow(true)
    val showInterfaceSelection: StateFlow<Boolean> = _showInterfaceSelection.asStateFlow()

    private val _captureInterfaces = MutableStateFlow(
        listOf(
            CaptureInterfaceOption("wlan0", "wlan0", "Wi-Fi", "192.168.1.105", "Ready", listOf(2, 5, 3, 8, 12, 9, 6, 11), selected = true),
            CaptureInterfaceOption("rmnet0", "rmnet0", "Mobile Data", "10.84.22.7", "Standby", listOf(1, 1, 2, 1, 3, 2, 2, 1)),
            CaptureInterfaceOption("tun0", "tun0", "VPN", "10.8.0.2", "Available", listOf(0, 0, 2, 4, 2, 0, 1, 3)),
            CaptureInterfaceOption("lo", "lo", "Loopback", "127.0.0.1", "Local", listOf(1, 0, 1, 0, 1, 0, 1, 0)),
            CaptureInterfaceOption("ap0", "ap0", "Hotspot", "192.168.43.1", "Dormant", listOf(0, 0, 0, 1, 0, 0, 1, 0))
        )
    )
    val captureInterfaces: StateFlow<List<CaptureInterfaceOption>> = _captureInterfaces.asStateFlow()

    private val _isDeviceScanning = MutableStateFlow(false)
    val isDeviceScanning: StateFlow<Boolean> = _isDeviceScanning.asStateFlow()

    // Current live sniffing packet feed in memory (shows on Monitor tab)
    private val _livePacketFeed = MutableStateFlow<List<PacketEntity>>(emptyList())
    val livePacketFeed: StateFlow<List<PacketEntity>> = _livePacketFeed.asStateFlow()

    private val _selectedPacketForAudit = MutableStateFlow<PacketEntity?>(null)
    val selectedPacketForAudit: StateFlow<PacketEntity?> = _selectedPacketForAudit.asStateFlow()

    // Keeps track of the live counters
    private val _activeDeviceCount = MutableStateFlow(14)
    val activeDeviceCount: StateFlow<Int> = _activeDeviceCount.asStateFlow()

    private val _securityAlertCount = MutableStateFlow(3)
    val securityAlertCount: StateFlow<Int> = _securityAlertCount.asStateFlow()

    // Speed indicator in MB/s
    private val _liveNetworkSpeed = MutableStateFlow(0.0)
    val liveNetworkSpeed: StateFlow<Double> = _liveNetworkSpeed.asStateFlow()

    // Local analysis states
    private val _isLocalAnalysisRunning = MutableStateFlow(false)
    val isLocalAnalysisRunning: StateFlow<Boolean> = _isLocalAnalysisRunning.asStateFlow()

    private val _packetAnalysisResult = MutableStateFlow<String?>(null)
    val packetAnalysisResult: StateFlow<String?> = _packetAnalysisResult.asStateFlow()

    private val _overallAuditResult = MutableStateFlow<String?>(null)
    val overallAuditResult: StateFlow<String?> = _overallAuditResult.asStateFlow()

    private val _pcapExportStatus = MutableStateFlow<String?>(null)
    val pcapExportStatus: StateFlow<String?> = _pcapExportStatus.asStateFlow()

    private val _isAuditRunning = MutableStateFlow(false)
    val isAuditRunning: StateFlow<Boolean> = _isAuditRunning.asStateFlow()

    private val _appearanceMode = MutableStateFlow(AppearanceMode.SYSTEM)
    val appearanceMode: StateFlow<AppearanceMode> = _appearanceMode.asStateFlow()

    private val _terminalInput = MutableStateFlow("")
    val terminalInput: StateFlow<String> = _terminalInput.asStateFlow()

    private val _terminalLines = MutableStateFlow(
        listOf(
            TerminalLine(
                TerminalLineKind.INFO,
                "WireRifter local terminal ready. Type help for available commands."
            )
        )
    )
    val terminalLines: StateFlow<List<TerminalLine>> = _terminalLines.asStateFlow()

    // Subnet wifi details
    val wifiSsid = MutableStateFlow("WireRifter_AP")
    val wifiLinkSpeed = MutableStateFlow(433)
    val localIpAddress = MutableStateFlow("192.168.1.105")
    val gatewayIpAddress = MutableStateFlow("192.168.1.1")

    private var snifferJob: Job? = null
    private var lastAlertNotificationAt = 0L

    private val _powerSaverMode = MutableStateFlow(true)
    val powerSaverMode: StateFlow<Boolean> = _powerSaverMode.asStateFlow()

    private val _deepPacketInspection = MutableStateFlow(true)
    val deepPacketInspection: StateFlow<Boolean> = _deepPacketInspection.asStateFlow()

    private val _deviceCareMode = MutableStateFlow(true)
    val deviceCareMode: StateFlow<Boolean> = _deviceCareMode.asStateFlow()

    private val _scanSensitivity = MutableStateFlow(70f)
    val scanSensitivity: StateFlow<Float> = _scanSensitivity.asStateFlow()

    private val _packetRetentionLimit = MutableStateFlow(250f)
    val packetRetentionLimit: StateFlow<Float> = _packetRetentionLimit.asStateFlow()

    private val _alertCooldownSeconds = MutableStateFlow(12f)
    val alertCooldownSeconds: StateFlow<Float> = _alertCooldownSeconds.asStateFlow()

    init {
        viewModelScope.launch {
            // Load Wi-Fi parameters dynamically on startup
            wifiSsid.value = repository.getWifiSsid()
            wifiLinkSpeed.value = repository.getWifiLinkSpeed()
            localIpAddress.value = repository.lookupLocalIpAddress()
            gatewayIpAddress.value = repository.getGatewayIpAddress()

            // Initialize default intrusion rules if none exist in Room
            repository.initializeDefaultRules()

            // Pre-seed some devices so the list doesn't start completely empty
            repository.performSubnetDiscoveryScan()
            
            // Sync live devices count to dashboard
            allDevices.collect { devList ->
                if (devList.isNotEmpty()) {
                    _activeDeviceCount.value = devList.size
                }
            }
        }
    }

    // --- Action Methods ---

    fun toggleCaptureInterface(id: String) {
        _captureInterfaces.value = _captureInterfaces.value.map { item ->
            if (item.id == id) item.copy(selected = !item.selected) else item
        }
    }

    fun requestInterfaceSelection() {
        if (_isCaptureActive.value) return
        _showInterfaceSelection.value = true
    }

    fun startCaptureFromSelectedInterfaces() {
        val current = _captureInterfaces.value
        if (current.none { it.selected }) {
            _captureInterfaces.value = current.mapIndexed { index, item ->
                if (index == 0) item.copy(selected = true) else item
            }
        }
        _showInterfaceSelection.value = false
        startSnifferCapture()
    }

    fun startSnifferCapture() {
        if (_isCaptureActive.value) return
        
        // Reset list and set fresh session UUID
        _livePacketFeed.value = emptyList()
        _currentSessionId.value = UUID.randomUUID().toString()
        _isCaptureActive.value = true
        _liveNetworkSpeed.value = Random.nextDouble(1.1, 5.8)

        // Start non-root VpnService safely on Android 9+
        try {
            com.example.services.WireRifterVpnService.startVpn(getApplication())
        } catch (e: Exception) {
            Log.e("WireRifterViewModel", "Could not request VpnService launch", e)
        }

        snifferJob = viewModelScope.launch(Dispatchers.Default) {
            val dateTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
            
            while (_isCaptureActive.value) {
                val delayRange = if (_powerSaverMode.value) 900L..2400L else 250L..1100L
                delay(Random.nextLong(delayRange.first, delayRange.last))
                
                // Update bandwidth fluctuation
                _liveNetworkSpeed.value = if (Random.nextBoolean()) {
                    Math.max(0.2, _liveNetworkSpeed.value + Random.nextDouble(-0.8, 0.8))
                } else {
                    _liveNetworkSpeed.value
                }

                // Fabricate simulated network packet details
                val simulatedPacket = generateSimulatedPacket(
                    _currentSessionId.value,
                    dateTimeFormat.format(Date())
                )

                // Inspect packet through Room rules
                val inspectedPacket = applyIdsRulesCheck(simulatedPacket)

                // Update Feed (limit memory view to top 100 on screen, prepending latest)
                val updatedList = (listOf(inspectedPacket) + _livePacketFeed.value).take(_packetRetentionLimit.value.toInt())
                _livePacketFeed.value = updatedList

                // If packet matches an intrusion design:
                if (inspectedPacket.isSuspicious) {
                    _securityAlertCount.value = _securityAlertCount.value + 1
                    // Auto-persist hazardous/flagged packet to database session logs!
                    repository.insertPacket(inspectedPacket)

                    // Configure real push notification immediately for the threat event!
                    maybeTriggerPushNotification(inspectedPacket.alertMessage ?: "Intrusion threat matched signature rules.")
                } else if (Random.nextInt(10) < 2) {
                    // Occasionally auto-persist normal packets to demonstrate session archiving
                    repository.insertPacket(inspectedPacket)
                }
            }
        }
    }

    fun stopSnifferCapture() {
        if (!_isCaptureActive.value) return
        _isCaptureActive.value = false
        _liveNetworkSpeed.value = 0.0
        snifferJob?.cancel()
        snifferJob = null

        // Stop non-root VPN safely
        try {
            com.example.services.WireRifterVpnService.stopVpn(getApplication())
        } catch (e: Exception) {
            Log.e("WireRifterViewModel", "Could not request VpnService shutdown", e)
        }

        // Save entire session summary in Database
        viewModelScope.launch {
            val capCount = _livePacketFeed.value.size
            if (capCount > 0) {
                val alertsNum = _livePacketFeed.value.count { it.isSuspicious }
                val sessionSummary = SessionEntity(
                    sessionId = _currentSessionId.value,
                    title = "Audit - ${wifiSsid.value}",
                    timestamp = System.currentTimeMillis(),
                    packetCount = capCount,
                    averageSpeed = Random.nextDouble(1.5, 4.5),
                    alertCount = alertsNum
                )
                repository.saveSession(sessionSummary)
            }
        }
    }

    fun triggerPushNotification(title: String, message: String) {
        val context = getApplication<Application>()
        val channelId = "wirerifter_security_alerts"
        val manager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val chan = android.app.NotificationChannel(
                channelId,
                "WireRifter Live Intrusion Alerts",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Dispatches push alerts immediately when leaks are matched"
                enableVibration(true)
            }
            manager.createNotificationChannel(chan)
        }

        val clickIntent = android.content.Intent(context, com.example.MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            0,
            clickIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun maybeTriggerPushNotification(message: String) {
        val now = System.currentTimeMillis()
        val cooldownMs = (_alertCooldownSeconds.value * 1000).toLong()
        if (now - lastAlertNotificationAt >= cooldownMs) {
            lastAlertNotificationAt = now
            triggerPushNotification("WireRifter Security Alert", message)
        }
    }

    fun setPowerSaverMode(enabled: Boolean) {
        _powerSaverMode.value = enabled
    }

    fun setDeepPacketInspection(enabled: Boolean) {
        _deepPacketInspection.value = enabled
    }

    fun setDeviceCareMode(enabled: Boolean) {
        _deviceCareMode.value = enabled
    }

    fun setScanSensitivity(value: Float) {
        _scanSensitivity.value = value.coerceIn(10f, 100f)
    }

    fun setPacketRetentionLimit(value: Float) {
        _packetRetentionLimit.value = value.coerceIn(50f, 500f)
    }

    fun setAlertCooldownSeconds(value: Float) {
        _alertCooldownSeconds.value = value.coerceIn(0f, 60f)
    }

    fun setAppearanceMode(mode: AppearanceMode) {
        _appearanceMode.value = mode
    }

    fun onTerminalInputChange(text: String) {
        _terminalInput.value = text.take(180)
    }

    fun submitTerminalCommand() {
        val command = _terminalInput.value.trim()
        if (command.isBlank()) return
        _terminalInput.value = ""
        appendTerminal(TerminalLineKind.COMMAND, "> $command")
        viewModelScope.launch(Dispatchers.IO) {
            runTerminalCommand(command)
        }
    }

    private fun appendTerminal(kind: TerminalLineKind, text: String) {
        _terminalLines.value = (_terminalLines.value + TerminalLine(kind, text)).takeLast(140)
    }

    private suspend fun runTerminalCommand(raw: String) {
        val parts = raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val command = parts.firstOrNull()?.lowercase(Locale.US) ?: return

        try {
            when (command) {
                "help", "?" -> appendTerminal(
                    TerminalLineKind.OUTPUT,
                    "Commands: help, clear, ipconfig, ifconfig, ping <host>, dns <host>, arp, scan.net, subnet <cidr>, whois <ip>, netstat"
                )

                "clear" -> _terminalLines.value = listOf(
                    TerminalLine(TerminalLineKind.INFO, "Terminal cleared.")
                )

                "ipconfig", "ifconfig" -> appendTerminal(
                    TerminalLineKind.OUTPUT,
                    buildString {
                        appendLine("SSID: ${wifiSsid.value}")
                        appendLine("Local address: ${localIpAddress.value}")
                        appendLine("Gateway: ${gatewayIpAddress.value}")
                        appendLine("Link speed: ${wifiLinkSpeed.value} Mbps")
                        appendLine("Capture active: ${if (_isCaptureActive.value) "yes" else "no"}")
                    }.trimEnd()
                )

                "ping" -> {
                    val host = parts.getOrNull(1)
                    if (host.isNullOrBlank()) {
                        appendTerminal(TerminalLineKind.ERROR, "Usage: ping <host>")
                    } else {
                        val started = System.nanoTime()
                        val address = InetAddress.getByName(host)
                        val reachable = address.isReachable(1500)
                        val elapsedMs = (System.nanoTime() - started) / 1_000_000
                        appendTerminal(
                            if (reachable) TerminalLineKind.SUCCESS else TerminalLineKind.WARNING,
                            "${address.hostAddress} ${if (reachable) "reachable" else "no reply"} in ${elapsedMs}ms"
                        )
                    }
                }

                "dns", "resolve" -> {
                    val host = parts.getOrNull(1)
                    if (host.isNullOrBlank()) {
                        appendTerminal(TerminalLineKind.ERROR, "Usage: dns <host>")
                    } else {
                        val addresses = InetAddress.getAllByName(host).joinToString("\n") {
                            "${it.hostName} -> ${it.hostAddress}"
                        }
                        appendTerminal(TerminalLineKind.SUCCESS, addresses)
                    }
                }

                "arp" -> {
                    val devices = allDevices.value
                    if (devices.isEmpty()) {
                        appendTerminal(TerminalLineKind.WARNING, "No devices in the local inventory yet.")
                    } else {
                        appendTerminal(
                            TerminalLineKind.OUTPUT,
                            devices.joinToString("\n") {
                                "${it.ipAddress.padEnd(15)} ${it.macAddress.padEnd(17)} ${it.hostname} ${it.osFamily} ${it.osVersion}"
                            }
                        )
                    }
                }

                "scan.net" -> {
                    if (_isDeviceScanning.value) {
                        appendTerminal(TerminalLineKind.WARNING, "Subnet discovery is already running.")
                        return
                    }
                    _isDeviceScanning.value = true
                    appendTerminal(TerminalLineKind.INFO, "Starting paced local subnet discovery.")
                    try {
                        val found = repository.performSubnetDiscoveryScan()
                        _activeDeviceCount.value = found.size
                        appendTerminal(
                            TerminalLineKind.SUCCESS,
                            "Discovered ${found.size} local nodes.\n" + found.take(10).joinToString("\n") {
                                "${it.ipAddress} ${it.hostname} ${it.osFingerprint} confidence=${it.fingerprintConfidence}%"
                            }
                        )
                    } finally {
                        _isDeviceScanning.value = false
                    }
                }

                "subnet" -> {
                    val cidr = parts.getOrNull(1)
                    if (cidr.isNullOrBlank()) {
                        appendTerminal(TerminalLineKind.ERROR, "Usage: subnet <ipv4/prefix>")
                    } else {
                        appendTerminal(TerminalLineKind.OUTPUT, describeCidr(cidr))
                    }
                }

                "whois" -> {
                    val ip = parts.getOrNull(1)
                    if (ip.isNullOrBlank()) {
                        appendTerminal(TerminalLineKind.ERROR, "Usage: whois <ip>")
                    } else {
                        appendTerminal(TerminalLineKind.OUTPUT, describeAddressClass(ip))
                    }
                }

                "netstat" -> appendTerminal(
                    TerminalLineKind.INFO,
                    "Android app sandboxes do not expose the kernel socket table here. Use Monitor packets and saved captures for flow inspection."
                )

                else -> appendTerminal(TerminalLineKind.ERROR, "Unknown command: $command")
            }
        } catch (e: Exception) {
            appendTerminal(TerminalLineKind.ERROR, e.message ?: "Command failed.")
        }
    }

    fun triggerDeviceSubnetDiscovery() {
        if (_isDeviceScanning.value) return
        _isDeviceScanning.value = true
        viewModelScope.launch {
            try {
                val found = repository.performSubnetDiscoveryScan()
                _activeDeviceCount.value = found.size
            } catch (e: Exception) {
                Log.e("WireRifterVM", "Error performing subnet scan", e)
            } finally {
                _isDeviceScanning.value = false
            }
        }
    }

    fun toggleDeviceTrust(mac: String, currentTrust: Boolean) {
        viewModelScope.launch {
            repository.updateDeviceTrust(mac, !currentTrust)
        }
    }

    fun addNewIdsRule(name: String, protocol: String, regex: String, severity: String) {
        viewModelScope.launch {
            val newRule = IdsRuleEntity(
                name = name,
                protocolFilter = protocol,
                regexPattern = regex,
                severity = severity,
                isEnabled = true
            )
            repository.insertRule(newRule)
        }
    }

    fun deleteIdsRule(rule: IdsRuleEntity) {
        viewModelScope.launch {
            repository.deleteRule(rule)
        }
    }

    fun selectPacketForInspector(packet: PacketEntity?) {
        _selectedPacketForAudit.value = packet
        _packetAnalysisResult.value = null // reset prior analysis text
    }

    // --- Local Forensics ---

    fun runLocalPacketForensics(packet: PacketEntity) {
        _isLocalAnalysisRunning.value = true
        _packetAnalysisResult.value = null
        viewModelScope.launch {
            try {
                delay(180)
                _packetAnalysisResult.value = buildPacketForensicsReport(packet)
            } catch (e: Exception) {
                _packetAnalysisResult.value = "Local analysis error: ${e.message ?: "unable to parse packet"}"
            } finally {
                _isLocalAnalysisRunning.value = false
            }
        }
    }

    fun runSubnetSecurityAudit() {
        if (_isAuditRunning.value) return
        _isAuditRunning.value = true
        _overallAuditResult.value = null
        viewModelScope.launch {
            try {
                delay(220)
                val totalDevices = _activeDeviceCount.value
                val untrustedDevs = allDevices.value.count { !it.isTrusted }
                val currentAlerts = _securityAlertCount.value
                val customRulesString = allRules.value.joinToString(", ") { "${it.name} (${it.protocolFilter})" }
                val sampleSuspicious = _livePacketFeed.value
                    .filter { it.isSuspicious }
                    .take(3)
                    .map { "[${it.protocol}] ${it.sourceIp} -> ${it.destIp}: ${it.alertMessage}" }

                _overallAuditResult.value = buildString {
                    appendLine("WireRifter Local Audit Report")
                    appendLine()
                    appendLine("Network: ${wifiSsid.value}")
                    appendLine("Gateway: ${gatewayIpAddress.value}")
                    appendLine("Devices observed: $totalDevices")
                    appendLine("Untrusted devices: $untrustedDevs")
                    appendLine("Current alerts: $currentAlerts")
                    appendLine("Active rules: $customRulesString")
                    appendLine()
                    appendLine("Reading:")
                    appendLine(
                        when {
                            currentAlerts > 5 -> "High activity: review alerting packets and scan-sense entries first."
                            untrustedDevs > 0 -> "Moderate activity: one or more devices need owner validation and fingerprint confirmation."
                            else -> "Stable activity: no urgent signs in current capture state."
                        }
                    )
                    if (sampleSuspicious.isNotEmpty()) {
                        appendLine()
                        appendLine("Recent signals:")
                        sampleSuspicious.forEach { appendLine("- $it") }
                    }
                }
            } catch (e: Exception) {
                _overallAuditResult.value = "Failed to compile local audit: ${e.message ?: "unknown parser error"}"
            } finally {
                _isAuditRunning.value = false
            }
        }
    }

    private fun buildPacketForensicsReport(packet: PacketEntity): String {
        val payloadPreview = packet.payloadAscii.take(280).ifBlank { "No decoded payload bytes available." }
        val riskLine = when {
            packet.scanSignal.isNotBlank() -> "Scan-sense signal: ${packet.scanSignal}"
            packet.isSuspicious -> "Suspicious: ${packet.alertMessage ?: packet.heuristicClass}"
            packet.entropy > 7.2 -> "High entropy payload: likely compressed or encrypted content."
            packet.protocol == "DNS" -> "DNS traffic: inspect queried names and resolver direction."
            else -> "No immediate high-risk signal in this frame."
        }
        return """
            Local Packet Forensics

            Flow: ${packet.sourceIp}:${packet.sourcePort ?: "-"} -> ${packet.destIp}:${packet.destPort ?: "-"}
            Protocol: ${packet.protocol}
            Length: ${packet.length} bytes
            Header: ${packet.headerSummary.ifBlank { "header summary unavailable" }}
            Flags: ${packet.tcpFlags.ifBlank { "none" }}
            Entropy: ${String.format("%.4f", packet.entropy)} (${packet.densityClassification})
            Heuristic: ${packet.heuristicClass}
            Risk: $riskLine

            Decoded payload preview:
            $payloadPreview
        """.trimIndent()
    }

    fun clearVaultWorkspace() {
        viewModelScope.launch {
            repository.clearAllData()
            _livePacketFeed.value = emptyList()
            _securityAlertCount.value = 0
            _pcapExportStatus.value = null
            // Reload seed
            repository.performSubnetDiscoveryScan()
        }
    }

    fun deleteSavedSession(session: SessionEntity) {
        viewModelScope.launch {
            repository.deleteSession(session)
        }
    }

    fun exportAllPacketsAsPcap() {
        viewModelScope.launch {
            try {
                val file = repository.exportAllPacketsToPcap()
                _pcapExportStatus.value = "Saved PCAP: ${file.absolutePath}"
            } catch (e: Exception) {
                _pcapExportStatus.value = "PCAP export failed: ${e.message ?: "unknown error"}"
            }
        }
    }

    fun exportSessionAsPcap(session: SessionEntity) {
        viewModelScope.launch {
            try {
                val file = repository.exportSessionToPcap(session)
                _pcapExportStatus.value = "Saved PCAP: ${file.absolutePath}"
            } catch (e: Exception) {
                _pcapExportStatus.value = "PCAP export failed: ${e.message ?: "unknown error"}"
            }
        }
    }

    // --- Packet simulator logic ---

    private fun generateSimulatedPacket(sessId: String, timeStr: String): PacketEntity {
        val gateway = gatewayIpAddress.value
        val local = localIpAddress.value
        val gatewaySubnet = gateway.substringBeforeLast(".")

        // Pick protocol
        val protocolsList = listOf("TCP", "UDP", "DNS", "HTTP", "TLS", "QUIC", "ARP")
        val prot = protocolsList[Random.nextInt(protocolsList.size)]

        var src = local
        var dest = "8.8.8.8"
        var srcPort: Int? = Random.nextInt(32768, 65535)
        var destPort: Int? = 443
        var summary = "Encrypted TLS payload sync"
        var hex = "16 03 01 02 00 01 00 01 fc 03 03 b2 db 82 ec c6 b0..."
        var ascii = ".....TLS_RECORD_PROTOCOL_CLIENT_HELLO_CLIENT_KEY_EXCHANGE....."
        var isSuspicious = false
        var alertMessage: String? = null
        var tcpFlags = ""
        var scanSignal = ""
        var heuristicClass = "Normal Flow"

        when (prot) {
            "ARP" -> {
                srcPort = null
                destPort = null
                src = "70:3E:AC:8D:1F:19"
                dest = "FF:FF:FF:FF:FF:FF"
                summary = "ARP Request: Who has $gateway? Tell $local."
                hex = "ff ff ff ff ff ff 70 3e ac 8d 1f 19 08 06 00 01..."
                ascii = "ARP_DISCOVER_PROBE_REQUEST_WHO_HAS_${gateway}_TELL_${local}"
            }
            "DNS" -> {
                dest = "1.1.1.1"
                destPort = 53
                val maliciousDnsPool = listOf(
                    "api.unknown.ru",
                    "free-malware-scan.cc",
                    "botnet-command-center.xyz",
                    "untraceable-hacks.info"
                )
                val benignDnsPool = listOf(
                    "google.com",
                    "github.com",
                    "androidx.compose.io",
                    "openai.com",
                    "medium.com"
                )
                
                // 15% chance to construct a malicious DNS query
                val queryHost = if (Random.nextInt(100) < 15) {
                    maliciousDnsPool[Random.nextInt(maliciousDnsPool.size)]
                } else {
                    benignDnsPool[Random.nextInt(benignDnsPool.size)]
                }
                summary = "Standard query: A $queryHost"
                hex = "c1 a2 01 00 00 01 00 00 00 00 00 00 ${queryHost.length.toString(16)}..."
                ascii = "DNS_QUERY;CLASS=IN;TYPE=A;NAME=$queryHost"
            }
            "HTTP" -> {
                destPort = 80
                val httpPathPool = listOf(
                    "GET /index.html HTTP/1.1",
                    "POST /api/v1/auth/login HTTP/1.1",
                    "GET /assets/config.json HTTP/1.1",
                    "POST /submit-form HTTP/1.1"
                )
                val chosenPath = httpPathPool[Random.nextInt(httpPathPool.size)]
                dest = "$gatewaySubnet.${Random.nextInt(2, 254)}"
                
                if (chosenPath.contains("login") || chosenPath.contains("submit")) {
                    // Sensitive plain-text leak!
                    val username = listOf("admin", "sysop", "root", "dev_security").random()
                    val password = "PassWord_" + Random.nextInt(1000, 9999) + "_critical!"
                    summary = "$chosenPath (UNENCRYPTED BODY)"
                    ascii = "HTTP/1.1 POST /api/v1/auth/login\r\nContent-Type: application/x-www-form-urlencoded\r\nContent-Length: 54\r\n\r\nusername=$username&password=$password"
                    hex = ascii.encodeToByteArray().joinToString(" ") { String.format("%02x", it) }
                } else {
                    summary = "$chosenPath"
                    ascii = "HTTP/1.1 GET /assets/config.json\r\nHost: $dest\r\nAccept: */*\r\nUser-Agent: WireRifter_Agent_v1.0"
                    hex = ascii.encodeToByteArray().joinToString(" ") { String.format("%02x", it) }
                }
            }
            "UDP" -> {
                dest = "239.255.255.250"
                destPort = 1900
                summary = "SSDP Discovery M-SEARCH"
                hex = "4d 2d 53 45 41 52 43 48 20 2a 20 48 54 54 50 2f..."
                ascii = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: ssdp:all"
            }
            "QUIC" -> {
                dest = "142.250.72.110"
                destPort = 443
                summary = "gQUIC Packet: CID=ae348f9024c1bfed"
                hex = "0c ae 34 8f 90 24 c1 bf ed 00 00 01 02..."
                ascii = "gQUIC_CONNECTION_ESTABLISHMENT_MUTUAL_CIPHER_SUITE"
            }
        }

        val scanChance = (_scanSensitivity.value / 8f).toInt().coerceIn(2, 12)
        if (Random.nextInt(100) < scanChance) {
            src = "$gatewaySubnet.199"
            dest = local
            srcPort = Random.nextInt(41000, 65000)
            destPort = listOf(22, 23, 53, 80, 139, 443, 445, 8080).random()
            summary = "TCP connection probe toward local service port $destPort"
            tcpFlags = listOf("SYN", "FIN,PSH,URG", "NONE", "SYN,FIN").random()
            scanSignal = when (tcpFlags) {
                "SYN" -> "Repeated SYN probes suggest service discovery from $src."
                "NONE" -> "Null flag probe suggests stealth service discovery from $src."
                "SYN,FIN" -> "Invalid SYN/FIN collision suggests active reconnaissance from $src."
                else -> "Unusual FIN/PSH/URG pattern suggests stealth scanning from $src."
            }
            heuristicClass = "Network Scan Signal"
            isSuspicious = true
            alertMessage = scanSignal
            ascii = "SCAN_SIGNAL flags=$tcpFlags dst=$dest:$destPort source=$src"
            hex = ascii.encodeToByteArray().joinToString(" ") { String.format("%02x", it) }
        }

        val headerSummary = buildString {
            append("simulated proto=$prot")
            if (srcPort != null || destPort != null) append(" ports=${srcPort ?: "-"}>${destPort ?: "-"}")
            if (tcpFlags.isNotBlank()) append(" flags=$tcpFlags")
        }
        val rawHex = if (_deepPacketInspection.value) {
            "45 00 00 34 12 34 40 00 40 06 00 00 " + hex
        } else {
            ""
        }

        return PacketEntity(
            sessionId = sessId,
            timestamp = System.currentTimeMillis(),
            timestampStr = timeStr,
            protocol = prot,
            sourceIp = src,
            sourcePort = srcPort,
            destIp = dest,
            destPort = destPort,
            length = Random.nextInt(40, 1480),
            summary = summary,
            payloadHex = hex,
            payloadAscii = ascii,
            rawPacketHex = rawHex,
            headerSummary = headerSummary,
            tcpFlags = tcpFlags,
            isSuspicious = isSuspicious,
            alertMessage = alertMessage,
            heuristicClass = heuristicClass,
            scanSignal = scanSignal
        )
    }

    private fun applyIdsRulesCheck(packet: PacketEntity): PacketEntity {
        // Iterate through enabled database rules
        val activeRules = allRules.value.filter { it.isEnabled }
        for (rule in activeRules) {
            // Check protocol filter
            if (rule.protocolFilter != "ALL" && rule.protocolFilter.uppercase() != packet.protocol.uppercase()) {
                continue
            }

            // Check pattern against decoded ASCII payload OR packet summary
            val regex = try {
                Regex(rule.regexPattern, RegexOption.IGNORE_CASE)
            } catch (e: Exception) {
                null
            }

            if (regex != null) {
                if (regex.containsMatchIn(packet.payloadAscii) || regex.containsMatchIn(packet.summary)) {
                    // Alert Match!
                    return packet.copy(
                        isSuspicious = true,
                        alertMessage = "IDS Alert: [${rule.name}] Match Pattern! (Severity: ${rule.severity})"
                    )
                }
            }
        }
        return packet
    }

    private fun describeCidr(cidr: String): String {
        val pieces = cidr.split("/")
        require(pieces.size == 2) { "CIDR must look like 192.168.1.0/24" }
        val ip = ipv4ToLong(pieces[0])
        val prefix = pieces[1].toInt()
        require(prefix in 0..32) { "CIDR prefix must be 0..32" }

        val mask = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        val network = ip and mask
        val broadcast = network or (mask.inv() and 0xFFFFFFFFL)
        val usableStart = if (prefix >= 31) network else network + 1
        val usableEnd = if (prefix >= 31) broadcast else broadcast - 1
        val hosts = if (prefix >= 31) broadcast - network + 1 else (broadcast - network - 1).coerceAtLeast(0)

        return buildString {
            appendLine("CIDR: $cidr")
            appendLine("Network: ${longToIpv4(network)}")
            appendLine("Broadcast: ${longToIpv4(broadcast)}")
            appendLine("Usable range: ${longToIpv4(usableStart)} - ${longToIpv4(usableEnd)}")
            append("Usable hosts: $hosts")
        }
    }

    private fun describeAddressClass(ip: String): String {
        val value = ipv4ToLong(ip)
        val first = (value shr 24) and 0xFF
        val second = (value shr 16) and 0xFF
        val classification = when {
            first == 10L -> "private RFC1918"
            first == 172L && second in 16L..31L -> "private RFC1918"
            first == 192L && second == 168L -> "private RFC1918"
            first == 127L -> "loopback"
            first == 169L && second == 254L -> "link-local"
            first in 224L..239L -> "multicast"
            first >= 240L -> "reserved"
            else -> "publicly routable"
        }
        return "$ip is $classification. Remote registry lookup is intentionally not executed from the local terminal."
    }

    private fun ipv4ToLong(ip: String): Long {
        val parts = ip.split(".")
        require(parts.size == 4) { "Invalid IPv4 address: $ip" }
        return parts.fold(0L) { acc, part ->
            val octet = part.toLong()
            require(octet in 0..255) { "Invalid IPv4 octet: $part" }
            (acc shl 8) or octet
        }
    }

    private fun longToIpv4(value: Long): String {
        return listOf(24, 16, 8, 0).joinToString(".") { shift ->
            ((value shr shift) and 0xFF).toString()
        }
    }
}
