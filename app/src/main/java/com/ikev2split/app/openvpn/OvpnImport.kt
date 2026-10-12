package com.ikev2split.app.openvpn

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.ikev2split.app.data.OvpnProfile
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Imports many .ovpn files at once: any number of files, or .zip archives that contain them.
 * A file is accepted only when it is complete (every certificate / key is inside the file itself).
 */
object OvpnImport {
    class Result(val list: List<OvpnProfile>, val added: Int, val skipped: List<String>) {
        fun summary(): String = when {
            added == 0 && skipped.isEmpty() -> "No .ovpn files found"
            skipped.isEmpty() -> "$added profile(s) added"
            else -> "$added added, ${skipped.size} skipped (tap for details)"
        }
    }

    private const val MAX_FILE = 512 * 1024
    private const val MAX_ENTRIES = 2000
    private val external = setOf("ca", "cert", "key", "tls-auth", "tls-crypt", "tls-crypt-v2", "pkcs12", "secret", "dh", "extra-certs")
    private val remoteLine = Regex("^remote\\s+\\S+")
    private val spaces = Regex("\\s+")

    /** null = the profile is fine, otherwise the reason it cannot be used. */
    fun problem(text: String): String? {
        val lines = text.lines().map { it.trim() }
        if (lines.none { remoteLine.containsMatchIn(it) }) return "no 'remote' line (not an OpenVPN profile)"
        val inline = lines.filter { it.startsWith("<") && it.endsWith(">") && !it.startsWith("</") }
            .map { it.substring(1, it.length - 1) }.toSet()
        for (l in lines) {
            if (l.startsWith("#") || l.startsWith(";")) continue
            val w = l.split(spaces)
            if (w.size >= 2 && w[0] in external && w[0] !in inline) {
                return "'${w[0]} ${w[1]}' is a separate file: put its content inside <${w[0]}>...</${w[0]}>"
            }
        }
        return null
    }

    private fun displayName(ctx: Context, uri: Uri): String {
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getString(0)
                    if (!n.isNullOrBlank()) return n
                }
            }
        } catch (e: Exception) { }
        return (uri.lastPathSegment ?: "profile").substringAfterLast('/').substringAfterLast(':')
    }

    private fun cleanName(raw: String): String {
        var n = raw.substringAfterLast('/').substringAfterLast('\\')
        for (ext in listOf(".ovpn", ".conf", ".OVPN", ".CONF")) n = n.removeSuffix(ext)
        n = n.map { if (it.isLetterOrDigit() || it in "-_ .()") it else '-' }.joinToString("").trim().take(60)
        return n.ifBlank { "profile" }
    }

    private fun readLimited(ins: InputStream, limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = ins.read(buf)
            if (n < 0) break
            total += n
            if (total > limit) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** [current] is the list that exists now; the returned list contains it plus (or replacing) the imported ones. */
    fun process(ctx: Context, uris: List<Uri>, current: List<OvpnProfile>): Result {
        val found = LinkedHashMap<String, String>()   // name -> text
        val skipped = ArrayList<String>()
        var entries = 0

        fun accept(name: String, bytes: ByteArray?) {
            if (bytes == null) { skipped += "$name: file is too big"; return }
            val text = bytes.toString(Charsets.UTF_8).removePrefix("﻿")
            val why = problem(text)
            if (why != null) skipped += "$name: $why" else found[cleanName(name)] = text
        }

        for (u in uris) {
            val name = displayName(ctx, u)
            try {
                val stream = ctx.contentResolver.openInputStream(u)
                if (stream == null) { skipped += "$name: cannot open"; continue }
                stream.use { raw ->
                    if (name.endsWith(".zip", true)) {
                        ZipInputStream(raw).use { z ->
                            while (true) {
                                val e = z.nextEntry ?: break
                                if (e.isDirectory) continue
                                val en = e.name
                                if (!(en.endsWith(".ovpn", true) || en.endsWith(".conf", true))) continue
                                if (++entries > MAX_ENTRIES) { skipped += "zip: more than $MAX_ENTRIES files, the rest was ignored"; break }
                                accept(en, readLimited(z, MAX_FILE))
                            }
                        }
                    } else {
                        accept(name, readLimited(raw, MAX_FILE))
                    }
                }
            } catch (e: Exception) {
                skipped += "$name: ${e.message ?: "cannot read"}"
            }
        }

        // imported profiles replace local ones with the same name; server profiles keep their name
        val serverNames = current.filter { !it.local }.map { it.name }.toSet()
        val keep = current.filter { c -> !c.local || c.name !in found.keys }
        val add = found.map { (n, t) ->
            val finalName = if (n in serverNames) "$n (mine)" else n
            val cc = if (finalName.length > 2 && finalName[2] in "-_ ") finalName.take(2).uppercase() else ""
            OvpnProfile(finalName, cc, t, true)
        }
        return Result(keep.filter { k -> add.none { it.name == k.name } } + add, add.size, skipped)
    }
}
