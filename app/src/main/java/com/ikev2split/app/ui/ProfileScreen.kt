package com.ikev2split.app.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.ikev2split.app.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ikev2split.app.VpnViewModel
import com.ikev2split.app.update.Updater

@Composable
fun ProfileScreen(vm: VpnViewModel, go: (String) -> Unit) {
    val ctx = LocalContext.current
    val st = vm.store
    val theme by vm.theme.collectAsState()
    val auto by vm.auto.collectAsState()
    val showIp by vm.showIp.collectAsState()
    val failover by vm.failover.collectAsState()
    val directPing by vm.directPing.collectAsState()
    val verify by vm.verify.collectAsState()
    var reveal by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var mtuDialog by remember { mutableStateOf(false) }
    var bump by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Profile", Modifier.fillMaxWidth().padding(top = 20.dp), textAlign = TextAlign.Center, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        SectionTitle("Account Details")
        SettingRow(Icons.Filled.Person, "Subscription Type", value = "PureVPN")
        SettingRow(
            Icons.Filled.Info, "Username", value = if (reveal) st.user else "**********",
            trailing = { IconButton({ reveal = !reveal }) { Icon(if (reveal) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "show") } },
        )
        SettingRow(Icons.Filled.Shield, "Remote ID", value = st.remoteId)

        SectionTitle("VPN Settings")
        SettingRow(Icons.Filled.Palette, "Appearance", "Customize your app's look and feel", theme, { go("appearance") })
        SettingRow(Icons.Filled.CallSplit, "Split Tunneling", "Add IP addresses to bypass the VPN connection.", null, { dialog = "split" })
        ToggleRow(Icons.Filled.LocationOn, "Show IP on Map", "Show your public IP in the connection stats. Disable to hide it.", showIp) { vm.setShowIp(it) }
        SettingRow(
            Icons.Filled.Shield, "Internet Kill Switch", "Opens Android's Always-on VPN settings (Block connections without VPN).", "System",
            { ctx.startActivity(Intent(android.provider.Settings.ACTION_VPN_SETTINGS)) },
        )
        SettingRow(Icons.Filled.VpnKey, "VPN Protocol", "Choose the best protocol for your needs", "IKEv2", { dialog = "proto" })
        ToggleRow(Icons.Filled.Bolt, "Fastest Location auto-pick", "Ping all locations and connect to the fastest. Disable to use the selected location.", auto) { vm.setAuto(it) }
        ToggleRow(Icons.Filled.Refresh, "Auto reconnect", "If the tunnel dies, switch to the fastest other location. Your normal internet is restored first.", failover) { vm.setFailover(it) }
        ToggleRow(Icons.Filled.Shield, "Verify connection", "After connecting, check that traffic really passes; if not, try the next option automatically.", verify) { vm.setVerify(it) }
        ToggleRow(Icons.Filled.Speed, "Live ping while connected", "Show Iran-direct and server pings even when the VPN is on. If the internet stops working while connected, turn this off. Applies on the next connect.", directPing) { vm.setDirectPing(it) }
        SettingRow(
            Icons.Filled.Tune, "MTU", "Upper limit of the tunnel packet size. Android lowers it automatically when your network needs less. Applies on the next connect.",
            (st.mtu + 0 * bump).toString(), { mtuDialog = true },
        )
        SettingRow(Icons.Filled.Settings, "Advanced", "Server certificate, Remote ID, MTU, credentials", null, { go("advanced") })
        SettingRow(
            Icons.Filled.SystemUpdate, "Check for updates", "Download the newest version from the update server.",
            Updater.installedName(ctx), { vm.checkUpdate(true) },
        )
        SettingRow(Icons.Filled.Terminal, "Connection Log", "See exactly what happened during the last connection", null, { go("log") })
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF08CAD7)),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) { Image(painterResource(R.drawable.ic_logo_fg), null, Modifier.size(56.dp)) }
            Text("MYLO VPN", Modifier.padding(top = 8.dp), color = Muted, fontSize = 12.sp)
        }
    }

    if (mtuDialog) {
        var text by remember { mutableStateOf(st.mtu.toString()) }
        AlertDialog(
            onDismissRequest = { mtuDialog = false },
            title = { Text("MTU") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        text, { text = it.filter(Char::isDigit).take(4) }, singleLine = true, label = { Text("1280 - 1500") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1280, 1360, 1400, 1420).forEach { v ->
                            OutlinedButton({ text = v.toString() }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("$v", fontSize = 12.sp) }
                        }
                    }
                    OutlinedButton({ text = "1400" }) { Text("Recommended (1400)") }
                    Text(
                        "1400 is the recommended value. This is only a ceiling: raising it does not make the VPN faster, and if some sites hang, lower it (1360 or 1280).",
                        color = Muted, fontSize = 12.sp,
                    )
                }
            },
            confirmButton = { TextButton({ vm.setMtu(text.toIntOrNull() ?: 1400); bump++; mtuDialog = false }) { Text("Save") } },
            dismissButton = { TextButton({ mtuDialog = false }) { Text("Cancel") } },
        )
    }

    dialog?.let { d ->
        AlertDialog(
            onDismissRequest = { dialog = null },
            confirmButton = { TextButton({ dialog = null }) { Text("OK") } },
            title = { Text(if (d == "split") "Split Tunneling" else "VPN Protocol") },
            text = {
                Text(
                    if (d == "split")
                        "Sending Iranian sites outside the VPN needs control over routes, which Android's built-in IKEv2 engine (used by MYLO VPN) doesn't give apps. For true Iran-direct routing use the strongSwan app with 'Excluded subnets' (see Help). MYLO VPN's Live ping shows your direct connection while the VPN is on."
                    else
                        "IKEv2/IPsec with EAP-MSCHAPv2. WireGuard and OpenVPN aren't included in this version.",
                )
            },
        )
    }
}
