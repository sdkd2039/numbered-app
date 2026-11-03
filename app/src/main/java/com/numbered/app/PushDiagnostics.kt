package com.sutstudio.numberedapp

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.onesignal.OneSignal
import com.onesignal.notifications.IPermissionObserver
import com.onesignal.user.subscriptions.IPushSubscriptionObserver
import com.onesignal.user.subscriptions.PushSubscriptionChangedState

/**
 * Push registration diagnostics.
 *
 * Nothing here changes how push works; it only makes the registration state visible
 * in logcat (filter by tag "NumberedPush") so a failure can be classified as:
 *  - device side   (notifications blocked, no Google Play services)
 *  - app/Firebase  (no FCM token: wrong google-services.json, project or package)
 *  - OneSignal     (no subscription id: init failed, bad app id, no network)
 *
 * Secrets are never logged: the FCM token is masked and the Firebase API key is not read.
 */
object PushDiagnostics {

    const val TAG = "NumberedPush"

    /** Firebase project the app is expected to be built against. */
    private const val EXPECTED_FIREBASE_PROJECT_ID = "numbered-app-a5916"

    @Volatile
    private var initError: Throwable? = null

    private var observersRegistered = false

    private val subscriptionObserver = object : IPushSubscriptionObserver {
        override fun onPushSubscriptionChange(state: PushSubscriptionChangedState) {
            Log.i(
                TAG,
                "Push subscription changed: id=${state.current.id} " +
                    "token=${maskToken(state.current.token)} optedIn=${state.current.optedIn} " +
                    "(previous: id=${state.previous.id} token=${maskToken(state.previous.token)} " +
                    "optedIn=${state.previous.optedIn})"
            )
        }
    }

    private val permissionObserver = object : IPermissionObserver {
        override fun onNotificationPermissionChange(permission: Boolean) {
            Log.i(TAG, "Notification permission changed: granted=$permission")
        }
    }

    /** Called from Application.onCreate if OneSignal.initWithContext threw. */
    fun recordInitError(error: Throwable) {
        initError = error
        Log.e(TAG, "OneSignal.initWithContext FAILED: ${error.javaClass.name}: ${error.message}", error)
    }

    /** Logs device, app and Firebase configuration once at startup. */
    fun logEnvironment(context: Context) {
        val projectId = firebaseResource(context, "project_id")
        Log.i(
            TAG,
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        )
        Log.i(TAG, "App: package=${context.packageName}, Google Play services=${playServicesVersion(context)}")
        Log.i(
            TAG,
            "Firebase config bundled in this build: project_id=$projectId, " +
                "sender_id=${firebaseResource(context, "gcm_defaultSenderId")}, " +
                "app_id=${firebaseResource(context, "google_app_id")}"
        )
        if (projectId == null) {
            Log.e(TAG, "No Firebase config found in this build (google-services.json not applied). Push cannot register.")
        } else if (projectId != EXPECTED_FIREBASE_PROJECT_ID) {
            Log.e(
                TAG,
                "Firebase project mismatch: build uses '$projectId' but expected '$EXPECTED_FIREBASE_PROJECT_ID'. " +
                    "Replace app/google-services.json with the file downloaded from the new Firebase project."
            )
        }
    }

    /** Registers observers that log every permission and push-subscription change. Safe to call more than once. */
    @Synchronized
    fun registerObservers() {
        if (observersRegistered) return
        try {
            OneSignal.Notifications.addPermissionObserver(permissionObserver)
            OneSignal.User.pushSubscription.addObserver(subscriptionObserver)
            observersRegistered = true
            Log.i(TAG, "Push observers registered")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to register push observers: ${e.javaClass.name}: ${e.message}", e)
        }
    }

    /** Logs the current registration state and a one-line verdict. */
    fun logSnapshot(context: Context, reason: String) {
        val error = initError
        if (error != null) {
            Log.e(TAG, "[$reason] verdict=ONESIGNAL_INIT_FAILED (${error.javaClass.simpleName}: ${error.message})")
            return
        }
        try {
            val permission = OneSignal.Notifications.permission
            val systemEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            val subscription = OneSignal.User.pushSubscription
            val id = subscription.id
            val token = subscription.token
            val optedIn = subscription.optedIn
            val onesignalId = OneSignal.User.onesignalId

            Log.i(
                TAG,
                "[$reason] permission=$permission systemNotificationsEnabled=$systemEnabled " +
                    "onesignalId=$onesignalId subscriptionId=$id token=${maskToken(token)} optedIn=$optedIn"
            )
            Log.i(TAG, "[$reason] verdict=${verdict(permission && systemEnabled, id, token, optedIn)}")
        } catch (e: Throwable) {
            Log.e(TAG, "[$reason] could not read OneSignal state: ${e.javaClass.name}: ${e.message}", e)
        }
    }

    private fun verdict(notificationsAllowed: Boolean, id: String?, token: String?, optedIn: Boolean): String = when {
        !notificationsAllowed ->
            "DEVICE_NOTIFICATIONS_BLOCKED (permission denied or notifications disabled in system settings)"
        id.isNullOrEmpty() ->
            "NO_SUBSCRIPTION_ID (OneSignal has not created a push subscription: check init errors, app id, network)"
        token.isNullOrEmpty() ->
            "NO_PUSH_TOKEN (no FCM token: check google-services.json project/package, Google Play services, network)"
        !optedIn ->
            "NOT_OPTED_IN (subscription exists with a token but is opted out)"
        else ->
            "APP_SIDE_OK (id and token present, opted in; if no notification arrives, check OneSignal FCM credentials)"
    }

    private fun maskToken(token: String?): String =
        if (token.isNullOrEmpty()) "none" else "${token.take(6)}...(len=${token.length})"

    private fun firebaseResource(context: Context, name: String): String? {
        val resId = context.resources.getIdentifier(name, "string", context.packageName)
        return if (resId == 0) null else context.getString(resId)
    }

    private fun playServicesVersion(context: Context): String = try {
        context.packageManager.getPackageInfo("com.google.android.gms", 0).versionName ?: "unknown"
    } catch (e: Exception) {
        "NOT_FOUND (${e.javaClass.simpleName})"
    }
}
