package com.ikev2split.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ikev2split.app.update.UpdateState

@Composable
fun UpdateDialogs(u: UpdateState, onUpdate: () -> Unit, onLater: () -> Unit) {
    when (u) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = { if (!u.info.mandatory) onLater() },
            title = { Text("Update available") },
            text = { Text("MYLO VPN ${u.info.versionName}" + (if (u.info.notes.isNotBlank()) "\n\n${u.info.notes}" else "")) },
            confirmButton = { TextButton(onUpdate) { Text("Update") } },
            dismissButton = { if (!u.info.mandatory) TextButton(onLater) { Text("Later") } },
        )
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Downloading update") },
            text = {
                Column {
                    LinearProgressIndicator(progress = { u.percent / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    Text("${u.percent}%")
                }
            },
            confirmButton = {},
        )
        is UpdateState.Installing -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Installing update") },
            text = { Text("Android may ask you to confirm. The app restarts when it is done.") },
            confirmButton = {},
        )
        else -> {}
    }
}
