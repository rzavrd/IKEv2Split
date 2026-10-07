package com.ikev2split.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ikev2split.app.data.ListParser
import com.ikev2split.app.data.Server
import com.ikev2split.app.data.Store
import com.ikev2split.app.data.IconStyles
import com.ikev2split.app.update.Updater
import com.ikev2split.app.vpn.Live
import com.ikev2split.app.vpn.LivePing
import com.ikev2split.app.vpn.Pinger
import com.ikev2split.app.vpn.SpeedTest
import com.ikev2split.app.vpn.VpnController
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.cert.CertificateFactory

class VpnViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app).also { it.migrate() }
    val state = VpnController.state
    val log = VpnController.log
    val detail = VpnController.detail
    val stats = VpnController.stats

    val servers = MutableStateFlow(store.servers())
    val selected = MutableStateFlow(store.current)
    val auto = MutableStateFlow(store.autoSelect)
    val pinging = MutableStateFlow(false)
    val favs = MutableStateFlow(store.favorites())
    val history = MutableStateFlow(store.history())
    val theme = MutableStateFlow(store.theme)
    val showIp = MutableStateFlow(store.showIp)
    val failover = MutableStateFlow(store.failover)
    val iconStyle = MutableStateFlow(store.iconStyle)
    val speed = SpeedTest.state
    val live = MutableStateFlow(Live())
    val update = Updater.state
    val directPing = MutableStateFlow(store.directPing)
    val verify = MutableStateFlow(store.verify)
    val toast = MutableSharedFlow<String>(extraBufferCapacity = 4)

    private var pending: Server? = null
    private var lastPing = 0L
    private var fallbacks: List<Server> = emptyList()
    private var prep: Job? = null

    init {
        VpnController.init(app)
        // quiet update check at most every 6 hours
        viewModelScope.launch {
            delay(4000)
            if (System.currentTimeMillis() - store.lastUpdateCheck() > 6 * 3600_000L) checkUpdate(false)
        }
    }

    fun checkUpdate(manual: Boolean) {
        viewModelScope.launch {
            Updater.check(getApplication<Application>(), store.updateUrl, manual)
            store.setLastUpdateCheck(System.currentTimeMillis())
        }
    }

    fun applyUpdate() {
        val info = (Updater.state.value as? com.ikev2split.app.update.UpdateState.Available)?.info ?: return
        viewModelScope.launch { Updater.downloadAndInstall(getApplication<Application>(), info) }
    }

    fun dismissUpdate() = Updater.dismiss()

    // ------------------------------------------------------------ appearance / settings
    fun setTheme(n: String) { store.theme = n; theme.value = n }
    fun setShowIp(v: Boolean) { store.showIp = v; showIp.value = v }
    fun setFailover(v: Boolean) { store.failover = v; failover.value = v }
    fun setIcon(style: String) {
        store.iconStyle = style; iconStyle.value = style
        IconStyles.apply(getApplication<Application>(), style)
    }

    // ------------------------------------------------------------ history, favorites, speed test, live ping
    fun toggleFav(address: String) { favs.value = store.toggleFavorite(address) }
    fun refreshHistory() { history.value = store.history() }
    fun startSpeed() = SpeedTest.start(viewModelScope)
    fun stopSpeed() = SpeedTest.stop()

    /** One live-ping sample: direct (Iranian sites, real connection) and through the tunnel. */
    suspend fun measureLiveOnce() {
        val connected = state.value is VpnController.State.Connected
        val direct = if (connected && !store.directPing) null else LivePing.direct(getApplication<Application>())
        live.value = Live(direct, if (connected) LivePing.vpn() else null)
    }

    private fun isActive() = state.value is VpnController.State.Connected ||
        state.value is VpnController.State.Connecting

    // ------------------------------------------------------------ servers
    fun addServer(name: String, address: String, cc: String) {
        val a = address.trim().replace(" ", "")
        if (a.isEmpty()) { toast.tryEmit("Enter a server address"); return }
        if (servers.value.any { it.address == a }) { toast.tryEmit("Server already exists"); return }
        update(servers.value + Server(name.trim().ifEmpty { a }, a, cc.trim().uppercase().ifEmpty { com.ikev2split.app.data.guessCc(a) }))
    }

    fun removeServer(address: String) {
        update(servers.value.filter { it.address != address })
        if (selected.value == address) select("")
    }

    fun importText(text: String) {
        val add = ListParser.parse(text).filter { n -> servers.value.none { it.address == n.address } }
        update(servers.value + add)
        toast.tryEmit("${add.size} server(s) added")
    }

    private fun update(l: List<Server>) { servers.value = l; store.saveServers(l) }

    fun select(address: String) {
        selected.value = address
        store.current = address
        setAuto(false)
    }

    fun setAuto(v: Boolean) { auto.value = v; store.autoSelect = v }

    fun pingAll() {
        if (pinging.value) return
        viewModelScope.launch { pingInternal() }
    }

    /** Measures every server and updates the list as each answer arrives. */
    private suspend fun pingInternal() {
        // without a bypassable VPN the probes would go through the tunnel and show wrong numbers
        if (state.value is VpnController.State.Connected && !store.directPing) return
        pinging.value = true
        try {
            Pinger.pingAll(servers.value) { r ->
                servers.update { l -> l.map { if (it.address == r.address) it.copy(pingMs = r.pingMs) else it } }
            }
        } finally {
            pinging.value = false
            lastPing = System.currentTimeMillis()
        }
    }

    /** Runs while the Locations screen is open: re-measures everything every few seconds. */
    suspend fun livePingLoop(periodMs: Long = 4000) {
        while (true) {
            if (!pinging.value && state.value !is VpnController.State.Connecting) pingInternal()
            delay(periodMs)
        }
    }

    fun setDirectPing(v: Boolean) { store.directPing = v; directPing.value = v }
    fun setVerify(v: Boolean) { store.verify = v; verify.value = v }
    fun setMtu(v: Int) { store.mtu = v.coerceIn(1280, 1500) }

    // ------------------------------------------------------------ connect
    fun toggle(launchConsent: (Intent) -> Unit) {
        if (isActive()) { prep?.cancel(); VpnController.disconnect(); return }
        if (store.user.isBlank() || store.pass.isBlank()) {
            VpnController.fail("Enter username & password in Settings"); return
        }
        VpnController.markBusy()
        prep = viewModelScope.launch {
            var s = servers.value.firstOrNull { it.address == selected.value }
            if (auto.value || s == null) {
                if (System.currentTimeMillis() - lastPing > 60_000) {
                    VpnController.log("measuring servers...")
                    pingInternal()
                }
                s = servers.value.filter { it.pingMs != null }.minByOrNull { it.pingMs!! }
                    ?: s ?: servers.value.firstOrNull()
            }
            if (s == null) { VpnController.fail("No servers - add one in the Locations tab"); return@launch }
            selected.value = s.address
            pending = s
            fallbacks = servers.value.filter { it.address != s.address }
                .let { l -> if (auto.value) l.filter { it.pingMs != null }.sortedBy { it.pingMs } else l }
                .take(3)
            try {
                val i = VpnController.provision(s)
                if (i != null) launchConsent(i) else VpnController.connect(s, fallbacks)
            } catch (e: Exception) {
                VpnController.fail(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Called when the system VPN-consent dialog returns RESULT_OK. */
    fun consentGranted() { pending?.let { VpnController.connect(it, fallbacks) } }
    fun consentDenied() { VpnController.fail("VPN permission denied") }

    // ------------------------------------------------------------ certificate
    fun importCa(uri: Uri) {
        try {
            val b = getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            CertificateFactory.getInstance("X.509").generateCertificate(b.inputStream()) // validates PEM or DER
            store.saveCa(b)
            toast.tryEmit("Certificate saved")
        } catch (e: Exception) {
            toast.tryEmit("Invalid certificate: ${e.message}")
        }
    }
}
