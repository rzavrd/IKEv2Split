package com.ikev2split.app.data

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.os.Build
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class Check(
    val title: String, val desc: String, val ok: Boolean, val detail: String? = null,
    val subTitle: String? = null, val subDesc: String? = null, val subOk: Boolean = true,
)

/** Four device checks, shown in the "Secure Device Assistant". */
fun deviceChecks(ctx: Context): List<Check> {
    val patch = Build.VERSION.SECURITY_PATCH
    val patchOk = try { ChronoUnit.DAYS.between(LocalDate.parse(patch), LocalDate.now()) <= 150 } catch (e: Exception) { false }

    val locked = ctx.getSystemService(KeyguardManager::class.java)?.isDeviceSecure ?: false
    val status = ctx.getSystemService(DevicePolicyManager::class.java)?.storageEncryptionStatus
    val enc = status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE ||
        status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER ||
        status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY

    val bio = try {
        ctx.getSystemService(BiometricManager::class.java)
            ?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    } catch (e: Exception) { false }

    val rooted = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/data/local/xbin/su",
        "/data/local/bin/su", "/system/app/Superuser.apk",
    ).any { File(it).exists() } || (Build.TAGS?.contains("test-keys") == true)

    return listOf(
        Check("Latest security patch", "Installing recent Android security updates keeps your device safe from known vulnerabilities.",
            patchOk, "Your patch level: $patch"),
        Check("Screen lock enabled", "A PIN, pattern or password protects your data if the phone is lost or stolen.", locked, null,
            "Device encryption", "Encrypting storage helps safeguard your data in the event of unauthorized access to your device.", enc),
        Check("Biometric security enabled", "Fingerprint or face unlock gives quick and secure device access.", bio),
        Check(if (rooted) "Rooted device: detected" else "Rooted device: not detected",
            "Rooted devices are more vulnerable to malware, data theft and unauthorized access. Back up your data and restore factory settings to stay secure.",
            !rooted),
    )
}
