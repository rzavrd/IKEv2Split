package com.ikev2split.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ikev2split.app.VpnViewModel
import com.ikev2split.app.data.deviceChecks
import com.ikev2split.app.data.flagEmoji
import com.ikev2split.app.vpn.VpnController
import kotlinx.coroutines.delay

/**
 * Proportions follow the reference screenshot (dp at ~411dp width): hero 332, power button 108,
 * location card ~48, map 2.5:1, two cards 85, assistant card 103, 12dp gaps, 20dp side margins.
 */
@Composable
fun HomeScreen(vm: VpnViewModel, onPower: () -> Unit, onChange: () -> Unit, onAssistant: () -> Unit, onAppearance: () -> Unit) {
    val ctx = LocalContext.current
    val st by vm.state.collectAsState()
    val servers by vm.servers.collectAsState()
    val sel by vm.selected.collectAsState()
    val auto by vm.auto.collectAsState()
    val showIp by vm.showIp.collectAsState()
    val live by vm.live.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    LaunchedEffect(Unit) { while (true) { vm.measureLiveOnce(); delay(2000) } }
    LaunchedEffect(Unit) { vm.livePingLoop(60_000) }   // keeps server pings fresh for "Fastest Location"

    val connected = st as? VpnController.State.Connected
    val connecting = st is VpnController.State.Connecting
    val failed = st as? VpnController.State.Failed
    val server = servers.firstOrNull { it.address == sel }
    val best = servers.filter { it.pingMs != null }.minByOrNull { it.pingMs!! }
    val shown = if (auto) (best ?: server) else server
    val checks = remember { deviceChecks(ctx) }
    val done = checks.count { it.ok }

    val rot by rememberInfiniteTransition(label = "r").animateFloat(
        0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "rot",
    )

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        // ---------------- hero
        Box(Modifier.fillMaxWidth().height(332.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2, size.height - 54.dp.toPx())
                drawCircle(Brush.radialGradient(listOf(Glow, Color.Transparent), c, size.width * 0.62f), size.width * 0.62f, c)
                listOf(0.575f, 0.40f).forEach {
                    drawCircle(Glow.copy(alpha = 0.6f), size.width * it, c, style = Stroke(1.5.dp.toPx()))
                }
            }
            IconButton(onAppearance, Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 14.dp)) {
                Icon(Icons.Filled.Palette, "appearance", Modifier.size(30.dp))
            }
            Box(Modifier.align(Alignment.BottomCenter).size(108.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    if (connecting) {
                        val w = 3.dp.toPx()
                        drawArc(Teal, rot, 110f, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
                    }
                }
                Box(Modifier.fillMaxSize().clip(CircleShape).background(CardBg).clickable(onClick = onPower), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PowerSettingsNew, "power", Modifier.size(46.dp), tint = if (connected != null) Teal else if (connecting) Muted else Pink)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (!connecting) {
                Icon(if (connected != null) Icons.Filled.Lock else Icons.Filled.LockOpen, null, Modifier.size(22.dp), tint = if (connected != null) Teal else Ink)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                when { connected != null -> "Protected"; connecting -> "Connecting"; else -> "Not Protected" },
                fontSize = 22.sp, fontWeight = FontWeight.Bold,
            )
        }
        if (connecting) {
            val d by vm.detail.collectAsState()
            Text(d, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 2.dp), color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        failed?.let {
            Text(it.message, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp), color = Danger, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(14.dp))

        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ---------------- location card (~48dp)
            Row(
                Modifier.fillMaxWidth().clip(CardShape).background(CardBg).clickable(onClick = onChange).padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(flagEmoji(shown?.cc.orEmpty()), fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (auto) (best?.name ?: "Measuring...") else "Selected Location", color = Muted, fontSize = 12.sp, maxLines = 1)
                    Text(if (auto) "Fastest Location" else (server?.name ?: "No server selected"), fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                Text("Change", fontSize = 15.sp)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.LocationOn, null, Modifier.size(20.dp))
            }

            // ---------------- map card (2.5 : 1, zoomed on the server's region)
            Box(Modifier.fillMaxWidth().aspectRatio(2.49f).clip(CardShape).background(Color(0xFF172230))) {
                WorldMap(shown?.cc, connected != null, Modifier.fillMaxSize())
            }

            // ---------------- time protected + protocol (equal widths, 85dp)
            val week = remember(now / 10_000) { vm.store.week() }
            val total = week.sum()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).height(85.dp).clip(CardShape).background(CardBg).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text("Time Protected", color = Muted, fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text(if (total >= 3600) "${total / 3600}h" else "${total / 60}m", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("this week", color = Muted, fontSize = 11.sp)
                        }
                        val max = (week.maxOrNull() ?: 0L).coerceAtLeast(1800L)
                        val todayIdx = java.time.LocalDate.now().dayOfWeek.value - 1
                        week.forEachIndexed { i, s ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 3.dp)) {
                                Box(Modifier.width(8.dp).height(30.dp).clip(RoundedCornerShape(4.dp)).background(if (i > todayIdx) Track.copy(alpha = 0.25f) else Track), contentAlignment = Alignment.BottomCenter) {
                                    Box(Modifier.fillMaxWidth().fillMaxHeight((s.toFloat() / max).coerceIn(0.06f, 1f)).background(if (i == todayIdx) Teal else Teal.copy(alpha = 0.55f)))
                                }
                                Text("MTWTFSS"[i].toString(), fontSize = 8.sp, color = Muted)
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f).height(85.dp).clip(CardShape).background(CardBg).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Protocol", Modifier.weight(1f), color = Muted, fontSize = 14.sp)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Selected:", fontSize = 12.sp)
                    Text("IKEv2", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }

            // ---------------- secure device assistant (~103dp)
            Column(
                Modifier.fillMaxWidth().height(103.dp).clip(CardShape).background(CardBg).clickable(onClick = onAssistant).padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Secure Device Assistant", Modifier.weight(1f), color = Muted, fontSize = 14.sp)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp))
                }
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("$done out of ${checks.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("marked as done", fontSize = 15.sp)
                    }
                    Box(Modifier.size(104.dp, 54.dp), contentAlignment = Alignment.BottomCenter) {
                        Canvas(Modifier.fillMaxWidth().height(54.dp)) {
                            val sw = 14.dp.toPx()
                            val d = size.width - sw
                            val tl = Offset(sw / 2, sw / 2)
                            drawArc(Track.copy(alpha = 0.2f), 180f, 180f, false, tl, Size(d, d), style = Stroke(sw))
                            drawArc(Teal.copy(alpha = 0.85f), 180f, 180f * done / checks.size.coerceAtLeast(1), false, tl, Size(d, d), style = Stroke(sw))
                        }
                        Text("$done / ${checks.size}", color = Muted, fontSize = 16.sp, modifier = Modifier.padding(bottom = 2.dp))
                    }
                }
            }

            // ---------------- extras (below the reference layout)
            Row(Modifier.fillMaxWidth().clip(CardShape).background(CardBg).padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Live ping - Iran (direct)", color = Muted, fontSize = 12.sp)
                    Text(live.directMs?.let { "$it ms" } ?: (if (connected != null && !vm.store.directPing) "paused" else "no response"), fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        color = if (live.directMs == null) (if (connected != null && !vm.store.directPing) Muted else Danger) else Teal)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Via VPN", color = Muted, fontSize = 12.sp)
                    Text(if (connected != null) (live.vpnMs?.let { "$it ms" } ?: "...") else "-", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (connected != null) {
                val ss by vm.stats.collectAsState()
                Column(Modifier.fillMaxWidth().clip(CardShape).background(CardBg).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Metric("Download", fmtBits(ss.downBps), Teal)
                        Metric("Upload", fmtBits(ss.upBps), Pink)
                        Metric("Latency", ss.latencyMs?.let { "$it ms" } ?: "-", Ink)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Metric("Public IP", if (showIp) (ss.publicIp ?: "...") else "Hidden", Ink)
                        Metric("RX", fmtBytes(ss.rx), Ink)
                        Metric("TX", fmtBytes(ss.tx), Ink)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color) {
    Column {
        Text(label, color = Muted, fontSize = 12.sp)
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
