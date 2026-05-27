package com.example.streamzee.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val StreamzeeColorScheme = darkColorScheme(
    primary            = StreamzeePurple,
    onPrimary          = Color.White,
    secondary          = StreamzeeIndigo,
    onSecondary        = Color.White,
    tertiary           = StreamzeeGreen,
    background         = ScreenBackground,
    onBackground       = TextPrimary,
    surface            = CardBackground,
    onSurface          = TextPrimary,
    surfaceVariant     = CardBackground,
    onSurfaceVariant   = TextSecondary,
    error              = AccentRed,
    onError            = Color.White,
)

@Composable
fun streamzeeTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = StreamzeeColorScheme

    // Make status bar and navigation bar transparent
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = ScreenBackground.toArgb()
            window.navigationBarColor = SurfaceDark.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}