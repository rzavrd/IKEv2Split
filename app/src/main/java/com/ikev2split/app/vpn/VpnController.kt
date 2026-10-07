package com.ikev2split.app.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Ikev2VpnProfile
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.TrafficStats
import android.net.VpnManager
import android.net.eap.EapSessionConfig
import android.net.ipsec.ike.ChildSaProposal
import android.net.ipsec.ike.IkeFqdnIdentification
import android.net.ipsec.ike.IkeSaProposal
import android.net.ipsec.ike.IkeSessionParams
import android.net.ipsec.ike.IkeTunnelConnectionParams
import android.net.ipsec.ike.SaProposal
import android.net.ipsec.ike.TunnelModeChildSessionParams
import android.os.Build
import android.os.SystemClock
import android.system.OsConstants
import com.ikev2split.app.data.Hist
import com.ikev2split.app.data.Server
import com.ikev2split.app.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Stats(
    val downBps: Long = 0, val upBps: Long = 0,
    val rx: Long = 0, val tx: Long = 0,
    val latencyMs: Int? = null, val publicIp: String? = null,
    val downHist: List<Long> = emptyList(), val upHist: List<Long> = emptyList(),
)

/**
 * Android port of ikev2split-up / -down / -watch using the platform IKEv2 stack
 * (VpnManager + Ikev2VpnProfile). Android's system_server runs the tunnel, so it
 * survives the app being swiped away.
 */
object VpnController {
    sealed interface State {
        data object Disconnected : State
        /** attempt == 0 means "preparing / reconnecting" */
        data class Connecting(val server: String, val attempt: Int, val total: Int) : State
        data class Connected(val server: String, val since: Long, val ip: String?) : State
        data class Failed(val message: String) : State
    }

    private const val ATTEMPT_TIMEOUT_MS = 20_000L

    private lateinit var app: Context
    private lateinit var store: Store
    private lateinit var vm: VpnManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<State>(State.Disconnected)
    val state: StateFlow<State> = _state.asStateFlow()
    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()
    private val _detail = MutableStateFlow("")
    /** One-line progress / last error shown under the status while connecting. */
    val detail: StateFlow<String> = _detail.asStateFlow()
    val stats = MutableStateFlow(Stats())

