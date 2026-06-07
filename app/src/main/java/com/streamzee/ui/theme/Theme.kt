package com.streamzee.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

fun accentColor(name: String): Color = when (name) {
    "Blue" -> Color(0xFF3B82F6)
    "Green" -> Color(0xFF22C55E)
    "Teal" -> Color(0xFF14B8A6)
    "Orange" -> Color(0xFFF97316)
    "Red" -> Color(0xFFEF4444)
    "Pink" -> Color(0xFFEC4899)
    "Indigo" -> Color(0xFF6366F1)
    else -> StreamzeePurple
}

private fun darkScheme(accent: Color, lite: Boolean) = darkColorScheme(
    primary            = accent,
    onPrimary          = Color.White,
    primaryContainer   = accent.copy(alpha = 0.22f),
    onPrimaryContainer = Color.White,
    secondary          = accent,
    onSecondary        = Color.White,
    secondaryContainer = accent.copy(alpha = 0.16f),
    onSecondaryContainer = Color.White,
    tertiary           = StreamzeeGreen,
    background         = if (lite) Color(0xFF121216) else ScreenBackground,
    onBackground       = TextPrimary,
    surface            = if (lite) Color(0xFF1E1E24) else CardBackground,
    onSurface          = TextPrimary,
    surfaceVariant     = if (lite) Color(0xFF292930) else CardBackground,
    onSurfaceVariant   = TextSecondary,
    error              = AccentRed,
    onError            = Color.White,
)

private fun lightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = 0.16f),
    onPrimaryContainer = Color(0xFF18181B),
    secondary = accent,
    onSecondary = Color.White,
    secondaryContainer = accent.copy(alpha = 0.12f),
    onSecondaryContainer = Color(0xFF18181B),
    tertiary = StreamzeeGreen,
    background = Color(0xFFF7F7FA),
    onBackground = Color(0xFF18181B),
    surface = Color.White,
    onSurface = Color(0xFF18181B),
    surfaceVariant = Color(0xFFECECF1),
    onSurfaceVariant = Color(0xFF5F5F6B),
    outline = Color(0xFF797985),
    outlineVariant = Color(0xFFD2D2DA),
    error = AccentRed,
    onError = Color.White,
)

@Composable
fun streamzeeTheme(
    themeMode: String = "System",
    accentName: String = "Purple",
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        "Light" -> false
        "Dark", "Lite Dark" -> true
        else -> systemDark
    }
    val accent = accentColor(accentName)
    val colorScheme = if (useDark) {
        darkScheme(accent, lite = themeMode == "Lite Dark")
    } else {
        lightScheme(accent)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !useDark
                isAppearanceLightNavigationBars = !useDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
