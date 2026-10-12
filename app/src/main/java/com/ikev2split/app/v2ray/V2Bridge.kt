package com.ikev2split.app.v2ray

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import com.ikev2split.app.data.V2Profile
import java.net.InetSocketAddress
import java.net.Socket

/**
 * VMess / VLESS need a proxy core (Xray / sing-box) that MYLO VPN does not contain. The link is shared straight to
 * one of the free clients below (and also copied to the clipboard); the client imports it and makes the connection.
 */
object V2Bridge {
    /** (package, display name) in order of preference */
    val clients = listOf("com.v2ray.ang" to "v2rayNG", "app.hiddify.com" to "Hiddify", "moe.nb4a" to "NekoBox")

    fun installed(ctx: Context): Pair<String, String>? = clients.firstOrNull { (pkg, _) ->
        try { ctx.packageManager.getPackageInfo(pkg, 0); true } catch (e: Exception) { false }
    }

    fun copy(ctx: Context, p: V2Profile) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("MYLO ${p.kind}", p.link))
    }

    /** Shares the link to the installed client; if it cannot take a shared link, just opens it (the link is on the clipboard). */
    fun connect(ctx: Context, p: V2Profile): Boolean {
        val c = installed(ctx) ?: return false
        copy(ctx, p)
        try {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, p.link)
                .setPackage(c.first).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(send)
            return true
        } catch (e: Exception) { /* fall through to a plain launch */ }
        val i = ctx.packageManager.getLaunchIntentForPackage(c.first) ?: return false
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    /** Milliseconds to open a TCP connection to the server, or -1 when it does not answer. */
    fun tcpPing(host: String, port: Int): Int = try {
        val t = SystemClock.elapsedRealtime()
        Socket().use { it.connect(InetSocketAddress(host, port), 2500) }
        (SystemClock.elapsedRealtime() - t).toInt()
    } catch (e: Exception) { -1 }

    fun openStorePage(ctx: Context) {
        val pkg = clients.first().first
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/2dust/v2rayNG/releases")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
