package com.ikev2split.app.ui

<<<<<<< HEAD
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
=======
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
>>>>>>> origin/main
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
<<<<<<< HEAD
import androidx.compose.ui.platform.LocalContext
=======
>>>>>>> origin/main
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ikev2split.app.VpnViewModel
<<<<<<< HEAD
import com.ikev2split.app.data.OvpnProfile
import com.ikev2split.app.data.Regions
import com.ikev2split.app.openvpn.OpenVpnBridge
import com.ikev2split.app.data.V2Profile
import com.ikev2split.app.v2ray.V2Bridge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalClipboardManager
=======
import com.ikev2split.app.data.Regions
>>>>>>> origin/main
import com.ikev2split.app.data.Server
import com.ikev2split.app.data.countryName
import com.ikev2split.app.data.flagEmoji

@Composable
fun LocationsScreen(vm: VpnViewModel, onBack: () -> Unit) {
    val servers by vm.servers.collectAsState()
    val sel by vm.selected.collectAsState()
    val auto by vm.auto.collectAsState()
    val favs by vm.favs.collectAsState()
    val hist by vm.history.collectAsState()
<<<<<<< HEAD
    val ovpn by vm.ovpn.collectAsState()
    val ctx = LocalContext.current
    var ovpnPick by remember { mutableStateOf<OvpnProfile?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) vm.importOvpnMany(uris) }
    val report by vm.ovpnReport.collectAsState()
    val v2Ping by vm.v2Ping.collectAsState()
    val ovpnSel by vm.ovpnSel.collectAsState()
    val v2 by vm.v2.collectAsState()
    val v2Sel by vm.v2Sel.collectAsState()
    val clipboard = LocalClipboardManager.current
    var v2Pick by remember { mutableStateOf<V2Profile?>(null) }
    var showV2Add by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(when (vm.protocol.value) { "OpenVPN" -> 2; "V2Ray" -> 3; else -> 0 }) }
=======
    var q by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
>>>>>>> origin/main
    var region by remember { mutableStateOf("All Regions") }
    var sortBy by remember { mutableStateOf("Locations") }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var showAdd by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var regionMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }

<<<<<<< HEAD
    LaunchedEffect(Unit) { vm.refreshHistory(); vm.syncConfig() }
    LaunchedEffect(tab, v2.size) { if (tab == 3) vm.pingV2() }
=======
    LaunchedEffect(Unit) { vm.refreshHistory() }
>>>>>>> origin/main
    LaunchedEffect(Unit) { vm.livePingLoop(4000) }   // live ping while this screen is open

    val query = q.trim().lowercase()
    val filtered = servers.filter {
        query.isEmpty() || it.name.lowercase().contains(query) || it.address.lowercase().contains(query) ||
            countryName(it.cc).lowercase().contains(query)
    }
    val ranked = servers.filter { it.pingMs != null }.sortedBy { it.pingMs }
    val best = ranked.firstOrNull()
    val recents = hist.mapNotNull { h -> servers.firstOrNull { it.name == h.server } }.distinctBy { it.address }.take(3)
    val favServers = servers.filter { it.address in favs }

<<<<<<< HEAD
    fun pick(s: Server) { vm.setProtocol("IKEv2"); vm.select(s.address); onBack() }
=======
    fun pick(s: Server) { vm.select(s.address); onBack() }
>>>>>>> origin/main

    Column(Modifier.fillMaxSize()) {
        SubHeader("VPN Locations", onBack, trailing = {
            Box(Modifier.size(44.dp).clip(CircleShape).background(BarBg).clickable { showHelp = true }, contentAlignment = Alignment.Center) {
                Text("?", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        })

        // search
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(14.dp)).background(BarBg).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = Muted)
            BasicTextField(
                value = q, onValueChange = { q = it }, singleLine = true,
                textStyle = TextStyle(color = Ink, fontSize = 17.sp),
                cursorBrush = SolidColor(Teal),
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 14.dp),
                decorationBox = { inner ->
                    Box { if (q.isEmpty()) Text("Search for a city or country", color = Muted, fontSize = 17.sp); inner() }
                },
            )
        }
        Spacer(Modifier.height(12.dp))
