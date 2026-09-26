package com.numbered.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class RemainingWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray
    ) {
        ids.forEach { widgetId ->
            updateWidget(context, widgetId)
        }

        scheduleNextUpdate(context)
    }

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        super.onReceive(context, intent)

        when (intent.action) {

            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            ACTION_REFRESH -> {

                val manager =
                    AppWidgetManager.getInstance(context)

                val component =
                    ComponentName(
                        context,
                        RemainingWidget::class.java
                    )

                manager
                    .getAppWidgetIds(component)
                    .forEach { widgetId ->
                        updateWidget(
                            context,
                            widgetId
                        )
                    }

                scheduleNextUpdate(context)
            }
        }
    }

    companion object {

        private const val ACTION_REFRESH =
            "com.numbered.app.action.REMAINING_REFRESH"

        /*
         * تحديث عادي كل 30 دقيقة عندما تكون المناسبة بعيدة.
         */
        private const val NORMAL_UPDATE_INTERVAL =
            30L * 60L * 1000L

        /*
         * عندما تصبح المناسبة خلال 24 ساعة،
         * نطلب تحديثًا كل دقيقة.
         */
        private const val FAST_UPDATE_INTERVAL =
            60L * 1000L

        private const val ONE_DAY =
            24L * 60L * 60L * 1000L

        private fun updateWidget(
            context: Context,
            widgetId: Int
        ) {

            val views =
                RemoteViews(
                    context.packageName,
                    R.layout.widget_remaining
                )

            /*
             * مهم:
             *
             * نقرأ ساعة الجهاز في نفس لحظة الحساب.
             * لا نعتمد على عداد ينقص كل دقيقة.
             *
             * لذلك حتى لو تأخر Android في تشغيل التحديث،
             * عند التحديث التالي سيُعاد حساب الوقت الحقيقي.
             */
            val now =
                System.currentTimeMillis()

            val nextEvent =
                RemainingEventSchedule
                    .nextUpcoming(now)

            if (nextEvent == null) {

                views.setTextViewText(
                    R.id.remaining_event_name,
                    "لا توجد مناسبة قادمة"
                )

                views.setTextViewText(
                    R.id.remaining_days,
                    "0"
                )

                views.setTextViewText(
                    R.id.remaining_hours,
                    "0"
                )

                views.setTextViewText(
                    R.id.remaining_minutes,
                    "0"
                )

                views.setTextViewText(
                    R.id.remaining_hijri_date,
                    ""
                )

                /*
                 * عند عدم وجود مناسبة:
                 * نخفي الأيام والساعات ونترك الدقائق ظاهرة.
                 */
                views.setViewVisibility(
                    R.id.remaining_days_box,
                    View.GONE
                )

                views.setViewVisibility(
                    R.id.remaining_hours_box,
                    View.GONE
                )

                views.setViewVisibility(
                    R.id.remaining_minutes_box,
                    View.VISIBLE
                )

                views.setProgressBar(
                    R.id.remaining_progress,
                    100,
                    0,
                    false
                )

                updateAppWidget(
                    context,
                    widgetId,
                    views
                )

                return
            }

            /*
             * الوقت المتبقي الحقيقي بالملي ثانية.
             */
            val remainingMillis =
                (
                    nextEvent.gregorianMillis -
                        now
                    ).coerceAtLeast(0L)

            /*
             * نحسب كل وحدة من نفس القيمة.
             * لا يوجد عداد داخلي يمكن أن يتأخر عن ساعة الجوال.
             */
            val totalMinutes =
                TimeUnit.MILLISECONDS
                    .toMinutes(remainingMillis)

            val days =
                totalMinutes / 1440L

            val hours =
                (totalMinutes % 1440L) / 60L

            val minutes =
                totalMinutes % 60L

            views.setTextViewText(
                R.id.remaining_event_name,
                nextEvent.name
            )

            views.setTextViewText(
                R.id.remaining_days,
                days.toString()
            )

            views.setTextViewText(
                R.id.remaining_hours,
                hours.toString()
            )

            views.setTextViewText(
                R.id.remaining_minutes,
                minutes.toString()
            )

            views.setTextViewText(
                R.id.remaining_hijri_date,
                "${nextEvent.hijriDate} | ${nextEvent.gregorianDate}"
            )

            /*
             * التصميم الديناميكي:
             *
             * 3 وحدات:
             * أيام | ساعات | دقائق
             *
             * إذا الأيام = 0:
             * ساعات | دقائق
             *
             * إذا الأيام والساعات = 0:
             * دقائق فقط
             */
            when {

                days > 0L -> {

                    views.setViewVisibility(
                        R.id.remaining_days_box,
                        View.VISIBLE
                    )

                    views.setViewVisibility(
                        R.id.remaining_hours_box,
                        View.VISIBLE
                    )

                    views.setViewVisibility(
                        R.id.remaining_minutes_box,
                        View.VISIBLE
                    )
                }

                hours > 0L -> {

                    views.setViewVisibility(
                        R.id.remaining_days_box,
                        View.GONE
                    )

                    views.setViewVisibility(
                        R.id.remaining_hours_box,
                        View.VISIBLE
                    )

                    views.setViewVisibility(
                        R.id.remaining_minutes_box,
                        View.VISIBLE
                    )
                }

                else -> {

                    views.setViewVisibility(
                        R.id.remaining_days_box,
                        View.GONE
                    )

                    views.setViewVisibility(
                        R.id.remaining_hours_box,
                        View.GONE
                    )

                    views.setViewVisibility(
                        R.id.remaining_minutes_box,
                        View.VISIBLE
                    )
                }
            }

            /*
             * شريط التقدم.
             */
            val previousEvent =
                RemainingEventSchedule
                    .previousEvent(
                        nextEvent,
                        now
                    )

            val progress =
                calculateProgress(
                    previousEvent?.gregorianMillis,
                    nextEvent.gregorianMillis,
                    now
                )

            views.setProgressBar(
                R.id.remaining_progress,
                100,
                progress,
                false
            )

            /*
             * فتح التطبيق عند الضغط على الويدجت.
             */
            context.packageManager
                .getLaunchIntentForPackage(
                    context.packageName
                )
                ?.let { launchIntent ->

                    views.setOnClickPendingIntent(
                        R.id.remaining_root,
                        PendingIntent.getActivity(
                            context,
                            widgetId,
                            launchIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or
                                PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                }

            updateAppWidget(
                context,
                widgetId,
                views
            )
        }

        private fun updateAppWidget(
            context: Context,
            widgetId: Int,
            views: RemoteViews
        ) {
            AppWidgetManager
                .getInstance(context)
                .updateAppWidget(
                    widgetId,
                    views
                )
        }

        private fun calculateProgress(
            previousMillis: Long?,
            nextMillis: Long,
            nowMillis: Long
        ): Int {

            if (nextMillis <= nowMillis) {
                return 100
            }

            if (previousMillis == null) {
                return 0
            }

            val total =
                nextMillis - previousMillis

            if (total <= 0L) {
                return 0
            }

            val elapsed =
                (nowMillis - previousMillis)
                    .coerceIn(
                        0L,
                        total
                    )

            return (
                elapsed.toDouble() /
                    total.toDouble() *
                    100.0
                )
                .toInt()
                .coerceIn(
                    0,
                    100
                )
        }

        private fun scheduleNextUpdate(
            context: Context
        ) {

            /*
             * نحدد وقت التحديث بناءً على المناسبة القادمة.
             */
            val now =
                System.currentTimeMillis()

            val nextEvent =
                RemainingEventSchedule
                    .nextUpcoming(now)

            val remaining =
                nextEvent
                    ?.let {
                        (
                            it.gregorianMillis -
                                now
                        ).coerceAtLeast(0L)
                    }

            /*
             * إذا المناسبة خلال 24 ساعة:
             * تحديث كل دقيقة.
             *
             * غير ذلك:
             * كل 30 دقيقة لتقليل استهلاك البطارية.
             */
            val interval =
                if (
                    remaining != null &&
                    remaining <= ONE_DAY
                ) {
                    FAST_UPDATE_INTERVAL
                } else {
                    NORMAL_UPDATE_INTERVAL
                }

            val intent =
                Intent(
                    context,
                    RemainingWidget::class.java
                ).apply {
                    action = ACTION_REFRESH
                }

            val pendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    9001,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
                )

            val alarmManager =
                context.getSystemService(
                    Context.ALARM_SERVICE
                ) as AlarmManager

            val triggerAt =
                System.currentTimeMillis() +
                    interval

            /*
             * يسمح للنظام بإيقاظ التطبيق عند الحاجة،
             * لكن Android قد يؤخر التنفيذ قليلًا بسبب
             * توفير الطاقة.
             *
             * وهذا لا يؤثر على دقة الرقم لأن الحساب
             * نفسه يعتمد على System.currentTimeMillis().
             */
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC,
                triggerAt,
                pendingIntent
            )
        }
    }
}

