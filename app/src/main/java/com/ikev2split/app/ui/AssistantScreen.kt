package com.ikev2split.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.ikev2split.app.data.Check
import com.ikev2split.app.data.deviceChecks

@Composable
fun AssistantScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val checks = remember { deviceChecks(ctx) }
    Column(Modifier.fillMaxSize()) {
        SubHeader("Secure Device Assistant", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Check your device is fully protected:", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 10.dp))
            checks.forEach { c ->
                Column(Modifier.fillMaxWidth().clip(CardShape).background(CardBg).padding(18.dp)) {
                    CheckRow(c.title, c.desc, c.ok, 26)
                    if (c.detail != null) Text(c.detail, Modifier.padding(start = 42.dp, top = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (c.subTitle != null) {
                        Box(Modifier.padding(top = 14.dp, start = 42.dp).fillMaxWidth().height(1.dp).background(Muted.copy(alpha = 0.4f)))
                        Spacer(Modifier.size(14.dp))
                        Row(Modifier.padding(start = 42.dp)) { CheckRow(c.subTitle, c.subDesc.orEmpty(), c.subOk, 22) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(title: String, desc: String, ok: Boolean, icon: Int) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning, null, Modifier.size(icon.dp), tint = if (ok) Teal else Danger)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, fontSize = 19.sp)
            Text(desc, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
