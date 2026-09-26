package com.numbered.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

class DhikrWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray
    ) {
        ids.forEach { widgetId ->
            render(
                context,
                widgetId,
                DhikrRepository.loadCached(context)
            )
        }

        DhikrRepository.refresh(context) { data ->

            val appWidgetManager =
                AppWidgetManager.getInstance(context)

            val component =
                ComponentName(
                    context,
                    DhikrWidget::class.java
                )

            appWidgetManager
                .getAppWidgetIds(component)
                .forEach { widgetId ->

                    render(
                        context,
                        widgetId,
                        data
                    )
                }
        }
    }

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        super.onReceive(context, intent)

        val widgetId =
            intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )

        if (
            widgetId ==
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) {
            return
        }

        when (intent.action) {

            ACTION_PREVIOUS,
            ACTION_NEXT,
            ACTION_GROUP_PREVIOUS,
            ACTION_GROUP_NEXT,
            ACTION_TAP,
            ACTION_RESET -> {

                val data =
                    DhikrRepository.loadCached(context)

                if (data.isNotEmpty()) {

                    when (intent.action) {

                        ACTION_TAP -> incrementCounter(
                            context,
                            widgetId,
                            data
                        )

                        ACTION_RESET -> resetCounter(
                            context,
                            widgetId
                        )

                        else -> move(
                            context,
                            widgetId,
                            data,
                            intent.action!!
                        )
                    }

                    render(
                        context,
                        widgetId,
                        data
                    )
                }
            }
        }
    }

    companion object {

        private const val PREFS =
            "dhikr_widget_state"

        private const val KEY_GROUP =
            "group_"

        private const val KEY_INDEX =
            "index_"

        private const val KEY_COUNT =
            "count_"

        const val ACTION_PREVIOUS =
            "com.numbered.app.action.DHIKR_PREVIOUS"

        const val ACTION_NEXT =
            "com.numbered.app.action.DHIKR_NEXT"

        const val ACTION_GROUP_PREVIOUS =
            "com.numbered.app.action.DHIKR_GROUP_PREVIOUS"

        const val ACTION_GROUP_NEXT =
            "com.numbered.app.action.DHIKR_GROUP_NEXT"

        const val ACTION_TAP =
            "com.numbered.app.action.DHIKR_TAP"

        const val ACTION_RESET =
            "com.numbered.app.action.DHIKR_RESET"


        private fun render(
            context: Context,
            widgetId: Int,
            data: List<DhikrEntry>
        ) {

            val views =
                RemoteViews(
                    context.packageName,
                    R.layout.widget_dhikr
                )

            val groups =
                data
                    .map { it.group }
                    .distinct()

            val prefs =
                context.getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )

            val groupIndex =
                if (groups.isEmpty()) {
                    0
                } else {
                    prefs
                        .getInt(
                            KEY_GROUP + widgetId,
                            0
                        )
                        .coerceIn(
                            0,
                            groups.lastIndex
                        )
                }

            val entries =
                if (groups.isEmpty()) {
                    emptyList()
                } else {
                    data.filter {
                        it.group ==
                            groups[groupIndex]
                    }
                }

            val entryIndex =
                if (entries.isEmpty()) {
                    0
                } else {
                    prefs
                        .getInt(
                            KEY_INDEX + widgetId,
                            0
                        )
                        .coerceIn(
                            0,
                            entries.lastIndex
                        )
                }

            val entry =
                entries.getOrNull(entryIndex)

            val selectedGroup =
                if (groups.isEmpty()) {
                    "الذكر"
                } else {
                    groups[groupIndex]
                }

            val targetCount =
                parseCount(
                    entry?.count
                )

            val currentCount =
                prefs
                    .getInt(
                        KEY_COUNT + widgetId,
                        0
                    )
                    .let { saved ->

                        if (targetCount > 0) {
                            saved.coerceIn(
                                0,
                                targetCount
                            )
                        } else {
                            saved.coerceAtLeast(0)
                        }
                    }

            val selectedProgress =
                if (entries.isEmpty()) {
                    ""
                } else {
                    "${entryIndex + 1} / ${entries.size}"
                }

            views.setTextViewText(
                R.id.dhikr_group,
                selectedGroup
            )

            views.setTextViewText(
                R.id.dhikr_text,
                entry?.text
                    ?: "لا توجد بيانات متاحة"
            )

            views.setTextViewText(
                R.id.dhikr_count_label,

                if (targetCount > 0) {
                    "عدد المرات: $targetCount"
                } else {
                    ""
                }
            )

            views.setTextViewText(
                R.id.dhikr_counter_card,

                if (targetCount > 0) {
                    "$currentCount / $targetCount"
                } else {
                    currentCount.toString()
                }
            )

            views.setTextViewText(
                R.id.dhikr_progress,
                selectedProgress
            )

            views.setViewVisibility(
                R.id.dhikr_group_previous,

                if (groups.size > 1)
                    View.VISIBLE
                else
                    View.GONE
            )

            views.setViewVisibility(
                R.id.dhikr_group_next,

                if (groups.size > 1)
                    View.VISIBLE
                else
                    View.GONE
            )

            views.setViewVisibility(
                R.id.dhikr_counter_card,

                if (entry != null)
                    View.VISIBLE
                else
                    View.GONE
            )

            views.setViewVisibility(
                R.id.dhikr_count_label,

                if (targetCount > 0)
                    View.VISIBLE
                else
                    View.GONE
            )

            views.setViewVisibility(
                R.id.dhikr_progress,

                if (selectedProgress.isNotBlank())
                    View.VISIBLE
                else
                    View.GONE
            )

            /*
             * أزرار التنقل بين الأذكار
             */

            bind(
                views,
                context,
                widgetId,
                R.id.dhikr_previous,
                ACTION_PREVIOUS
            )

            bind(
                views,
                context,
                widgetId,
                R.id.dhikr_next,
                ACTION_NEXT
            )

            /*
             * أزرار تغيير المجموعة
             */

            bind(
                views,
                context,
                widgetId,
                R.id.dhikr_group_previous,
                ACTION_GROUP_PREVIOUS
            )

            bind(
                views,
                context,
                widgetId,
                R.id.dhikr_group_next,
                ACTION_GROUP_NEXT
            )

            /*
             * الضغط على نص الذكر
             * يزيد العداد
             */

            bind(
                views,
                context,
                widgetId,
                R.id.dhikr_text,
                ACTION_TAP
            )

            /*
             * الضغط على عداد الذكر
             * يزيد العداد
             */

            bind(
                views,
                context,
                widgetId,
                R.id.dhikr_counter_card,
                ACTION_TAP
            )

            /*
             * الضغط على رقم ترتيب الذكر
             * لا يغير الذكر
             */

            bind(
                views,
                context,
                widgetId,
                R.id.dhikr_progress,
                ACTION_TAP
            )

            /*
             * تحديث الويدجت
             */

            AppWidgetManager
                .getInstance(context)
                .updateAppWidget(
                    widgetId,
                    views
                )
        }


        private fun incrementCounter(
            context: Context,
            widgetId: Int,
            data: List<DhikrEntry>
        ) {

            val prefs =
                context.getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )

            val groups =
                data
                    .map { it.group }
                    .distinct()

            if (groups.isEmpty()) {
                return
            }

            val groupIndex =
                prefs
                    .getInt(
                        KEY_GROUP + widgetId,
                        0
                    )
                    .coerceIn(
                        0,
                        groups.lastIndex
                    )

            val entries =
                data.filter {
                    it.group ==
                        groups[groupIndex]
                }

            if (entries.isEmpty()) {
                return
            }

            val entryIndex =
                prefs
                    .getInt(
                        KEY_INDEX + widgetId,
                        0
                    )
                    .coerceIn(
                        0,
                        entries.lastIndex
                    )

            val entry =
                entries[entryIndex]

            val targetCount =
                parseCount(entry.count)

            val currentCount =
                prefs.getInt(
                    KEY_COUNT + widgetId,
                    0
                )

            val newCount =

                if (targetCount > 0) {

                    (currentCount + 1)
                        .coerceAtMost(targetCount)

                } else {

                    currentCount + 1
                }

            prefs
                .edit()
                .putInt(
                    KEY_COUNT + widgetId,
                    newCount
                )
                .apply()
        }


        private fun resetCounter(
            context: Context,
            widgetId: Int
        ) {

            context
                .getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )
                .edit()
                .putInt(
                    KEY_COUNT + widgetId,
                    0
                )
                .apply()
        }


        private fun bind(
            views: RemoteViews,
            context: Context,
            widgetId: Int,
            viewId: Int,
            action: String
        ) {

            val intent =
                Intent(
                    context,
                    DhikrWidget::class.java
                ).apply {

                    this.action =
                        action

                    putExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                        widgetId
                    )
                }

            views.setOnClickPendingIntent(

                viewId,

                PendingIntent.getBroadcast(

                    context,

                    widgetId * 10 + viewId,

                    intent,

                    PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
                )
            )
        }


        private fun move(
            context: Context,
            widgetId: Int,
            data: List<DhikrEntry>,
            action: String
        ) {

            val groups =
                data
                    .map { it.group }
                    .distinct()

            if (groups.isEmpty()) {
                return
            }

            val prefs =
                context.getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )

            var groupIndex =
                prefs
                    .getInt(
                        KEY_GROUP + widgetId,
                        0
                    )
                    .coerceIn(
                        0,
                        groups.lastIndex
                    )

            var entryIndex =
                prefs.getInt(
                    KEY_INDEX + widgetId,
                    0
                )

            if (
                action ==
                    ACTION_GROUP_PREVIOUS ||
                action ==
                    ACTION_GROUP_NEXT
            ) {

                val delta =
                    if (
                        action ==
                            ACTION_GROUP_NEXT
                    ) {
                        1
                    } else {
                        -1
                    }

                groupIndex =
                    (
                        groupIndex +
                            delta +
                            groups.size
                    ) % groups.size

                entryIndex = 0

            } else {

                val entries =
                    data.filter {
                        it.group ==
                            groups[groupIndex]
                    }

                if (entries.isEmpty()) {
                    return
                }

                val delta =
                    if (
                        action ==
                            ACTION_NEXT
                    ) {
                        1
                    } else {
                        -1
                    }

                entryIndex =
                    (
                        entryIndex +
                            delta +
                            entries.size
                    ) % entries.size
            }

            prefs
                .edit()
                .putInt(
                    KEY_GROUP + widgetId,
                    groupIndex
                )
                .putInt(
                    KEY_INDEX + widgetId,
                    entryIndex
                )
                .putInt(
                    KEY_COUNT + widgetId,
                    0
                )
                .apply()
        }


        private fun parseCount(
            raw: String?
        ): Int {

            val normalized =
                raw
                    .orEmpty()
                    .trim()
                    .map { char ->

                        when (char) {

                            '٠' -> '0'
                            '١' -> '1'
                            '٢' -> '2'
                            '٣' -> '3'
                            '٤' -> '4'
                            '٥' -> '5'
                            '٦' -> '6'
                            '٧' -> '7'
                            '٨' -> '8'
                            '٩' -> '9'

                            else -> char
                        }
                    }
                    .joinToString("")

            return normalized
                .filter { it.isDigit() }
                .toIntOrNull()
                ?: 0
        }
    }
}