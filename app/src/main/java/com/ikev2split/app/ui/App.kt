package com.ikev2split.app.ui

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ikev2split.app.VpnViewModel
import com.ikev2split.app.update.UpdateState

@Composable
fun AppRoot(vm: VpnViewModel, onPower: () -> Unit) {
    val ctx = LocalContext.current
    val theme by vm.theme.collectAsState()
    val sysDark = isSystemInDarkTheme()
    LaunchedEffect(theme, sysDark) {
        val p = Palettes.byName(theme, sysDark)
        Pal.cur = p
        (ctx as? ComponentActivity)?.let { a ->
            val bars = if (p.dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            a.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        }
    }
    LaunchedEffect(Unit) { vm.toast.collect { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() } }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sub by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = sub != null) { sub = null }
    val upd by vm.update.collectAsState()
    LaunchedEffect(upd) {
        when (val u = upd) {
            is UpdateState.Error -> { Toast.makeText(ctx, u.msg, Toast.LENGTH_LONG).show(); vm.dismissUpdate() }
            is UpdateState.UpToDate -> { Toast.makeText(ctx, "You have the latest version", Toast.LENGTH_SHORT).show(); vm.dismissUpdate() }
            else -> {}
        }
    }
    val homeFull = sub == null && tab == 0     // the hero extends behind the status bar

    AppTheme {
        UpdateDialogs(upd, { vm.applyUpdate() }, { vm.dismissUpdate() })
        Scaffold(
            containerColor = Bg,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = { if (sub == null) NavBar(tab) { tab = it } },
        ) { pad ->
            Box(
                Modifier.fillMaxSize().padding(pad)
                    .then(if (homeFull) Modifier else Modifier.statusBarsPadding())
                    .then(if (sub != null) Modifier.navigationBarsPadding() else Modifier),
            ) {
                when (sub) {
                    "locations" -> LocationsScreen(vm) { sub = null }
                    "appearance" -> AppearanceScreen(vm) { sub = null }
                    "assistant" -> AssistantScreen { sub = null }
                    "advanced" -> SubPage("Advanced", { sub = null }) { SettingsScreen(vm) }
                    "log" -> SubPage("Connection Log", { sub = null }) { LogScreen(vm) }
                    else -> when (tab) {
                        0 -> HomeScreen(vm, onPower, { sub = "locations" }, { sub = "assistant" }, { sub = "appearance" })
                        1 -> StatsScreen(vm)
                        2 -> SpeedTestScreen(vm)
                        3 -> HelpScreen { sub = it }
                        else -> ProfileScreen(vm) { sub = it }
                    }
                }
            }
        }
    }
}

/** Floating pill bar like the reference. */
@Composable
private fun NavBar(tab: Int, onTab: (Int) -> Unit) {
    val items = listOf(
        "VPN" to Icons.Filled.PowerSettingsNew,
        "Stats" to Icons.Filled.BarChart,
        "Speed Test" to Icons.Filled.Speed,
        "Help" to Icons.Filled.Help,
        "Profile" to Icons.Filled.Person,
    )
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().height(66.dp).clip(RoundedCornerShape(33.dp)).background(BarBg).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { i, (label, icon) ->
                Column(
                    Modifier.weight(1f).fillMaxHeight().padding(vertical = 5.dp).clip(RoundedCornerShape(28.dp))
                        .background(if (i == tab) Pal.cur.barSel else Color.Transparent).clickable { onTab(i) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(icon, label, Modifier.size(24.dp))
                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}
