package com.sutstudio.numberedapp

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

        // VERBOSE in debug builds; INFO in release so registration errors are still visible in logcat.
        OneSignal.Debug.logLevel = if (isDebuggable) LogLevel.VERBOSE else LogLevel.INFO

        PushDiagnostics.logEnvironment(this)

        try {
            OneSignal.initWithContext(this, ONESIGNAL_APP_ID)
            PushDiagnostics.registerObservers()
        } catch (e: Throwable) {
            // Push failing must not take the whole app down, but it must never be silent.
            PushDiagnostics.recordInitError(e)
        }
    }

    companion object {
        private const val ONESIGNAL_APP_ID =
            "1f922d01-9839-40aa-a468-4aff02cf5b87"
    }
}
