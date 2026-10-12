package com.ikev2split.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val faq = listOf(
    "I can't connect" to "Open Profile > Connection Log: it shows the exact error (for example AUTHENTICATION_FAILED means wrong username/password or certificate). Try another location, and make sure UDP ports 500 and 4500 aren't blocked by your network.",
    "Iranian sites don't open while connected" to "MYLO VPN sends all traffic through the VPN because Android's built-in IKEv2 can't exclude IP ranges. For Iran-direct routing install the strongSwan app, create an IKEv2 EAP profile with the same server and account, and paste Iran's IP ranges into 'Excluded subnets'.",
    "My internet stops when the VPN drops" to "MYLO VPN checks the tunnel every few seconds. If it is dead it stops the VPN so your normal internet comes back, then (with Auto reconnect on) connects to the fastest other location.",
    "How do I get a real kill switch?" to "Android only allows the system to enforce it. Profile > Internet Kill Switch opens the VPN settings: tap the gear next to MYLO VPN and enable Always-on VPN and Block connections without VPN.",
    "What is the Live ping?" to "On the home screen, 'Iran (direct)' is the latency of your real connection to Iranian sites, measured outside the tunnel. 'Via VPN' is the round trip through the tunnel.",
)

@Composable
fun HelpScreen(go: (String) -> Unit) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Help", Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        faq.forEachIndexed { i, (q, a) ->
            Column(Modifier.fillMaxWidth().clip(CardShape).background(CardBg).clickable { open = if (open == i) null else i }.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(q, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Icon(if (open == i) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
                }
                if (open == i) Text(a, Modifier.padding(top = 10.dp), color = Muted, fontSize = 14.sp)
            }
        }
        OutlinedButton({ go("log") }, Modifier.fillMaxWidth()) { Text("Open connection log") }
        OutlinedButton(
            { ctx.startActivity(Intent(android.provider.Settings.ACTION_VPN_SETTINGS)) }, Modifier.fillMaxWidth(),
        ) { Text("Open Android VPN settings") }
    }
}
