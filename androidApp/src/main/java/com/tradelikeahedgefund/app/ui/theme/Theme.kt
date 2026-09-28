package com.tradelikeahedgefund.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Wall Street palette, extracted from the web app's CSS variables.
val NavyBg = Color(0xFF0A0E1A)
val NavySurface = Color(0xFF131A2E)
val BronzeGold = Color(0xFFD4AF37)
val GoldDeep = Color(0xFF8A6D2B)
val TabActive = Color(0xFFE9C766)
val TabIdle = Color(0xFF7D8DB0)
val LineColor = Color(0xFF26314F)
val Ink = Color(0xFFF1F5F9)
val Muted = Color(0xFF8B94AD)
val BullGreen = Color(0xFF22C55E)
val BearRed = Color(0xFFEF4444)
val InfoBlue = Color(0xFF3B82F6)
val Amber = Color(0xFFF59E0B)

private val DarkScheme = darkColorScheme(
    primary = BronzeGold,
    onPrimary = NavyBg,
    secondary = TabActive,
    background = NavyBg,
    onBackground = Ink,
    surface = NavySurface,
    onSurface = Ink,
    surfaceVariant = LineColor,
    onSurfaceVariant = Muted,
    error = BearRed
)

@Composable
fun TlhfTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
