package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.PacketEntity
import com.example.ui.theme.BorderDark
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberPrimary
import com.example.ui.theme.CyberRed
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.TextGray
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.WireRifterViewModel

@Composable
fun MonitorScreen(
    viewModel: WireRifterViewModel,
    modifier: Modifier = Modifier
) {
    val isCapturing by viewModel.isCaptureActive.collectAsState()
    val packets by viewModel.livePacketFeed.collectAsState()
    val selectedPacket by viewModel.selectedPacketForAudit.collectAsState()
    val flowRate by viewModel.liveNetworkSpeed.collectAsState()
    var panelOpen by remember { mutableStateOf(false) }
    var displayFilter by remember { mutableStateOf("") }
    var panelTab by remember { mutableStateOf("Capture") }
    var scrollLocked by remember { mutableStateOf(false) }

    val filteredPackets = remember(packets, displayFilter) {
        packets.filter { packetMatchesQuery(it, displayFilter) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            MonitorToolbar(
                isCapturing = isCapturing,
                packetCount = filteredPackets.size,
                flowRate = flowRate,
                scrollLocked = scrollLocked,
                onTogglePanel = { panelOpen = !panelOpen },
                onToggleScrollLock = { scrollLocked = !scrollLocked },
                onCapture = {
                    if (isCapturing) viewModel.stopSnifferCapture() else viewModel.requestInterfaceSelection()
                }
            )

            PacketTable(
                packets = filteredPackets,
                selected = selectedPacket,
                onSelect = viewModel::selectPacketForInspector,
                modifier = Modifier
                    .weight(if (selectedPacket == null) 1f else 0.56f)
                    .fillMaxWidth()
            )

            Divider(thickness = 1.dp, color = CyberPrimary.copy(alpha = 0.65f))

            PacketDetailPanel(
                packet = selectedPacket,
                modifier = Modifier
                    .weight(0.25f)
                    .fillMaxWidth()
            )

            Divider(thickness = 1.dp, color = BorderDark)

            PacketBytesPanel(
                packet = selectedPacket,
                modifier = Modifier
                    .weight(0.19f)
                    .fillMaxWidth()
            )
        }

        if (panelOpen) {
            SlidePanel(
                filter = displayFilter,
                onFilterChange = { displayFilter = it },
                panelTab = panelTab,
                onPanelTabChange = { panelTab = it },
                isCapturing = isCapturing,
                onStartStop = {
                    if (isCapturing) viewModel.stopSnifferCapture() else viewModel.requestInterfaceSelection()
                },
                onClose = { panelOpen = false },
                modifier = Modifier
                    .fillMaxHeight()
                    .width(318.dp)
                    .align(Alignment.TopStart)
            )
        }

        if (scrollLocked && packets.size > filteredPackets.size) {
            Text(
                text = "LIVE - ${packets.size - filteredPackets.size} new packets",
                color = DarkBg,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .background(CyberPrimary)
                    .clickable { scrollLocked = false }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun MonitorToolbar(
    isCapturing: Boolean,
    packetCount: Int,
    flowRate: Double,
    scrollLocked: Boolean,
    onTogglePanel: () -> Unit,
    onToggleScrollLock: () -> Unit,
    onCapture: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(DarkSurfaceElevated)
            .border(1.dp, BorderDark)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RectTextButton(text = ">", onClick = onTogglePanel, width = 34.dp)
        RectTextButton(
            text = if (isCapturing) "Stop" else "Start",
            onClick = onCapture,
            width = 56.dp,
            active = isCapturing
        )
        RectTextButton(
            text = if (scrollLocked) "Unlock" else "Lock",
            onClick = onToggleScrollLock,
            width = 58.dp
        )
        Text(
            text = "Packets=$packetCount Rate=${String.format("%.1f", flowRate)}MB/s",
            color = TextGray,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        androidx.compose.material3.Icon(
            imageVector = if (isCapturing) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = null,
            tint = if (isCapturing) CyberRed else CyberGreen,
            modifier = Modifier.size(16.dp)
        )
        androidx.compose.material3.Icon(
            imageVector = Icons.Default.Pause,
            contentDescription = null,
            tint = if (scrollLocked) CyberPrimary else TextGray,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SlidePanel(
    filter: String,
    onFilterChange: (String) -> Unit,
    panelTab: String,
    onPanelTabChange: (String) -> Unit,
    isCapturing: Boolean,
    onStartStop: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(DarkSurface)
            .border(1.dp, CyberPrimary)
            .padding(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RectTextButton("<", onClose, width = 34.dp)
            Text(
                text = "Display Filter",
                color = TextWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
        OutlinedTextField(
            value = filter,
            onValueChange = onFilterChange,
            singleLine = true,
            placeholder = { Text("Apply a display filter <Ctrl-/>", color = TextGray, fontSize = 10.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(
                color = TextWhite,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            ),
            shape = RectangleShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyberPrimary,
                unfocusedBorderColor = BorderDark,
                cursorColor = CyberPrimary,
                focusedContainerColor = DarkBg,
                unfocusedContainerColor = DarkBg,
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite
            ),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            RectTextButton("Apply", {}, modifier = Modifier.weight(1f))
            RectTextButton("Clear", { onFilterChange("") }, modifier = Modifier.weight(1f))
        }
        LazyRow(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            items(listOf("Go", "Capture", "Analyse", "Statistics", "Telephony", "Wireless", "Tools", "Help")) { tab ->
                val selected = tab == panelTab
                Text(
                    text = tab,
                    color = if (selected) DarkBg else TextWhite,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(if (selected) CyberPrimary else DarkSurfaceElevated)
                        .border(1.dp, BorderDark)
                        .clickable { onPanelTabChange(tab) }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }
        PanelTabContent(panelTab, isCapturing, onStartStop)
    }
}

@Composable
private fun PanelTabContent(
    tab: String,
    isCapturing: Boolean,
    onStartStop: () -> Unit
) {
    val lines = when (tab) {
        "Go" -> listOf("Go to packet number", "First packet", "Last packet", "Previous packet", "Next packet", "Go to conversation")
        "Capture" -> listOf(if (isCapturing) "Stop capture" else "Start capture", "Restart capture", "Capture options", "Capture filter", "Refresh interfaces")
        "Analyse" -> listOf("Display filters", "Apply as filter", "Prepare as filter", "Conversation filter", "Enabled protocols", "Decode as", "Follow stream", "Expert information")
        "Statistics" -> listOf("Capture properties", "Protocol hierarchy", "Conversations", "Endpoints", "Packet lengths", "I/O graph", "TCP stream graph", "Flow graph")
        "Telephony" -> listOf("VoIP calls", "SIP flows", "RTP streams", "RTSP", "Mobile network summary")
        "Wireless" -> listOf("WLAN traffic", "Bluetooth interfaces", "802.11 statistics", "Hotspot activity")
        "Tools" -> listOf("Firewall rule generator", "Plaintext credential watch", "Custom signature rules", "Export objects")
        else -> listOf("About WireRifter", "Shortcut reference", "Filter reference", "Protocol reference")
    }

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        items(lines) { line ->
            Text(
                text = line,
                color = TextWhite,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(DarkBg)
                    .border(1.dp, BorderDark)
                    .clickable {
                        if (line == "Start capture" || line == "Stop capture") onStartStop()
                    }
                    .padding(horizontal = 6.dp, vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun PacketTable(
    packets: List<PacketEntity>,
    selected: PacketEntity?,
    onSelect: (PacketEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.background(DarkBg)) {
        PacketHeader()
        LazyColumn(modifier = Modifier.fillMaxSize().testTag("packet_table")) {
            items(packets) { packet ->
                PacketTableRow(
                    packet = packet,
                    selected = packet == selected,
                    ordinal = packets.indexOf(packet) + 1,
                    onClick = { onSelect(packet) }
                )
            }
        }
    }
}

@Composable
private fun PacketHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(DarkSurfaceElevated)
            .border(1.dp, BorderDark)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderText("No.", Modifier.width(38.dp))
        HeaderText("Time", Modifier.width(58.dp))
        HeaderText("Source", Modifier.weight(1.2f))
        HeaderText("Destination", Modifier.weight(1.2f))
        HeaderText("Protocol", Modifier.width(58.dp))
        HeaderText("Length", Modifier.width(48.dp))
        HeaderText("Info", Modifier.weight(1.6f))
    }
}

@Composable
private fun PacketTableRow(
    packet: PacketEntity,
    selected: Boolean,
    ordinal: Int,
    onClick: () -> Unit
) {
    val bg = if (selected) CyberPrimary else protocolRowColor(packet)
    val text = if (selected) TextWhite else Color(0xFF101318)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(27.dp)
            .background(bg)
            .border(1.dp, BorderDark.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DataText(ordinal.toString(), Modifier.width(38.dp), text, alignEnd = true)
        DataText(packet.timestampStr, Modifier.width(58.dp), text)
        DataText(packet.sourceIp, Modifier.weight(1.2f), text)
        DataText(packet.destIp, Modifier.weight(1.2f), text)
        DataText(packet.protocol.uppercase(), Modifier.width(58.dp), text, bold = true)
        DataText(packet.length.toString(), Modifier.width(48.dp), text, alignEnd = true)
        DataText(packet.summary, Modifier.weight(1.6f), text)
    }
}

@Composable
private fun PacketDetailPanel(packet: PacketEntity?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(DarkSurface)
            .border(1.dp, BorderDark)
    ) {
        PanelTitle("Packet Details")
        if (packet == null) {
            EmptyPanelText("Select a packet row to inspect decoded fields.")
            return
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(decodedTree(packet)) { line ->
                Text(
                    text = line,
                    color = if (line.startsWith(">")) CyberPrimary else TextWhite,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .border(1.dp, BorderDark.copy(alpha = 0.5f))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun PacketBytesPanel(packet: PacketEntity?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(DarkBg)
            .border(1.dp, BorderDark)
    ) {
        PanelTitle("Packet Bytes")
        if (packet == null) {
            EmptyPanelText("Raw hex and ASCII dump appears here.")
            return
        }
        Text(
            text = buildHexDump(packet),
            color = TextWhite,
            fontSize = 9.sp,
            lineHeight = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun PanelTitle(text: String) {
    Text(
        text = text,
        color = TextGray,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(DarkSurfaceElevated)
            .border(1.dp, BorderDark)
            .padding(horizontal = 6.dp, vertical = 5.dp)
    )
}

@Composable
private fun EmptyPanelText(text: String) {
    Text(
        text = text,
        color = TextGray,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(8.dp)
    )
}

@Composable
private fun HeaderText(text: String, modifier: Modifier) {
    Text(
        text = text,
        color = TextGray,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}

@Composable
private fun DataText(
    text: String,
    modifier: Modifier,
    color: Color,
    bold: Boolean = false,
    alignEnd: Boolean = false
) {
    Text(
        text = text,
        color = color,
        fontSize = 9.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
        textAlign = if (alignEnd) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start
    )
}

@Composable
private fun RectTextButton(
    text: String,
    onClick: () -> Unit,
    width: androidx.compose.ui.unit.Dp? = null,
    active: Boolean = false,
    modifier: Modifier = Modifier
) {
    val base = if (width != null) modifier.width(width) else modifier
    Text(
        text = text,
        color = if (active) DarkBg else TextWhite,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = base
            .height(26.dp)
            .background(if (active) CyberPrimary else DarkSurface)
            .border(1.dp, if (active) CyberPrimary else BorderDark)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp)
    )
}

private fun protocolRowColor(packet: PacketEntity): Color {
    if (packet.isSuspicious || packet.tcpFlags.contains("RST", ignoreCase = true)) return Color(0xFFFFC7C7)
    if (packet.tcpFlags.contains("SYN", ignoreCase = true)) return Color(0xFFCFF5CF)
    return when (packet.protocol.uppercase()) {
        "TCP" -> Color(0xFFE5D8F3)
        "UDP", "DNS" -> Color(0xFFD6E9FF)
        "HTTP" -> Color(0xFFD8F0D2)
        "ARP" -> Color(0xFFFFF3C5)
        "ICMP" -> Color(0xFFD7FAFF)
        "TLS", "QUIC" -> Color(0xFFDDE5EF)
        "SMB" -> Color(0xFFEBD8C8)
        else -> Color(0xFFE1E3E8)
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
            lower in listOf("tcp", "udp", "dns", "http", "arp", "tls", "icmp") -> packet.protocol.equals(lower, ignoreCase = true)
            lower.startsWith("proto=") -> packet.protocol.equals(lower.substringAfter("="), ignoreCase = true)
            lower.startsWith("ip.src") || lower.startsWith("src=") -> packet.sourceIp.contains(lower.substringAfterLast("=").trim())
            lower.startsWith("ip.dst") || lower.startsWith("dst=") -> packet.destIp.contains(lower.substringAfterLast("=").trim())
            lower.startsWith("ip=") -> {
                val value = lower.substringAfter("=")
                packet.sourceIp.contains(value) || packet.destIp.contains(value)
            }
            lower.startsWith("tcp.port") || lower.startsWith("udp.port") || lower.startsWith("port=") -> {
                val value = lower.substringAfterLast("=").trim().toIntOrNull()
                value != null && (packet.sourcePort == value || packet.destPort == value)
            }
            else -> listOf(
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
            ).joinToString(" ").lowercase().contains(lower)
        }
    }
}

private fun decodedTree(packet: PacketEntity): List<String> {
    val frame = listOf(
        "> Frame ${packet.id}: ${packet.length} bytes captured",
        "  Arrival Time: ${packet.timestampStr}",
        "  Protocol: ${packet.protocol}",
        "  Capture Length: ${packet.length}"
    )
    val ip = listOf(
        "> Internet Protocol, Src: ${packet.sourceIp}, Dst: ${packet.destIp}",
        "  Source Address: ${packet.sourceIp}",
        "  Destination Address: ${packet.destIp}",
        "  Header: ${packet.headerSummary.ifBlank { "unavailable" }}"
    )
    val transport = listOf(
        "> Transport, Src Port: ${packet.sourcePort ?: "-"}, Dst Port: ${packet.destPort ?: "-"}",
        "  Source Port: ${packet.sourcePort ?: "-"}",
        "  Destination Port: ${packet.destPort ?: "-"}",
        "  Flags: ${packet.tcpFlags.ifBlank { "none" }}"
    )
    val app = listOf(
        "> Application Data",
        "  Summary: ${packet.summary}",
        "  Entropy: ${String.format("%.4f", packet.entropy)}",
        "  Class: ${packet.densityClassification}",
        "  Signal: ${packet.scanSignal.ifBlank { packet.heuristicClass }}"
    )
    return frame + ip + transport + app
}

private fun buildHexDump(packet: PacketEntity): String {
    val source = packet.rawPacketHex.ifBlank { packet.payloadHex }
    val bytes = source.split(Regex("\\s+"))
        .mapNotNull { it.take(2).toIntOrNull(16)?.toByte() }
        .ifEmpty { packet.payloadAscii.encodeToByteArray().toList() }
    if (bytes.isEmpty()) return "No packet bytes available."

    return bytes.chunked(16).mapIndexed { row, chunk ->
        val offset = (row * 16).toString(16).padStart(4, '0')
        val hex = chunk.joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }.padEnd(47)
        val ascii = chunk.joinToString("") { byte ->
            val value = byte.toInt() and 0xFF
            if (value in 32..126) value.toChar().toString() else "."
        }
        "$offset  $hex  $ascii"
    }.joinToString("\n")
}
