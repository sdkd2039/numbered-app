package com.sutstudio.numberedtv

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.sutstudio.numberedtv.channels.HomeChannels

/**
 * نقطة بدء قنوات الشاشة الرئيسية دون تعديل MainActivity:
 * عند أول إنشاء لـ MainActivity (وليس عند استعادتها بعد تدوير) نجهّز القناتين ونجدول التحديث.
 */
class NumberedTvApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is MainActivity && savedInstanceState == null) {
                    HomeChannels.onAppStarted(activity)
                }
            }

            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
