package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.PacketEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.WireRifterViewModel

@Composable
fun StatisticsScreen(
    viewModel: WireRifterViewModel,
    modifier: Modifier = Modifier
) {
    val packets by viewModel.recentSavedPackets.collectAsState()
    val devices by viewModel.allDevices.collectAsState()
    val alerts by viewModel.securityAlertCount.collectAsState()

    val protocolCounts = packets.groupingBy { it.protocol.uppercase() }.eachCount().toList()
        .sortedByDescending { it.second }
    val topTalkers = packets.flatMap { listOf(it.sourceIp, it.destIp) }
        .groupingBy { it }
        .eachCount()
        .toList()
        .sortedByDescending { it.second }
        .take(8)
    val scanSignals = packets.filter { it.scanSignal.isNotBlank() }
    val totalBytes = packets.sumOf { it.length.toLong() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Statistics", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                    Text("Local packet and host telemetry", fontSize = 11.sp, color = TextGray, fontFamily = FontFamily.Monospace)
                }
                Icon(Icons.Default.BarChart, contentDescription = null, tint = CyberPrimary, modifier = Modifier.size(28.dp))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("Packets", packets.size.toString(), Modifier.weight(1f))
                StatTile("Bytes", formatBytes(totalBytes), Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("Devices", devices.size.toString(), Modifier.weight(1f))
                StatTile("Alerts", alerts.toString(), Modifier.weight(1f), danger = alerts > 0)
            }
        }

        item {
            StatSection("Protocol Breakdown") {
                if (protocolCounts.isEmpty()) {
                    EmptyStatText("No persisted packets yet.")
                } else {
                    protocolCounts.forEach { (protocol, count) ->
                        StatMeter(label = protocol, value = count, max = protocolCounts.maxOf { it.second })
                    }
                }
            }
        }

        item {
            StatSection("Top Talkers") {
                if (topTalkers.isEmpty()) {
                    EmptyStatText("No endpoint activity recorded.")
                } else {
                    topTalkers.forEach { (ip, count) ->
                        StatLine(left = ip, right = "$count packets")
                    }
                }
            }
        }

        item {
            StatSection("Scan Sense") {
                if (scanSignals.isEmpty()) {
                    EmptyStatText("No scan signals in saved packet window.")
                } else {
                    scanSignals.take(6).forEach { packet ->
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = CyberAmber, modifier = Modifier.size(16.dp))
                            Column {
                                Text(packet.scanSignal, color = TextWhite, fontSize = 10.sp)
                                Text(flowLabel(packet), color = TextGray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, danger: Boolean = false) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(0.dp))
            .background(DarkSurface)
            .border(1.dp, if (danger) CyberRed else BorderDark, RoundedCornerShape(0.dp))
            .padding(14.dp)
    ) {
        Text(label.uppercase(), color = TextGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(value, color = if (danger) CyberRed else TextWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun StatSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(0.dp))
            .background(DarkSurface)
            .border(1.dp, BorderDark, RoundedCornerShape(0.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun StatMeter(label: String, value: Int, max: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StatLine(label, value.toString())
        Box(modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(0.dp)).background(DarkBg)) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((value.toFloat() / max.coerceAtLeast(1)).coerceIn(0f, 1f))
                    .background(CyberPrimary)
            )
        }
    }
}

@Composable
private fun StatLine(left: String, right: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(left, color = TextWhite, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Text(right, color = CyberPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun EmptyStatText(text: String) {
    Text(text, color = TextGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
}

private fun flowLabel(packet: PacketEntity): String {
    return "${packet.sourceIp}:${packet.sourcePort ?: "-"} -> ${packet.destIp}:${packet.destPort ?: "-"}"
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
}