<<<<<<< HEAD
        Segmented(listOf("Best", "All", "OpenVPN", "V2Ray"), tab) { tab = it }
=======
        Segmented(listOf("Recommended", "All Locations"), tab) { tab = it }
>>>>>>> origin/main
        Spacer(Modifier.height(4.dp))

        if (tab == 1 && query.isEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Box {
                    TextButton({ regionMenu = true }) { Text(region, color = Teal); Icon(Icons.Filled.KeyboardArrowDown, null, tint = Teal) }
                    DropdownMenu(regionMenu, { regionMenu = false }) {
                        Regions.names.forEach { r -> DropdownMenuItem(text = { Text(r) }, onClick = { region = r; regionMenu = false }) }
                    }
                }
                Box {
                    TextButton({ sortMenu = true }) { Text("Sort: $sortBy", color = Teal); Icon(Icons.Filled.KeyboardArrowDown, null, tint = Teal) }
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        listOf("Locations", "Name", "Ping").forEach { r -> DropdownMenuItem(text = { Text(r) }, onClick = { sortBy = r; sortMenu = false }) }
                    }
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            if (query.isNotEmpty()) {
                items(filtered.sortedBy { it.pingMs ?: Int.MAX_VALUE }, key = { "q_" + it.address }) { s ->
                    ServerRow(s, !auto && s.address == sel, s.address in favs, false, true, { pick(s) }, { vm.toggleFav(s.address) }, { vm.removeServer(s.address) })
                }
            } else if (tab == 0) {
                item { Header(Icons.Filled.Bolt, "Fastest Servers", "Top 5 (Ping)") }
                if (best == null) {
                    item { Text("Measuring servers...", Modifier.padding(vertical = 14.dp), color = Muted) }
                } else {
                    item(key = "auto") { FastestRow(best, auto) { vm.setAuto(true); onBack() } }
                    items(ranked.take(5).drop(1), key = { "f_" + it.address }) { s ->
                        ServerRow(s, !auto && s.address == sel, false, false, false, { pick(s) }, null, null)
                    }
                }
                if (recents.isNotEmpty()) {
                    item { Header(Icons.Filled.History, "Recent Locations", "Last ${recents.size}") }
                    items(recents, key = { "r_" + it.address }) { s ->
                        ServerRow(s, !auto && s.address == sel, false, true, false, { pick(s) }, null, null)
                    }
                }
                if (favServers.isNotEmpty()) {
                    item { Header(Icons.Filled.Favorite, "Favorites", "${favServers.size}", chevron = false) }
                    items(favServers, key = { "v_" + it.address }) { s ->
                        ServerRow(s, !auto && s.address == sel, true, false, false, { pick(s) }, { vm.toggleFav(s.address) }, null)
                    }
                }
                item { Header(Icons.Filled.Public, "All Countries", "", chevron = false) }
                item {
                    Text(
                        "${servers.size} locations  |  ${servers.map { it.cc }.distinct().size} countries",
                        Modifier.padding(start = 46.dp, bottom = 4.dp), color = Muted, fontSize = 13.sp,
                    )
                }
                countryGroups(this, servers, expanded, { expanded = it }, vm, sel, auto, favs, ::pick, false, null)
<<<<<<< HEAD
            } else if (tab == 2) {
                item { Header(Icons.Filled.Public, "OpenVPN", "${ovpn.size}", chevron = false) }
                if (ovpn.isEmpty()) {
                    item {
                        Text(
                            "No OpenVPN profiles yet. They appear here when the administrator adds them, or import an .ovpn file below.",
                            Modifier.padding(vertical = 12.dp), color = Muted,
                        )
                    }
                }
                items(ovpn, key = { "o_${it.local}_${it.name}" }) { o ->
                    OvpnRow(o, o.name == ovpnSel, { vm.setProtocol("OpenVPN"); vm.selectOvpn(o.name); ovpnPick = o }, if (o.local) ({ vm.removeOvpn(o.name) }) else null)
                }
                item {
                    Row(Modifier.padding(top = 8.dp)) {
                        TextButton({ picker.launch(arrayOf("*/*")) }) { Text("Import files / .zip") }
                        TextButton({ vm.syncConfig(force = true, report = true) }) { Text("Refresh") }
                    }
                }
            } else if (tab == 3) {
                item { Header(Icons.Filled.Public, "VMess / VLESS", "${v2.size}", chevron = false) }
                item {
                    Text(
                        "Paste vmess:// or vless:// links, or a subscription address. MYLO VPN keeps them here and shows how fast each server answers. Connect shares the link to v2rayNG, Hiddify or NekoBox, which make the connection.",
                        Modifier.padding(bottom = 8.dp), color = Muted, fontSize = 13.sp,
                    )
                }
                items(v2, key = { "v_" + it.link }) { p -> V2Row(p, p.link == v2Sel, v2Ping[p.link]) { vm.setProtocol("V2Ray"); vm.selectV2(p.link); v2Pick = p } }
                item {
                    Row(Modifier.padding(top = 8.dp)) {
                        TextButton({ showV2Add = true }) { Text("Add link / list") }
                        TextButton({ vm.pingV2() }) { Text("Test speed") }
                    }
                }
=======
>>>>>>> origin/main
            } else {
                val groups = servers.groupBy { it.cc.ifEmpty { "??" } }
                    .filterKeys { region == "All Regions" || Regions.of(it) == region }
                val sorted = when (sortBy) {
                    "Name" -> groups.toList().sortedBy { countryName(it.first) }
                    "Ping" -> groups.toList().sortedBy { g -> g.second.mapNotNull { it.pingMs }.minOrNull() ?: Int.MAX_VALUE }
                    else -> groups.toList().sortedByDescending { it.second.size }
                }
                countryGroups(this, sorted.flatMap { it.second }, expanded, { expanded = it }, vm, sel, auto, favs, ::pick, true, sorted.map { it.first })
            }
            item { Spacer(Modifier.size(24.dp)) }
        }
    }

