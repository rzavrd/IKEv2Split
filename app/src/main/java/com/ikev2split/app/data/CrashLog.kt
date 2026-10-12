package com.ikev2split.app.data

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Saves the stack trace of a crash so the next launch can show it (Profile > Connection Log). */
object CrashLog {
    private fun file(ctx: Context) = File(ctx.filesDir, "last_crash.txt")

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val when_ = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                file(app).writeText(
                    "$when_  Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})  ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                        "thread: ${t.name}\n" + e.stackTraceToString().take(6000),
                )
            } catch (_: Throwable) { }
            old?.uncaughtException(t, e)
        }
    }

    fun last(ctx: Context): String? = try { file(ctx).takeIf { it.exists() }?.readText() } catch (_: Throwable) { null }
    fun clear(ctx: Context) { try { file(ctx).delete() } catch (_: Throwable) { } }
}
