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
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.IdsRuleEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.WireRifterViewModel

@Composable
fun AnalysisScreen(
    viewModel: WireRifterViewModel,
    modifier: Modifier = Modifier
) {
    val rules by viewModel.allRules.collectAsState()
    val isAuditRunning by viewModel.isAuditRunning.collectAsState()
    val auditResult by viewModel.overallAuditResult.collectAsState()
    val devices by viewModel.allDevices.collectAsState()
    val alertCount by viewModel.securityAlertCount.collectAsState()

    var showRuleCreator by remember { mutableStateOf(false) }

    val rogueCount = remember(devices) {
        devices.count { !it.isTrusted }
    }

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
                    text = "Intrusion Analysis",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    letterSpacing = 0.sp
                )
                Text(
                    text = "Heuristic Signature Engines & Local Audit",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextGray
                )
            }
        }

        // --- 2. Live AP Shield Security Summary ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkSurface)
                .border(1.dp, BorderDark, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = if (rogueCount == 0 && alertCount == 0) CyberGreen else CyberAmber,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = if (rogueCount == 0 && alertCount == 0) "SHIELD STATE: STABLE" else "SHIELD STATE: HEURISTICS WARNING",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (rogueCount == 0 && alertCount == 0) CyberGreen else CyberAmber
                    )
                }

                Text(
                    text = "WireRifter signature engines continuously match active headers against heuristic filters. Run a local security audit below.",
                    fontSize = 11.sp,
                    color = TextGray
                )

                Button(
                    onClick = { viewModel.runSubnetSecurityAudit() },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberTertiary),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isAuditRunning,
                    modifier = Modifier.fillMaxWidth().testTag("compile_audit_btn")
                ) {
                    if (isAuditRunning) {
                        CircularProgressIndicator(color = DarkBg, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Consolidating Wi-Fi Packets...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(imageVector = Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Compile Local Security Audit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Expanded compliance readout from local report
        auditResult?.let { text ->
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, BorderHighlight, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Analytics, contentDescription = null, tint = CyberTertiary, modifier = Modifier.size(16.dp))
                        Text("WireRifter INTELLIGENCE AUDIT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberTertiary, fontFamily = FontFamily.Monospace)
                    }
                    Text(
                        text = text,
                        fontSize = 11.sp,
                        color = TextWhite,
                        lineHeight = 16.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- 3. Custom Firewalls and Signature Rules (IDS) ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(imageVector = Icons.Outlined.BugReport, contentDescription = null, tint = CyberPrimary, modifier = Modifier.size(18.dp))
                Text(
                    text = "IDS Signature Rules",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
            }

            Text(
                text = "+ CREATE RULE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CyberPrimary,
                modifier = Modifier
                    .clickable { showRuleCreator = !showRuleCreator }
                    .testTag("toggle_rule_creator_btn")
            )
        }

        // Form to add a rule if active
        if (showRuleCreator) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurface)
                    .border(1.dp, BorderHighlight, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                RuleCreatorPanel(
                    onRuleSaved = { name, proto, regex, severity ->
                        viewModel.addNewIdsRule(name, proto, regex, severity)
                        showRuleCreator = false
                    },
                    onCancel = { showRuleCreator = false }
                )
            }
        }

        // Rules List Column
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (rules.isEmpty()) {
                Text(
                    text = "No detection signatures loaded. Add or seed defaults.",
                    color = TextGray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                rules.forEach { rule ->
                    RuleRow(rule = rule, onDelete = { viewModel.deleteIdsRule(rule) })
                }
            }
        }
    }
}

@Composable
fun RuleRow(
    rule: IdsRuleEntity,
    onDelete: () -> Unit
) {
    val severityColor = when (rule.severity.uppercase()) {
        "HIGH" -> CyberRed
        "MEDIUM" -> CyberAmber
        else -> CyberCyan
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = rule.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(severityColor.copy(alpha = 0.15f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = rule.severity,
                            fontSize = 8.sp,
                            color = severityColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "Filter: ${rule.protocolFilter} - Pattern: \"${rule.regexPattern}\"",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp).testTag("delete_rule_${rule.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove heuristic rule",
                    tint = TextGray.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleCreatorPanel(
    onRuleSaved: (String, String, String, String) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var regex by remember { mutableStateOf("") }
    var selectedProtocol by remember { mutableStateOf("ALL") }
    var selectedSeverity by remember { mutableStateOf("MEDIUM") }

    val protocols = listOf("ALL", "TCP", "UDP", "DNS", "HTTP")
    val severities = listOf("LOW", "MEDIUM", "HIGH")

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Create Intrusion Rule Signature",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = CyberPrimary
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("IDS Rule Title", fontSize = 11.sp) },
            textStyle = TextStyle(fontSize = 12.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyberPrimary,
                unfocusedBorderColor = BorderDark,
                focusedLabelColor = CyberPrimary,
                unfocusedLabelColor = TextGray
            ),
            modifier = Modifier.fillMaxWidth().testTag("rule_title_field")
        )

        OutlinedTextField(
            value = regex,
            onValueChange = { regex = it },
            label = { Text("Regex Matching Expression (PCRE)", fontSize = 11.sp) },
            textStyle = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyberPrimary,
                unfocusedBorderColor = BorderDark,
                focusedLabelColor = CyberPrimary,
                unfocusedLabelColor = TextGray
            ),
            modifier = Modifier.fillMaxWidth().testTag("rule_regex_field")
        )

        // Protocol selector chips
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Layer protocol", fontSize = 10.sp, color = TextGray, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                protocols.forEach { pr ->
                    val isSel = selectedProtocol == pr
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) CyberPrimary else DarkBg)
                            .border(1.dp, if (isSel) CyberPrimary else BorderDark, RoundedCornerShape(8.dp))
                            .clickable { selectedProtocol = pr }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(pr, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (isSel) DarkBg else TextWhite)
                    }
                }
            }
        }

        // Severity selector chips
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("IDS Severity Index", fontSize = 10.sp, color = TextGray, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                severities.forEach { sv ->
                    val isSel = selectedSeverity == sv
                    val itemCol = when (sv) {
                        "HIGH" -> CyberRed
                        "MEDIUM" -> CyberAmber
                        else -> CyberCyan
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) itemCol else DarkBg)
                            .border(1.dp, if (isSel) itemCol else BorderDark, RoundedCornerShape(8.dp))
                            .clickable { selectedSeverity = sv }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(sv, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (isSel) DarkBg else TextWhite)
                    }
                }
            }
        }

        // Bottom CTAs
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Cancel",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextGray,
                modifier = Modifier
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 12.dp)
            )

            Button(
                onClick = {
                    if (name.isNotEmpty() && regex.isNotEmpty()) {
                        onRuleSaved(name, selectedProtocol, regex, selectedSeverity)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp).testTag("save_rule_btn")
            ) {
                Text("Save Directive", fontSize = 11.sp, color = DarkBg, fontWeight = FontWeight.Bold)
            }
        }
    }
}
