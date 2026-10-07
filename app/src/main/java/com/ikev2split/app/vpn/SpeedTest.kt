package com.ikev2split.app.vpn

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

data class SpeedState(
    val running: Boolean = false, val phase: String = "", val liveMbps: Double = 0.0,
    val down: Double? = null, val up: Double? = null,
    val latency: Int? = null, val jitter: Int? = null, val loss: Int? = null,
    val endpointIp: String? = null,
    val country: String? = null, val provider: String? = null, val ip: String? = null,
    val error: String? = null,
)

/** Real speed test against Cloudflare's public speed endpoints. Goes through the VPN when connected. */
object SpeedTest {
    val state = MutableStateFlow(SpeedState())
    private var job: Job? = null
    private const val HOST = "speed.cloudflare.com"

    fun start(scope: CoroutineScope) {
        if (state.value.running) return
        job = scope.launch(Dispatchers.IO) { run() }
    }

    fun stop() {
        job?.cancel()
        state.update { it.copy(running = false, phase = "", liveMbps = 0.0) }
    }

    private suspend fun run() {
        state.value = SpeedState(running = true, phase = "Preparing")
        try {
            loadInfo()
            latency()
            transfer(true)
            transfer(false)
            state.update { it.copy(phase = "Done") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state.update { it.copy(error = e.message ?: e.javaClass.simpleName) }
        } finally {
            state.update { it.copy(running = false, liveMbps = 0.0) }
        }
    }

    private fun loadInfo() {
        try {
            val c = URL("https://ipinfo.io/json").openConnection() as HttpURLConnection
            c.connectTimeout = 6000; c.readTimeout = 6000
            val o = JSONObject(c.inputStream.bufferedReader().readText())
            val org = o.optString("org").replace(Regex("^AS\\d+\\s*"), "")
            state.update { it.copy(ip = o.optString("ip"), country = o.optString("country"), provider = org) }
        } catch (e: Exception) { /* optional info */ }
    }

    private suspend fun latency() {
        state.update { it.copy(phase = "Latency") }
        val ip = try { InetAddress.getByName(HOST).hostAddress } catch (e: Exception) { null }
        state.update { it.copy(endpointIp = ip) }
        val times = mutableListOf<Long>()
        var lost = 0
        repeat(10) {
            val t = SystemClock.elapsedRealtime()
            val ok = try { Socket().use { s -> s.connect(InetSocketAddress(HOST, 443), 2000) }; true } catch (e: Exception) { false }
            if (ok) times.add(SystemClock.elapsedRealtime() - t) else lost++
            delay(80)
        }
        val avg = if (times.isEmpty()) null else times.average().toInt()
        val jit = if (times.size > 1) times.zipWithNext { a, b -> abs(a - b) }.average().toInt() else 0
        state.update { it.copy(latency = avg, jitter = jit, loss = lost * 10) }
    }

    private suspend fun transfer(down: Boolean) = coroutineScope {
        state.update { it.copy(phase = if (down) "Download" else "Upload", liveMbps = 0.0) }
        val total = AtomicLong(0)
        val start = SystemClock.elapsedRealtime()
        val end = start + if (down) 8000L else 6000L
        val workers = (1..4).map { launch(Dispatchers.IO) { if (down) downloadLoop(total, end) else uploadLoop(total, end) } }
        var last = 0L
        var lastT = start
        while (SystemClock.elapsedRealtime() < end && workers.any { it.isActive }) {
            delay(250)
            val now = SystemClock.elapsedRealtime()
            val b = total.get()
            val mbps = (b - last) * 8.0 / ((now - lastT) / 1000.0) / 1e6
            last = b; lastT = now
            state.update { it.copy(liveMbps = mbps) }
        }
        workers.forEach { it.cancel() }
        val secs = ((SystemClock.elapsedRealtime() - start) / 1000.0).coerceAtLeast(0.5)
        val avg = total.get() * 8.0 / secs / 1e6
        state.update { if (down) it.copy(down = avg, liveMbps = 0.0) else it.copy(up = avg, liveMbps = 0.0) }
    }

    private fun downloadLoop(total: AtomicLong, end: Long) {
        val buf = ByteArray(64 * 1024)
        while (SystemClock.elapsedRealtime() < end) {
            try {
                val c = URL("https://$HOST/__down?bytes=50000000").openConnection() as HttpURLConnection
                c.connectTimeout = 4000; c.readTimeout = 4000
                c.inputStream.use { ins ->
                    while (SystemClock.elapsedRealtime() < end) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        total.addAndGet(n.toLong())
                    }
                }
                c.disconnect()
            } catch (e: Exception) { return }
        }
    }

    private fun uploadLoop(total: AtomicLong, end: Long) {
        val chunk = ByteArray(64 * 1024) { 0x55 }
        val size = 5_000_000
        while (SystemClock.elapsedRealtime() < end) {
            try {
                val c = URL("https://$HOST/__up").openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.doOutput = true
                c.connectTimeout = 4000; c.readTimeout = 4000
                c.setFixedLengthStreamingMode(size)
                c.outputStream.use { out ->
                    var sent = 0
                    while (sent < size && SystemClock.elapsedRealtime() < end) {
                        val n = minOf(chunk.size, size - sent)
                        out.write(chunk, 0, n)
                        sent += n
                        total.addAndGet(n.toLong())
                    }
                }
                runCatching { c.responseCode }
                c.disconnect()
            } catch (e: Exception) { return }
        }
    }
}
