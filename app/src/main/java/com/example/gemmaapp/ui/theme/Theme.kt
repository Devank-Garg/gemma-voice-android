package com.example.gemmaapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColorScheme = darkColorScheme(
    primary = BrandPurple,
    onPrimary = Color.White,
    primaryContainer = BrandPurple.copy(alpha = 0.15f),
    onPrimaryContainer = BrandPurpleLight,
    secondary = BrandCyan,
    onSecondary = Color.White,
    secondaryContainer = BrandCyan.copy(alpha = 0.15f),
    onSecondaryContainer = BrandCyanLight,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = CardDark,
    onSurfaceVariant = TextSecondary,
    outline = BorderDark,
    error = ErrorRed,
    onError = Color.White,
)

@Composable
fun GemmaAPPTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = Typography,
        content = content
    )
}