data class RemainingEvent(
    val name: String,
    val hijriDate: String,
    val gregorianDate: String,
    val gregorianMillis: Long
)

object RemainingEventSchedule {

    private val timeZone =
        TimeZone.getTimeZone("Asia/Riyadh")

    private val locale =
        Locale("ar", "SA")

    private fun dateMillis(
        year: Int,
        month: Int,
        day: Int
    ): Long {

        val calendar =
            Calendar.getInstance(
                timeZone,
                locale
            )

        calendar.clear()

        calendar.set(
            year,
            month - 1,
            day,
            0,
            0,
            0
        )

        return calendar.timeInMillis
    }

    private fun formatGregorianDate(
        year: Int,
        month: Int,
        day: Int
    ): String {

        val calendar =
            Calendar.getInstance(
                timeZone,
                locale
            )

        calendar.clear()

        calendar.set(
            year,
            month - 1,
            day,
            0,
            0,
            0
        )

        val formatter =
            SimpleDateFormat(
                "d MMMM yyyy",
                locale
            )

        formatter.timeZone =
            timeZone

        return formatter.format(
            calendar.time
        )
    }

    private val events:
        List<RemainingEvent> =
        listOf(

            RemainingEvent(
                name = "شهر رمضان المبارك",
                hijriDate = "1 رمضان 1448هـ",
                gregorianDate = "8 فبراير 2027م",
                gregorianMillis = dateMillis(
                    2027,
                    2,
                    8
                )
            ),

            RemainingEvent(
                name = "عيد الفطر المبارك",
                hijriDate = "1 شوال 1448هـ",
                gregorianDate = "9 مارس 2027م",
                gregorianMillis = dateMillis(
                    2027,
                    3,
                    9
                )
            ),

            RemainingEvent(
                name = "عشر ذي الحجة",
                hijriDate = "1 ذو الحجة 1448هـ",
                gregorianDate = "7 مايو 2027م",
                gregorianMillis = dateMillis(
                    2027,
                    5,
                    7
                )
            ),

            RemainingEvent(
                name = "عيد الأضحى المبارك",
                hijriDate = "10 ذو الحجة 1448هـ",
                gregorianDate = "16 مايو 2027م",
                gregorianMillis = dateMillis(
                    2027,
                    5,
                    16
                )
            )
        )

    fun nextUpcoming(
        nowMillis: Long
    ): RemainingEvent? {

        return events.firstOrNull {
            it.gregorianMillis > nowMillis
        }
    }

    fun previousEvent(
        nextEvent: RemainingEvent,
        nowMillis: Long
    ): RemainingEvent? {

        val index =
            events.indexOf(nextEvent)

        if (index <= 0) {
            return null
        }

        return events[index - 1]
    }

    fun localMidnightMillis(
        nowMillis: Long
    ): Long {

        val calendar =
            Calendar.getInstance(
                timeZone,
                locale
            )

        calendar.timeInMillis =
            nowMillis

        calendar.set(
            Calendar.HOUR_OF_DAY,
            0
        )

        calendar.set(
            Calendar.MINUTE,
            0
        )

        calendar.set(
            Calendar.SECOND,
            0
        )

        calendar.set(
            Calendar.MILLISECOND,
            0
        )

        return calendar.timeInMillis
    }
}