package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.database.PacketEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.WireRifterViewModel

@Composable
fun MonitorScreen(
    viewModel: WireRifterViewModel,
    modifier: Modifier = Modifier
) {
    val isCapturing by viewModel.isCaptureActive.collectAsState()
    val livePackets by viewModel.livePacketFeed.collectAsState()
    val activeDevices by viewModel.activeDeviceCount.collectAsState()
    val securityAlerts by viewModel.securityAlertCount.collectAsState()
    val flowRate by viewModel.liveNetworkSpeed.collectAsState()
    val selectedPacket by viewModel.selectedPacketForAudit.collectAsState()

    var protocolFilter by remember { mutableStateOf("ALL") }
    var queryText by remember { mutableStateOf("") }

    // Filter packets locally with protocol chips plus an analyst query bar.
    val filteredPackets = remember(livePackets, protocolFilter, queryText) {
        livePackets.filter { packet ->
            val protocolMatches = protocolFilter == "ALL" || packet.protocol.equals(protocolFilter, ignoreCase = true)
            protocolMatches && packetMatchesQuery(packet, queryText)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(16.dp)
    ) {
        // --- 1. Top Section Info Header ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Live Active Feed",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    letterSpacing = 0.sp
                )
                Text(
                    text = "Bound Interface: wlan0 (PROMISCUOUS)",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextGray
                )
            }
            
            // Live pulsing monitor indicator
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isCapturing) Color(0x2200E676) else Color(0x2294A3B8))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isCapturing) CyberGreen else TextGray)
                    )
                    Text(
                        text = if (isCapturing) "SWEEPER ACTIVE" else "IDLE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCapturing) CyberGreen else TextGray,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // --- 2. Stat Widgets (Matching Sleek Theme) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Widget: Connected Nodes
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(DarkSurface)
                    .border(1.dp, BorderDark, RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "Active Devices",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = CyberPrimary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = String.format("%02d", activeDevices),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Light,
                            color = TextWhite
                        )
                        Text(
                            text = "LIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberSecondary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
            }

            // Widget: Security Alerts (Matches the 31111D red border/background card from Sleek HTML)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF31111D))
                    .border(1.dp, Color(0xFF93000A), RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "Security Alerts",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFF2B8B5),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = String.format("%02d", securityAlerts),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Light,
                            color = Color(0xFFF2B8B5)
                        )
                        if (securityAlerts > 0) {
                            Text(
                                text = "RISK",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberRed,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- 3. Filter Chips Row ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val chips = listOf("ALL", "TCP", "UDP", "DNS", "HTTP", "ARP")
            chips.forEach { item ->
                val isSelected = protocolFilter == item
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) CyberPrimary else DarkSurface)
                        .border(1.dp, if (isSelected) CyberPrimary else BorderDark, RoundedCornerShape(12.dp))
                        .clickable { protocolFilter = item }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = item,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) DarkBg else TextWhite,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        OutlinedTextField(
            value = queryText,
            onValueChange = { queryText = it },
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.FilterAlt, contentDescription = null, tint = CyberPrimary)
            },
            trailingIcon = {
                if (queryText.isNotBlank()) {
                    IconButton(onClick = { queryText = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear packet query", tint = TextGray)
                    }
                }
            },
            placeholder = {
                Text(
                    "filter: ip=192.168.1.45 port=53 proto=dns alert scan password",
                    fontSize = 11.sp,
                    color = TextGray
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite,
                focusedBorderColor = CyberPrimary,
                unfocusedBorderColor = BorderDark,
                cursorColor = CyberPrimary,
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .testTag("packet_query_bar")
        )

        // --- 4. Main live Packet list container (Elevated container matching the sleek CSS) ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(DarkSurface)
                .border(1.dp, BorderDark, RoundedCornerShape(24.dp))
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Packet Trace Logs",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(CyberSecondary.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isCapturing) "LIVE (${String.format("%.1f", flowRate)} MB/s) - ${filteredPackets.size} shown" else "STREAM STOPPED - ${filteredPackets.size} shown",
                            fontSize = 9.sp,
                            color = CyberPrimary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                if (filteredPackets.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NetworkWifi,
                                contentDescription = null,
                                tint = BorderDark,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = if (isCapturing) "Listening on interface..." else "Interactive network adapter is quiet.",
                                color = TextGray,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            if (!isCapturing) {
                                Button(
                                    onClick = { viewModel.startSnifferCapture() },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary),
                                    modifier = Modifier.testTag("launch_sniff_btn")
                                ) {
                                    Text("Activate Interface Capture", color = TextWhite, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredPackets) { pkt ->
                            PacketRow(packet = pkt) {
                                viewModel.selectPacketForInspector(pkt)
                            }
                        }
                    }
                }
            }
        }

        // --- 5. Custom Floating Action Controller ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CyberPrimary)
                    .clickable {
                        if (isCapturing) {
                            viewModel.stopSnifferCapture()
                        } else {
                            viewModel.startSnifferCapture()
                        }
                    }
                    .testTag("sniffer_controller_fab"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isCapturing) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = "Trigger Capture Mode",
                    tint = DarkBg,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }

    // --- Packet Inspection Modal ---
    selectedPacket?.let { pkt ->
        PacketInspectorDialog(
            packet = pkt,
            viewModel = viewModel,
            onDismiss = { viewModel.selectPacketForInspector(null) }
        )
    }
}

