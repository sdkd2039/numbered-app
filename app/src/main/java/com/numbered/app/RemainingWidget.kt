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
                        updateWidget(context, widgetId)
                    }

                scheduleNextUpdate(context)
            }
        }
    }

    companion object {

        private const val ACTION_REFRESH =
            "com.numbered.app.action.REMAINING_REFRESH"

        private const val NORMAL_UPDATE_INTERVAL =
            30L * 60L * 1000L

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
             * الوقت الحقيقي الحالي من الجهاز.
             */
            val now =
                System.currentTimeMillis()

            /*
             * البحث عن أقرب مناسبة قادمة.
             */
            val nextEvent =
                RemainingEventSchedule.nextUpcoming(now)

            /*
             * لا توجد مناسبة قادمة.
             */
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
             * الفرق الحقيقي بين الآن وموعد المناسبة.
             */
            val remainingMillis =
                (
                    nextEvent.gregorianMillis - now
                ).coerceAtLeast(0L)

            /*
             * أيام + ساعات + دقائق فقط.
             *
             * لا توجد ثواني.
             */
            val days =
                TimeUnit.MILLISECONDS
                    .toDays(remainingMillis)

            val hours =
                TimeUnit.MILLISECONDS
                    .toHours(remainingMillis) % 24L

            val minutes =
                TimeUnit.MILLISECONDS
                    .toMinutes(remainingMillis) % 60L

            /*
             * اسم المناسبة.
             */
            views.setTextViewText(
                R.id.remaining_event_name,
                nextEvent.name
            )

            /*
             * الأيام.
             */
            views.setTextViewText(
                R.id.remaining_days,
                days.toString()
            )

            /*
             * الساعات.
             */
            views.setTextViewText(
                R.id.remaining_hours,
                hours.toString()
            )

            /*
             * الدقائق.
             */
            views.setTextViewText(
                R.id.remaining_minutes,
                minutes.toString()
            )

            /*
             * التاريخ.
             */
            views.setTextViewText(
                R.id.remaining_hijri_date,
                "${nextEvent.hijriDate} | ${nextEvent.gregorianDate}"
            )

            /*
             * دائمًا نعرض:
             * الأيام + الساعات + الدقائق.
             */
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

            /*
             * حساب شريط التقدم.
             *
             * المناسبة السابقة ← الآن ← المناسبة الحالية
             */
            val previousEvent =
                RemainingEventSchedule.previousEvent(
                    nextEvent
                )

            val progress =
                calculateProgress(
                    previousEvent?.gregorianMillis,
                    nextEvent.gregorianMillis,
                    now
                )

            /*
             * تحديث شريط التقدم.
             */
            views.setProgressBar(
                R.id.remaining_progress,
                100,
                progress,
                false
            )

            /*
             * الضغط على الويدجت يفتح التطبيق.
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

        /*
         * تحديث الويدجت.
         */
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

        /*
         * حساب نسبة التقدم من المناسبة السابقة
         * حتى المناسبة الحالية.
         */
        private fun calculateProgress(
            previousMillis: Long?,
            nextMillis: Long,
            nowMillis: Long
        ): Int {

            /*
             * إذا لم توجد مناسبة سابقة،
             * نبدأ من 0%.
             */
            if (previousMillis == null) {
                return 0
            }

            /*
             * حماية من التواريخ غير الصحيحة.
             */
            if (nextMillis <= previousMillis) {
                return 0
            }

            val totalDuration =
                nextMillis - previousMillis

            val elapsedDuration =
                (
                    nowMillis - previousMillis
                ).coerceIn(
                    0L,
                    totalDuration
                )

            val progress =
                (
                    elapsedDuration.toDouble()
                        / totalDuration.toDouble()
                        * 100.0
                ).toInt()

            return progress.coerceIn(
                0,
                100
            )
        }

        /*
         * جدولة التحديث القادم.
         */
        private fun scheduleNextUpdate(
            context: Context
        ) {

            val now =
                System.currentTimeMillis()

            val nextEvent =
                RemainingEventSchedule.nextUpcoming(now)

            val remaining =
                nextEvent?.let {
                    (
                        it.gregorianMillis - now
                    ).coerceAtLeast(0L)
                }

            /*
             * إذا بقي أقل من 24 ساعة:
             * تحديث كل دقيقة.
             *
             * غير ذلك:
             * تحديث كل 30 دقيقة.
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
                System.currentTimeMillis() + interval

            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC,
                triggerAt,
                pendingIntent
            )
        }
    }
}


/*
 * بيانات المناسبات.
 */
data class RemainingEvent(
    val name: String,
    val hijriDate: String,
    val gregorianDate: String,
    val gregorianMillis: Long
)


object RemainingEventSchedule {

    /*
     * توقيت المملكة العربية السعودية.
     */
    private val timeZone =
        TimeZone.getTimeZone("Asia/Riyadh")

    private val locale =
        Locale("ar", "SA")

    /*
     * تحويل التاريخ إلى milliseconds.
     *
     * الساعة:
     * 00:00:00
     *
     * بتوقيت الرياض.
     */
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

    /*
     * المناسبات.
     *
     * نضيف عيد الأضحى 2026 كمناسبة سابقة
     * حتى يستطيع شريط رمضان 2027 حساب
     * نسبة التقدم من المناسبة السابقة.
     */
    private val events =
        listOf(

            /*
             * مناسبة سابقة فقط لحساب التقدم.
             */
            RemainingEvent(
                name = "عيد الأضحى المبارك",
                hijriDate = "10 ذو الحجة 1447هـ",
                gregorianDate = "27 مايو 2026م",
                gregorianMillis = dateMillis(
                    2026,
                    5,
                    27
                )
            ),

            /*
             * رمضان 2027
             */
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

            /*
             * عيد الفطر 2027
             */
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

            /*
             * عشر ذي الحجة 2027
             */
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

            /*
             * عيد الأضحى 2027
             */
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

    /*
     * الحصول على أقرب مناسبة قادمة.
     */
    fun nextUpcoming(
        nowMillis: Long
    ): RemainingEvent? {

        return events.firstOrNull {
            it.gregorianMillis > nowMillis
        }
    }

    /*
     * الحصول على المناسبة السابقة
     * للمناسبة القادمة.
     */
    fun previousEvent(
        nextEvent: RemainingEvent
    ): RemainingEvent? {

        val index =
            events.indexOf(nextEvent)

        if (index <= 0) {
            return null
        }

        return events[index - 1]
    }
}