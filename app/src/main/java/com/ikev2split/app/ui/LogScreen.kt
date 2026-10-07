package com.ikev2split.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ikev2split.app.VpnViewModel

@Composable
fun LogScreen(vm: VpnViewModel) {
    val lines by vm.log.collectAsState()
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        if (lines.isEmpty()) Text("Nothing yet.", color = Muted)
        LazyColumn { items(lines.reversed()) { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Muted, modifier = Modifier.padding(vertical = 2.dp)) } }
    }
}
