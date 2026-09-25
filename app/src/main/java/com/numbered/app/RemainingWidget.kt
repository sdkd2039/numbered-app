package com.numbered.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import java.util.Calendar
import java.util.TimeZone

class RemainingWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateAll(context)
        scheduleRefresh(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        updateAll(context)
        scheduleRefresh(context)
    }

    override fun onDisabled(context: Context) {
        cancelRefresh(context)
        super.onDisabled(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_REFRESH,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                updateAll(context)
                scheduleRefresh(context)
            }
        }
    }

    companion object {
        private const val ACTION_REFRESH =
            "com.numbered.app.action.REMAINING_WIDGET_REFRESH"

        private const val REQUEST_CODE = 1448

        // تحديث العداد كل دقيقة حتى تتغير الدقائق والثواني باستمرار.
        private const val REFRESH_INTERVAL_MILLIS = 60_000L

        private const val MILLIS_PER_SECOND = 1_000L
        private const val MILLIS_PER_MINUTE = 60L * MILLIS_PER_SECOND
        private const val MILLIS_PER_HOUR = 60L * MILLIS_PER_MINUTE
        private const val MILLIS_PER_DAY = 24L * MILLIS_PER_HOUR

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, RemainingWidget::class.java)

            manager.getAppWidgetIds(component).forEach { widgetId ->
                updateOne(context, manager, widgetId)
            }
        }

        private fun updateOne(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int
        ) {
            val views = RemoteViews(
                context.packageName,
                R.layout.widget_remaining
            )

            val nowMillis = System.currentTimeMillis()
            val event = RemainingEventSchedule.nextUpcoming(nowMillis)

            if (event == null) {
                views.setTextViewText(
                    R.id.remaining_event_name,
                    "لا يوجد حدث قادم"
                )

                views.setTextViewText(
                    R.id.remaining_hijri_date,
                    "سيتم تحديث الموعد القادم عند إضافته"
                )

                setTimerValues(
                    views,
                    "00",
                    "00",
                    "00",
                    "00"
                )
            } else {
                val remainingMillis =
                    (event.gregorianMillis - nowMillis).coerceAtLeast(0L)

                val days = remainingMillis / MILLIS_PER_DAY

                val hours =
                    (remainingMillis % MILLIS_PER_DAY) / MILLIS_PER_HOUR

                val minutes =
                    (remainingMillis % MILLIS_PER_HOUR) / MILLIS_PER_MINUTE

                val seconds =
                    (remainingMillis % MILLIS_PER_MINUTE) / MILLIS_PER_SECOND

                views.setTextViewText(
                    R.id.remaining_event_name,
                    event.name
                )

                views.setTextViewText(
                    R.id.remaining_hijri_date,
                    event.hijriDate
                )

                setTimerValues(
                    views,
                    days.toString(),
                    hours.toString().padStart(2, '0'),
                    minutes.toString().padStart(2, '0'),
                    seconds.toString().padStart(2, '0')
                )
            }

            manager.updateAppWidget(widgetId, views)
        }

        private fun setTimerValues(
            views: RemoteViews,
            days: String,
            hours: String,
            minutes: String,
            seconds: String
        ) {
            views.setTextViewText(
                R.id.remaining_days,
                days
            )

            views.setTextViewText(
                R.id.remaining_hours,
                hours
            )

            views.setTextViewText(
                R.id.remaining_minutes,
                minutes
            )

            views.setTextViewText(
                R.id.remaining_seconds,
                seconds
            )
        }

        private fun scheduleRefresh(context: Context) {
            val alarmManager =
                context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(
                context,
                RemainingWidget::class.java
            ).apply {
                action = ACTION_REFRESH
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1_000L,
                REFRESH_INTERVAL_MILLIS,
                pendingIntent
            )
        }

        private fun cancelRefresh(context: Context) {
            val alarmManager =
                context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(
                context,
                RemainingWidget::class.java
            ).apply {
                action = ACTION_REFRESH
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.cancel(pendingIntent)
        }
    }
}

private data class RemainingEvent(
    val name: String,
    val hijriDate: String,
    val gregorianDate: String,
    val gregorianMillis: Long
)

/*
 * SINGLE EVENT-DATA SECTION.
 * These are the confirmed dates currently used by the app.
 */
private object RemainingEventSchedule {

    private val confirmedEvents = listOf(
        RemainingEvent(
            name = "رمضان المبارك",
            hijriDate = "1 رمضان 1448هـ",
            gregorianDate = "8 فبراير 2027م",
            gregorianMillis =
                localMidnightMillis(2027, Calendar.FEBRUARY, 8)
        ),

        RemainingEvent(
            name = "عيد الفطر المبارك",
            hijriDate = "1 شوال 1448هـ",
            gregorianDate = "9 مارس 2027م",
            gregorianMillis =
                localMidnightMillis(2027, Calendar.MARCH, 9)
        ),

        RemainingEvent(
            name = "عشر ذي الحجة",
            hijriDate = "1 ذو الحجة 1448هـ",
            gregorianDate = "7 مايو 2027م",
            gregorianMillis =
                localMidnightMillis(2027, Calendar.MAY, 7)
        ),

        RemainingEvent(
            name = "عيد الأضحى المبارك",
            hijriDate = "10 ذو الحجة 1448هـ",
            gregorianDate = "16 مايو 2027م",
            gregorianMillis =
                localMidnightMillis(2027, Calendar.MAY, 16)
        )
    )

    fun nextUpcoming(nowMillis: Long): RemainingEvent? =
        confirmedEvents.firstOrNull {
            it.gregorianMillis > nowMillis
        }

    private fun localMidnightMillis(
        year: Int,
        month: Int,
        day: Int
    ): Long =
        Calendar.getInstance(TimeZone.getDefault()).apply {
            clear()
            set(year, month, day, 0, 0, 0)
        }.timeInMillis
}