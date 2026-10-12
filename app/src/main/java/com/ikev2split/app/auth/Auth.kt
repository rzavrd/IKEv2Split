package com.ikev2split.app.auth

import android.content.Context
import android.os.Build
import com.ikev2split.app.data.Defaults
import com.ikev2split.app.data.OvpnProfile
import com.ikev2split.app.data.Store
import com.ikev2split.app.vpn.VpnController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface AuthState {
    /** No AUTH_URL configured: the app works without login. */
    data object Disabled : AuthState
    data object LoggedOut : AuthState
    data object Busy : AuthState
    data class LoggedIn(val username: String, val expiresAt: Long) : AuthState
}

/**
 * Account login against your own server (server/mylo_server.py).
 * The VPN account, server list and OpenVPN profiles are downloaded only after a successful sign-in and
 * are wiped when the user signs out or the administrator disables the account.
 */
object Auth {
    val state = MutableStateFlow<AuthState>(AuthState.Disabled)
    val error = MutableStateFlow<String?>(null)
    /** bumps whenever the stored servers / profiles changed, so screens reload them */
    val configTick = MutableStateFlow(0)
    private lateinit var store: Store

    fun enabled(): Boolean = Defaults.AUTH_URL.isNotBlank()

    fun init(ctx: Context) {
        if (this::store.isInitialized) return
        store = Store(ctx.applicationContext)
        state.value = when {
            !enabled() -> AuthState.Disabled
            store.authToken.isNotEmpty() -> AuthState.LoggedIn(store.authUser, store.authExpires())
            else -> AuthState.LoggedOut
        }
    }

    private class Resp(val code: Int, val body: String, val etag: String = "")

