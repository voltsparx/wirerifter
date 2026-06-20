package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.SessionEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.AppearanceMode
import com.example.ui.viewmodel.WireRifterViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun VaultScreen(
    viewModel: WireRifterViewModel,
    modifier: Modifier = Modifier
) {
    val savedSessions by viewModel.savedSessions.collectAsState()
    val powerSaver by viewModel.powerSaverMode.collectAsState()
    val deepInspection by viewModel.deepPacketInspection.collectAsState()
    val deviceCare by viewModel.deviceCareMode.collectAsState()
    val scanSensitivity by viewModel.scanSensitivity.collectAsState()
    val packetRetention by viewModel.packetRetentionLimit.collectAsState()
    val alertCooldown by viewModel.alertCooldownSeconds.collectAsState()
    val pcapExportStatus by viewModel.pcapExportStatus.collectAsState()
    val appearanceMode by viewModel.appearanceMode.collectAsState()
    var showClearConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // --- 1. Top Section Info Header ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Archive Sandbox",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    letterSpacing = 0.sp
                )
                Text(
                    text = "Historical Scans & Cybersecurity Reference",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextGray
                )
            }
        }

        // --- 2. System Settings controls ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkSurface)
                .border(1.dp, BorderDark, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "WireRifter System Utilities",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
                Text(
                    text = "Remove all historical sweeps, logged nodes, blacklist trusts, and clear temporary database configurations safely.",
                    fontSize = 10.sp,
                    color = TextGray
                )

                Button(
                    onClick = { viewModel.exportAllPacketsAsPcap() },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("export_all_pcap_btn")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(imageVector = Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Export Packet Archive (.pcap)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkBg)
                    }
                }

                pcapExportStatus?.let { status ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkBg)
                            .border(1.dp, BorderDark, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = status,
                            fontSize = 9.sp,
                            color = if (status.startsWith("Saved")) CyberGreen else CyberRed,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Button(
                    onClick = { showClearConfirmation = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberRed),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("clear_vault_btn")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Purge DB and Run Warm Reset", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkSurface)
                .border(1.dp, BorderDark, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Capture Settings",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
                SettingsAppearanceModeRow(
                    selected = appearanceMode,
                    onSelect = viewModel::setAppearanceMode
                )
                SettingsToggleRow(
                    title = "Power Saver Capture",
                    subtitle = "Slower sampling, calmer UI updates, lower battery pressure.",
                    checked = powerSaver,
                    onCheckedChange = viewModel::setPowerSaverMode
                )
                SettingsToggleRow(
                    title = "Deep Packet Inspection",
                    subtitle = "Keep raw-frame hex and decoded payload fields for packet inspector.",
                    checked = deepInspection,
                    onCheckedChange = viewModel::setDeepPacketInspection
                )
                SettingsToggleRow(
                    title = "Device Care Mode",
                    subtitle = "Treat each node as part of the local network body and reduce repeated alert noise.",
                    checked = deviceCare,
                    onCheckedChange = viewModel::setDeviceCareMode
                )
                SettingsSliderRow(
                    title = "Scan Sensitivity",
                    valueLabel = "${scanSensitivity.toInt()}%",
                    value = scanSensitivity,
                    range = 10f..100f,
                    onValueChange = viewModel::setScanSensitivity
                )
                SettingsSliderRow(
                    title = "Packet Retention",
                    valueLabel = "${packetRetention.toInt()} frames",
                    value = packetRetention,
                    range = 50f..500f,
                    onValueChange = viewModel::setPacketRetentionLimit
                )
                SettingsSliderRow(
                    title = "Alert Cooldown",
                    valueLabel = "${alertCooldown.toInt()}s",
                    value = alertCooldown,
                    range = 0f..60f,
                    onValueChange = viewModel::setAlertCooldownSeconds
                )
            }
        }

        if (showClearConfirmation) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF31111D))
                    .border(1.dp, Color(0xFF93000A), RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "CONFIRM COMPLETE INVENTORY DELETE?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color(0xFFF2B8B5),
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "This removes all saved PCAP summaries, IDS rules exceptions, and resets system metrics. Operation cannot be undone.",
                        fontSize = 10.sp,
                        color = Color(0xFFF2B8B5)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF2B8B5),
                            modifier = Modifier
                                .clickable { showClearConfirmation = false }
                                .padding(horizontal = 12.dp)
                        )
                        Button(
                            onClick = {
                                viewModel.clearVaultWorkspace()
                                showClearConfirmation = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberRed),
                            modifier = Modifier.height(28.dp).testTag("confirm_purge_btn")
                        ) {
                            Text("Purge Workspace", fontSize = 10.sp, color = TextWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- 3. Saved Audit Sessions List ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(imageVector = Icons.Outlined.History, contentDescription = null, tint = CyberPrimary, modifier = Modifier.size(18.dp))
            Text(
                text = "Archived Capture Runs (${savedSessions.size})",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (savedSessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkSurface)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No saved capture runs recorded yet. Start/Stop sniffing first.",
                        color = TextGray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                savedSessions.forEach { ses ->
                    SessionRow(
                        session = ses,
                        onExport = { viewModel.exportSessionAsPcap(ses) },
                        onDelete = { viewModel.deleteSavedSession(ses) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- 4. Educational Cybersecurity Reference Panel ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkSurface)
                .border(1.dp, BorderDark, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Outlined.Info, contentDescription = null, tint = CyberPrimary, modifier = Modifier.size(18.dp))
                    Text(
                        text = "WireRifter Cybersecurity Reference",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                }

                ReferenceTopic(
                    title = "1. How Packet Sniffing Operates",
                    desc = "Standard network adapters bind to specific IP sockets. Under Promiscuous Sniff mode, interface network cards capture all surrounding broadcast and frame transactions, parsing protocols bit-by-bit from ether-header values."
                )

                ReferenceTopic(
                    title = "2. How Rogue Elements are Identified",
                    desc = "Subnet tools examine active local MAC blocks. If a responder yields a hardware manufacturer vendor that doesn't correspond to your residential layout, or if multiple IPs point to a single MAC address (ARP spoofing), the node is flagged for inspection."
                )

                ReferenceTopic(
                    title = "3. Mitigating Plain-text HTTP Leaks",
                    desc = "Standard HTTP dispatches raw string queries across local bridges. Passersby can record credentials easily. Ensure all destination paths use TLS (HTTPS) standard, which wraps packages in safe symmetric encryption before socket transmission."
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- Play Protect & Android Security Safe Harbor Notice ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF2B1C10)) // Warm amber slate background
                .border(1.5.dp, Color(0xFFE08D3C), RoundedCornerShape(20.dp)) // Cyber warning amber border
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Security Status Advisor",
                        tint = Color(0xFFE08D3C),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Android Security & Play Protect Notice",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                }

                Text(
                    text = "BECAUSE OF WHAT THIS APP LITERALLY DOES:",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE08D3C),
                    letterSpacing = 0.5.sp,
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = "WireRifter compiles low-level Rust libraries and initiates local system socket binding to capture and analyze raw Wi-Fi network packets on interface bridges. This acts identically to professional network sniffing frameworks.\n\n" +
                           "As a result, Google Play Protect or on-device security checks may flag this binary as a 'High-Risk Network Tool' or 'Potentially Unwanted Application' (PUA/PUP) during installation.",
                    fontSize = 10.sp,
                    color = TextWhite.copy(alpha = 0.9f),
                    lineHeight = 14.sp
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkBg)
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "VERIFIED COMPLIANCE CHECKS:",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberGreen,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "- 100% client-side scan pipeline: no servers, zero telemetry callbacks.\n" +
                                   "- Fully open-source repository: run compilation directly from source.\n" +
                                   "- No Internet broadcast threads: operates with total secure air-gap isolation.\n" +
                                   "- Local capture: executed on local network loops.",
                            fontSize = 9.sp,
                            color = TextGray,
                            lineHeight = 13.sp
                        )
                    }
                }

                Text(
                    text = "If prompted with a threat flag on installation, review the source and whitelist WireRifter only when you trust your local build.",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE08D3C)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- 5. Open Source Credits & Developer Profile ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkSurfaceElevated)
                .border(2.dp, CyberPrimary.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Code icon",
                        tint = CyberPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Open Source Integration Credits",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                }

                Text(
                    text = "This application is fully registered as an open-source security intelligence project. All packet-sniffing parsing algorithms, digital threat filters, heuristics models, and Shannon Entropy estimators operate purely client-side without collecting user telemetry.",
                    fontSize = 10.sp,
                    color = TextGray,
                    lineHeight = 14.sp
                )

                Divider(thickness = 0.5.dp, color = BorderDark, modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lead Author",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberSecondary
                    )
                    Text(
                        text = "Voltsparx - Niyor Kalita",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = TextWhite
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Collaborator Email",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberSecondary
                    )
                    Text(
                        text = "voltsparx@gmail.com",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = CyberPrimary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Version Distribution",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberSecondary
                    )
                    Text(
                        text = "v1.0.0-Stable (GitHub Release)",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = CyberCyan
                    )
                }

                // GitHub Web Repository reference badge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkBg)
                        .clickable { /* Trigger external web repository */ }
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Launch,
                            contentDescription = "Web Link Icon",
                            tint = CyberPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Column {
                            Text(
                                text = "GitHub Official Code Repository",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Text(
                                text = "https://github.com/voltsparx/wirerifter",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextGray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsAppearanceModeRow(
    selected: AppearanceMode,
    onSelect: (AppearanceMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            Text("Appearance Mode", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextWhite)
            Text("Choose dark, light, or follow the Android system setting.", fontSize = 9.sp, color = TextGray, lineHeight = 12.sp)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppearanceChoice(
                label = "System",
                selected = selected == AppearanceMode.SYSTEM,
                onClick = { onSelect(AppearanceMode.SYSTEM) },
                modifier = Modifier.weight(1f)
            )
            AppearanceChoice(
                label = "Dark",
                selected = selected == AppearanceMode.DARK,
                onClick = { onSelect(AppearanceMode.DARK) },
                modifier = Modifier.weight(1f)
            )
            AppearanceChoice(
                label = "Light",
                selected = selected == AppearanceMode.LIGHT,
                onClick = { onSelect(AppearanceMode.LIGHT) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AppearanceChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) CyberPrimary else DarkBg)
            .border(1.dp, if (selected) CyberPrimary else BorderDark, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) DarkBg else TextWhite,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextWhite)
            Text(subtitle, fontSize = 9.sp, color = TextGray, lineHeight = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsSliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextWhite)
            Text(valueLabel, fontSize = 10.sp, color = CyberPrimary, fontFamily = FontFamily.Monospace)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = CyberPrimary,
                activeTrackColor = CyberPrimary,
                inactiveTrackColor = BorderDark
            )
        )
    }
}

@Composable
fun SessionRow(
    session: SessionEntity,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val timeLabel = remember(session.timestamp) { formatter.format(Date(session.timestamp)) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, BorderDark, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$timeLabel - Throughput: ${String.format("%.1f", session.averageSpeed)} MB/s",
                    fontSize = 10.sp,
                    color = TextGray
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Packets: ${session.packetCount}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = CyberPrimary
                    )
                    Text(
                        text = "Alerts: ${session.alertCount}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (session.alertCount > 0) CyberRed else CyberGreen
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onExport,
                    modifier = Modifier.size(32.dp).testTag("export_session_${session.sessionId.take(4)}")
                ) {
                    Icon(
                        imageVector = Icons.Default.SaveAlt,
                        contentDescription = "Export archived capture run",
                        tint = CyberPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp).testTag("delete_session_${session.sessionId.take(4)}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove archived capture run",
                        tint = TextGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ReferenceTopic(title: String, desc: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = CyberCyan,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = desc,
            fontSize = 10.sp,
            color = TextGray,
            lineHeight = 14.sp
        )
    }
}
