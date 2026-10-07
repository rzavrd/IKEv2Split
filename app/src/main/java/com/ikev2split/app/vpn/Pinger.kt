package com.ikev2split.app.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.ikev2split.app.data.Server
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Server latency, measured over the phone's real (non-VPN) connection, so it works while connected too.
 * Probe = a UDP/500 IKE_SA_INIT request; any answer from the server (accept or error) gives the round trip.
 * Falls back to ICMP only when no VPN is active.
 */
object Pinger {
    private lateinit var app: Context
    private val rnd = SecureRandom()

    fun init(ctx: Context) { if (!this::app.isInitialized) app = ctx.applicationContext }

    private fun networks(): Pair<Network?, Boolean> {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        val all = cm.allNetworks.toList()
        var direct: Network? = null
        var vpn = false
        for (n in all) {
            val c = cm.getNetworkCapabilities(n) ?: continue
            if (c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) vpn = true
            else if (direct == null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) direct = n
        }
        return direct to vpn
    }

    suspend fun ping(host: String): Int? = withContext(Dispatchers.IO) {
        val (net, vpn) = networks()
        val ms = if (net != null) ikeProbe(net, host) else null
        ms ?: if (!vpn) icmp(host) else null
    }

    suspend fun pingAll(list: List<Server>, onResult: ((Server) -> Unit)? = null): List<Server> = coroutineScope {
        val sem = Semaphore(6)
        list.map { s ->
            async {
                sem.withPermit {
                    val r = s.copy(pingMs = ping(s.address))
                    onResult?.invoke(r)
                    r
                }
            }
        }.awaitAll()
    }

    private fun ikeProbe(net: Network, host: String): Int? {
        var sock: DatagramSocket? = null
        return try {
            val addr = net.getByName(host)
            sock = DatagramSocket()
            net.bindSocket(sock)
            sock.soTimeout = 1500
            val pkt = ikeInit()
            val t = SystemClock.elapsedRealtime()
            sock.send(DatagramPacket(pkt, pkt.size, addr, 500))
            val rp = DatagramPacket(ByteArray(1500), 1500)
            sock.receive(rp)
            (SystemClock.elapsedRealtime() - t).toInt()
        } catch (e: Exception) {
            null
        } finally {
            sock?.close()
        }
    }

    private fun ByteBuffer.u8(v: Int): ByteBuffer = put(v.toByte())
    private fun ByteBuffer.u16(v: Int): ByteBuffer = putShort(v.toShort())

    /** A well-formed IKE_SA_INIT request (AES128-CBC / HMAC-SHA1 / MODP1024) with random key material. */
    private fun ikeInit(): ByteArray {
        val spi = ByteArray(8).also { rnd.nextBytes(it) }
        val ke = ByteArray(128).also { rnd.nextBytes(it); it[0] = (it[0].toInt() and 0x7f).toByte() }
        val nonce = ByteArray(32).also { rnd.nextBytes(it) }
        val b = ByteBuffer.allocate(248)
        // header (28 bytes)
        b.put(spi); b.put(ByteArray(8))
        b.u8(33); b.u8(0x20); b.u8(34); b.u8(0x08); b.putInt(0); b.putInt(248)
        // SA payload (48 bytes)
        b.u8(34); b.u8(0); b.u16(48)
        b.u8(0); b.u8(0); b.u16(44); b.u8(1); b.u8(1); b.u8(0); b.u8(4)
        b.u8(3); b.u8(0); b.u16(12); b.u8(1); b.u8(0); b.u16(12); b.u16(0x800E); b.u16(128) // ENCR AES-CBC, key 128
        b.u8(3); b.u8(0); b.u16(8); b.u8(2); b.u8(0); b.u16(2)                              // PRF HMAC-SHA1
        b.u8(3); b.u8(0); b.u16(8); b.u8(3); b.u8(0); b.u16(2)                              // INTEG HMAC-SHA1-96
        b.u8(0); b.u8(0); b.u16(8); b.u8(4); b.u8(0); b.u16(2)                              // DH MODP1024
        // KE payload (136 bytes)
        b.u8(40); b.u8(0); b.u16(136); b.u16(2); b.u16(0); b.put(ke)
        // Nonce payload (36 bytes)
        b.u8(0); b.u8(0); b.u16(36); b.put(nonce)
        return b.array()
    }

    private fun icmp(host: String): Int? = try {
        val p = ProcessBuilder("ping", "-c", "1", "-W", "2", host).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor(4, TimeUnit.SECONDS)
        Regex("time=([0-9.]+)").find(out)?.groupValues?.get(1)?.toFloat()?.toInt()
    } catch (e: Exception) {
        null
    }
}
