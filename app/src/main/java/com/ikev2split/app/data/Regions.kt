package com.ikev2split.app.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import java.util.Locale

object Regions {
    val names = listOf("All Regions", "Europe", "Americas", "Asia Pacific", "Middle East", "Africa")
    fun of(cc: String): String = when (cc.uppercase()) {
        "US", "CA", "BR", "AR", "CL", "MX" -> "Americas"
        "SG", "HK", "JP", "KR", "AU", "NZ", "BN", "IN", "TH", "MY", "ID" -> "Asia Pacific"
        "AE", "TR", "IL", "SA" -> "Middle East"
        "NG", "ZA", "EG" -> "Africa"
        else -> "Europe"
    }
}

fun countryName(cc: String): String =
    if (cc.length == 2) Locale("", cc).getDisplayCountry(Locale.ENGLISH).ifEmpty { cc } else "Other"

/** Launcher icon choices; each one is an <activity-alias> in the manifest. */
object IconStyles {
    val all = listOf("Classic", "Dynamic", "Neon", "Grey")
    fun apply(ctx: Context, style: String) {
        val pm = ctx.packageManager
        all.forEach { s ->
            pm.setComponentEnabledSetting(
                ComponentName(ctx.packageName, "com.ikev2split.app.Icon$s"),
                if (s == style) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