    private fun call(
        method: String, path: String, token: String?, body: JSONObject?,
        connectMs: Int = 10_000, readMs: Int = 15_000, etag: String = "",
    ): Resp {
        val c = URL(Defaults.AUTH_URL.trimEnd('/') + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = connectMs
        c.readTimeout = readMs
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("Accept-Encoding", "gzip")
        if (etag.isNotEmpty()) c.setRequestProperty("If-None-Match", etag)
        if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            c.doOutput = true
            c.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        val code = c.responseCode
        val raw = if (code in 200..299) c.inputStream else c.errorStream
        val stream = if (raw != null && c.contentEncoding.equals("gzip", true)) java.util.zip.GZIPInputStream(raw) else raw
        val text = stream?.bufferedReader()?.readText().orEmpty()
        return Resp(code, text, c.getHeaderField("ETag").orEmpty())
    }

    private fun message(code: String) = when (code) {
        "invalid" -> "Wrong username or password"
        "disabled" -> "This account is disabled"
        "expired" -> "This account has expired"
        "device_limit" -> "This account is already used on the maximum number of phones"
        "rate" -> "Too many attempts. Try again in a few minutes"
        else -> "Sign-in failed"
    }

    private fun errorCode(body: String): String = try { JSONObject(body.ifEmpty { "{}" }).optString("error") } catch (e: Exception) { "" }

    suspend fun login(user: String, pass: String) = withContext(Dispatchers.IO) {
        state.value = AuthState.Busy
        error.value = null
        try {
            val r = call(
                "POST", "/api/login", null,
                JSONObject().put("username", user).put("password", pass).put("deviceId", store.deviceId()).put("model", Build.MODEL),
            )
            if (r.code != 200) {
                error.value = message(errorCode(r.body))
                state.value = AuthState.LoggedOut
                return@withContext
            }
            val o = JSONObject(r.body)
            store.authToken = o.getString("token")
            store.authUser = o.optString("username", user)
            store.setAuthExpires(o.optLong("expiresAt", 0))
            if (loadConfig()) {
                state.value = AuthState.LoggedIn(store.authUser, store.authExpires())
            } else {
                wipe()
                if (error.value == null) error.value = "Could not load your VPN settings"
                state.value = AuthState.LoggedOut
            }
        } catch (e: Exception) {
            error.value = "Cannot reach the server"
            state.value = AuthState.LoggedOut
        }
    }

    /** Downloads the VPN account, servers and OpenVPN profiles. false = denied or failed. */
    suspend fun loadConfig(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try {
            val r = call("GET", "/api/config", store.authToken, null, etag = if (force) "" else store.configEtag)
            if (r.code == 401 || r.code == 403) {
                revoke(errorCode(r.body))
                return@withContext false
            }
            if (r.code == 304) return@withContext true          // nothing changed on the server
            if (r.code != 200) return@withContext false
            val o = JSONObject(r.body)
            val v = o.optJSONObject("vpn")
            if (v != null) {
                store.user = v.optString("user")
                store.pass = v.optString("pass")
                v.optString("remoteId").takeIf { it.isNotBlank() }?.let { store.remoteId = it }
            }
            val arr = o.optJSONArray("servers")
            if (arr != null) {
                val pairs = (0 until arr.length()).map { i ->
                    val x = arr.getJSONObject(i)
                    x.optString("cc").uppercase() to x.getString("address")
                }
                val list = Defaults.named(pairs)
                store.saveServers(list)
                if (store.current.isEmpty() || list.none { it.address == store.current }) store.current = list.firstOrNull()?.address ?: ""
            }
            val oa = o.optJSONArray("openvpn")
            if (oa != null) {
                val remote = (0 until oa.length()).map { i ->
                    val x = oa.getJSONObject(i)
                    OvpnProfile(x.getString("name"), x.optString("cc"), x.getString("config"), false)
                }
                store.saveOvpn(remote + store.ovpn().filter { l -> l.local && remote.none { it.name == l.name } })
            }
            store.setAuthExpires(o.optLong("expiresAt", store.authExpires()))
            store.configEtag = r.etag
            configTick.value = configTick.value + 1
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Heartbeat. false = access was removed (the user is signed out and the VPN is stopped). Offline = true. */
    suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        if (!enabled()) return@withContext true
        val token = store.authToken
        if (token.isEmpty()) {
            state.value = AuthState.LoggedOut
            return@withContext false
        }
        val exp = store.authExpires()
        val locallyExpired = exp > 0 && exp < System.currentTimeMillis() / 1000
        try {
            val r = call("GET", "/api/ping", token, null, connectMs = 5_000, readMs = 6_000)
            if (r.code == 401 || r.code == 403) {
                revoke(errorCode(r.body))
                false
            } else {
                if (r.code == 200) {
                    try {
                        val newExp = JSONObject(r.body).optLong("expiresAt", exp)
                        if (newExp != exp) {
                            store.setAuthExpires(newExp)
                            val s = state.value
                            if (s is AuthState.LoggedIn) state.value = s.copy(expiresAt = newExp)
                        }
                    } catch (e: Exception) { }
                }
                true
            }
        } catch (e: Exception) {
            if (locallyExpired) { revoke("expired"); false } else true     // offline: only trust the local date when it already says expired
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        VpnController.disconnect()
        try { call("POST", "/api/logout", store.authToken, JSONObject()) } catch (e: Exception) { }
        wipe()
        error.value = null
        state.value = if (enabled()) AuthState.LoggedOut else AuthState.Disabled
    }

    private fun revoke(code: String) {
        wipe()
        error.value = if (code == "invalid" || code.isEmpty()) "Your access was removed. Sign in again." else message(code)
        state.value = AuthState.LoggedOut
        VpnController.disconnect()
    }

    /** Forget the session and everything the server had handed out. */
    private fun wipe() {
        store.authToken = ""
        store.authUser = ""
        store.setAuthExpires(0)
        store.user = ""
        store.pass = ""
        store.saveServers(emptyList())
        store.saveOvpn(store.ovpn().filter { it.local })
        store.configEtag = ""
        store.current = ""
        configTick.value = configTick.value + 1
    }
}
