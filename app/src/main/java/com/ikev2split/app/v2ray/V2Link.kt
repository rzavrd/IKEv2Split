package com.ikev2split.app.v2ray

import android.util.Base64
import com.ikev2split.app.data.V2Profile
import org.json.JSONObject
import java.net.URLDecoder

/**
 * Reads vmess:// and vless:// share links (and a pasted subscription body, which is those links in base64).
 * It only checks that a link is well formed; it never contacts the server.
 */
object V2Link {
    private val invisible = Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2060\\uFEFF]")
    private val linkRe = Regex("(?i)(vmess|vless)://[^\\s,;\"'<>]+")

    /** Every link found in [text] -> profiles, plus the links that could not be understood. */
    fun parseAll(text: String): Pair<List<V2Profile>, List<String>> {
        val ok = ArrayList<V2Profile>()
        val bad = ArrayList<String>()
        for (l in links(text)) {
            val p = parse(l)
            if (p != null) ok += p else bad += l.take(40)
        }
        return ok to bad
    }

    /** Plain links first; if there are none the whole text may be a base64 subscription body. */
    fun links(text: String): List<String> {
        val clean = invisible.replace(text, "").trim()
        val direct = linkRe.findAll(clean).map { it.value }.toList()
        if (direct.isNotEmpty()) return direct
        return try {
            linkRe.findAll(b64(clean.replace(Regex("\\s"), ""))).map { it.value }.toList()
        } catch (e: Exception) { emptyList() }
    }

    fun parse(raw: String): V2Profile? = try {
        val s = invisible.replace(raw, "").trim()
        when {
            s.startsWith("vmess://", true) -> vmess(s)
            s.startsWith("vless://", true) -> uriStyle(s, "vless")
            else -> null
        }
    } catch (e: Exception) { null }

    private fun b64(s: String): String {
        val clean = s.trim().replace('-', '+').replace('_', '/')
        val padded = clean + "=".repeat((4 - clean.length % 4) % 4)
        return String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8)
    }

    private fun describe(net: String, sec: String) = net.ifBlank { "tcp" } + (if (sec.isNotBlank() && sec != "none") "+$sec" else "")

    /** vmess://BASE64(JSON), or the newer vmess://uuid@host:port?... form. Any id text is accepted (it need not be a UUID). */
    private fun vmess(s: String): V2Profile? {
        val body = s.substring(8)
        val json = try { JSONObject(b64(body.substringBefore('#'))) } catch (e: Exception) { null }
        if (json == null) return uriStyle(s, "vmess")
        val host = json.optString("add").trim()
        val port = json.optString("port").trim().toIntOrNull()
        val id = json.optString("id").trim()
        if (host.isEmpty() || port == null || port !in 1..65535 || id.isEmpty()) return null
        val name = json.optString("ps").trim().ifBlank { host }.take(60)
        return V2Profile(name, "vmess", host, port, s, describe(json.optString("net"), json.optString("tls")))
    }

    private fun dec(s: String): String = try { URLDecoder.decode(s, "UTF-8") } catch (e: Exception) { s }

    /** scheme://id@host:port?key=value&...#name */
    private fun uriStyle(s: String, kind: String): V2Profile? {
        val rest = s.substring(kind.length + 3)
        val hash = rest.indexOf('#')
        val label = if (hash >= 0) dec(rest.substring(hash + 1)) else ""
        val noFrag = if (hash >= 0) rest.substring(0, hash) else rest
        val query = noFrag.substringAfter('?', "")
        val main = noFrag.substringBefore('?')
        val at = main.lastIndexOf('@')
        if (at <= 0) return null
        val id = dec(main.substring(0, at))
        val hostPort = main.substring(at + 1).trimEnd('/')
        val host: String
        val port: Int?
        if (hostPort.startsWith("[")) {            // [ipv6]:port
            host = hostPort.substringAfter('[').substringBefore(']')
            port = hostPort.substringAfter("]:", "").toIntOrNull()
        } else {
            host = hostPort.substringBeforeLast(':')
            port = hostPort.substringAfterLast(':', "").toIntOrNull()
        }
        if (id.isEmpty() || host.isEmpty() || port == null || port !in 1..65535) return null
        val params = query.split('&').filter { it.contains('=') }.associate { it.substringBefore('=') to dec(it.substringAfter('=')) }
        return V2Profile(label.trim().ifBlank { host }.take(60), kind, host, port, s, describe(params["type"].orEmpty(), params["security"].orEmpty()))
    }

    /** Country code from a flag emoji in the name (two regional-indicator symbols), or "" */
    fun ccOf(name: String): String {
        var i = 0
        while (i < name.length) {
            val cp = name.codePointAt(i)
            val n = Character.charCount(cp)
            if (cp in 0x1F1E6..0x1F1FF && i + n < name.length) {
                val cp2 = name.codePointAt(i + n)
                if (cp2 in 0x1F1E6..0x1F1FF) return "" + ('A' + (cp - 0x1F1E6)) + ('A' + (cp2 - 0x1F1E6))
            }
            i += n
        }
        return ""
    }
}
