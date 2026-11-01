package com.sutstudio.numberedtv.channels

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

/** يحدّث بطاقات القناتين (العد التنازلي، استبعاد المنتهي، تغيّر البيانات). */
class ChannelSyncWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        try {
            HomeChannels.syncAll(applicationContext)
        } catch (e: Exception) {
            // الدورة التالية ستعيد المحاولة؛ لا نريد تكرارًا سريعًا عند أجهزة لا تدعم القنوات.
            Log.w("ChannelSyncWorker", "Channel sync failed", e)
        }
        return Result.success()
    }
}
