package com.ikev2split.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

/** Receives the PackageInstaller result: asks the user to confirm when Android requires it. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    i.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") i.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.let { c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> Updater.dismiss()
            else -> Updater.fail("Install did not finish: " + (i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "cancelled"))
        }
    }
}
