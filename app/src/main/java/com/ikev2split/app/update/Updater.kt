package com.ikev2split.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
<<<<<<< HEAD
import com.ikev2split.app.data.Defaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
=======
import kotlinx.coroutines.Dispatchers
>>>>>>> origin/main
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateInfo(
    val versionCode: Int, val versionName: String, val apkUrl: String,
    val sha256: String, val notes: String, val mandatory: Boolean,
<<<<<<< HEAD
    /** the same file offered by other sources (your own server, GitHub ...): tried one after the other when a download fails */
    val mirrors: List<String> = emptyList(),
=======
>>>>>>> origin/main
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val percent: Int) : UpdateState
    data class Installing(val info: UpdateInfo) : UpdateState
    data class Error(val msg: String) : UpdateState
}

/**
 * Self-hosted updater: reads update.json from your server, downloads the new APK, verifies its SHA-256
 * and hands it to Android's PackageInstaller. Android itself refuses any APK that is not signed with the
 * same key as the installed app, so nobody can swap in a different app through this path.
 */
object Updater {
    val state = MutableStateFlow<UpdateState>(UpdateState.Idle)

<<<<<<< HEAD
    @Suppress("DEPRECATION")
    fun installedCode(ctx: Context): Int {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        // longVersionCode exists from Android 9 (API 28); Android 8 only has versionCode
        return if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else pi.versionCode
    }
=======
    fun installedCode(ctx: Context): Int =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode.toInt()
>>>>>>> origin/main

    fun installedName(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"

    private fun configured(url: String) = url.isNotBlank() && !url.contains("YOUR-DOMAIN") && url.startsWith("https://")

<<<<<<< HEAD
    private fun fetchFeed(u: String): UpdateInfo {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000; c.readTimeout = 8_000
        c.setRequestProperty("User-Agent", "MYLO-VPN")
        c.setRequestProperty("Cache-Control", "no-cache")
        if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
        val o = JSONObject(c.inputStream.bufferedReader().readText())
        return UpdateInfo(
            o.getInt("versionCode"), o.optString("versionName"), o.getString("apkUrl"),
            o.getString("sha256"), o.optString("notes"), o.optBoolean("mandatory", false),
        )
    }

    /** Where to look: the address in Profile > Advanced / Defaults (comma separated) plus your own account server. */
    private fun feeds(url: String): List<String> {
        val list = url.split(',', ' ', '\n').map { it.trim() }.toMutableList()
        if (Defaults.AUTH_URL.isNotBlank()) list += Defaults.AUTH_URL.trimEnd('/') + "/update.json"
        return list.filter { configured(it) }.distinct()
    }

    suspend fun check(ctx: Context, url: String, manual: Boolean) = withContext(Dispatchers.IO) {
        val urls = feeds(url)
=======
    suspend fun check(ctx: Context, url: String, manual: Boolean) = withContext(Dispatchers.IO) {
        val urls = url.split(',', ' ', '\n').map { it.trim() }.filter { configured(it) }
>>>>>>> origin/main
        if (urls.isEmpty()) {
            if (manual) state.value = UpdateState.Error("Update server is not set (run tools/release.py setup, or Profile > Advanced > Update URL).")
            return@withContext
        }
        if (manual) state.value = UpdateState.Checking
<<<<<<< HEAD
        val results = coroutineScope {
            urls.map { u -> async { try { Result.success(fetchFeed(u)) } catch (e: Exception) { Result.failure<UpdateInfo>(e) } } }.awaitAll()
        }
        val infos = results.mapNotNull { it.getOrNull() }
        if (infos.isEmpty()) {
            val why = results.mapNotNull { it.exceptionOrNull() }.joinToString("; ") { it.message ?: it.javaClass.simpleName }.take(160)
            state.value = if (manual) UpdateState.Error("Could not check for updates: $why") else UpdateState.Idle
            return@withContext
        }
        val best = infos.maxByOrNull { it.versionCode }!!
        val same = infos.filter { it.versionCode == best.versionCode && it.sha256.equals(best.sha256, ignoreCase = true) }
        val withMirrors = best.copy(mirrors = same.map { it.apkUrl }.filter { it != best.apkUrl }.distinct())
        state.value = if (best.versionCode > installedCode(ctx)) UpdateState.Available(withMirrors)
        else if (manual) UpdateState.UpToDate else UpdateState.Idle
=======
        var lastError: Exception? = null
        for (u in urls) {
            try {
                val c = URL(u).openConnection() as HttpURLConnection
                c.connectTimeout = 10_000; c.readTimeout = 10_000
                val o = JSONObject(c.inputStream.bufferedReader().readText())
                val info = UpdateInfo(
                    o.getInt("versionCode"), o.optString("versionName"), o.getString("apkUrl"),
                    o.getString("sha256"), o.optString("notes"), o.optBoolean("mandatory", false),
                )
                state.value = if (info.versionCode > installedCode(ctx)) UpdateState.Available(info)
                else if (manual) UpdateState.UpToDate else UpdateState.Idle
                return@withContext
            } catch (e: Exception) {
                lastError = e
            }
        }
        state.value = if (manual) UpdateState.Error("Could not check for updates: ${lastError?.message ?: lastError?.javaClass?.simpleName}") else UpdateState.Idle
>>>>>>> origin/main
    }

    fun dismiss() { state.value = UpdateState.Idle }
    fun fail(msg: String) { state.value = UpdateState.Error(msg) }

    suspend fun downloadAndInstall(ctx: Context, info: UpdateInfo) = withContext(Dispatchers.IO) {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            state.value = UpdateState.Error("Allow 'Install unknown apps' for MYLO VPN, come back and tap Update again.")
            return@withContext
        }
<<<<<<< HEAD
        val sources = (listOf(info.apkUrl) + info.mirrors).distinct()
        val f = File(ctx.cacheDir, "update.apk")
        var last = "no source"
        for (src in sources) {
            try {
                state.value = UpdateState.Downloading(info, 0)
                download(src, f, info)
                state.value = UpdateState.Installing(info)
                install(ctx, f)
                return@withContext
            } catch (e: Exception) {
                f.delete()
                last = e.message ?: e.javaClass.simpleName
                if (e is java.net.UnknownHostException || e is java.net.SocketTimeoutException) last = "cannot reach the download server ($last)"
            }
        }
        state.value = UpdateState.Error("Update failed: $last" + if (sources.size == 1) "" else " (tried ${sources.size} sources)")
    }

    private fun download(src: String, f: File, info: UpdateInfo) {
        val md = MessageDigest.getInstance("SHA-256")
        val c = URL(src).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", "MYLO-VPN")
        if (c.responseCode != 200) throw IllegalStateException("server answered ${c.responseCode}")
        val total = c.contentLengthLong
        var done = 0L
        var lastPct = -1
        c.inputStream.use { ins ->
            f.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n); md.update(buf, 0, n); done += n
                    if (total > 0) {
                        val pct = (done * 100 / total).toInt()
                        if (pct != lastPct) { lastPct = pct; state.value = UpdateState.Downloading(info, pct) }
                    }
                }
            }
        }
        if (total > 0 && done != total) throw IllegalStateException("download was cut off ($done of $total bytes)")
        val got = md.digest().joinToString("") { "%02x".format(it) }
        if (info.sha256.isBlank() || !got.equals(info.sha256, ignoreCase = true)) throw IllegalStateException("checksum mismatch - file rejected")