<<<<<<< HEAD
    ovpnPick?.let { o ->
        val has = OpenVpnBridge.installed(ctx)
        AlertDialog(
            onDismissRequest = { ovpnPick = null },
            title = { Text(o.name) },
            text = {
                Text(
                    if (has) "OpenVPN connections are made by the free OpenVPN for Android app.\n\n1. Tap Import (once per profile) and confirm in that app.\n2. Come back and tap Connect."
                    else "OpenVPN needs the free \"OpenVPN for Android\" app. Install it, then come back here.",
                )
            },
            confirmButton = {
                if (has) {
                    TextButton({
                        try { OpenVpnBridge.connect(ctx, o) } catch (e: Exception) { vm.toast.tryEmit("Import the profile first") }
                        ovpnPick = null
                    }) { Text("Connect") }
                } else {
                    TextButton({ OpenVpnBridge.openStorePage(ctx); ovpnPick = null }) { Text("Install") }
                }
            },
            dismissButton = {
                Row {
                    if (has) {
                        TextButton({
                            try { OpenVpnBridge.importProfile(ctx, o) } catch (e: Exception) { vm.toast.tryEmit("Could not open OpenVPN for Android") }
                        }) { Text("Import") }
                    }
                    TextButton({ ovpnPick = null }) { Text("Cancel") }
                }
            },
        )
    }
    if (report.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { vm.ovpnReport.value = emptyList() },
            title = { Text("Skipped files (${report.size})") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    report.take(30).forEach { Text(it, fontSize = 13.sp) }
                    if (report.size > 30) Text("... and ${report.size - 30} more", color = Muted, fontSize = 12.sp)
                    Text("A .ovpn file must contain its certificates and keys inside the file (between <ca>...</ca>, <cert>, <key>, <tls-auth>).", color = Muted, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton({ vm.ovpnReport.value = emptyList() }) { Text("OK") } },
        )
    }
    v2Pick?.let { p ->
        val client = V2Bridge.installed(ctx)
        AlertDialog(
            onDismissRequest = { v2Pick = null },
            title = { Text(p.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${p.kind.uppercase()}  ${p.host}:${p.port}", color = Muted)
                    Text(
                        if (client != null) "Connect copies the link and opens ${client.second}. There, choose import from clipboard and connect."
                        else "No V2Ray client found. Install v2rayNG (or Hiddify / NekoBox), then come back.",
                    )
                }
            },
            confirmButton = {
                if (client != null) {
                    TextButton({
                        val ok = try { V2Bridge.connect(ctx, p) } catch (e: Throwable) { false }
                        vm.toast.tryEmit(if (ok) "Link copied" else "Could not open ${client.second}")
                        v2Pick = null
                    }) { Text("Connect") }
                } else TextButton({ V2Bridge.openStorePage(ctx); v2Pick = null }) { Text("Install") }
            },
            dismissButton = {
                Row {
                    TextButton({ V2Bridge.copy(ctx, p); vm.toast.tryEmit("Link copied"); v2Pick = null }) { Text("Copy") }
                    TextButton({ vm.removeV2(p.link); v2Pick = null }) { Text("Delete") }
                }
            },
        )
    }
    if (showV2Add) {
        var t by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showV2Add = false },
            title = { Text("Add VMess / VLESS link") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        t, { t = it }, Modifier.fillMaxWidth(), minLines = 5,
                        placeholder = { Text("vmess:// or vless:// links (one per line), or a subscription address https://...") },
                    )
                    OutlinedButton({ t = clipboard.getText()?.text.orEmpty() }) { Text("Paste from clipboard") }
                }
            },
            confirmButton = { TextButton({ vm.addV2(t); showV2Add = false }) { Text("Add") } },
            dismissButton = { TextButton({ showV2Add = false }) { Text("Cancel") } },
        )
    }
