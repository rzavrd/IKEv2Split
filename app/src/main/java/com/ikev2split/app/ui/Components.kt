package com.ikev2split.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SubHeader(title: String, onBack: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(BarBg).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "back")
        }
        Text(title, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { trailing?.invoke() }
    }
}

@Composable
fun SubPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SubHeader(title, onBack)
        content()
    }
}

@Composable
fun SectionTitle(t: String) {
    Text(t, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 6.dp))
}

@Composable
fun SettingRow(
    icon: ImageVector, title: String, subtitle: String? = null, value: String? = null,
    onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null,
) {
    val base = if (onClick != null) Modifier.fillMaxWidth().clickable(onClick = onClick) else Modifier.fillMaxWidth()
    Row(base.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(26.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp)
            if (subtitle != null) Text(subtitle, color = Muted, fontSize = 13.sp)
        }
        if (value != null) Text(value, Modifier.padding(start = 8.dp), fontSize = 15.sp)
        if (trailing != null) trailing()
        else if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
    }
}

@Composable
fun ToggleRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(icon, title, subtitle, trailing = {
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Teal, checkedThumbColor = Color.White, checkedBorderColor = Teal),
        )
    })
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(24.dp)).background(BarBg).padding(4.dp),
    ) {
        options.forEachIndexed { i, o ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                    .background(if (i == selected) Track.copy(alpha = if (Pal.cur.dark) 0.35f else 0.9f) else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(o, color = if (i == selected) Teal else Ink, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
fun SignalBars(level: Int) {
    Canvas(Modifier.size(18.dp, 16.dp)) {
        val bw = size.width / 5f
        for (i in 0 until 3) {
            val h = size.height * (0.4f + 0.3f * i)
            drawRoundRect(
                if (i < level) Teal else Muted.copy(alpha = 0.35f),
                Offset(i * bw * 2f, size.height - h), Size(bw, h), CornerRadius(2f, 2f),
            )
        }
    }
}
