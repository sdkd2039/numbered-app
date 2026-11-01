package com.sutstudio.numberedtv.channels

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.sutstudio.numberedtv.MainActivity
import com.sutstudio.numberedtv.R
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * قناتان في الشاشة الرئيسية لـ Android TV (Preview Channels):
 *  - الذكر: ثلاث بطاقات للفئات (الصباح والمساء والمنوعة).
 *  - المتبقي: بطاقة لكل مناسبة قادمة مع العد التنازلي.
 *
 * قنوات الشاشة الرئيسية تتطلب Android 8.0 (API 26) فأعلى على جهاز Android TV.
 * على أي جهاز آخر لا يفعل هذا الكائن شيئًا ولا يؤثر على التطبيق.
 */
object HomeChannels {

    const val EXTRA_CONTENT_TYPE = "com.sutstudio.numberedtv.extra.CONTENT_TYPE"
    const val EXTRA_CONTENT_ID = "com.sutstudio.numberedtv.extra.CONTENT_ID"
    const val CONTENT_TYPE_DHIKR = "dhikr"
    const val CONTENT_TYPE_REMAINING = "remaining"

    private const val TAG = "HomeChannels"
    private const val PREFS = "home_channels"
    private const val PERIODIC_WORK = "numbered_tv_channels_periodic"
    private const val ONE_TIME_WORK = "numbered_tv_channels_now"
    private const val LOGO_SAMPLE_SIZE = 8 // 2048px -> 256px

    private class ChannelSpec(val key: String, val name: String, val description: String)

    private val DHIKR_CHANNEL = ChannelSpec(
        key = "numbered_dhikr",
        name = "الذكر",
        description = "أذكار الصباح والمساء والمنوعة"
    )

    private val REMAINING_CHANNEL = ChannelSpec(
        key = "numbered_remaining",
        name = "المتبقي",
        description = "المناسبات القادمة والعد التنازلي"
    )

