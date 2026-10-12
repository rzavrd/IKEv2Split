package com.ikev2split.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
<<<<<<< HEAD
import com.ikev2split.app.auth.Auth
import com.ikev2split.app.auth.AuthState
import com.ikev2split.app.data.ListParser
import com.ikev2split.app.data.OvpnProfile
import com.ikev2split.app.data.V2Profile
import com.ikev2split.app.openvpn.OpenVpnBridge
import com.ikev2split.app.openvpn.OvpnImport
import com.ikev2split.app.v2ray.V2Bridge
import com.ikev2split.app.v2ray.V2Link
=======
import com.ikev2split.app.data.ListParser
>>>>>>> origin/main
import com.ikev2split.app.data.Server
import com.ikev2split.app.data.Store
import com.ikev2split.app.data.IconStyles
import com.ikev2split.app.update.Updater
import com.ikev2split.app.vpn.Live
import com.ikev2split.app.vpn.LivePing
import com.ikev2split.app.vpn.Pinger
import com.ikev2split.app.vpn.SpeedTest
import com.ikev2split.app.vpn.VpnController
<<<<<<< HEAD
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
=======
>>>>>>> origin/main
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
<<<<<<< HEAD
    val auth = Auth.state
    val authError = Auth.error
    val ovpn = MutableStateFlow(store.ovpn())
    val protocol = MutableStateFlow(store.protocol.let { if (it == "IKEv2" && !VpnController.ikeSupported) "OpenVPN" else it })
    val ovpnSel = MutableStateFlow(store.ovpnCurrent)
    val v2 = MutableStateFlow(store.v2())
    val v2Sel = MutableStateFlow(store.v2Current)
    /** link -> milliseconds to reach the server, -1 = no answer */
    val v2Ping = MutableStateFlow<Map<String, Int>>(emptyMap())
    /** Reasons shown after importing .ovpn files (empty = nothing to show) */
    val ovpnReport = MutableStateFlow<List<String>>(emptyList())
=======
>>>>>>> origin/main
    val directPing = MutableStateFlow(store.directPing)
    val verify = MutableStateFlow(store.verify)
    val toast = MutableSharedFlow<String>(extraBufferCapacity = 4)

    private var pending: Server? = null
    private var lastPing = 0L
    private var fallbacks: List<Server> = emptyList()
    private var prep: Job? = null

