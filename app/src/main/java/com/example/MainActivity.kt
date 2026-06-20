package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.AnalysisScreen
import com.example.ui.screens.DevicesScreen
import com.example.ui.screens.InterfaceSelectionScreen
import com.example.ui.screens.MonitorScreen
import com.example.ui.screens.StatisticsScreen
import com.example.ui.screens.TerminalScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.theme.BorderDark
import com.example.ui.theme.CyberPrimary
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TextGray
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.AppearanceMode
import com.example.ui.viewmodel.WireRifterViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: WireRifterViewModel = viewModel()
            val appearanceMode by viewModel.appearanceMode.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (appearanceMode) {
                AppearanceMode.SYSTEM -> systemDark
                AppearanceMode.DARK -> true
                AppearanceMode.LIGHT -> false
            }
            MyApplicationTheme(darkTheme = darkTheme) {
                MainAppShell(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppShell(
    viewModel: WireRifterViewModel = viewModel()
) {
    var currentTab by remember { mutableStateOf("monitor") }
    val showInterfaceSelection by viewModel.showInterfaceSelection.collectAsState()

    if (showInterfaceSelection) {
        InterfaceSelectionScreen(viewModel = viewModel)
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            CompactBottomNav(
                currentTab = currentTab,
                onTabSelected = { currentTab = it }
            )
        },
        contentWindowInsets = WindowInsets.systemBars
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBg)
                .padding(paddingValues)
        ) {
            when (currentTab) {
                "monitor" -> MonitorScreen(viewModel = viewModel)
                "devices" -> DevicesScreen(viewModel = viewModel)
                "analysis" -> AnalysisScreen(viewModel = viewModel)
                "statistics" -> StatisticsScreen(viewModel = viewModel)
                "terminal" -> TerminalScreen(viewModel = viewModel)
                "vault" -> VaultScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun CompactBottomNav(
    currentTab: String,
    onTabSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBg)
    ) {
        Divider(thickness = 1.dp, color = BorderDark)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RectangleShape)
                .background(DarkSurface),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavCell("monitor", "Monitor", Icons.Default.NetworkCheck, currentTab, onTabSelected, "nav_monitor_tab", Modifier.weight(1f))
            NavCell("devices", "Devices", Icons.Default.Dns, currentTab, onTabSelected, "nav_devices_tab", Modifier.weight(1f))
            NavCell("analysis", "Analysis", Icons.Default.Shield, currentTab, onTabSelected, "nav_analysis_tab", Modifier.weight(1f))
            NavCell("statistics", "Stats", Icons.Default.BarChart, currentTab, onTabSelected, "nav_statistics_tab", Modifier.weight(1f))
            NavCell("terminal", "Terminal", Icons.Default.Code, currentTab, onTabSelected, "nav_terminal_tab", Modifier.weight(1f))
            NavCell("vault", "Vault", Icons.Default.AdminPanelSettings, currentTab, onTabSelected, "nav_vault_tab", Modifier.weight(1f))
        }
    }
}

@Composable
private fun NavCell(
    route: String,
    label: String,
    icon: ImageVector,
    currentTab: String,
    onTabSelected: (String) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val selected = route == currentTab
    val alpha = if (selected) 1f else 0.48f
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable { onTabSelected(route) }
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(if (selected) CyberPrimary else Color.Transparent)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = CyberPrimary.copy(alpha = alpha),
            modifier = Modifier.size(17.dp)
        )
        Text(
            text = label,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) TextWhite else TextGray,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