@Composable
fun PacketRow(
    packet: PacketEntity,
    onClick: () -> Unit
) {
    val highlightColor = when {
        packet.isSuspicious -> CyberRed
        packet.protocol == "DNS" -> CyberAmber
        packet.protocol == "HTTP" -> CyberCyan
        packet.protocol == "TCP" -> CyberTertiary
        else -> TextGray
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.dp, if (packet.isSuspicious) CyberRed.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(8.dp))
            .background(if (packet.isSuspicious) CyberRed.copy(alpha = 0.05f) else Color.Transparent)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Timestamp
        Text(
            text = packet.timestampStr,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = CyberPrimary,
            modifier = Modifier.width(52.dp)
        )

        // Protocol Badge
        Box(
            modifier = Modifier
                .width(42.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(highlightColor.copy(alpha = 0.2f))
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = packet.protocol,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = highlightColor
            )
        }

        // Host Mapping and Summary
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = packet.sourceIp,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "->",
                    fontSize = 10.sp,
                    color = TextGray
                )
                Text(
                    text = packet.destIp,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = packet.summary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = if (packet.isSuspicious) CyberRed else TextGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (packet.isSuspicious) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Threat Alert Detected",
                tint = CyberRed,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

private fun packetMatchesQuery(packet: PacketEntity, query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return true

    return trimmed.split(Regex("\\s+")).all { token ->
        val lower = token.lowercase()
        when {
            lower == "alert" || lower == "suspicious" -> packet.isSuspicious
            lower == "scan" -> packet.scanSignal.isNotBlank() || packet.heuristicClass.contains("scan", ignoreCase = true)
            lower.startsWith("proto=") -> packet.protocol.equals(lower.substringAfter("="), ignoreCase = true)
            lower.startsWith("ip=") -> {
                val value = lower.substringAfter("=")
                packet.sourceIp.lowercase().contains(value) || packet.destIp.lowercase().contains(value)
            }
            lower.startsWith("src=") -> packet.sourceIp.lowercase().contains(lower.substringAfter("="))
            lower.startsWith("dst=") -> packet.destIp.lowercase().contains(lower.substringAfter("="))
            lower.startsWith("port=") -> {
                val value = lower.substringAfter("=").toIntOrNull()
                value != null && (packet.sourcePort == value || packet.destPort == value)
            }
            lower.startsWith("len>") -> packet.length > (lower.substringAfter(">").toIntOrNull() ?: Int.MAX_VALUE)
            lower.startsWith("len<") -> packet.length < (lower.substringAfter("<").toIntOrNull() ?: Int.MIN_VALUE)
            lower.startsWith("entropy>") -> packet.entropy > (lower.substringAfter(">").toDoubleOrNull() ?: Double.MAX_VALUE)
            else -> {
                val haystack = listOf(
                    packet.protocol,
                    packet.sourceIp,
                    packet.destIp,
                    packet.summary,
                    packet.payloadAscii,
                    packet.payloadHex,
                    packet.headerSummary,
                    packet.tcpFlags,
                    packet.heuristicClass,
                    packet.alertMessage.orEmpty(),
                    packet.scanSignal
                ).joinToString(" ").lowercase()
                haystack.contains(lower)
            }
        }
    }
}

@Composable
fun PacketInspectorDialog(
    packet: PacketEntity,
    viewModel: WireRifterViewModel,
    onDismiss: () -> Unit
) {
    val isAnalyzing by viewModel.isLocalAnalysisRunning.collectAsState()
    val aiResult by viewModel.packetAnalysisResult.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .background(DarkSurface)
                .border(1.dp, BorderDark, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = CyberPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Frame Inspector",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close frame inspector",
                            tint = TextGray
                        )
                    }
                }

                // Scrollable specifications
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (packet.isSuspicious) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF31111D))
                                .border(1.dp, Color(0xFF93000A), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = Color(0xFFF2B8B5)
                                )
                                Column {
                                    Text(
                                        text = "IDS INTRUSION DETECTED",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color(0xFFF2B8B5),
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = packet.alertMessage ?: "Unknown custom match alert rule triggered.",
                                        fontSize = 10.sp,
                                        color = Color(0xFFF2B8B5),
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }

                    // Network info tables
                    InfoPropertyRow("Timestamp", packet.timestampStr)
                    InfoPropertyRow("Protocol Type", packet.protocol)
                    InfoPropertyRow("Source Host Address", "${packet.sourceIp}:${packet.sourcePort ?: "unassigned"}")
                    InfoPropertyRow("Destination Host Address", "${packet.destIp}:${packet.destPort ?: "unassigned"}")
                    InfoPropertyRow("Wire Payload Length", "${packet.length} bytes")
                    InfoPropertyRow("Transaction Statement", packet.summary)
                    if (packet.headerSummary.isNotBlank()) {
                        InfoPropertyRow("Decoded Header", packet.headerSummary)
                    }
                    if (packet.tcpFlags.isNotBlank()) {
                        InfoPropertyRow("TCP Flags", packet.tcpFlags)
                    }
                    if (packet.scanSignal.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF31111D))
                                .border(1.dp, Color(0xFF93000A), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "NETWORK SCAN SENSE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF2B8B5),
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = packet.scanSignal,
                                    fontSize = 10.sp,
                                    color = Color(0xFFF2B8B5)
                                )
                            }
                        }
                    }

                    // Native Advanced Insights and Shannon Entropy Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkBg)
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "NATIVE HEURISTICS",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberPrimary,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = packet.heuristicClass.uppercase(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (packet.isSuspicious) CyberRed else CyberGreen,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            
                            Divider(thickness = 0.5.dp, color = BorderDark)
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SHANNON ENTROPY",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberPrimary,
                                    letterSpacing = 0.5.sp
                                )
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = String.format("%.4f H", packet.entropy),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextWhite,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = packet.densityClassification,
                                        fontSize = 8.sp,
                                        color = TextGray
                                    )
                                }
                            }
                            
                            // Visual Shannon Entropy Bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFF222222))
                            ) {
                                val fractionalWidth = (packet.entropy / 8.0).coerceIn(0.0, 1.0).toFloat()
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fractionalWidth)
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(CyberGreen, CyberPrimary, CyberRed)
                                            )
                                        )
                                )
                            }
                        }
                    }

                    // Hex Dump view
                    if (packet.rawPacketHex.isNotBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "FULL RAW FRAME HEX",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberPrimary,
                                letterSpacing = 0.5.sp
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkBg)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = packet.rawPacketHex,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = CyberCyan,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // Hex Dump view
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "HEXADECIMAL DUMP",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberPrimary,
                            letterSpacing = 0.5.sp
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkBg)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = packet.payloadHex,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = CyberGreen,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // ASCII Decoded view
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "RAW DECODED ASCII CONTENT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberPrimary,
                            letterSpacing = 0.5.sp
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkBg)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = packet.payloadAscii,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextWhite,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // --- 6. Local Forensic Panel ---
                    Divider(color = BorderDark, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.Analytics,
                                contentDescription = null,
                                tint = CyberTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Local Forensic Analyzer",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                        }
                        
                        Button(
                            onClick = { viewModel.runLocalPacketForensics(packet) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberTertiary),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !isAnalyzing,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp).testTag("action_gpt_analyze")
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(color = DarkBg, modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Text("Run Forensics", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (isAnalyzing) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                CircularProgressIndicator(color = CyberTertiary, strokeWidth = 2.dp)
                                Text("Parsing hex payload offsets...", color = TextGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }

                    aiResult?.let { text ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurfaceElevated)
                                .border(1.dp, BorderHighlight, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Analytics, contentDescription = null, tint = CyberTertiary, modifier = Modifier.size(14.dp))
                                    Text("FORENSIC REPORT SIGNAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberTertiary, fontFamily = FontFamily.Monospace)
                                }
                                Text(
                                    text = text,
                                    fontSize = 11.sp,
                                    color = TextWhite,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoPropertyRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 11.sp, color = TextGray, fontWeight = FontWeight.Bold)
        Text(text = value, fontSize = 11.sp, color = TextWhite, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
