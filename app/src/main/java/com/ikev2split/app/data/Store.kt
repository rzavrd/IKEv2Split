package com.ikev2split.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

data class Hist(
    val server: String, val cc: String, val start: Long, val end: Long,
    val rx: Long, val tx: Long, val status: String,
)

data class Server(
    val name: String,
    val address: String,
    val cc: String = "",
    val pingMs: Int? = null,
)

/** Replaces /etc/ikev2split.conf + /etc/ikev2-servers.list from the router. */
class Store(private val ctx: Context) {
    private val p = ctx.getSharedPreferences("ikev2split", Context.MODE_PRIVATE)

    private fun strPref(k: String, d: String = "") = object : ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = p.getString(k, d) ?: d
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            p.edit().putString(k, value).apply()
        }
    }

    private fun boolPref(k: String, d: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = p.getBoolean(k, d)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            p.edit().putBoolean(k, value).apply()
        }
    }

    private fun intPref(k: String, d: Int) = object : ReadWriteProperty<Any?, Int> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = p.getInt(k, d)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            p.edit().putInt(k, value).apply()
        }
    }

    var user by strPref("user")
    var pass by strPref("pass")
    var remoteId by strPref("rid", "pointtoserver.com")   // RID in the router config
    var current by strPref("cur")                          // CUR
    var failover by boolPref("failover", true)             // FAILOVER
    var autoSelect by boolPref("auto", false)              // "Fastest location"
    var mtu by intPref("mtu", 1400)                        // MTU
    var theme by strPref("theme", "System")
    var iconStyle by strPref("icon", "Classic")
    var showIp by boolPref("showip", true)
    var directPing by boolPref("directping", false)   // bypassable VPN: live ping while connected
    var verify by boolPref("verify", true)            // check that traffic really passes after connecting
    var updateUrl by strPref("upd", Defaults.UPDATE_URL)
    fun lastUpdateCheck(): Long = p.getLong("updchk", 0)
    fun setLastUpdateCheck(v: Long) { p.edit().putLong("updchk", v).apply() }

    /** Unique local IKE id, generated once (LID in the router script). */
    fun localId(): String {
        val v = p.getString("lid", null)
        if (!v.isNullOrEmpty()) return v
        val b = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val id = "r" + b.joinToString("") { "%02x".format(it) }
        p.edit().putString("lid", id).apply()
        return id
    }

    /** One-time seeding of the built-in account + server list (and cc for servers added by hand). */
    fun migrate() {
        val seed = p.getInt("seed", 0)
        if (seed >= 3) return
        if (seed < 2) {
            val defaults = Defaults.servers()
            val extra = servers().filter { c -> defaults.none { it.address == c.address } }
                .map { it.copy(cc = it.cc.ifEmpty { guessCc(it.address) }) }
            saveServers(defaults + extra)
            user = Defaults.USER; pass = Defaults.PASS; remoteId = Defaults.RID
            if (current.isEmpty() || (defaults + extra).none { it.address == current }) current = defaults.first().address
        }
        // v3: back to the settings that are known to work
        autoSelect = false
        if (mtu == 1420) mtu = 1400
        p.edit().putInt("seed", 3).apply()
    }

    // ---------- favorites ----------
    fun favorites(): Set<String> = p.getStringSet("favs", emptySet())?.toSet() ?: emptySet()
    fun toggleFavorite(addr: String): Set<String> {
        val n = favorites().toMutableSet()
        if (!n.add(addr)) n.remove(addr)
        p.edit().putStringSet("favs", n).apply()
        return n
    }

    // ---------- traffic usage + history ----------
    fun addBytes(rx: Long, tx: Long) {
        val d = LocalDate.now().toEpochDay()
        p.edit().putLong("rx_$d", p.getLong("rx_$d", 0) + rx).putLong("tx_$d", p.getLong("tx_$d", 0) + tx).apply()
    }

    /** (rx, tx) summed over the last [days] days including today. */
    fun usage(days: Int): Pair<Long, Long> {
        val today = LocalDate.now().toEpochDay()
        var rx = 0L; var tx = 0L
        for (i in 0 until days) { rx += p.getLong("rx_${today - i}", 0); tx += p.getLong("tx_${today - i}", 0) }
        return rx to tx
    }

    fun history(): List<Hist> {
        val a = JSONArray(p.getString("hist", "[]") ?: "[]")
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Hist(o.getString("server"), o.optString("cc"), o.getLong("start"), o.getLong("end"),
                o.getLong("rx"), o.getLong("tx"), o.optString("status"))
        }
    }

    fun addHistory(h: Hist) {
        val a = JSONArray()
        (listOf(h) + history()).take(60).forEach {
            a.put(JSONObject().put("server", it.server).put("cc", it.cc).put("start", it.start).put("end", it.end)
                .put("rx", it.rx).put("tx", it.tx).put("status", it.status))
        }
        p.edit().putString("hist", a.toString()).apply()
    }

    // ---------- servers ----------
    fun servers(): List<Server> {
        val raw = p.getString("servers", "[]") ?: "[]"
        val a = JSONArray(raw)
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Server(o.getString("name"), o.getString("address"), o.optString("cc"))
        }
    }

    fun saveServers(list: List<Server>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("name", it.name).put("address", it.address).put("cc", it.cc))
        }
        p.edit().putString("servers", a.toString()).apply()
    }

    // ---------- CA / server certificate ----------
    private val caFile get() = File(ctx.filesDir, "ca.cer")
    fun hasCa() = caFile.exists()
    private fun bundledCa(): ByteArray? =
        try { ctx.assets.open("purevpn_ca.crt").use { it.readBytes() } } catch (e: Exception) { null }
    fun hasBundledCa() = bundledCa() != null
    /** user-imported cert wins, then the one bundled in assets/purevpn_ca.crt, else null (system trust store) */
    fun caBytes(): ByteArray? = if (caFile.exists()) caFile.readBytes() else bundledCa()
    fun saveCa(b: ByteArray) = caFile.writeBytes(b)
    fun clearCa() { caFile.delete() }

    // ---------- usage (the "Time Protected" card) ----------
    fun addSeconds(n: Int) {
        val k = "u_" + LocalDate.now().toEpochDay()
        p.edit().putLong(k, p.getLong(k, 0) + n).apply()
    }

    /** Seconds protected for Mon..Sun of the current week. */
    fun week(): List<Long> {
        val mon = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (0..6).map { p.getLong("u_" + mon.plusDays(it.toLong()).toEpochDay(), 0) }
    }
}

/** Same line format as the router's ikev2-servers.list:  name|address|cc|proto|file */
object ListParser {
    fun parse(text: String): List<Server> = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val f = line.split("|").map { it.trim() }
            val proto = f.getOrNull(3).orEmpty().lowercase()
            if (proto.isNotEmpty() && proto != "ikev2") return@mapNotNull null  // OpenVPN/WG not supported here
            val addr = (if (f.size > 1) f[1] else f[0]).replace(" ", "")
            if (addr.isEmpty()) return@mapNotNull null
            Server(
                name = if (f.size > 1 && f[0].isNotEmpty()) f[0] else addr,
                address = addr,
                cc = f.getOrNull(2).orEmpty().uppercase(),
            )
        }.toList()
}

fun flagEmoji(cc: String): String =
    if (cc.length == 2 && cc.all { it in 'A'..'Z' })
        String(Character.toChars(127397 + cc[0].code)) + String(Character.toChars(127397 + cc[1].code))
    else "\uD83C\uDF10"
