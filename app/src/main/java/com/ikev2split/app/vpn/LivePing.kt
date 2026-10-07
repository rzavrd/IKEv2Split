package com.ikev2split.app.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

data class Live(val directMs: Int? = null, val vpnMs: Int? = null)

/**
 * Live latency of the real (non-VPN) connection to Iranian sites, and of the tunnel.
 * The direct probe is bound to the underlying network, which works because the VPN profile is "bypassable".
 */
object LivePing {
    private val domestic = listOf("www.digikala.com", "www.aparat.com")

    suspend fun direct(ctx: Context): Int? = withContext(Dispatchers.IO) {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        val net = cm.allNetworks.firstOrNull { n ->
            cm.getNetworkCapabilities(n)?.let {
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            } == true
        } ?: return@withContext null
        for (h in domestic) {
            val ms = probe(net, h)
            if (ms != null) return@withContext ms
        }
        null
    }

    private fun probe(net: Network, host: String): Int? = try {
        val addr = net.getByName(host)
        val s = Socket()
        net.bindSocket(s)
        val t = SystemClock.elapsedRealtime()
        s.connect(InetSocketAddress(addr, 443), 2000)
        val ms = (SystemClock.elapsedRealtime() - t).toInt()
        s.close()
        ms
    } catch (e: Exception) { null }

    /** Round trip through the default route (= the tunnel while connected). */
    suspend fun vpn(): Int? = withContext(Dispatchers.IO) {
        try {
            val s = Socket()
            val t = SystemClock.elapsedRealtime()
            s.connect(InetSocketAddress("1.1.1.1", 443), 2000)
            val ms = (SystemClock.elapsedRealtime() - t).toInt()
            s.close()
            ms
        } catch (e: Exception) { null }
    }
}
