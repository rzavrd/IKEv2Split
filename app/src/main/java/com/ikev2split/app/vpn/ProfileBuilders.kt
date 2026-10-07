package com.ikev2split.app.vpn

import android.content.Context
import android.net.Ikev2VpnProfile
import android.net.eap.EapSessionConfig
import android.net.ipsec.ike.ChildSaProposal
import android.net.ipsec.ike.IkeFqdnIdentification
import android.net.ipsec.ike.IkeSaProposal
import android.net.ipsec.ike.IkeSessionParams
import android.net.ipsec.ike.IkeTunnelConnectionParams
import android.net.ipsec.ike.SaProposal
import android.net.ipsec.ike.TunnelModeChildSessionParams
import android.system.OsConstants
import com.ikev2split.app.data.Server
import com.ikev2split.app.data.Store
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

private fun serverCa(store: Store): X509Certificate? = store.caBytes()?.let {
    CertificateFactory.getInstance("X.509").generateCertificate(it.inputStream()) as X509Certificate
}

/**
 * Android 12+ (API 31): full control of IKE/ESP proposals, remote ID and local ID.
 * Kept in its own object so older phones never load these classes.
 */
object ModernProfile {
    private fun ike(enc: Int, integ: Int, prf: Int, dh: Int) = IkeSaProposal.Builder()
        .addEncryptionAlgorithm(SaProposal.ENCRYPTION_ALGORITHM_AES_CBC, enc)
        .addIntegrityAlgorithm(integ)
        .addPseudorandomFunction(prf)
        .addDhGroup(dh)
        .build()

    private fun child(enc: Int, integ: Int, dh: Int) = ChildSaProposal.Builder()
        .addEncryptionAlgorithm(SaProposal.ENCRYPTION_ALGORITHM_AES_CBC, enc)
        .addIntegrityAlgorithm(integ)
        .addDhGroup(dh)
        .build()

    fun build(app: Context, store: Store, s: Server, attempt: Int): Ikev2VpnProfile {
        val user = store.user
        val rid = store.remoteId.ifBlank { "pointtoserver.com" }
        val lid = if (attempt >= 2) user else store.localId()

        // proposals = aes256-sha256-modp2048, aes256-sha1-modp2048, aes128-sha1-modp1024  (router)
        val ikeProposals = listOf(
            ike(256, SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA2_256_128, SaProposal.PSEUDORANDOM_FUNCTION_SHA2_256, SaProposal.DH_GROUP_2048_BIT_MODP),
            ike(256, SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA1_96, SaProposal.PSEUDORANDOM_FUNCTION_HMAC_SHA1, SaProposal.DH_GROUP_2048_BIT_MODP),
            ike(128, SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA1_96, SaProposal.PSEUDORANDOM_FUNCTION_HMAC_SHA1, SaProposal.DH_GROUP_1024_BIT_MODP),
        )
        // ESP_FAST = aes128-sha1-modp1024 ; ESP_SAFE adds aes256-sha256-modp2048 and aes128gcm16-modp2048
        val fast = child(128, SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA1_96, SaProposal.DH_GROUP_1024_BIT_MODP)
        val childProposals = if (attempt == 0) listOf(fast) else listOf(
            fast,
            child(256, SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA2_256_128, SaProposal.DH_GROUP_2048_BIT_MODP),
            ChildSaProposal.Builder()
                .addEncryptionAlgorithm(SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_16, 128)
                .addDhGroup(SaProposal.DH_GROUP_2048_BIT_MODP)
                .build(),
        )

        val eap = EapSessionConfig.Builder()
            .setEapIdentity(user.toByteArray())
            .setEapMsChapV2Config(user, store.pass)
            .build()

        val ikeParams = IkeSessionParams.Builder()
            .setServerHostname(s.address)
            .setLocalIdentification(IkeFqdnIdentification(lid))
            .setRemoteIdentification(IkeFqdnIdentification(rid))
            .setAuthEap(serverCa(store), eap)   // null -> system trust store
            .setDpdDelaySeconds(30)
            .also { b -> ikeProposals.forEach { b.addIkeSaProposal(it) } }
            .build()

        val childParams = TunnelModeChildSessionParams.Builder()
            .addInternalAddressRequest(OsConstants.AF_INET)
            .addInternalDnsServerRequest(OsConstants.AF_INET)
            .also { b -> childProposals.forEach { b.addChildSaProposal(it) } }
            .build()

        return Ikev2VpnProfile.Builder(IkeTunnelConnectionParams(ikeParams, childParams))
            .setBypassable(store.directPing)    // true only if the user enabled live ping while connected
            .setMetered(false)
            .setMaxMtu(store.mtu.coerceIn(1280, 1500))
            .setRequiresInternetValidation(false)
            .build()
    }
}

/**
 * Android 11 (API 30): the platform only offers the simple profile. The remote ID is the server address
 * and the proposal list is the system default, so some servers may refuse it.
 */
object LegacyProfile {
    fun build(store: Store, s: Server, attempt: Int): Ikev2VpnProfile {
        val lid = if (attempt >= 2) store.user else store.localId()
        return Ikev2VpnProfile.Builder(s.address, lid)
            .setAuthUsernamePassword(store.user, store.pass, serverCa(store))
            .setBypassable(store.directPing)
            .setMetered(false)
            .setMaxMtu(store.mtu.coerceIn(1280, 1500))
            .setRequiresInternetValidation(false)
            .build()
    }
}
