package com.ikev2split.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ikev2split.app.VpnViewModel
import com.ikev2split.app.data.CrashLog

@Composable
fun LogScreen(vm: VpnViewModel) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val lines by vm.log.collectAsState()
    var crash by remember { mutableStateOf(CrashLog.last(ctx)) }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        crash?.let { c ->
            Text("The app closed unexpectedly last time", color = Danger, fontWeight = FontWeight.SemiBold)
            Text(c.take(900), fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Muted, modifier = Modifier.padding(vertical = 6.dp))
            Row {
                TextButton({ clip.setText(AnnotatedString(c)); vm.toast.tryEmit("Crash report copied") }) { Text("Copy report") }
                TextButton({ CrashLog.clear(ctx); crash = null }) { Text("Dismiss") }
            }
        }
        if (lines.isEmpty()) Text("Nothing yet.", color = Muted)
        LazyColumn { items(lines.reversed()) { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Muted, modifier = Modifier.padding(vertical = 2.dp)) } }
    }
}