=======
>>>>>>> origin/main
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("VPN Locations") },
            text = {
                Text("Pings are measured live over your real connection and refresh every few seconds. Great is under 100 ms, Good under 180, Fair under 300. The best servers are listed first.")
            },
            confirmButton = { TextButton({ showHelp = false }) { Text("OK") } },
            dismissButton = {
                Row {
                    TextButton({ showHelp = false; showAdd = true }) { Text("Add server") }
                    TextButton({ showHelp = false; showImport = true }) { Text("Import list") }
                }
            },
        )
    }
    if (showAdd) {
        var n by remember { mutableStateOf("") }; var a by remember { mutableStateOf("") }; var c by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add server") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(n, { n = it }, label = { Text("Name (optional)") }, singleLine = true)
                    OutlinedTextField(a, { a = it }, label = { Text("Address / hostname") }, singleLine = true)
                    OutlinedTextField(c, { c = it.take(2) }, label = { Text("Country code (auto if empty)") }, singleLine = true)
                }
            },
            confirmButton = { TextButton({ vm.addServer(n, a, c); showAdd = false }) { Text("Add") } },
            dismissButton = { TextButton({ showAdd = false }) { Text("Cancel") } },
        )
    }
    if (showImport) {
        var t by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("Import server list") },
            text = {
                OutlinedTextField(t, { t = it }, Modifier.fillMaxWidth(), minLines = 6,
                    placeholder = { Text("UK - London|uk2.example.com|GB") })
            },
            confirmButton = { TextButton({ vm.importText(t); showImport = false }) { Text("Import") } },
            dismissButton = { TextButton({ showImport = false }) { Text("Cancel") } },
        )
    }
}

