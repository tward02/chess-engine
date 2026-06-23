package com.tward.ui.board

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColours = lightColors(
    primary = Color(0xFF4E7837),
    primaryVariant = Color(0xFF2F5320),
    secondary = Color(0xFFB58863),
    background = Color(0xFFF3F1EC),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1B1B1B),
    onSurface = Color(0xFF1B1B1B)
)

private val DarkColours = darkColors(
    primary = Color(0xFF7FA86A),
    primaryVariant = Color(0xFFB7D29E),
    secondary = Color(0xFFB58863),
    background = Color(0xFF1E1F22),
    surface = Color(0xFF2B2D31),
    onPrimary = Color(0xFF11240A),
    onBackground = Color(0xFFE6E6E6),
    onSurface = Color(0xFFE6E6E6)
)

/**
 * App theme with a chess-green palette. Follows the OS light/dark setting by default
 * ([isSystemInDarkTheme]); pass [darkTheme] explicitly to override (e.g. a manual toggle).
 */
@Composable
fun ChessTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colors = if (darkTheme) DarkColours else LightColours, content = content)
}
