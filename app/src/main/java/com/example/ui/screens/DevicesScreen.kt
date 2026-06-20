package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.DeviceEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.WireRifterViewModel
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DevicesScreen(
    viewModel: WireRifterViewModel,
    modifier: Modifier = Modifier
) {
    val devices by viewModel.allDevices.collectAsState()
    val isScanning by viewModel.isDeviceScanning.collectAsState()
    val localIp by viewModel.localIpAddress.collectAsState()
    val gatewayIp by viewModel.gatewayIpAddress.collectAsState()
    val ssid by viewModel.wifiSsid.collectAsState()

    // Screen tab selection states: "list" vs "topology"
    var activeViewMode by remember { mutableStateOf("topology") }
    var selectedDeviceFromTopology by remember { mutableStateOf<DeviceEntity?>(null) }

    val rogueCount = remember(devices) {
        devices.count { !it.isTrusted }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(16.dp)
    ) {
        // --- 1. Subnet Info Header ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Subnet Inventory",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    letterSpacing = 0.sp
                )
                Text(
                    text = "Target Subnet: ${gatewayIp.substringBeforeLast(".")}.0 / 24",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextGray
                )
            }

            Button(
                onClick = { viewModel.triggerDeviceSubnetDiscovery() },
                enabled = !isScanning,
                colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary),
                shape = RoundedCornerShape(0.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp).testTag("device_scan_btn")
            ) {
                if (isScanning) {
                    CircularProgressIndicator(color = DarkBg, modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text("Probe Subnet", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkBg)
                    }
                }
            }
        }

        // --- 2. Live SSID & IP Parameters Card ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(0.dp))
                .background(DarkSurface)
                .border(1.dp, BorderDark, RoundedCornerShape(0.dp))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Router, contentDescription = null, tint = CyberPrimary, modifier = Modifier.size(18.dp))
                    Text(
                        text = ssid,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    NetworkParamLabel("Local IP", localIp)
                    NetworkParamLabel("Gateway", gatewayIp)
                    NetworkParamLabel("Rogue Indicators", "$rogueCount Threats")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- Segment Selection Buttons ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(0.dp))
                .background(DarkSurfaceElevated)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(0.dp))
                    .background(if (activeViewMode == "topology") CyberPrimary.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { activeViewMode = "topology" }
                    .testTag("toggle_topology_view"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Outlined.Hub, contentDescription = null, tint = if (activeViewMode == "topology") CyberPrimary else TextGray, modifier = Modifier.size(16.dp))
                    Text("Topology Map", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (activeViewMode == "topology") TextWhite else TextGray)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(0.dp))
                    .background(if (activeViewMode == "list") CyberPrimary.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { activeViewMode = "list" }
                    .testTag("toggle_list_view"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Outlined.List, contentDescription = null, tint = if (activeViewMode == "list") CyberPrimary else TextGray, modifier = Modifier.size(16.dp))
                    Text("Node Inventory", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (activeViewMode == "list") TextWhite else TextGray)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Warning state for detected rogue nodes
        AnimatedVisibility(visible = rogueCount > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(0.dp))
                    .background(Color(0xFF31111D))
                    .border(1.dp, Color(0xFF93000A), RoundedCornerShape(0.dp))
                    .padding(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF2B8B5))
                    Column {
                        Text(
                            text = "SECURITY AUDIT WARNING",
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = Color(0xFFF2B8B5),
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Found $rogueCount node(s) marked untrusted. Tap node to isolate details.",
                            fontSize = 9.sp,
                            color = Color(0xFFF2B8B5)
                        )
                    }
                }
            }
        }

        // --- 3. Render list or topology graph ---
        if (activeViewMode == "topology") {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(0.dp))
                    .background(DarkSurface)
                    .border(1.dp, BorderDark, RoundedCornerShape(0.dp))
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "Real-Time Topology Graph (Tap Device to Inspect)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (devices.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No network nodes discovered yet.", color = TextGray, fontSize = 11.sp)
                        }
                    } else {
                        // Interactive Topology Graph Map
                        NetworkTopologyMap(
                            devices = devices,
                            localIp = localIp,
                            selectedDevice = selectedDeviceFromTopology,
                            onDeviceSelect = { selectedDeviceFromTopology = it },
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                    }

                    // Bottom info sheet showing selected node details
                    AnimatedVisibility(visible = selectedDeviceFromTopology != null) {
                        selectedDeviceFromTopology?.let { node ->
                            val isOwnAgent = node.ipAddress == localIp
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(0.dp))
                                    .background(DarkSurfaceElevated)
                                    .border(1.dp, if (node.isTrusted) BorderDark else CyberRed, RoundedCornerShape(0.dp))
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(node.hostname, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                                                if (isOwnAgent) {
                                                    Box(modifier = Modifier.clip(RoundedCornerShape(0.dp)).background(CyberPrimary.copy(alpha = 0.2f)).padding(horizontal = 4.dp)) {
                                                        Text("SELF", fontSize = 8.sp, color = CyberPrimary, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                            Text("${node.ipAddress} - ${node.macAddress.uppercase()}", fontSize = 10.sp, color = TextGray, fontFamily = FontFamily.Monospace)
                                        }

                                        IconButton(onClick = { selectedDeviceFromTopology = null }, modifier = Modifier.size(24.dp)) {
                                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close details panel", tint = TextGray, modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    Divider(thickness = 0.5.dp, color = BorderDark)

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Vendor: ${node.vendor}", fontSize = 10.sp, color = TextGray)
                                            Text("Trust State: ${if (node.isTrusted) "Trusted Node" else "POTENTIAL THREAT (UNTRUSTED)"}", fontSize = 10.sp, color = if (node.isTrusted) CyberGreen else CyberRed, fontWeight = FontWeight.Bold)
                                            
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text("OS Fingerprint: ${node.osFingerprint}", fontSize = 9.sp, color = TextWhite, fontWeight = FontWeight.Medium)
                                            Text("Device Type: ${node.deviceType}", fontSize = 9.sp, color = CyberCyan, fontWeight = FontWeight.SemiBold)
                                            Text("Confidence: ${node.fingerprintConfidence}% | Care: ${node.careState}", fontSize = 8.sp, color = CyberPrimary, fontFamily = FontFamily.Monospace)
                                            Text("Metric Signatures: ${node.ttlFingerprint}", fontSize = 8.sp, color = TextGray, fontFamily = FontFamily.Monospace)
                                        }

                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        ) {
                                            val tColor = when {
                                                node.trustScore >= 90 -> CyberGreen
                                                node.trustScore >= 70 -> CyberPrimary
                                                else -> CyberRed
                                            }
                                            Text(
                                                text = "${node.trustScore}%",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = tColor,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text("TRUST SCORE", fontSize = 7.sp, color = TextGray, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    if (!isOwnAgent && !node.isGateway) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Button(
                                            onClick = {
                                                viewModel.toggleDeviceTrust(node.macAddress, node.isTrusted)
                                                selectedDeviceFromTopology = node.copy(
                                                    isTrusted = !node.isTrusted,
                                                    trustScore = if (node.isTrusted) 15 else 85
                                                )
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = if (node.isTrusted) CyberRed else CyberGreen),
                                            shape = RoundedCornerShape(0.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(32.dp)
                                                .testTag("topology_panel_toggle_btn")
                                        ) {
                                            Text(if (node.isTrusted) "Block / Demote Trust" else "Authorize / Trust Node", fontSize = 11.sp, color = DarkBg, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // classic list view of nodes
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(0.dp))
                    .background(DarkSurface)
                    .border(1.dp, BorderDark, RoundedCornerShape(0.dp))
                    .padding(14.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "Discovered Network Nodes (${devices.size})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (devices.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No subnet nodes found. Run search sweeps above.", color = TextGray, fontSize = 11.sp)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(devices) { device ->
                                DeviceRow(
                                    device = device,
                                    isOwnAgent = device.ipAddress == localIp,
                                    onTrustToggle = { viewModel.toggleDeviceTrust(device.macAddress, device.isTrusted) }
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
fun NetworkTopologyMap(
    devices: List<DeviceEntity>,
    localIp: String,
    selectedDevice: DeviceEntity?,
    onDeviceSelect: (DeviceEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        val density = LocalDensity.current
        val widthDp = with(density) { widthPx.toDp() }
        val heightDp = with(density) { heightPx.toDp() }

        // Find standard gateway & clients
        val gatewayNode = devices.firstOrNull { it.isGateway } ?: devices.firstOrNull() ?: DeviceEntity(
            macAddress = "00:00:00:00:00:00", ipAddress = "192.168.1.1", hostname = "gateway.default", vendor = "Generic", isTrusted = true, isGateway = true, lastSeen = 0
        )
        val clients = remember(devices) {
            devices.filter { it.macAddress != gatewayNode.macAddress }
        }

        val clientCount = clients.size
        val radiusPx = (minOf(widthPx, heightPx) * 0.35f).coerceAtMost(320f)

        // Coordinates calculations
        val centerXPx = widthPx / 2f
        val startYPx = heightPx / 2f

        // Infinite packet vector animation
        val infiniteTransition = rememberInfiniteTransition()
        val progress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )

        // 1. Draw connection vectors (Lines) on Background Canvas
        Canvas(modifier = Modifier.matchParentSize()) {
            clients.forEachIndexed { idx, _ ->
                val angle = (2 * Math.PI * idx) / clientCount.coerceAtLeast(1)
                val destXPx = centerXPx + radiusPx * cos(angle).toFloat()
                val destYPx = startYPx + radiusPx * sin(angle).toFloat()

                // Draw line between client and central gateway
                drawLine(
                    color = BorderDark.copy(alpha = 0.6f),
                    start = Offset(centerXPx, startYPx),
                    end = Offset(destXPx, destYPx),
                    strokeWidth = 1.5.dp.toPx()
                )

                // Flow packet particles along lines simulating live sniffer sweeps!
                val particleX = centerXPx + (destXPx - centerXPx) * progress
                val particleY = startYPx + (destYPx - startYPx) * progress
                drawCircle(
                    color = if (clients[idx].isTrusted) CyberPrimary else CyberRed,
                    radius = 3.dp.toPx(),
                    center = Offset(particleX, particleY)
                )
            }
        }

        // 2. Lay down Interactive nodes using offsets
        // Central Gateway Router Node
        val gatewayX = with(density) { centerXPx.toDp() } - 24.dp
        val gatewayY = with(density) { startYPx.toDp() } - 24.dp

        Box(
            modifier = Modifier
                .offset(x = gatewayX, y = gatewayY)
                .size(48.dp)
                .clip(RectangleShape)
                .background(Brush.radialGradient(listOf(CyberPrimary.copy(alpha = 0.35f), DarkBg)))
                .border(2.dp, CyberPrimary, RectangleShape)
                .clickable { onDeviceSelect(gatewayNode) }
                .testTag("node_gateway"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Dns,
                contentDescription = "Gateway Center Node",
                tint = CyberPrimary,
                modifier = Modifier.size(24.dp)
            )
        }

        // Clients nodes clustered concentrically
        clients.forEachIndexed { idx, client ->
            val angle = (2 * Math.PI * idx) / clientCount.coerceAtLeast(1)
            val nodeXPx = centerXPx + radiusPx * cos(angle).toFloat()
            val nodeYPx = startYPx + radiusPx * sin(angle).toFloat()

            val nodeX = with(density) { nodeXPx.toDp() } - 20.dp
            val nodeY = with(density) { nodeYPx.toDp() } - 20.dp

            val isSelected = selectedDevice?.macAddress == client.macAddress
            val statusColor = if (client.isTrusted) CyberPrimary else CyberRed
            val ownAgent = client.ipAddress == localIp

            Box(
                modifier = Modifier
                    .offset(x = nodeX, y = nodeY)
                    .size(40.dp)
                    .clip(RectangleShape)
                    .background(DarkSurfaceElevated)
                    .border(
                        if (isSelected) 2.5.dp else 1.2.dp,
                        if (isSelected) CyberCyan else statusColor,
                        RectangleShape
                    )
                    .clickable { onDeviceSelect(client) }
                    .testTag("node_client_${idx}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        ownAgent -> Icons.Default.Smartphone
                        client.isTrusted -> Icons.Default.LaptopMac
                        else -> Icons.Default.PersonSearch
                    },
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(18.dp)
                )

                // Tiny indicator label matching the layout
                Box(
                    modifier = Modifier
                        .offset(y = 22.dp)
                        .clip(RoundedCornerShape(0.dp))
                        .background(DarkBg.copy(alpha = 0.85f))
                        .border(0.5.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(0.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = client.ipAddress.substringAfterLast("."),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceRow(
    device: DeviceEntity,
    isOwnAgent: Boolean,
    onTrustToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, BorderDark, RoundedCornerShape(0.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        shape = RoundedCornerShape(0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val isRogue = !device.isTrusted
                val statusColor = if (isRogue) CyberRed else CyberPrimary
                
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RectangleShape)
                        .background(statusColor.copy(alpha = 0.1f))
                        .border(1.dp, statusColor.copy(alpha = 0.2f), RectangleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            device.isGateway -> Icons.Default.Dns
                            isOwnAgent -> Icons.Default.Smartphone
                            device.deviceType.contains("NAS") -> Icons.Default.CloudQueue
                            device.deviceType.contains("Printer") -> Icons.Default.Print
                            device.deviceType.contains("Desktop") -> Icons.Default.LaptopMac
                            device.deviceType.contains("Sensor") || device.deviceType.contains("Autom") -> Icons.Default.SettingsInputAntenna
                            else -> Icons.Default.Computer
                        },
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = device.hostname,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isOwnAgent) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(0.dp))
                                    .background(CyberPrimary.copy(alpha = 0.15f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text("SELF", fontSize = 7.sp, color = CyberPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    
                    Text(
                        text = "${device.ipAddress} - ${device.macAddress.uppercase()}",
                        fontSize = 11.sp,
                        color = TextGray,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Modern visual badge showing Trust Score
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    val scoreColor = when {
                        device.trustScore >= 90 -> CyberGreen
                        device.trustScore >= 70 -> CyberPrimary
                        else -> CyberRed
                    }
                    Text(
                        text = "${device.trustScore}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "TRUST LEVEL",
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider(thickness = 0.5.dp, color = BorderDark)
            Spacer(modifier = Modifier.height(8.dp))

            // OS Fingerprint & Analyzer Details block
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(0.dp))
                    .background(DarkBg)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "OS: ${device.osFingerprint}",
                        fontSize = 10.sp,
                        color = TextWhite,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "TYPE: ${device.deviceType}",
                        fontSize = 10.sp,
                        color = CyberCyan,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Stack signature: ${device.ttlFingerprint}",
                        fontSize = 9.sp,
                        color = TextGray,
                        fontFamily = FontFamily.Monospace
                    )
                    
                    if (!isOwnAgent && !device.isGateway) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(0.dp))
                                .background(if (!device.isTrusted) CyberGreen.copy(alpha = 0.15f) else CyberRed.copy(alpha = 0.15f))
                                .clickable { onTrustToggle() }
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (!device.isTrusted) "Authorize Node" else "Revoke Trust",
                                fontSize = 9.sp,
                                color = if (!device.isTrusted) CyberGreen else CyberRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    text = "OS family: ${device.osFamily} ${device.osVersion} | confidence ${device.fingerprintConfidence}% | care ${device.careState}",
                    fontSize = 9.sp,
                    color = CyberPrimary,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Traits: ${device.stackTraits}",
                    fontSize = 9.sp,
                    color = TextGray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun NetworkParamLabel(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = TextGray,
            letterSpacing = 0.8.sp
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TextWhite,
            fontFamily = FontFamily.Monospace
        )
    }
}