    private class Card(
        val internalId: String,
        val title: String,
        val description: String,
        val type: Int,
        val weight: Int,
        val intentUri: Uri,
        val posterResId: Int
    )

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun isSupported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    /** يُستدعى عند فتح التطبيق: ينشئ القناتين، يجدول التحديث، ويطلب موافقة المستخدم عند الحاجة. */
    fun onAppStarted(context: Context) {
        val app = context.applicationContext
        if (!isSupported(app)) return

        executor.execute {
            try {
                val channels = ensureChannels(app)
                scheduleSync(app)

                val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val pending = channels.values.filter {
                    !it.isBrowsable && !prefs.getBoolean(askedKey(it.internalProviderId), false)
                }
                if (pending.isNotEmpty()) {
                    val editor = prefs.edit()
                    pending.forEach { editor.putBoolean(askedKey(it.internalProviderId), true) }
                    editor.apply()

                    val ids = pending.map { it.id }.toLongArray()
                    mainHandler.post {
                        try {
                            app.startActivity(
                                Intent(app, ChannelApprovalActivity::class.java)
                                    .putExtra(ChannelApprovalActivity.EXTRA_CHANNEL_IDS, ids)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not open channel approval screen", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Home channels are unavailable on this device", e)
            }
        }
    }

    /** تحديث دوري كل 15 دقيقة (أقل فترة يسمح بها WorkManager) + تحديث فوري واحد. */
    fun scheduleSync(context: Context) {
        val app = context.applicationContext
        try {
            val workManager = WorkManager.getInstance(app)
            workManager.enqueueUniqueWork(
                ONE_TIME_WORK,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequest.Builder(ChannelSyncWorker::class.java).build()
            )
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequest.Builder(ChannelSyncWorker::class.java, 15, TimeUnit.MINUTES).build()
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not schedule channel sync", e)
        }
    }

    /** يطابق بطاقات القناتين مع البيانات الحالية: يضيف الجديد، يحدّث المتغيّر، ويحذف ما لم يعد موجودًا. */
    @Synchronized
    fun syncAll(context: Context) {
        if (!isSupported(context)) return
        val app = context.applicationContext
        val channels = ensureChannels(app)
        val now = System.currentTimeMillis()

        channels[DHIKR_CHANNEL.key]?.let { channel ->
            buildDhikrCards(app)?.let { syncPrograms(app, channel.id, it) }
        }
        channels[REMAINING_CHANNEL.key]?.let { channel ->
            buildRemainingCards(app, now)?.let { syncPrograms(app, channel.id, it) }
        }
    }

    // ------------------------------------------------------------ Channels

    @Synchronized
    private fun ensureChannels(context: Context): Map<String, Channel> {
        val helper = PreviewChannelHelper(context)
        val existing = queryOwnChannels(context).mapNotNull { it.internalProviderId }.toSet()

        for (spec in listOf(DHIKR_CHANNEL, REMAINING_CHANNEL)) {
            if (spec.key !in existing) {
                helper.publishChannel(buildChannel(context, spec))
            }
        }

        val result = LinkedHashMap<String, Channel>()
        for (channel in queryOwnChannels(context)) {
            val key = channel.internalProviderId ?: continue
            if (key == DHIKR_CHANNEL.key || key == REMAINING_CHANNEL.key) result[key] = channel
        }
        return result
    }

    private fun queryOwnChannels(context: Context): List<Channel> {
        val result = ArrayList<Channel>()
        context.contentResolver.query(
            TvContractCompat.Channels.CONTENT_URI,
            Channel.PROJECTION,
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                result.add(Channel.fromCursor(cursor))
            }
        }
        return result
    }

    private fun buildChannel(context: Context, spec: ChannelSpec): PreviewChannel {
        val builder = PreviewChannel.Builder()
            .setDisplayName(spec.name)
            .setDescription(spec.description)
            .setAppLinkIntent(Intent(context, MainActivity::class.java))
            .setInternalProviderId(spec.key)
        loadLogo(context)?.let { builder.setLogo(it) }
        return builder.build()
    }

    private fun loadLogo(context: Context): Bitmap? =
        try {
            val options = BitmapFactory.Options().apply { inSampleSize = LOGO_SAMPLE_SIZE }
            BitmapFactory.decodeResource(context.resources, R.drawable.tv_icon, options)
        } catch (e: Exception) {
            null
        }

    private fun askedKey(channelKey: String?) = "asked_$channelKey"

    // --------------------------------------------------------------- Cards

    private fun readAsset(context: Context, path: String): String? =
        try {
            context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read $path", e)
            null
        }

    /** null = تعذّرت قراءة الملف، فلا نمس البطاقات الموجودة. */
    private fun buildDhikrCards(context: Context): List<Card>? {
        val csv = readAsset(context, ChannelData.DHIKR_CSV_PATH) ?: return null
        val entries = ChannelData.parseDhikr(csv)
        val categories = listOf(
            "الصباح" to R.drawable.dhikr_morning,
            "المساء" to R.drawable.dhikr_evening,
            "منوعة" to R.drawable.dhikr_misc
        )
        return categories.mapIndexedNotNull { index, (category, posterResId) ->
            val categoryEntries = entries.filter { it.category.trim() == category }
            if (categoryEntries.isEmpty()) return@mapIndexedNotNull null
            val first = categoryEntries.first()
            Card(
                internalId = "dhikr-category:$category",
                title = category,
                description = "عدد الأذكار: ${categoryEntries.size} · اضغط لفتح الفئة",
                type = TvContractCompat.PreviewProgramColumns.TYPE_CLIP,
                weight = categories.size - index,
                intentUri = programIntentUri(context, CONTENT_TYPE_DHIKR, category),
                posterResId = posterResId
            )
        }
    }

    /** المناسبات المنتهية تُستبعد، والباقي من الأقرب إلى الأبعد. */
    private fun buildRemainingCards(context: Context, nowMillis: Long): List<Card>? {
        val csv = readAsset(context, ChannelData.REMAINING_CSV_PATH) ?: return null
        val upcoming = ChannelData.parseUpcoming(csv).mapNotNull { entry ->
            ChannelData.countdown(entry.startMillis, nowMillis)?.let { entry to it }
        }

        // اعرض أقرب سنة هجرية قادمة والسنة التي تليها فقط.
        // عند انتهاء مناسبات السنة الأولى ينتقل النطاق تلقائيًا إلى السنتين التاليتين.
        val firstHijriYear = upcoming
            .mapNotNull { (entry, _) -> ChannelData.hijriYear(entry.hijriDate) }
            .minOrNull() ?: return emptyList()
        val visible = upcoming.filter { (entry, _) ->
            val year = ChannelData.hijriYear(entry.hijriDate)
            year != null && year in firstHijriYear..(firstHijriYear + 1)
        }

        return visible.mapIndexed { index, (entry, countdown) ->
            val id = "${entry.startMillis}:${entry.name}"
            Card(
                internalId = "remaining:$id",
                title = ChannelData.upcomingTitle(entry),
                description = ChannelData.upcomingDescription(entry, countdown),
                type = TvContractCompat.PreviewProgramColumns.TYPE_EVENT,
                weight = visible.size - index,
                intentUri = programIntentUri(context, CONTENT_TYPE_REMAINING, id),
                posterResId = remainingPoster(entry)
            )
        }
    }

    /**
     * يختار الصورة من التاريخ الهجري نفسه، لا من اسم المناسبة.
     * هذا يمنع اختلاف كتابة «عيد الأضحى» من التأثير على الصورة.
     */
    private fun remainingPoster(entry: UpcomingEntry): Int {
        val date = ChannelData.normalizeDigits(entry.hijriDate)
            .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ى', 'ي')
            .replace('ة', 'ه')
            .replace(Regex("\\s+"), " ")
            .trim()

        return when {
            date.startsWith("10 ذو الحجه") || date.startsWith("١٠ ذو الحجه") ->
                R.drawable.remaining_eid_adha
            date.startsWith("1 شوال") ->
                R.drawable.remaining_eid_fitr
            date.startsWith("1 ذو الحجه") ->
                R.drawable.remaining_hajj
            date.startsWith("1 رمضان") ->
                R.drawable.remaining_ramadan
            else -> R.drawable.tv_banner
        }
    }

    /** الضغط على البطاقة يفتح MainActivity (الـ WebView كما هو) مع تمرير نوع المحتوى ومعرّفه في الـ Intent. */
    private fun programIntentUri(context: Context, type: String, id: String): Uri {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("numberedtv://$type/${Uri.encode(id)}"))
            .setClass(context, MainActivity::class.java)
            .putExtra(EXTRA_CONTENT_TYPE, type)
            .putExtra(EXTRA_CONTENT_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME))
    }

