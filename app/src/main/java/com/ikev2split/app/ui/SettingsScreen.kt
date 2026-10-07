package com.ikev2split.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ikev2split.app.VpnViewModel

@Composable
fun SettingsScreen(vm: VpnViewModel) {
    val st = vm.store
    val ctx = LocalContext.current
    var user by remember { mutableStateOf(st.user) }
    var pass by remember { mutableStateOf(st.pass) }
    var rid by remember { mutableStateOf(st.remoteId) }
    var mtu by remember { mutableStateOf(st.mtu.toString()) }
    var failover by remember { mutableStateOf(st.failover) }
    var hasCa by remember { mutableStateOf(st.hasCa()) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { vm.importCa(uri); hasCa = st.hasCa() }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Text("Account", color = Muted)
        OutlinedTextField(user, { user = it; st.user = it.trim() }, Modifier.fillMaxWidth(), label = { Text("VPN username") }, singleLine = true)
        OutlinedTextField(pass, { pass = it; st.pass = it }, Modifier.fillMaxWidth(), label = { Text("VPN password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation())

        Text("IKEv2", color = Muted, modifier = Modifier.padding(top = 8.dp))
        OutlinedTextField(rid, { rid = it; st.remoteId = it.trim() }, Modifier.fillMaxWidth(), label = { Text("Remote ID (server identity)") }, singleLine = true)
        OutlinedTextField(mtu, { val d = it.filter(Char::isDigit).take(4); mtu = d; d.toIntOrNull()?.let { v -> st.mtu = v } }, Modifier.fillMaxWidth(),
            label = { Text("MTU (1280-1500)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

        Text(if (hasCa) "Server certificate: imported" else if (st.hasBundledCa()) "Server certificate: built-in" else "Server certificate: system trust store (none bundled)", color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button({ picker.launch(arrayOf("*/*")) }) { Text("Import .der / .pem / .crt") }
            if (hasCa) OutlinedButton({ st.clearCa(); hasCa = false }) { Text("Remove") }
        }

        var upd by remember { mutableStateOf(st.updateUrl) }
        OutlinedTextField(upd, { upd = it; st.updateUrl = it.trim() }, Modifier.fillMaxWidth(), label = { Text("Update URL (https://.../update.json)") }, singleLine = true)

        Text("Reliability", color = Muted, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Auto failover", fontWeight = FontWeight.SemiBold)
                Text("If the tunnel stays down for 15 s, switch to the fastest other server", color = Muted, fontSize = 12.sp)
            }
            Switch(failover, { failover = it; st.failover = it })
        }
        Text("Kill switch", color = Muted, modifier = Modifier.padding(top = 8.dp))
        Text("Android only lets the system enforce this: open VPN settings, tap the gear next to this app, and turn on 'Always-on VPN' and 'Block connections without VPN'.", fontSize = 12.sp, color = Muted)
        OutlinedButton({ ctx.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }) { Text("Open system VPN settings") }
        Text("Changes apply on the next connect.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}
