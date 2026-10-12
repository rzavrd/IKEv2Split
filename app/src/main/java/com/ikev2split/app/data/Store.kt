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

<<<<<<< HEAD
data class OvpnProfile(val name: String, val cc: String, val config: String, val local: Boolean = false)

/** A manually added VMess / VLESS link. [kind] is "vmess" or "vless". */
data class V2Profile(val name: String, val kind: String, val host: String, val port: Int, val link: String, val extra: String = "")

=======
>>>>>>> origin/main
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
<<<<<<< HEAD
    /** "IKEv2" (runs inside this app), "OpenVPN" or "V2Ray" (VMess/VLESS): both are opened in a companion app. */
    var protocol by strPref("proto", if (android.os.Build.VERSION.SDK_INT >= 30) "IKEv2" else "OpenVPN")
    var ovpnCurrent by strPref("ovpncur")
    var v2Current by strPref("v2cur")
    var authToken by strPref("tok")
    var authUser by strPref("tuser")
    fun authExpires(): Long = p.getLong("texp", 0)
    fun setAuthExpires(v: Long) { p.edit().putLong("texp", v).apply() }
    fun deviceId(): String {
        val v = p.getString("did", null)
        if (!v.isNullOrEmpty()) return v
        val id = java.util.UUID.randomUUID().toString()
        p.edit().putString("did", id).apply()
        return id
    }
    var directPing by boolPref("directping", false)   // bypassable VPN: live ping while connected
    var verify by boolPref("verify", true)            // check that traffic really passes after connecting
    var updateUrl by strPref("upd", Defaults.UPDATE_URL)
    /** Which connect variant (0..2) worked last time for a server: it is tried first next time. */
    fun variantFor(addr: String): Int = p.getInt("var_$addr", -1)
    fun setVariant(addr: String, v: Int) { p.edit().putInt("var_$addr", v).apply() }
    var configEtag by strPref("cfgetag")
=======
    var directPing by boolPref("directping", false)   // bypassable VPN: live ping while connected
    var verify by boolPref("verify", true)            // check that traffic really passes after connecting
    var updateUrl by strPref("upd", Defaults.UPDATE_URL)
>>>>>>> origin/main
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
<<<<<<< HEAD
            if (current.isEmpty() || (defaults + extra).none { it.address == current }) current = defaults.firstOrNull()?.address ?: ""
=======
            if (current.isEmpty() || (defaults + extra).none { it.address == current }) current = defaults.first().address
>>>>>>> origin/main
        }
        // v3: back to the settings that are known to work
        autoSelect = false
        if (mtu == 1420) mtu = 1400
        p.edit().putInt("seed", 3).apply()
    }

<<<<<<< HEAD
    // ---------- OpenVPN profiles (from the server, or imported by the user) ----------
    private val ovpnFile get() = File(ctx.filesDir, "ovpn.json")
    fun ovpn(): List<OvpnProfile> {
        if (!ovpnFile.exists()) return emptyList()
        return try {
            val a = JSONArray(ovpnFile.readText())
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                OvpnProfile(o.getString("name"), o.optString("cc"), o.getString("config"), o.optBoolean("local"))
            }
        } catch (e: Exception) { emptyList() }
    }
    fun saveOvpn(l: List<OvpnProfile>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("name", it.name).put("cc", it.cc).put("config", it.config).put("local", it.local)) }
        ovpnFile.writeText(a.toString())
    }

    // ---------- VMess / VLESS links added by hand ----------
    private val v2File get() = File(ctx.filesDir, "v2.json")
    fun v2(): List<V2Profile> {
        if (!v2File.exists()) return emptyList()
        return try {
            val a = JSONArray(v2File.readText())
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                V2Profile(o.getString("name"), o.getString("kind"), o.getString("host"), o.getInt("port"), o.getString("link"), o.optString("extra"))
            }
        } catch (e: Exception) { emptyList() }
    }
    fun saveV2(l: List<V2Profile>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("name", it.name).put("kind", it.kind).put("host", it.host).put("port", it.port).put("link", it.link).put("extra", it.extra)) }
        v2File.writeText(a.toString())
    }

=======
>>>>>>> origin/main
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
