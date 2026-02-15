package com.suraj.apps.omni.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6BFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7FF),
    secondary = Color(0xFF20A4F3),
    tertiary = Color(0xFFF4B400),
    background = Color(0xFFF5F7FB),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F3F7),
    onSurface = Color(0xFF1A1C1E),
    onSurfaceVariant = Color(0xFF555F71)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BA8FF),
    onPrimary = Color(0xFF002E90),
    primaryContainer = Color(0xFF0E4ED1),
    secondary = Color(0xFF83D1FF),
    tertiary = Color(0xFFFFCF5C),
    background = Color(0xFF0E121B),
    surface = Color(0xFF151B26),
    surfaceVariant = Color(0xFF222A38),
    onSurface = Color(0xFFE9EDF4),
    onSurfaceVariant = Color(0xFFC2C9D6)
)

@Composable
fun OmniTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = OmniTypography,
        content = content
    )
}
