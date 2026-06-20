package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.SafetyCheck
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.AnalysisScreen
import com.example.ui.screens.DevicesScreen
import com.example.ui.screens.MonitorScreen
import com.example.ui.screens.StatisticsScreen
import com.example.ui.screens.TerminalScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.CyberPrimary
import com.example.ui.theme.BorderDark
import com.example.ui.theme.TextWhite
import com.example.ui.theme.TextGray
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
  
  // Collect state values to verify values
  val isCapturing by viewModel.isCaptureActive.collectAsState()
  val activeDevices by viewModel.activeDeviceCount.collectAsState()
  val securityAlerts by viewModel.securityAlertCount.collectAsState()
  val currentSsid by viewModel.wifiSsid.collectAsState()

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    bottomBar = {
      // Rounded bottom bar directly matching CSS values in the Sleek Interface HTML template
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(DarkBg)
          .padding(horizontal = 14.dp, vertical = 6.dp)
      ) {
        Row(
          modifier = Modifier
          .fillMaxWidth()
          .height(76.dp)
          .clip(RoundedCornerShape(32.dp))
          .background(DarkSurface)
          .padding(horizontal = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          BottomNavItem(
            route = "monitor",
            label = "Monitor",
            activeIcon = Icons.Default.NetworkCheck,
            inactiveIcon = Icons.Default.NetworkWifi,
            isSelected = currentTab == "monitor",
            onClick = { currentTab = "monitor" },
            testTag = "nav_monitor_tab"
          )
          BottomNavItem(
            route = "devices",
            label = "Devices",
            activeIcon = Icons.Default.Dns,
            inactiveIcon = Icons.Default.Router,
            isSelected = currentTab == "devices",
            onClick = { currentTab = "devices" },
            testTag = "nav_devices_tab"
          )
          BottomNavItem(
            route = "analysis",
            label = "Analysis",
            activeIcon = Icons.Default.Shield,
            inactiveIcon = Icons.Default.HealthAndSafety,
            isSelected = currentTab == "analysis",
            onClick = { currentTab = "analysis" },
            testTag = "nav_analysis_tab"
          )
          BottomNavItem(
            route = "statistics",
            label = "Stats",
            activeIcon = Icons.Default.BarChart,
            inactiveIcon = Icons.Default.BarChart,
            isSelected = currentTab == "statistics",
            onClick = { currentTab = "statistics" },
            testTag = "nav_statistics_tab"
          )
          BottomNavItem(
            route = "terminal",
            label = "Terminal",
            activeIcon = Icons.Default.Code,
            inactiveIcon = Icons.Default.Code,
            isSelected = currentTab == "terminal",
            onClick = { currentTab = "terminal" },
            testTag = "nav_terminal_tab"
          )
          BottomNavItem(
            route = "vault",
            label = "Vault",
            activeIcon = Icons.Default.AdminPanelSettings,
            inactiveIcon = Icons.Default.Settings,
            isSelected = currentTab == "vault",
            onClick = { currentTab = "vault" },
            testTag = "nav_vault_tab"
          )
        }
      }
    },
    contentWindowInsets = WindowInsets.systemBars
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(DarkBg)
        .padding(paddingValues)
    ) {
      // --- Sleek Interface Platform Header ---
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          // App mark echoing the black/white/electric-blue launcher icon.
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(DarkSurfaceElevated),
            contentAlignment = Alignment.Center
          ) {
            Image(
              painter = painterResource(id = R.mipmap.wirerifter_icon),
              contentDescription = "WireRifter",
              contentScale = ContentScale.Fit,
              modifier = Modifier.size(34.dp)
            )
          }
          Column {
            Text(
              text = "WireRifter",
              fontSize = 18.sp,
              fontWeight = FontWeight.SemiBold,
              color = TextWhite,
              letterSpacing = 0.sp
            )
            Text(
              text = "SSID: $currentSsid (wlan0)",
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace,
              color = TextGray
            )
          }
        }

        // Action Buttons at top right
        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          TopActionButton(
            icon = Icons.Default.Security,
            contentDesc = "Audit status badge",
            tint = if (securityAlerts > 0) Color(0xFFFF1744) else CyberPrimary,
            testTag = "system_status_top_icon"
          )
          TopActionButton(
            icon = Icons.Default.HelpOutline,
            contentDesc = "Interface helper manual",
            tint = TextGray,
            testTag = "system_help_top_icon"
          )
        }
      }

      Divider(thickness = 1.dp, color = BorderDark, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))

      // --- Screen Container with smooth cross-dissolve ---
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
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
}

@Composable
fun TopActionButton(
  icon: ImageVector,
  contentDesc: String,
  tint: Color,
  testTag: String
) {
  Box(
    modifier = Modifier
      .size(40.dp)
      .clip(CircleShape)
      .background(DarkSurfaceElevated)
      .clickable { }
      .testTag(testTag),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDesc,
      tint = tint,
      modifier = Modifier.size(18.dp)
    )
  }
}

@Composable
fun BottomNavItem(
  route: String,
  label: String,
  activeIcon: ImageVector,
  inactiveIcon: ImageVector,
  isSelected: Boolean,
  onClick: () -> Unit,
  testTag: String
) {
  val iconColor = if (isSelected) CyberPrimary else TextGray
  val labelColor = if (isSelected) TextWhite else TextGray

  Column(
    modifier = Modifier
      .clickable(onClick = onClick)
      .padding(vertical = 4.dp, horizontal = 2.dp)
      .testTag(testTag),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    // If selected, show a subtle pill background under the icon like M3 Bottom Navigation
    Box(
      modifier = Modifier
        .clip(RoundedCornerShape(16.dp))
        .background(if (isSelected) CyberPrimary.copy(alpha = 0.18f) else Color.Transparent)
        .padding(horizontal = 10.dp, vertical = 6.dp),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = if (isSelected) activeIcon else inactiveIcon,
        contentDescription = label,
        tint = iconColor,
        modifier = Modifier.size(20.dp)
      )
    }
    Text(
      text = label,
      fontSize = 9.sp,
      fontWeight = FontWeight.Medium,
      color = labelColor
    )
  }
}
