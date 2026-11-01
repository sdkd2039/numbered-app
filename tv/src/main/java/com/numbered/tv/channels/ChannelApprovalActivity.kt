package com.sutstudio.numberedtv.channels

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.tvprovider.media.tv.TvContractCompat

/**
 * شاشة شفافة بلا واجهة: تعرض نافذة النظام الرسمية "إضافة قناة إلى الشاشة الرئيسية"
 * لكل قناة لم يوافق المستخدم عليها بعد، واحدة تلو الأخرى، ثم تُغلق نفسها.
 */
class ChannelApprovalActivity : Activity() {

    private val queue = ArrayDeque<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            intent.getLongArrayExtra(EXTRA_CHANNEL_IDS)?.let { queue.addAll(it.toList()) }
            requestNext()
        } else {
            finish()
        }
    }

    private fun requestNext() {
        val channelId = queue.removeFirstOrNull()
        if (channelId == null) {
            finish()
            return
        }
        val request = Intent(TvContractCompat.ACTION_REQUEST_CHANNEL_BROWSABLE)
            .putExtra(TvContractCompat.EXTRA_CHANNEL_ID, channelId)
        try {
            startActivityForResult(request, REQUEST_BROWSABLE)
        } catch (e: Exception) {
            Log.w(TAG, "Channel approval screen is unavailable", e)
            requestNext()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_BROWSABLE) requestNext()
    }

    companion object {
        const val EXTRA_CHANNEL_IDS = "com.sutstudio.numberedtv.extra.CHANNEL_IDS"
        private const val REQUEST_BROWSABLE = 1001
        private const val TAG = "ChannelApproval"
    }
}
