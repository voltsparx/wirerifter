package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WireRifterDarkColorScheme = darkColorScheme(
    primary = CyberPrimary,
    onPrimary = DarkBg,
    secondary = CyberSecondary,
    onSecondary = TextWhite,
    tertiary = CyberTertiary,
    onTertiary = TextWhite,
    background = DarkBg,
    onBackground = TextWhite,
    surface = DarkSurface,
    onSurface = TextWhite,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextGray,
    error = CyberRed,
    onError = TextWhite,
    outline = BorderHighlight,
    outlineVariant = BorderDark
)

private val WireRifterLightColorScheme = lightColorScheme(
    primary = CyberPrimary,
    onPrimary = TextWhite,
    secondary = CyberSecondary,
    onSecondary = DarkBg,
    tertiary = CyberTertiary,
    onTertiary = DarkBg,
    background = Color(0xFFF7F9FF),
    onBackground = Color(0xFF141827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF141827),
    surfaceVariant = Color(0xFFE7ECFA),
    onSurfaceVariant = Color(0xFF4D5872),
    error = CyberRed,
    onError = TextWhite,
    outline = CyberPrimary,
    outlineVariant = Color(0xFFD5DCEF)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) WireRifterDarkColorScheme else WireRifterLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
