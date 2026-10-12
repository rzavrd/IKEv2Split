package com.ikev2split.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ikev2split.app.VpnViewModel
import com.ikev2split.app.data.countryName
import com.ikev2split.app.vpn.VpnController
import kotlin.math.cos
import kotlin.math.sin

private fun gaugeFrac(m: Double): Float {
    val pts = listOf(0.0 to 0f, 100.0 to 0.25f, 250.0 to 0.5f, 500.0 to 0.75f, 1000.0 to 1f)
    if (m >= 1000) return 1f
    for (i in 1 until pts.size) {
        if (m <= pts[i].first) {
            val (a, fa) = pts[i - 1]
            val (b, fb) = pts[i]
            return fa + ((m - a) / (b - a)).toFloat() * (fb - fa)
        }
    }
    return 1f
}

private fun f1(d: Double?): String = d?.let { "%.1f".format(it) } ?: "-"

@Composable
fun SpeedTestScreen(vm: VpnViewModel) {
    val s by vm.speed.collectAsState()
    val st by vm.state.collectAsState()
    val servers by vm.servers.collectAsState()
    val sel by vm.selected.collectAsState()
    val ss by vm.stats.collectAsState()
    val connected = st is VpnController.State.Connected
    val server = servers.firstOrNull { it.address == sel }
    val shown = if (s.running) s.liveMbps else (s.down ?: 0.0)
    val frac by animateFloatAsState(gaugeFrac(shown), tween(300), label = "gauge")
    val measurer = rememberTextMeasurer()
    val tick = TextStyle(color = Muted, fontSize = 14.sp)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.BottomCenter) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2
                val cy = size.height - 64.dp.toPx()
                val r = size.width * 0.36f
                drawCircle(Brush.radialGradient(listOf(Glow, Color.Transparent), Offset(cx, cy), size.width * 0.8f), size.width * 0.8f, Offset(cx, cy))
                val sw = 10.dp.toPx()
                drawArc(Track.copy(alpha = 0.25f), 180f, 180f, false, Offset(cx - r, cy - r), Size(2 * r, 2 * r), style = Stroke(sw, cap = StrokeCap.Round))
                drawArc(Teal, 180f, 180f * frac, false, Offset(cx - r, cy - r), Size(2 * r, 2 * r), style = Stroke(sw, cap = StrokeCap.Round))
                listOf(0 to 0f, 100 to 0.25f, 250 to 0.5f, 500 to 0.75f, 1000 to 1f).forEach { (v, f) ->
                    val a = Math.toRadians((180 + 180 * f).toDouble())
                    val rr = r * 0.78f
                    val txt = measurer.measure(v.toString(), tick)
                    drawText(txt, topLeft = Offset(cx + (rr * cos(a)).toFloat() - txt.size.width / 2f, cy + (rr * sin(a)).toFloat() - txt.size.height / 2f))
                }
            }
            Box(
                Modifier.padding(bottom = 4.dp).size(120.dp).clip(CircleShape).background(CardBg)
                    .clickable { if (s.running) vm.stopSpeed() else vm.startSpeed() },
                contentAlignment = Alignment.Center,
            ) { Icon(if (s.running) Icons.Filled.Stop else Icons.Filled.PlayArrow, null, Modifier.size(56.dp)) }
        }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                when {
                    s.running -> "${s.phase}  ${"%.1f".format(shown)} Mbps"
                    s.phase == "Done" -> "Test complete"
                    else -> "Tap to start the Speed Test"
                },
                Modifier.clip(RoundedCornerShape(16.dp)).background(if (Pal.cur.dark) Color.White else Color(0xFF14202B)).padding(horizontal = 22.dp, vertical = 14.dp),
                color = if (Pal.cur.dark) Color(0xFF14202B) else Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            )
        }
        if (s.error != null) Text(s.error!!, Modifier.padding(20.dp), color = Danger, fontSize = 13.sp)

        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.fillMaxWidth().clip(CardShape).background(CardBg).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoLine("VPN Server:", server?.name ?: "-", true)
                InfoLine("VPN IP:", if (connected) (ss.publicIp ?: "-") else "-")
                InfoLine("Protocol:", if (connected) "IKEv2" else "-")
                InfoLine("Test Endpoint:", "speed.cloudflare.com")
                InfoLine("Test Endpoint IP:", s.endpointIp ?: "-")
                Box(Modifier.fillMaxWidth().height(1.dp).background(Muted.copy(alpha = 0.3f)))
                BigLine("Download", "${f1(s.down)} Mbps")
                BigLine("Upload", "${f1(s.up)} Mbps")
                BigLine("Latency", "${s.latency ?: "-"} ms")
                BigLine("Jitter", "${s.jitter ?: "-"} ms")
                BigLine("Packet loss", "${s.loss ?: "-"} %")
            }
            Column(Modifier.fillMaxWidth().clip(CardShape).background(CardBg).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoLine("Your Country:", s.country?.takeIf { it.isNotEmpty() }?.let { countryName(it) } ?: "-")
                InfoLine("Your Provider:", s.provider?.takeIf { it.isNotEmpty() } ?: "-")
                InfoLine("Your IP:", s.ip?.takeIf { it.isNotEmpty() } ?: "-")
            }
            Text("The test runs through the VPN while connected, so it shows the tunnel's speed.", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun InfoLine(k: String, v: String, accent: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, fontSize = 17.sp)
        Text(v, fontSize = 17.sp, color = if (accent) Teal else Ink, fontWeight = if (accent) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun BigLine(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, fontSize = 18.sp, color = Muted)
        Text(v, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}
