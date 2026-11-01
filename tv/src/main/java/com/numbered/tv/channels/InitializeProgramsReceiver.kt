package com.sutstudio.numberedtv.channels

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.tvprovider.media.tv.TvContractCompat

/** النظام يرسل هذا الحدث بعد تثبيت التطبيق على Android TV لتجهيز القنوات قبل أول فتح. */
class InitializeProgramsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TvContractCompat.ACTION_INITIALIZE_PROGRAMS &&
            HomeChannels.isSupported(context)
        ) {
            HomeChannels.scheduleSync(context)
        }
    }
}
