package com.kiara.ai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class Palette(val primary: Color, val secondary: Color)

val ThemeNames = listOf("Neon Purple", "Cyber Blue", "Rose Neon")

fun paletteFor(name: String) = when (name) {
    "Cyber Blue" -> Palette(Color(0xFF3DA5FF), Color(0xFF7B61FF))
    "Rose Neon" -> Palette(Color(0xFFFF5FA2), Color(0xFFA56BFF))
    else -> Palette(Color(0xFFB66DFF), Color(0xFF4D8DFF))
}

val LocalPalette = staticCompositionLocalOf { paletteFor("Neon Purple") }

@Composable
fun KiaraTheme(themeName: String, content: @Composable () -> Unit) {
    val p = paletteFor(themeName)
    val scheme = darkColorScheme(
        primary = p.primary,
        secondary = p.secondary,
        tertiary = p.secondary,
        background = Color(0xFF0A0714),
        surface = Color(0xFF140E2B),
        surfaceVariant = Color(0xFF1E1640),
        onPrimary = Color.Black,
        onSecondary = Color.White,
        onBackground = Color(0xFFEDE7FF),
        onSurface = Color(0xFFEDE7FF),
        onSurfaceVariant = Color(0xFFB9ADDB),
        outline = Color(0xFF5B4B8F)
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

@Composable
fun AppBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A0714), Color(0xFF140A33), Color(0xFF0A1030)))),
        content = content
    )
}

/** Translucent "glass" panel with a neon gradient edge. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.07f))
            .border(BorderStroke(1.dp, Brush.linearGradient(listOf(p.primary.copy(alpha = 0.6f), p.secondary.copy(alpha = 0.25f)))), shape)
            .padding(14.dp),
        content = content
    )
}