/** Expandable country rows. [order] (country codes) keeps a given sort order; otherwise sorted by name. */
private fun countryGroups(
    scope: LazyListScope, list: List<Server>,
    expanded: Set<String>, setExpanded: (Set<String>) -> Unit, vm: VpnViewModel,
    sel: String, auto: Boolean, favs: Set<String>, pick: (Server) -> Unit, deletable: Boolean,
    order: List<String>?,
) {
    val groups = list.groupBy { it.cc.ifEmpty { "??" } }
    val keys = order ?: groups.keys.sortedBy { countryName(it) }
    keys.forEach { cc ->
        val g = groups[cc] ?: return@forEach
        val bestMs = g.mapNotNull { it.pingMs }.minOrNull()
        scope.item(key = "g_$cc") {
            Column {
                Row(
                    Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)
                        .clickable { setExpanded(if (cc in expanded) expanded - cc else expanded + cc) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(flagEmoji(if (cc == "??") "" else cc), fontSize = 26.sp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (cc == "??") "Other" else countryName(cc), fontSize = 18.sp)
                        Text(
                            "${g.size} location${if (g.size == 1) "" else "s"}" + (bestMs?.let { "  |  best $it ms" } ?: ""),
                            color = Muted, fontSize = 13.sp,
                        )
                    }
                    Icon(if (cc in expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
                }
                Divider()
            }
        }
        if (cc in expanded) {
            scope.items(g.sortedBy { it.pingMs ?: Int.MAX_VALUE }, key = { "c_" + it.address }) { s ->
                ServerRow(s, !auto && s.address == sel, s.address in favs, false, true, { pick(s) }, { vm.toggleFav(s.address) },
                    if (deletable) ({ vm.removeServer(s.address) }) else null)
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(start = 46.dp).height(1.dp).background(Muted.copy(alpha = 0.2f)))
}

@Composable
private fun Header(icon: ImageVector, title: String, right: String, chevron: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(28.dp), tint = Muted)
        Spacer(Modifier.width(14.dp))
        Text(title, Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (right.isNotEmpty()) Text(right, color = Teal, fontSize = 14.sp)
        if (chevron) Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(20.dp), tint = Teal)
    }
}

/** "66 ms  Great |||" on one line, like the reference's "Bad |||". */
@Composable
private fun Rating(ms: Int?) {
    val (label, bars) = quality(ms)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (ms != null) Text("$ms ms", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        Text(label, color = Teal, fontSize = 17.sp)
        Spacer(Modifier.width(6.dp))
        SignalBars(bars)
    }
}

@Composable
private fun FastestRow(best: Server, selected: Boolean, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 62.dp).clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(flagEmoji(best.cc), fontSize = 26.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Fastest Location", fontSize = 20.sp, color = if (selected) Teal else Ink, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                Text(best.name, color = Muted, fontSize = 13.sp, maxLines = 1)
            }
            Rating(best.pingMs)
        }
        Divider()
    }
}

@Composable
private fun ServerRow(
    s: Server, selected: Boolean, fav: Boolean, compact: Boolean, detail: Boolean,
    onClick: () -> Unit, onFav: (() -> Unit)?, onDelete: (() -> Unit)?,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(flagEmoji(s.cc), fontSize = 26.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, fontSize = 20.sp, maxLines = 1, color = if (selected) Teal else Ink, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                if (detail) Text(s.address, color = Muted, fontSize = 12.sp, maxLines = 1)
            }
            if (s.pingMs != null) {
                if (compact) Text("${s.pingMs}ms", color = Muted, fontSize = 17.sp) else Rating(s.pingMs)
            }
            if (onFav != null) IconButton(onFav) { Icon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "favorite", tint = if (fav) Pink else Muted) }
            if (onDelete != null) IconButton(onDelete) { Icon(Icons.Filled.Delete, "delete", tint = Muted) }
        }
        Divider()
    }
}
<<<<<<< HEAD

@Composable
private fun OvpnRow(p: OvpnProfile, selected: Boolean, onClick: () -> Unit, onDelete: (() -> Unit)?) {
    Column {
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(flagEmoji(p.cc), fontSize = 26.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, fontSize = 20.sp, maxLines = 1)
                Text("OpenVPN", color = Muted, fontSize = 12.sp)
            }
            if (selected) Icon(Icons.Filled.Check, "selected", tint = Teal)
            if (onDelete != null) IconButton(onDelete) { Icon(Icons.Filled.Delete, "delete", tint = Muted) }
        }
        Divider()
    }
}

@Composable
private fun V2Row(p: V2Profile, selected: Boolean, ping: Int?, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(flagEmoji(com.ikev2split.app.v2ray.V2Link.ccOf(p.name)), fontSize = 26.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, fontSize = 20.sp, maxLines = 1)
                Text(
                    "${p.kind.uppercase()}  ${p.host}:${p.port}" + (if (p.extra.isNotBlank()) "  ${p.extra}" else ""),
                    color = Muted, fontSize = 12.sp, maxLines = 1,
                )
            }
            if (ping != null) {
                Text(
                    if (ping >= 0) "$ping ms" else "no answer",
                    color = if (ping < 0) Danger else if (ping < 300) Teal else Muted, fontSize = 13.sp,
                )
                Spacer(Modifier.width(8.dp))
            }
            if (selected) Icon(Icons.Filled.Check, "selected", tint = Teal)
        }
        Divider()
    }
}
=======
>>>>>>> origin/main