<<<<<<< HEAD
    /** An unexpected error while connecting is shown as a message instead of closing the app. */
    private val guard = CoroutineExceptionHandler { _, e -> VpnController.fail("${e.javaClass.simpleName}: ${e.message ?: "unexpected error"}") }

    init {
        VpnController.init(app)
        Auth.init(app)
        // when the account server hands out new servers / profiles (or they are wiped), reload them
        viewModelScope.launch {
            Auth.configTick.collect {
                servers.value = store.servers()
                ovpn.value = store.ovpn()
                selected.value = store.current
            }
        }
        viewModelScope.launch {
            if (Auth.enabled() && Auth.state.value is AuthState.LoggedIn && Auth.ping()) Auth.loadConfig()
        }
=======
    init {
        VpnController.init(app)
>>>>>>> origin/main
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

<<<<<<< HEAD
    // ------------------------------------------------------------ account (login server)
    fun login(user: String, pass: String) { viewModelScope.launch { Auth.login(user, pass) } }
    fun logout() { viewModelScope.launch { Auth.logout() } }
    fun heartbeat() { viewModelScope.launch { Auth.ping() } }

    /** Pulls changes the administrator made (new servers / OpenVPN files). [force] ignores the saved ETag. */
    fun syncConfig(force: Boolean = false, report: Boolean = false) {
        viewModelScope.launch(guard) {
            if (!Auth.enabled() || Auth.state.value !is AuthState.LoggedIn) { if (report) toast.tryEmit("Nothing to update (not signed in)"); return@launch }
            val ok = Auth.loadConfig(force)
            if (report) toast.tryEmit(if (ok) "Lists are up to date" else "Could not reach the server")
        }
    }

    // ------------------------------------------------------------ OpenVPN profiles
    /** Any number of .ovpn files, or .zip archives of them. */
    fun importOvpnMany(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(guard) {
            val r = withContext(Dispatchers.IO) { OvpnImport.process(getApplication<Application>(), uris, store.ovpn()) }
            if (r.added > 0) { store.saveOvpn(r.list); ovpn.value = r.list }
            ovpnReport.value = r.skipped
            toast.tryEmit(r.summary())
        }
    }

    fun importOvpn(uri: Uri) = importOvpnMany(listOf(uri))

    fun removeOvpn(name: String) {
        val l = store.ovpn().filter { it.name != name }
        store.saveOvpn(l)
        ovpn.value = l
    }

    fun selectOvpn(name: String) { store.ovpnCurrent = name; ovpnSel.value = name }

    // ------------------------------------------------------------ protocol + VMess / VLESS links
    fun setProtocol(p: String) {
        if (p == "IKEv2" && !VpnController.ikeSupported) { toast.tryEmit("IKEv2 needs Android 11 or newer"); return }
        store.protocol = p; protocol.value = p
    }

    /** Paste links, a pasted subscription body, or a subscription address (https://...). */
    fun addV2(text: String) {
        val t = text.trim()
        if (t.startsWith("http://", true) || t.startsWith("https://", true)) {
            viewModelScope.launch(guard) {
                toast.tryEmit("Downloading the list...")
                val body = withContext(Dispatchers.IO) { try { fetchText(t) } catch (e: Exception) { null } }
                if (body == null) toast.tryEmit("Could not download that address") else addV2Text(body)
            }
        } else addV2Text(t)
    }

    private fun fetchText(u: String): String {
        val c = java.net.URL(u).openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "MYLO-VPN")
        if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
        return c.inputStream.use { ins ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                total += n
                if (total > 2_000_000) throw IllegalStateException("list too big")
                out.write(buf, 0, n)
            }
            out.toString("UTF-8")
        }
    }

    /** Measures how fast every saved server answers (TCP connect, outside the tunnel when possible). */
    fun pingV2() {
        val list = v2.value
        if (list.isEmpty()) return
        viewModelScope.launch(guard) {
            val sem = Semaphore(8)
            val res = withContext(Dispatchers.IO) {
                list.map { p -> async { sem.withPermit { p.link to V2Bridge.tcpPing(p.host, p.port) } } }.awaitAll()
            }
            v2Ping.value = res.toMap()
        }
    }

    private fun addV2Text(text: String) {
        val (ok, bad) = V2Link.parseAll(text)
        if (ok.isEmpty()) { toast.tryEmit("No valid vmess:// or vless:// link found"); return }
        val cur = store.v2()
        val names = cur.map { it.name }.toMutableSet()
        val add = ok.filter { n -> cur.none { it.link == n.link } }.map { n ->
            var name = n.name; var k = 2
            while (name in names) name = "${n.name} ($k)".also { k++ }
            names += name
            n.copy(name = name)
        }
        val list = cur + add
        store.saveV2(list); v2.value = list
        if (store.v2Current.isEmpty() || list.none { it.link == store.v2Current }) selectV2(list.firstOrNull()?.link ?: "")
        toast.tryEmit(if (add.isEmpty()) "Already added" else "${add.size} link(s) added" + if (bad.isNotEmpty()) ", ${bad.size} skipped" else "")
    }

    fun removeV2(link: String) {
        val l = store.v2().filter { it.link != link }
        store.saveV2(l); v2.value = l
        if (store.v2Current == link) selectV2(l.firstOrNull()?.link ?: "")
    }

    fun selectV2(link: String) { store.v2Current = link; v2Sel.value = link }

    /** OpenVPN and VMess/VLESS are run by companion apps: MYLO VPN hands them the profile. */
    private fun toggleExternal() {
        val app = getApplication<Application>()
        if (Auth.enabled() && Auth.state.value !is AuthState.LoggedIn) { toast.tryEmit("Sign in first"); return }
        viewModelScope.launch(guard) {
            if (Auth.enabled() && withTimeoutOrNull(2500) { Auth.ping() } == false) { toast.tryEmit("Your access was removed. Sign in again."); return@launch }
            if (store.protocol == "OpenVPN") {
                val p = ovpn.value.firstOrNull { it.name == ovpnSel.value } ?: ovpn.value.firstOrNull()
                when {
                    p == null -> toast.tryEmit("Add an OpenVPN profile in Locations > OpenVPN")
                    !OpenVpnBridge.installed(app) -> { toast.tryEmit("Install OpenVPN for Android, then try again"); OpenVpnBridge.openStorePage(app) }
                    else -> try { OpenVpnBridge.connect(app, p) } catch (e: Throwable) {
                        toast.tryEmit("Import the profile first: Locations > OpenVPN > tap it > Import")
                    }
                }
            } else {
                val p = v2.value.firstOrNull { it.link == v2Sel.value } ?: v2.value.firstOrNull()
                when {
                    p == null -> toast.tryEmit("Add a vmess:// or vless:// link in Locations > V2Ray")
                    else -> {
                        val opened = try { V2Bridge.connect(app, p) } catch (e: Throwable) { false }
                        if (opened) toast.tryEmit("Link copied. In that app choose import from clipboard, then connect")
                        else { toast.tryEmit("Install v2rayNG, Hiddify or NekoBox, then try again"); V2Bridge.openStorePage(app) }
                    }
                }
            }
        }
    }

=======
>>>>>>> origin/main
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
<<<<<<< HEAD
        if (store.protocol != "IKEv2" && !isActive()) { toggleExternal(); return }
        if (isActive()) { prep?.cancel(); VpnController.disconnect(); return }
        if (!VpnController.ikeSupported) { VpnController.fail("IKEv2 needs Android 11 or newer. Choose OpenVPN or V2Ray in Profile > VPN Protocol."); return }
        if (Auth.enabled() && Auth.state.value !is AuthState.LoggedIn) { VpnController.fail("Sign in first"); return }
=======
        if (isActive()) { prep?.cancel(); VpnController.disconnect(); return }
>>>>>>> origin/main
        if (store.user.isBlank() || store.pass.isBlank()) {
            VpnController.fail("Enter username & password in Settings"); return
        }
        VpnController.markBusy()
<<<<<<< HEAD
        prep = viewModelScope.launch(guard) {
            // the account check runs next to the connection instead of before it; a removed account is signed out and disconnected
            if (Auth.enabled()) launch { Auth.ping() }
            var s = servers.value.firstOrNull { it.address == selected.value }
            if (auto.value || s == null) {
                val fresh = System.currentTimeMillis() - lastPing < 15 * 60_000 && servers.value.any { it.pingMs != null }
                if (!fresh) {
                    VpnController.log("measuring servers...")
                    withTimeoutOrNull(3500) { pingInternal() }   // whatever answered in time is used
=======
        prep = viewModelScope.launch {
            var s = servers.value.firstOrNull { it.address == selected.value }
            if (auto.value || s == null) {
                if (System.currentTimeMillis() - lastPing > 60_000) {
                    VpnController.log("measuring servers...")
                    pingInternal()
>>>>>>> origin/main
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
<<<<<<< HEAD
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                VpnController.fail("${e.javaClass.simpleName}: ${e.message ?: ""}")
=======
            } catch (e: Exception) {
                VpnController.fail(e.message ?: e.javaClass.simpleName)
>>>>>>> origin/main
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
