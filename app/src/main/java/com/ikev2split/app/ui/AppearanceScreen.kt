package com.ikev2split.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.ikev2split.app.R
import com.ikev2split.app.VpnViewModel
import com.ikev2split.app.data.IconStyles

private val iconColors = mapOf(
    "Classic" to (Color(0xFF08CAD7) to Color(0xFF08CAD7)),
    "Dynamic" to (Color(0xFFE0709A) to Color(0xFF5B2A86)),
    "Neon" to (Color(0xFFB5E03A) to Color(0xFF1C8B7A)),
    "Grey" to (Color(0xFF8D99A6) to Color(0xFF2A3A4C)),
)

@Composable
fun AppearanceScreen(vm: VpnViewModel, onBack: () -> Unit) {
    val theme by vm.theme.collectAsState()
    val icon by vm.iconStyle.collectAsState()
    val sysDark = isSystemInDarkTheme()

    Column(Modifier.fillMaxSize()) {
        SubHeader("Appearance", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text("Color Scheme", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 14.dp))
            Palettes.names.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { n -> SchemeThumb(n, n == theme, sysDark) { vm.setTheme(n) } }
                }
            }
            Text("App Icon", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconStyles.all.forEach { n -> IconTile(n, n == icon) { vm.setIcon(n) } }
            }
            Text("The home-screen icon may take a few seconds to update.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
        }
    }
}

@Composable
private fun SchemeThumb(name: String, selected: Boolean, sysDark: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier.width(100.dp).height(150.dp).clip(shape)
                .border(if (selected) 3.dp else 1.dp, if (selected) Teal else Muted.copy(alpha = 0.4f), shape),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                if (name == "System") {
                    clipRect(0f, 0f, size.width / 2, size.height) { drawThumb(Palettes.sand) }
                    clipRect(size.width / 2, 0f, size.width, size.height) { drawThumb(Palettes.twilight) }
                } else drawThumb(Palettes.byName(name, sysDark))
            }
        }
        Text(name, Modifier.padding(top = 6.dp), color = if (selected) Teal else Ink, fontSize = 15.sp)
    }
}

private fun DrawScope.drawThumb(p: Palette) {
    drawRect(p.bg)
    val hero = size.height * 0.4f
    drawRect(
        Brush.radialGradient(listOf(p.glow, p.glow.copy(alpha = 0.35f)), Offset(size.width / 2, hero), size.width * 0.8f),
        topLeft = Offset.Zero, size = Size(size.width, hero),
    )
    drawCircle(if (p.dark) p.card else Color.White, size.width * 0.15f, Offset(size.width / 2, hero))
    val card = if (p.dark) p.card else p.bar
    val x0 = size.width * 0.1f
    val cw = size.width * 0.8f
    val ch = size.height * 0.085f
    val r = CornerRadius(6f, 6f)
    drawRoundRect(card, Offset(x0, hero + size.height * 0.1f), Size(cw, ch), r)
    val half = (cw - size.width * 0.04f) / 2
    for (row in 0..1) {
        val y = hero + size.height * (0.22f + 0.1f * row)
        drawRoundRect(card, Offset(x0, y), Size(half, ch), r)
        drawRoundRect(card, Offset(x0 + half + size.width * 0.04f, y), Size(half, ch), r)
    }
}

@Composable
private fun IconTile(name: String, selected: Boolean, onClick: () -> Unit) {
    val (c1, c2) = iconColors[name] ?: (Teal to Teal)
    val shape = RoundedCornerShape(18.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier.size(72.dp).clip(shape).background(Brush.linearGradient(listOf(c1, c2)))
                .border(if (selected) 3.dp else 0.dp, if (selected) Teal else Color.Transparent, shape),
            contentAlignment = Alignment.Center,
        ) { Image(painterResource(R.drawable.ic_logo_fg), null, Modifier.size(72.dp)) }
        Text(name, Modifier.padding(top = 6.dp), color = if (selected) Teal else Ink, fontSize = 15.sp)
    }
}
