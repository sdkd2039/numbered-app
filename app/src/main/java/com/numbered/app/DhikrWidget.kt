package com.numbered.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class DhikrWidget : AppWidgetProvider() {

    companion object {

        private const val ACTION_NEXT =
            "com.numbered.app.DHIKR_NEXT"

        private const val ACTION_PREVIOUS =
            "com.numbered.app.DHIKR_PREVIOUS"

        private const val ACTION_RESET =
            "com.numbered.app.DHIKR_RESET"

        private const val ACTION_INCREMENT =
            "com.numbered.app.DHIKR_INCREMENT"

        private const val ACTION_CATEGORY =
            "com.numbered.app.DHIKR_CATEGORY"

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

        private val CATEGORY_ORDER =
            listOf(
                "الصباح",
                "المساء",
                "منوعة"
            )
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            updateWidget(
                context,
                appWidgetManager,
                widgetId
            )
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

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return
        }

        when (intent.action) {

            ACTION_INCREMENT -> {
                increment(
                    context,
                    widgetId
                )
            }

            ACTION_NEXT -> {
                move(
                    context,
                    widgetId,
                    1
                )
            }

            ACTION_PREVIOUS -> {
                move(
                    context,
                    widgetId,
                    -1
                )
            }

            ACTION_RESET -> {
                reset(
                    context,
                    widgetId
                )
            }

            ACTION_CATEGORY -> {
                cycleCategory(
                    context,
                    widgetId
                )
            }
        }
    }

    private fun increment(
        context: Context,
        widgetId: Int
    ) {

        val data =
            DhikrRepository.load(context)

        if (data.isEmpty()) {
            updateWidget(
                context,
                AppWidgetManager.getInstance(context),
                widgetId
            )
            return
        }

        val prefs =
            context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )

        val category =
            prefs.getString(
                KEY_CATEGORY_PREFIX + widgetId,
                DEFAULT_CATEGORY
            ) ?: DEFAULT_CATEGORY

        val list =
            data.filter {
                normalizeCategory(it.category) ==
                    normalizeCategory(category)
            }.ifEmpty {
                data
            }

        if (list.isEmpty()) {
            updateWidget(
                context,
                AppWidgetManager.getInstance(context),
                widgetId
            )
            return
        }

        val index =
            prefs.getInt(
                KEY_INDEX_PREFIX + widgetId,
                0
            ).coerceIn(
                0,
                list.lastIndex
            )

        val item =
            list[index]

        val target =
            item.count.coerceAtLeast(1)

        val countKey =
            countKeyFor(
                widgetId,
                item.id
            )

        val current =
            prefs.getInt(
                countKey,
                0
            ).coerceIn(
                0,
                target
            )

        val incremented =
            (current + 1).coerceAtMost(target)

        if (incremented >= target) {

            // Save the completed count for this dhikr.
            // Do NOT modify the next dhikr's saved count.
            var newIndex =
                index + 1

            if (newIndex > list.lastIndex) {
                newIndex = 0
            }

            prefs.edit()
                .putInt(
                    countKey,
                    target
                )
                .putInt(
                    KEY_INDEX_PREFIX + widgetId,
                    newIndex
                )
                .apply()

        } else {

            prefs.edit()
                .putInt(
                    countKey,
                    incremented
                )
                .apply()
        }

        updateWidget(
            context,
            AppWidgetManager.getInstance(context),
            widgetId
        )
    }

    private fun move(
        context: Context,
        widgetId: Int,
        direction: Int
    ) {

        val data =
            DhikrRepository.load(context)

        if (data.isEmpty()) {
            updateWidget(
                context,
                AppWidgetManager.getInstance(context),
                widgetId
            )
            return
        }

        val prefs =
            context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )

        val category =
            prefs.getString(
                KEY_CATEGORY_PREFIX + widgetId,
                DEFAULT_CATEGORY
            ) ?: DEFAULT_CATEGORY

        val list =
            data.filter {
                normalizeCategory(it.category) ==
                    normalizeCategory(category)
            }.ifEmpty {
                data
            }

        if (list.isEmpty()) {
            updateWidget(
                context,
                AppWidgetManager.getInstance(context),
                widgetId
            )
            return
        }

        var index =
            prefs.getInt(
                KEY_INDEX_PREFIX + widgetId,
                0
            ).coerceIn(
                0,
                list.lastIndex
            )

        index += direction

        if (index < 0) {
            index = list.lastIndex
        }

        if (index > list.lastIndex) {
            index = 0
        }

        // Only change the selected dhikr.
        // Never reset the count of the dhikr being left
        // or the count of the dhikr being entered.
        prefs.edit()
            .putInt(
                KEY_INDEX_PREFIX + widgetId,
                index
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

        val data =
            DhikrRepository.load(context)

        if (data.isEmpty()) {
            updateWidget(
                context,
                AppWidgetManager.getInstance(context),
                widgetId
            )
            return
        }

        val prefs =
            context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )

        val category =
            prefs.getString(
                KEY_CATEGORY_PREFIX + widgetId,
                DEFAULT_CATEGORY
            ) ?: DEFAULT_CATEGORY

        val list =
            data.filter {
                normalizeCategory(it.category) ==
                    normalizeCategory(category)
            }.ifEmpty {
                data
            }

        if (list.isEmpty()) {
            updateWidget(
                context,
                AppWidgetManager.getInstance(context),
                widgetId
            )
            return
        }

        val index =
            prefs.getInt(
                KEY_INDEX_PREFIX + widgetId,
                0
            ).coerceIn(
                0,
                list.lastIndex
            )

        val item =
            list[index]

        // Reset ONLY the currently displayed dhikr.
        prefs.edit()
            .putInt(
                countKeyFor(
                    widgetId,
                    item.id
                ),
                0
            )
            .apply()

        updateWidget(
            context,
            AppWidgetManager.getInstance(context),
            widgetId
        )
    }

    private fun cycleCategory(
        context: Context,
        widgetId: Int
    ) {

        val prefs =
            context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )

        val currentCategory =
            prefs.getString(
                KEY_CATEGORY_PREFIX + widgetId,
                DEFAULT_CATEGORY
            ) ?: DEFAULT_CATEGORY

        val currentIndex =
            CATEGORY_ORDER.indexOfFirst {
                normalizeCategory(it) ==
                    normalizeCategory(currentCategory)
            }

        val nextIndex =
            if (currentIndex == -1) {
                0
            } else {
                (currentIndex + 1) % CATEGORY_ORDER.size
            }

        val nextCategory =
            CATEGORY_ORDER[nextIndex]

        // Category switching moves to the first dhikr
        // of the selected category, but does NOT reset
        // that dhikr's saved counter.
        prefs.edit()
            .putString(
                KEY_CATEGORY_PREFIX + widgetId,
                nextCategory
            )
            .putInt(
                KEY_INDEX_PREFIX + widgetId,
                0
            )
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

        val views =
            RemoteViews(
                context.packageName,
                R.layout.widget_dhikr
            )

        val data =
            DhikrRepository.load(context)

        if (data.isEmpty()) {

            views.setTextViewText(
                R.id.dhikr_category,
                "الأذكار"
            )

            views.setTextViewText(
                R.id.dhikr_text,
                "تعذر قراءة بيانات الأذكار"
            )

            views.setTextViewText(
                R.id.dhikr_count,
                "0"
            )

            views.setTextViewText(
                R.id.dhikr_target,
                "من 0"
            )

            manager.updateAppWidget(
                widgetId,
                views
            )

            return
        }

        val prefs =
            context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )

        val category =
            prefs.getString(
                KEY_CATEGORY_PREFIX + widgetId,
                DEFAULT_CATEGORY
            ) ?: DEFAULT_CATEGORY

        val list =
            data.filter {
                normalizeCategory(it.category) ==
                    normalizeCategory(category)
            }.ifEmpty {
                data
            }

        if (list.isEmpty()) {

            views.setTextViewText(
                R.id.dhikr_category,
                "الأذكار"
            )

            views.setTextViewText(
                R.id.dhikr_text,
                "لا توجد أذكار"
            )

            views.setTextViewText(
                R.id.dhikr_count,
                "0"
            )

            views.setTextViewText(
                R.id.dhikr_target,
                "من 0"
            )

            manager.updateAppWidget(
                widgetId,
                views
            )

            return
        }

        var index =
            prefs.getInt(
                KEY_INDEX_PREFIX + widgetId,
                0
            )

        index =
            index.coerceIn(
                0,
                list.lastIndex
            )

        val item =
            list[index]

        val target =
            item.count.coerceAtLeast(1)

        val countKey =
            countKeyFor(
                widgetId,
                item.id
            )

        val current =
            prefs.getInt(
                countKey,
                0
            ).coerceIn(
                0,
                target
            )

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
            "من $target"
        )

        views.setProgressBar(
            R.id.dhikr_progress,
            target,
            current,
            false
        )

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

        views.setOnClickPendingIntent(
            R.id.dhikr_category,
            actionIntent(
                context,
                ACTION_CATEGORY,
                widgetId
            )
        )

        views.setOnClickPendingIntent(
            R.id.dhikr_counter_area,
            createIncrementIntent(
                context,
                widgetId
            )
        )

        manager.updateAppWidget(
            widgetId,
            views
        )
    }

    /**
     * Creates a unique SharedPreferences key for each
     * widget + dhikr combination.
     *
     * Example:
     * count_123_s1
     * count_123_s2
     * count_123_m1
     */
    private fun countKeyFor(
        widgetId: Int,
        dhikrId: String
    ): String {
        return KEY_COUNT_PREFIX +
            widgetId +
            "_" +
            dhikrId
    }

    private fun createIncrementIntent(
        context: Context,
        widgetId: Int
    ): PendingIntent {

        val intent =
            Intent(
                context,
                DhikrWidget::class.java
            ).apply {

                action =
                    ACTION_INCREMENT

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

    private fun actionIntent(
        context: Context,
        action: String,
        widgetId: Int
    ): PendingIntent {

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

        val requestCode =
            when (action) {

                ACTION_NEXT ->
                    widgetId * 10 + 1

                ACTION_PREVIOUS ->
                    widgetId * 10 + 2

                ACTION_RESET ->
                    widgetId * 10 + 3

                ACTION_CATEGORY ->
                    widgetId * 10 + 5

                else ->
                    widgetId * 10 + 9
            }

        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun normalizeCategory(
        value: String
    ): String {

        return value
            .trim()
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ة", "ه")
    }
}
