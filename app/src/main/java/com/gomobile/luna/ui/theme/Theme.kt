package com.gomobile.luna.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF0F3D6E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E4F7),
    onPrimaryContainer = Color(0xFF001C3A),
    secondary = Color(0xFF00658E),
    secondaryContainer = Color(0xFFC7E7FF),
    background = Color(0xFFF6F8FB),
    surface = Color.White,
    surfaceVariant = Color(0xFFE6EBF1),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA6C8FF),
    onPrimary = Color(0xFF00315F),
    primaryContainer = Color(0xFF1E4B80),
    onPrimaryContainer = Color(0xFFD5E4F7),
    secondary = Color(0xFF85CFFF),
    secondaryContainer = Color(0xFF004C6C),
    background = Color(0xFF10141A),
    surface = Color(0xFF181C22),
    surfaceVariant = Color(0xFF2A3038),
)

/** Status colours used for achievement and aging badges. */
object StatusColors {
    val good = Color(0xFF1B873F)
    val warn = Color(0xFFC77700)
    val bad = Color(0xFFC62828)
}

@Composable
fun LunaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