    private fun posterUri(context: Context, resourceId: Int): Uri =
        Uri.parse("android.resource://${context.packageName}/$resourceId")

    private fun buildProgram(context: Context, channelId: Long, card: Card): PreviewProgram =
        PreviewProgram.Builder()
            .setChannelId(channelId)
            .setType(card.type)
            .setTitle(card.title)
            .setDescription(card.description)
            .setPosterArtUri(posterUri(context, card.posterResId))
            .setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9)
            .setIntentUri(card.intentUri)
            .setInternalProviderId(card.internalId)
            .setWeight(card.weight)
            .build()

    private fun queryPrograms(context: Context, channelId: Long): List<PreviewProgram> {
        val result = ArrayList<PreviewProgram>()
        context.contentResolver.query(
            TvContractCompat.buildPreviewProgramsUriForChannel(channelId),
            PreviewProgram.PROJECTION,
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                result.add(PreviewProgram.fromCursor(cursor))
            }
        }
        return result
    }

    private fun syncPrograms(context: Context, channelId: Long, cards: List<Card>) {
        val helper = PreviewChannelHelper(context)
        val existing = HashMap<String, PreviewProgram>()
        val staleIds = ArrayList<Long>()

        for (program in queryPrograms(context, channelId)) {
            val key = program.internalProviderId
            if (key == null || existing.containsKey(key)) {
                staleIds.add(program.id)
            } else {
                existing[key] = program
            }
        }

        val wanted = cards.map { it.internalId }.toSet()
        for ((key, program) in existing) {
            if (key !in wanted) staleIds.add(program.id)
        }

        for (id in staleIds) {
            try {
                helper.deletePreviewProgram(id)
            } catch (e: Exception) {
                Log.w(TAG, "Could not delete program $id", e)
            }
        }

        for (card in cards) {
            try {
                val old = existing[card.internalId]
                if (old == null) {
                    helper.publishPreviewProgram(buildProgram(context, channelId, card))
                } else if (isChanged(context, old, card)) {
                    helper.updatePreviewProgram(old.id, buildProgram(context, channelId, card))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not publish card ${card.internalId}", e)
            }
        }
    }

    private fun isChanged(context: Context, old: PreviewProgram, card: Card): Boolean =
        old.title != card.title ||
            old.description != card.description ||
            old.intentUri != card.intentUri ||
            old.posterArtUri != posterUri(context, card.posterResId) ||
            old.weight != card.weight
}
