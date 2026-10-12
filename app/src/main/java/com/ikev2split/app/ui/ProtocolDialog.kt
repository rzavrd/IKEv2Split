package com.ikev2split.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ikev2split.app.VpnViewModel
import com.ikev2split.app.vpn.VpnController

fun protoLabel(p: String) = when (p) {
    "OpenVPN" -> "OpenVPN"
    "V2Ray" -> "V2Ray"
    else -> "IKEv2"
}

/** The protocol chooser: IKEv2 (built in), OpenVPN and VMess/VLESS (opened in a companion app). */
@Composable
fun ProtocolDialog(vm: VpnViewModel, onDismiss: () -> Unit) {
    val cur by vm.protocol.collectAsState()
    val options = listOf(
        Triple("IKEv2", "IKEv2 / IPsec", "Built in. The tunnel runs inside MYLO VPN."),
        Triple("OpenVPN", "OpenVPN", "Uses your .ovpn profiles (Locations > OpenVPN). The tunnel is made by the free OpenVPN for Android app."),
        Triple("V2Ray", "VMess / VLESS", "Your own vmess:// or vless:// links (Locations > V2Ray). Opened in v2rayNG, Hiddify or NekoBox."),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("VPN Protocol") },
        text = {
            Column {
                options.forEach { (id, title, sub) ->
                    val off = id == "IKEv2" && !VpnController.ikeSupported
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !off) { vm.setProtocol(id); onDismiss() }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(cur == id, { vm.setProtocol(id); onDismiss() }, enabled = !off, colors = RadioButtonDefaults.colors(selectedColor = Teal))
                        Column(Modifier.padding(start = 4.dp)) {
                            Text(title, fontWeight = FontWeight.SemiBold, color = if (off) Muted else Ink)
                            Text(if (off) "Needs Android 11 or newer. Not available on this phone." else sub, color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
    )
}
