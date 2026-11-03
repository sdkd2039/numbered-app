package com.sutstudio.numberedapp

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import com.onesignal.user.subscriptions.IPushSubscriptionObserver
import com.onesignal.user.subscriptions.PushSubscriptionChangedState

/**
 * Application entry point.
 * Initializes OneSignal once per process.
 */
class NumberedApplication : Application() {

    private val pushSubscriptionObserver = object : IPushSubscriptionObserver {
        override fun onPushSubscriptionChange(state: PushSubscriptionChangedState) {
            val current = state.current
            Log.i(TAG, "Push subscription changed: idPresent=${!current.id.isNullOrBlank()}, tokenPresent=${!current.token.isNullOrBlank()}, optedIn=${current.optedIn}")
        }
    }

    override fun onCreate() {
        super.onCreate()

        val isDebuggable =
            (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        if (isDebuggable) {
            OneSignal.Debug.logLevel = LogLevel.VERBOSE
        }

        OneSignal.initWithContext(this, ONESIGNAL_APP_ID)
        OneSignal.User.pushSubscription.addObserver(pushSubscriptionObserver)

        Log.i(TAG, "OneSignal initialized; push id present=${!OneSignal.User.pushSubscription.id.isNullOrBlank()}, token present=${!OneSignal.User.pushSubscription.token.isNullOrBlank()}, optedIn=${OneSignal.User.pushSubscription.optedIn}")
    }

    companion object {
        private const val TAG = "NumberedOneSignal"
        private const val ONESIGNAL_APP_ID =
            "1f922d01-9839-40aa-a468-4aff02cf5b87"
    }
}
