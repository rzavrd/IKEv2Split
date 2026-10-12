package com.ikev2split.app.openvpn

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.ikev2split.app.data.OvpnProfile
import java.io.File

/**
 * OpenVPN is run by the free "OpenVPN for Android" app (package de.blinkt.openvpn).
 * MYLO VPN hands it the profile and asks it to connect; the tunnel itself belongs to that app.
 */
object OpenVpnBridge {
    const val PKG = "de.blinkt.openvpn"

    fun installed(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(PKG, 0)
        true
    } catch (e: Exception) {
        false
    }

    fun safeName(name: String) = name.replace(Regex("[^A-Za-z0-9._-]"), "_")

    /** Opens the profile in OpenVPN for Android, which imports it (the user confirms there once). */
    fun importProfile(ctx: Context, p: OvpnProfile) {
        val dir = File(ctx.cacheDir, "ovpn").apply { mkdirs() }
        val f = File(dir, safeName(p.name) + ".ovpn")
        f.writeText(p.config)
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/x-openvpn-profile")
            .setPackage(PKG)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    /** Starts an already imported profile by name. */
    fun connect(ctx: Context, p: OvpnProfile) {
        val i = Intent(Intent.ACTION_MAIN)
            .setClassName(PKG, "de.blinkt.openvpn.LaunchVPN")
            .putExtra("de.blinkt.openvpn.shortcutProfileName", safeName(p.name))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    fun openStorePage(ctx: Context) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PKG")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(market)
        } catch (e: Exception) {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/$PKG/")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