    private val up = MutableStateFlow(false)
    @Volatile private var vpnIp: String? = null
    @Volatile private var errorFlag: String? = null
    @Volatile private var lastError: String? = null
    private var job: Job? = null
    private var want = false
    private var since = 0L
    private var histServer: Server? = null
    private var histStart = 0L
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val eventReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val code = i.getIntExtra(VpnManager.EXTRA_ERROR_CODE, -1)
            when (i.categories?.firstOrNull().orEmpty()) {
                VpnManager.CATEGORY_EVENT_IKE_ERROR -> {
                    val m = "IKE error $code: ${ikeName(code)}"
                    log(m); lastError = m; errorFlag = m
                }
                VpnManager.CATEGORY_EVENT_NETWORK_ERROR -> {
                    val m = "network error $code: ${netName(code)}"
                    log(m); lastError = m
                }
                VpnManager.CATEGORY_EVENT_DEACTIVATED_BY_USER -> {
                    log("VPN was turned off from Android settings")
                    if (want) disconnect()
                }
            }
        }
    }

    private fun ikeName(c: Int) = when (c) {
        7 -> "INVALID_SYNTAX"
        14 -> "NO_PROPOSAL_CHOSEN (cipher mismatch)"
        17 -> "INVALID_KE_PAYLOAD"
        24 -> "AUTHENTICATION_FAILED (wrong username/password, server certificate, or remote ID)"
        35 -> "NO_ADDITIONAL_SAS"
        36 -> "INTERNAL_ADDRESS_FAILURE"
        37 -> "FAILED_CP_REQUIRED"
        38 -> "TS_UNACCEPTABLE"
        43 -> "TEMPORARY_FAILURE"
        else -> "see Logcat (filter: Ike)"
    }

    private fun netName(c: Int) = when (c) {
        0 -> "unknown host (DNS)"
        1 -> "timeout - server not answering on UDP 500/4500"
        2 -> "network lost"
        3 -> "I/O error"
        else -> "unknown"
    }

    fun init(ctx: Context) {
        if (this::app.isInitialized) return
        app = ctx.applicationContext
        store = Store(app)
        Pinger.init(app)
        vm = app.getSystemService(Context.VPN_MANAGEMENT_SERVICE) as VpnManager
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        cm.registerNetworkCallback(req, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { up.value = true }
            override fun onLost(network: Network) { up.value = false }
            override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
                vpnIp = lp.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address }?.hostAddress
            }
        })
        if (Build.VERSION.SDK_INT >= 33) {
            val f = IntentFilter(VpnManager.ACTION_VPN_MANAGER_EVENT).apply {
                addCategory(VpnManager.CATEGORY_EVENT_IKE_ERROR)
                addCategory(VpnManager.CATEGORY_EVENT_NETWORK_ERROR)
                addCategory(VpnManager.CATEGORY_EVENT_DEACTIVATED_BY_USER)
            }
            app.registerReceiver(eventReceiver, f, Context.RECEIVER_NOT_EXPORTED)
        }
    }

    fun log(m: String) { _log.update { (it + "${fmt.format(Date())}  $m").takeLast(300) } }
    fun fail(msg: String) { want = false; endHistory("Failed"); log("FAILED: $msg"); _state.value = State.Failed(msg) }
    fun markBusy() { _detail.value = "Preparing..."; _state.value = State.Connecting("", 0, 0) }

    /** Returns a consent Intent if the user must approve this app as a VPN, else null. */
    fun provision(s: Server): Intent? = vm.provisionVpnProfile(buildProfile(s, 0))

    fun connect(s: Server, fallbacks: List<Server> = emptyList()) {
        job?.cancel()
        job = scope.launch { runChain(listOf(s) + fallbacks) }
    }

    fun disconnect() {
        want = false
        job?.cancel(); job = null
        runCatching { vm.stopProvisionedVpnProfile() }
        endHistory("Disconnected")
        _state.value = State.Disconnected
        log("disconnected")
    }

    // ---------------------------------------------------------------- connect loop
    /** Try the chosen server (3 ESP/ID variants), then fall through to the next servers (1 try each). */
    private suspend fun runChain(chain: List<Server>) {
        want = true
        lastError = null
        log("device: Android API ${Build.VERSION.SDK_INT}, profile=${if (Build.VERSION.SDK_INT >= 31) "modern" else "legacy"}, mtu cap ${store.mtu}, bypassable=${store.directPing}, verify=${store.verify}")
        stopAndWait()
        for ((idx, s) in chain.withIndex()) {
            store.current = s.address
            if (tryServer(s, if (idx == 0) 3 else 1)) { monitor(s); return }
            if (!want) return
            log("giving up on ${s.address}")
        }
        fail("Could not connect to any server" + (lastError?.let { " - last error: $it" } ?: ". Check Logcat (filter: Ike) for the exact reason."))
    }

    private suspend fun stopAndWait() {
        runCatching { vm.stopProvisionedVpnProfile() }
        withTimeoutOrNull(3000) { up.first { !it } }
        delay(300)
    }

    private suspend fun awaitUp(): Boolean {
        val end = SystemClock.elapsedRealtime() + ATTEMPT_TIMEOUT_MS
        errorFlag = null
        while (SystemClock.elapsedRealtime() < end) {
            if (up.value) return true
            if (errorFlag != null) return false
            delay(250)
        }
        return false
    }

    private suspend fun tryServer(s: Server, attempts: Int): Boolean {
        for (n in 0 until attempts) {
            val variant = if (attempts == 1) 1 else n
            _state.value = State.Connecting(s.name, n + 1, attempts)
            _detail.value = "${s.name} - try ${n + 1}/$attempts" + (lastError?.let { "\n$it" } ?: "")
            log("connecting to ${s.address} (try ${n + 1}/$attempts: ${describe(variant)})")
            try {
                if (vm.provisionVpnProfile(buildProfile(s, variant)) != null) { fail("VPN permission was revoked"); return false }
                if (Build.VERSION.SDK_INT >= 33) vm.startProvisionedVpnProfileSession() else vm.startProvisionedVpnProfile()
            } catch (e: Exception) {
                fail("${e.javaClass.simpleName}: ${e.message}"); return false
            }
            vpnIp = null
            if (awaitUp()) {
                log("tunnel up (virtual IP $vpnIp)")
                _detail.value = "Checking connection..."
                if (!store.verify || validateTunnel()) {
                    since = System.currentTimeMillis()
                    histServer = s; histStart = since
                    _state.value = State.Connected(s.name, since, vpnIp)
                    log("connection verified - traffic passes")
                    return true
                }
                log("tunnel is up but NO traffic passes (option ${variant + 1}) - trying the next option")
                stopAndWait()
                continue
            }
            log("try ${n + 1} failed" + (errorFlag?.let { ": $it" } ?: " / timed out"))
            stopAndWait()
        }
        return false
    }

    private fun describe(i: Int) = when (i) {
        0 -> "fast ESP, unique local id"
        1 -> "broad ESP list, unique local id"
        else -> "broad ESP list, username as local id"
    }

    // ---------------------------------------------------------------- monitor / stats / history
    private fun endHistory(status: String) {
        val s = histServer ?: return
        histServer = null
        val st = stats.value
        store.addHistory(Hist(s.name, s.cc, histStart, System.currentTimeMillis(), st.rx, st.tx, status))
    }

    private fun fetchPublicIp() {
        scope.launch(Dispatchers.IO) {
            try {
                val c = URL("https://api.ipify.org").openConnection() as HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000
                val ip = c.inputStream.bufferedReader().readText().trim()
                stats.update { it.copy(publicIp = ip) }
            } catch (e: Exception) { log("public IP lookup failed: ${e.javaClass.simpleName}") }
        }
    }

    private fun measureLatency() {
        scope.launch(Dispatchers.IO) {
            val t = SystemClock.elapsedRealtime()
            val ok = try { Socket().use { it.connect(InetSocketAddress("1.1.1.1", 443), 3000) }; true } catch (e: Exception) { false }
            if (ok) stats.update { it.copy(latencyMs = (SystemClock.elapsedRealtime() - t).toInt()) }
        }
    }

    private val probeTargets = listOf("1.1.1.1" to 443, "8.8.8.8" to 443, "9.9.9.9" to 443, "www.cloudflare.com" to 443)

    /** True if at least one of several well-known endpoints answers through the tunnel. */
    private suspend fun probeTunnel(): Boolean = withContext(Dispatchers.IO) {
        for ((h, port) in probeTargets) {
            val ok = try { Socket().use { it.connect(InetSocketAddress(h, port), 2000) }; true } catch (e: Exception) { false }
            if (ok) return@withContext true
        }
        false
    }

    private suspend fun validateTunnel(): Boolean {
        val end = SystemClock.elapsedRealtime() + 9_000
        while (SystemClock.elapsedRealtime() < end) {
            if (probeTunnel()) return true
            delay(600)
        }
        return false
    }

    /**
     * Port of ikev2split-watch: liveness check, failover, usage accounting.
     * Deliberately conservative: the tunnel is only called dead after ~25 s without inbound traffic AND
     * three failed probes. Then the VPN is stopped so the normal internet comes back.
     */
    private suspend fun monitor(s: Server) {
        val rx0 = TrafficStats.getTotalRxBytes().coerceAtLeast(0)
        val tx0 = TrafficStats.getTotalTxBytes().coerceAtLeast(0)
        var lastRx = rx0; var lastTx = tx0
        var pendRx = 0L; var pendTx = 0L
        var tick = 0
        var fails = 0
        var lastRxAt = SystemClock.elapsedRealtime()
        stats.value = Stats()
        _detail.value = ""
        fetchPublicIp(); measureLatency()
        while (want) {
            if (up.value && fails < 3) {
                if (state.value !is State.Connected) _state.value = State.Connected(s.name, since, vpnIp)
                delay(1000)
                val rx = TrafficStats.getTotalRxBytes().coerceAtLeast(0)
                val tx = TrafficStats.getTotalTxBytes().coerceAtLeast(0)
                val dr = (rx - lastRx).coerceAtLeast(0); val dt = (tx - lastTx).coerceAtLeast(0)
                lastRx = rx; lastTx = tx; pendRx += dr; pendTx += dt
                stats.update {
                    it.copy(downBps = dr, upBps = dt, rx = rx - rx0, tx = tx - tx0,
                        downHist = (it.downHist + dr).takeLast(60), upHist = (it.upHist + dt).takeLast(60))
                }
                if (dr > 2000) { lastRxAt = SystemClock.elapsedRealtime(); fails = 0 }
                tick++
                if (tick % 5 == 0 && SystemClock.elapsedRealtime() - lastRxAt > 10_000) {
                    if (probeTunnel()) { fails = 0; lastRxAt = SystemClock.elapsedRealtime() }
                    else { fails++; log("no traffic for 10 s and probe failed ($fails/3)") }
                }
                if (tick % 10 == 0) {
                    store.addSeconds(10); store.addBytes(pendRx, pendTx); pendRx = 0; pendTx = 0
                    measureLatency()
                }
            } else {
                _detail.value = "Reconnecting..."
                _state.value = State.Connecting(s.name, 0, 0)
                var recovered = false
                val deadline = SystemClock.elapsedRealtime() + 15_000
                while (SystemClock.elapsedRealtime() < deadline && want) {
                    if (up.value && probeTunnel()) { recovered = true; break }
                    delay(1000)
                }
                if (!want) return
                if (recovered) { fails = 0; lastRxAt = SystemClock.elapsedRealtime(); continue }

                log("connection lost: ${s.address}")
                endHistory("Connection lost")
                stopAndWait()   // give the phone its normal internet back
                if (store.failover) {
                    val n = Pinger.pingAll(store.servers().filter { it.address != s.address })
                        .filter { it.pingMs != null }.minByOrNull { it.pingMs!! }
                    if (n != null) { log("failover -> ${n.address}"); connect(n); return }
                }
                want = false
                _state.value = State.Failed("VPN dropped - normal internet restored")
                log("VPN stopped so normal internet works again")
                return
            }
        }
    }

    // ---------------------------------------------------------------- profile builder
    private fun buildProfile(s: Server, attempt: Int): Ikev2VpnProfile =
        if (Build.VERSION.SDK_INT >= 31) ModernProfile.build(app, store, s, attempt)
        else LegacyProfile.build(store, s, attempt)
}
