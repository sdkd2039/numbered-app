package com.numbered.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews

class DhikrWidget : AppWidgetProvider() {

    companion object {
        private const val ACTION_NEXT =
            "com.numbered.app.DHIKR_NEXT"

        private const val ACTION_PREVIOUS =
            "com.numbered.app.DHIKR_PREVIOUS"

        private const val ACTION_RESET =
            "com.numbered.app.DHIKR_RESET"

        private const val PREFS =
            "dhikr_widget_state"

        private const val KEY_INDEX_PREFIX =
            "index_"

        private const val KEY_COUNT_PREFIX =
            "count_"

        private const val KEY_CATEGORY_PREFIX =
            "category_"

        private const val DEFAULT_CATEGORY =
            "الصباح"

        private const val ZAIN_APPEARANCE =
            R.style.WidgetZainTextAppearance
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        super.onReceive(context, intent)

        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        when (intent.action) {
            ACTION_NEXT -> move(context, widgetId, 1)
            ACTION_PREVIOUS -> move(context, widgetId, -1)
            ACTION_RESET -> reset(context, widgetId)
        }
    }

    private fun move(
        context: Context,
        widgetId: Int,
        direction: Int
    ) {
        val data = DhikrRepository.load(context)
        if (data.isEmpty()) {
            updateWidget(context, AppWidgetManager.getInstance(context), widgetId)
            return
        }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val category = prefs.getString(
            KEY_CATEGORY_PREFIX + widgetId,
            DEFAULT_CATEGORY
        ) ?: DEFAULT_CATEGORY

        val list = data.filter {
            normalizeCategory(it.category) == normalizeCategory(category)
        }

        if (list.isEmpty()) {
            updateWidget(context, AppWidgetManager.getInstance(context), widgetId)
            return
        }

        var index = prefs.getInt(
            KEY_INDEX_PREFIX + widgetId,
            0
        )

        index += direction

        if (index < 0) {
            index = list.lastIndex
        }

        if (index > list.lastIndex) {
            index = 0
        }

        prefs.edit()
            .putInt(KEY_INDEX_PREFIX + widgetId, index)
            .putInt(
                KEY_COUNT_PREFIX + widgetId,
                0
            )
            .apply()

        updateWidget(
            context,
            AppWidgetManager.getInstance(context),
            widgetId
        )
    }

    private fun reset(
        context: Context,
        widgetId: Int
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_COUNT_PREFIX + widgetId, 0)
            .apply()

        updateWidget(
            context,
            AppWidgetManager.getInstance(context),
            widgetId
        )
    }

    private fun updateWidget(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int
    ) {
        val views = RemoteViews(
            context.packageName,
            R.layout.widget_dhikr
        )

        val data = DhikrRepository.load(context)

        if (data.isEmpty()) {
            views.setTextViewText(
                R.id.dhikr_text,
                "تعذر قراءة بيانات الأذكار"
            )

            views.setTextViewText(
                R.id.dhikr_count,
                "0"
            )

            applyFont(views)

            manager.updateAppWidget(widgetId, views)
            return
        }

        val prefs = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

        val category = prefs.getString(
            KEY_CATEGORY_PREFIX + widgetId,
            DEFAULT_CATEGORY
        ) ?: DEFAULT_CATEGORY

        val list = data.filter {
            normalizeCategory(it.category) == normalizeCategory(category)
        }.ifEmpty {
            data
        }

        var index = prefs.getInt(
            KEY_INDEX_PREFIX + widgetId,
            0
        )

        index = index.coerceIn(0, list.lastIndex)

        val item = list[index]

        var current = prefs.getInt(
            KEY_COUNT_PREFIX + widgetId,
            0
        )

        current = current.coerceIn(0, item.count)

        views.setTextViewText(
            R.id.dhikr_category,
            item.category
        )

        views.setTextViewText(
            R.id.dhikr_text,
            item.text
        )

        views.setTextViewText(
            R.id.dhikr_count,
            current.toString()
        )

        views.setTextViewText(
            R.id.dhikr_target,
            "من ${item.count}"
        )

        views.setProgressBar(
            R.id.dhikr_progress,
            item.count,
            current,
            false
        )

        applyFont(views)

        views.setOnClickPendingIntent(
            R.id.dhikr_next,
            actionIntent(
                context,
                ACTION_NEXT,
                widgetId
            )
        )

        views.setOnClickPendingIntent(
            R.id.dhikr_previous,
            actionIntent(
                context,
                ACTION_PREVIOUS,
                widgetId
            )
        )

        views.setOnClickPendingIntent(
            R.id.dhikr_reset,
            actionIntent(
                context,
                ACTION_RESET,
                widgetId
            )
        )

        /*
         * الضغط على العداد نفسه:
         * 0 → 1 → 2 → 3 ...
         */
        views.setOnClickPendingIntent(
            R.id.dhikr_counter_area,
            createIncrementIntent(
                context,
                widgetId
            )
        )

        manager.updateAppWidget(widgetId, views)
    }

    private fun createIncrementIntent(
        context: Context,
        widgetId: Int
    ): PendingIntent {
        val intent = Intent(
            context,
            DhikrWidget::class.java
        ).apply {
            action = "com.numbered.app.DHIKR_INCREMENT"
            putExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                widgetId
            )
        }

        return PendingIntent.getBroadcast(
            context,
            widgetId * 10 + 4,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onReceiveCompat(
        context: Context,
        intent: Intent
    ) {
        // غير مستخدمة.
    }

    private fun actionIntent(
        context: Context,
        action: String,
        widgetId: Int
    ): PendingIntent {
        val intent = Intent(
            context,
            DhikrWidget::class.java
        ).apply {
            this.action = action
            putExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                widgetId
            )
        }

        val requestCode = when (action) {
            ACTION_NEXT -> widgetId * 10 + 1
            ACTION_PREVIOUS -> widgetId * 10 + 2
            ACTION_RESET -> widgetId * 10 + 3
            else -> widgetId * 10 + 9
        }

        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun applyFont(
        views: RemoteViews
    ) {
        views.setTextViewTextAppearance(
            R.id.dhikr_category,
            ZAIN_APPEARANCE
        )

        views.setTextViewTextAppearance(
            R.id.dhikr_text,
            ZAIN_APPEARANCE
        )

        views.setTextViewTextAppearance(
            R.id.dhikr_count,
            ZAIN_APPEARANCE
        )

        views.setTextViewTextAppearance(
            R.id.dhikr_target,
            ZAIN_APPEARANCE
        )

        views.setTextViewTextAppearance(
            R.id.dhikr_previous,
            ZAIN_APPEARANCE
        )

        views.setTextViewTextAppearance(
            R.id.dhikr_reset,
            ZAIN_APPEARANCE
        )

        views.setTextViewTextAppearance(
            R.id.dhikr_next,
            ZAIN_APPEARANCE
        )
    }

    private fun normalizeCategory(value: String): String {
        return value
            .trim()
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ة", "ه")
    }
}