=======
        try {
            state.value = UpdateState.Downloading(info, 0)
            val f = File(ctx.cacheDir, "update.apk")
            val md = MessageDigest.getInstance("SHA-256")
            val c = URL(info.apkUrl).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000; c.readTimeout = 20_000
            if (c.responseCode != 200) throw IllegalStateException("server answered ${c.responseCode}")
            val total = c.contentLengthLong
            var done = 0L
            c.inputStream.use { ins ->
                f.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n); md.update(buf, 0, n); done += n
                        if (total > 0) state.value = UpdateState.Downloading(info, (done * 100 / total).toInt())
                    }
                }
            }
            val got = md.digest().joinToString("") { "%02x".format(it) }
            if (info.sha256.isBlank() || !got.equals(info.sha256, ignoreCase = true)) {
                f.delete()
                throw IllegalStateException("checksum mismatch - update rejected")
            }
            state.value = UpdateState.Installing(info)
            install(ctx, f)
        } catch (e: Exception) {
            state.value = UpdateState.Error("Update failed: ${e.message ?: e.javaClass.simpleName}")
        }
>>>>>>> origin/main
    }

    private fun install(ctx: Context, f: File) {
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        val id = pi.createSession(params)
        pi.openSession(id).use { session ->
            f.inputStream().use { ins ->
                session.openWrite("mylo.apk", 0, f.length()).use { out ->
                    ins.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(ctx, UpdateReceiver::class.java).setAction("com.mylo.vpn.UPDATE_RESULT")
            val pending = PendingIntent.getBroadcast(ctx, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.commit(pending.intentSender)
        }
    }
}
