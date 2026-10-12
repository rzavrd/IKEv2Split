package com.ikev2split.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ikev2split.app.VpnViewModel
import com.ikev2split.app.data.flagEmoji
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatsScreen(vm: VpnViewModel) {
    val ss by vm.stats.collectAsState()
    val hist by vm.history.collectAsState()
    LaunchedEffect(Unit) { vm.refreshHistory() }
    val fmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.US) }

    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Statistics", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)) }

        item {
            Column(Modifier.fillMaxWidth().clip(CardShape).background(CardBg).padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Live", color = Muted)
                    Text("Down ${fmtBits(ss.downBps)}", color = Teal, fontSize = 13.sp)
                    Text("Up ${fmtBits(ss.upBps)}", color = Pink, fontSize = 13.sp)
                }
                Canvas(Modifier.fillMaxWidth().height(110.dp).padding(top = 8.dp)) {
                    drawSeries(ss.downHist, Teal)
                    drawSeries(ss.upHist, Pink)
                }
            }
        }

        item {
            Column(Modifier.fillMaxWidth().clip(CardShape).background(CardBg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Data used", color = Muted)
                listOf("Today" to 1, "Last 7 days" to 7, "Last 30 days" to 30).forEach { (label, days) ->
                    val (rx, tx) = remember(ss.rx, ss.tx, hist) { vm.store.usage(days) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label)
                        Text("down ${fmtBytes(rx)}  up ${fmtBytes(tx)}", color = Muted, fontSize = 13.sp)
                    }
                }
            }
        }

        item { Text("Connection history", color = Muted, modifier = Modifier.padding(top = 4.dp)) }
        if (hist.isEmpty()) item { Text("No connections yet.", color = Muted, fontSize = 13.sp) }
        items(hist) { h ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardBg).padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("${flagEmoji(h.cc)}  ${h.server}", maxLines = 1, fontWeight = FontWeight.SemiBold)
                    Text("${fmt.format(Date(h.start))} - ${h.status}", color = Muted, fontSize = 12.sp)
                }
                Column {
                    Text(fmtDur((h.end - h.start) / 1000), fontSize = 14.sp)
                    Text(fmtBytes(h.rx + h.tx), color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

private fun DrawScope.drawSeries(values: List<Long>, color: Color) {
    if (values.size < 2) return
    val max = (values.maxOrNull() ?: 1L).coerceAtLeast(1L).toFloat()
    val path = Path()
    values.forEachIndexed { i, v ->
        val x = size.width * i / (values.size - 1)
        val y = size.height - (v / max) * size.height * 0.95f
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color, style = Stroke(2.dp.toPx()))
}
