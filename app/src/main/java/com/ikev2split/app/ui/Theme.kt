package com.ikev2split.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class Palette(
    val name: String, val dark: Boolean,
    val bg: Color, val card: Color, val ink: Color, val muted: Color,
    val accent: Color, val idle: Color, val glow: Color,
    val bar: Color, val barSel: Color, val track: Color,
)

object Palettes {
    val twilight = Palette("Twilight", true, Color(0xFF050F18), Color(0xFF111F2C), Color.White, Color(0xFF8FA3B5),
        Color(0xFF4DB89C), Color(0xFF4DB89C), Color(0xFF123F3C), Color(0xFF16293A), Color(0xFF0A1B2C), Color(0xFFCDD3D8))
    val midnight = Palette("Midnight", true, Color(0xFF061523), Color(0xFF12263A), Color.White, Color(0xFF8FA3B5),
        Color(0xFFC6E03F), Color(0xFFC6E03F), Color(0xFF6FA43E), Color(0xFF16293C), Color(0xFF0A1B2C), Color(0xFFCDD3D8))
    val sand = Palette("Sand", false, Color(0xFFF7F6F1), Color.White, Color(0xFF14202B), Color(0xFF6B7A86),
        Color(0xFF2E9E6B), Color(0xFF2E9E6B), Color(0xFFC9DC3A), Color(0xFFEDECE5), Color.White, Color(0xFFD5D9DC))
    val sky = Palette("Sky", false, Color(0xFFF3F6F5), Color.White, Color(0xFF14202B), Color(0xFF66777F),
        Color(0xFF2F8F83), Color(0xFF2F8F83), Color(0xFF9CCBC5), Color(0xFFE8EFEE), Color.White, Color(0xFFD5DCDC))
    val autumn = Palette("Autumn", false, Color(0xFFF7F6F1), Color.White, Color(0xFF14202B), Color(0xFF6B7A86),
        Color(0xFF3F9A5A), Color(0xFF3F9A5A), Color(0xFF86B84F), Color(0xFFEDECE5), Color.White, Color(0xFFD5D9DC))

    val white = Palette("White", false, Color(0xFFFFFFFF), Color(0xFFF2F4F6), Color(0xFF101820), Color(0xFF66737F),
        Color(0xFF1FA37A), Color(0xFF1FA37A), Color(0xFFBFE8DA), Color(0xFFEEF0F3), Color(0xFFFFFFFF), Color(0xFFD8DDE2))

    val names = listOf("System", "White", "Sand", "Midnight", "Sky", "Twilight", "Autumn")

    fun byName(n: String, systemDark: Boolean): Palette = when (n) {
        "White" -> white
        "Sand" -> sand
        "Midnight" -> midnight
        "Sky" -> sky
        "Twilight" -> twilight
        "Autumn" -> autumn
        else -> if (systemDark) twilight else sand
    }
}

/** Current palette. Set outside composition (MainActivity / theme picker); every composable reads it. */
object Pal {
    var cur by mutableStateOf(Palettes.twilight)
}

val Bg: Color get() = Pal.cur.bg
val CardBg: Color get() = Pal.cur.card
val Ink: Color get() = Pal.cur.ink
val Muted: Color get() = Pal.cur.muted
val Teal: Color get() = Pal.cur.accent
val Pink: Color get() = Pal.cur.idle
val Glow: Color get() = Pal.cur.glow
val Track: Color get() = Pal.cur.track
val BarBg: Color get() = Pal.cur.bar
val Danger = Color(0xFFE5484D)
val CardShape = RoundedCornerShape(20.dp)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val p = Pal.cur
    val scheme = if (p.dark) darkColorScheme(
        primary = p.accent, background = p.bg, surface = p.card, onSurface = p.ink, onBackground = p.ink, secondary = p.accent,
    ) else lightColorScheme(
        primary = p.accent, background = p.bg, surface = p.card, onSurface = p.ink, onBackground = p.ink, secondary = p.accent,
    )
    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = p.bg, contentColor = p.ink) { Box(Modifier.fillMaxSize()) { content() } }
    }
}
