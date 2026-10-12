package com.ikev2split.app

import android.app.Application
import com.ikev2split.app.data.CrashLog

class MyloApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
