package com.numbered.app

import android.app.Application
import android.content.pm.ApplicationInfo
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel

/**
 * Application entry point.
 * Initializes OneSignal once per process.
 */
class NumberedApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val isDebuggable =
            (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        if (isDebuggable) {
            OneSignal.Debug.logLevel = LogLevel.VERBOSE
        }

        OneSignal.initWithContext(this, ONESIGNAL_APP_ID)
    }

    companion object {
        private const val ONESIGNAL_APP_ID =
            "1f922d01-9839-40aa-a468-4aff02cf5b87"
    }
}
