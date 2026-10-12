package com.ikev2split.app.ui

fun fmtBits(bytesPerSec: Long): String {
    val bits = bytesPerSec * 8.0
    return when {
        bits >= 1e6 -> "%.1f Mbps".format(bits / 1e6)
        bits >= 1e3 -> "%.0f Kbps".format(bits / 1e3)
        else -> "%.0f bps".format(bits)
    }
}

fun fmtBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.2f GB".format(b / (1024.0 * 1024 * 1024))
    b >= 1L shl 20 -> "%.1f MB".format(b / (1024.0 * 1024))
    b >= 1L shl 10 -> "%.0f KB".format(b / 1024.0)
    else -> "$b B"
}

fun fmtDur(sec: Long): String = "%02d:%02d:%02d".format(sec / 3600, sec % 3600 / 60, sec % 60)

/** label + signal bars (1..3) from a ping in ms */
fun quality(ms: Int?): Pair<String, Int> = when {
    ms == null -> "" to 0
    ms < 100 -> "Great" to 3
    ms < 180 -> "Good" to 3
    ms < 300 -> "Fair" to 2
    ms < 500 -> "Bad" to 1
    else -> "Very bad" to 1
}
