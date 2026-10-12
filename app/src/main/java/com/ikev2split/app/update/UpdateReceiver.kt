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
            PackageInstaller.STATUS_FAILURE_ABORTED -> Updater.fail("Installation was cancelled. Tap Update again and confirm.")
            PackageInstaller.STATUS_FAILURE_BLOCKED -> Updater.fail("Android or Play Protect blocked the installation. In the Play Protect dialog choose 'Install anyway', or turn off scanning for this install.")
            PackageInstaller.STATUS_FAILURE_CONFLICT -> Updater.fail("The new version conflicts with the installed one. Uninstall MYLO VPN once and install the new APK.")
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> Updater.fail("This update was signed with a different key than the installed app, or does not fit this phone. Uninstall MYLO VPN once and install the new APK.")
            PackageInstaller.STATUS_FAILURE_INVALID -> Updater.fail("The downloaded file is not a valid app. Try again later.")
            PackageInstaller.STATUS_FAILURE_STORAGE -> Updater.fail("Not enough free storage to install the update.")
            else -> Updater.fail("Install did not finish: " + (i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "unknown error"))
        }
    }
}
