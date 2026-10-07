package com.ikev2split.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.ikev2split.app.data.Geo

private const val SPAN_LON = 96.0
private const val SPAN_LAT = 38.4   // 96 x 38.4 degrees = 2.5 : 1, like the card

/**
 * Regional dot map zoomed on the selected server (about a continent wide), with a pulsing marker.
 * When Tehran is inside the window an arc to the server is drawn too.
 */
@Composable
fun WorldMap(cc: String?, connected: Boolean, modifier: Modifier = Modifier) {
    val anim = rememberInfiniteTransition(label = "map")
    val t by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "pulse")

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val srv = Geo.latLon(cc)
        val me = Geo.latLon("IR")

        // window centred on the server (or on Iran when nothing is selected), kept inside the world
        val cLat = srv?.first ?: me?.first ?: 40.0
        val cLon = srv?.second ?: me?.second ?: 30.0
        val lon0 = (cLon - SPAN_LON / 2).coerceIn(-180.0, 180.0 - SPAN_LON)
        val latTop = (cLat + SPAN_LAT / 2).coerceIn(-60.0 + SPAN_LAT, 84.0)

        fun px(lon: Double) = ((lon - lon0) / SPAN_LON * w).toFloat()
        fun py(lat: Double) = ((latTop - lat) / SPAN_LAT * h).toFloat()

        drawRect(Brush.verticalGradient(listOf(Color(0xFF1E2B3A), Color(0xFF172230))))

        // land dots (only the visible window is drawn)
        val cell = (w / SPAN_LON).toFloat()
        val r = cell * 0.36f
        val land = Color(0xFF63758C)
        val colFrom = (lon0 + 180.0).toInt().coerceIn(0, Geo.COLS - 1)
        val colTo = (lon0 + SPAN_LON + 180.0).toInt().coerceIn(0, Geo.COLS - 1)
        val rowFrom = (84.0 - latTop).toInt().coerceIn(0, Geo.ROWS - 1)
        val rowTo = (84.0 - (latTop - SPAN_LAT)).toInt().coerceIn(0, Geo.ROWS - 1)
        for (row in rowFrom..rowTo) {
            val line = Geo.LAND[row]
            val y = py(84.0 - (row + 0.5))
            for (col in colFrom..colTo) {
                if (line[col] == '1') drawCircle(land, r, Offset(px(-180.0 + col + 0.5), y))
            }
        }

        val accent = if (connected) Teal else Pink
        fun inside(x: Float, y: Float) = x in 0f..w && y in 0f..h

        if (srv != null) {
            val sx = px(srv.second); val sy = py(srv.first)
            if (me != null) {
                val mx = px(me.second); val my = py(me.first)
                if (inside(mx, my)) {
                    val dist = kotlin.math.hypot(sx - mx, sy - my)
                    val path = Path().apply {
                        moveTo(mx, my)
                        quadraticBezierTo((mx + sx) / 2f, minOf(my, sy) - dist * 0.35f, sx, sy)
                    }
                    drawPath(path, accent.copy(alpha = 0.2f), style = Stroke(width = cell * 1.6f, cap = StrokeCap.Round))
                    val phase = if (connected) -t * cell * 6f else 0f
                    drawPath(
                        path, accent.copy(alpha = if (connected) 0.95f else 0.55f),
                        style = Stroke(width = cell * 0.5f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(cell * 1.8f, cell * 1.8f), phase)),
                    )
                    drawCircle(Color.White.copy(alpha = 0.25f), cell * 2.4f, Offset(mx, my))
                    drawCircle(Color.White, cell * 0.9f, Offset(mx, my))
                }
            }
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), Color.Transparent), Offset(sx, sy), cell * 7f), cell * 7f, Offset(sx, sy))
            drawCircle(accent.copy(alpha = (1f - t) * 0.6f), cell * (1.5f + t * 6f), Offset(sx, sy), style = Stroke(cell * 0.45f))
            drawCircle(Color.White, cell * 1.2f, Offset(sx, sy))
            drawCircle(accent, cell * 0.85f, Offset(sx, sy))
        }
    }
}
