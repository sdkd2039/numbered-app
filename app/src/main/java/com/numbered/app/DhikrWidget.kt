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

        /*
         * خط Zain لهذا الويدجت فقط.
         */
        private const val ZAIN_APPEARANCE =
            R.style.WidgetZainTextAppearance
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

            /*
             * زيادة العداد:
             * 0 → 1 → 2 → 3 ...
             */
            ACTION_INCREMENT -> {
                increment(
                    context,
                    widgetId
                )
            }

            /*
             * الذكر التالي.
             */
            ACTION_NEXT -> {
                move(
                    context,
                    widgetId,
                    1
                )
            }

            /*
             * الذكر السابق.
             */
            ACTION_PREVIOUS -> {
                move(
                    context,
                    widgetId,
                    -1
                )
            }

            /*
             * إعادة العداد إلى صفر.
             */
            ACTION_RESET -> {
                reset(
                    context,
                    widgetId
                )
            }
        }
    }

    /*
     * زيادة عداد الذكر.
     */
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

        val current =
            prefs.getInt(
                KEY_COUNT_PREFIX + widgetId,
                0
            )

        /*
         * زيادة واحدة فقط.
         *
         * مثال:
         * 0 → 1
         * 1 → 2
         * 2 → 3
         */
        val newCount =
            (current + 1).coerceAtMost(
                item.count
            )

        prefs.edit()
            .putInt(
                KEY_COUNT_PREFIX + widgetId,
                newCount
            )
            .apply()

        updateWidget(
            context,
            AppWidgetManager.getInstance(context),
            widgetId
        )
    }

    /*
     * الانتقال للذكر السابق أو التالي.
     */
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
            )

        index += direction

        /*
         * إذا وصلنا قبل أول ذكر،
         * ننتقل إلى آخر ذكر.
         */
        if (index < 0) {
            index = list.lastIndex
        }

        /*
         * إذا تجاوزنا آخر ذكر،
         * نعود إلى أول ذكر.
         */
        if (index > list.lastIndex) {
            index = 0
        }

        /*
         * عند تغيير الذكر يرجع العداد إلى صفر.
         */
        prefs.edit()
            .putInt(
                KEY_INDEX_PREFIX + widgetId,
                index
            )
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

    /*
     * إعادة عداد الذكر الحالي إلى صفر.
     */
    private fun reset(
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

    /*
     * تحديث شكل الويدجت.
     */
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

        /*
         * تطبيق خط Zain على عناصر
         * Dhikr Widget فقط.
         */
        applyFont(views)

        val data =
            DhikrRepository.load(context)

        /*
         * في حال فشل قراءة CSV.
         */
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

        /*
         * محاولة عرض فئة الصباح.
         * إذا لم توجد، نستخدم جميع البيانات.
         */
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

        /*
         * تحديد الذكر الحالي.
         */
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

        /*
         * قراءة العداد الحالي.
         */
        var current =
            prefs.getInt(
                KEY_COUNT_PREFIX + widgetId,
                0
            )

        current =
            current.coerceIn(
                0,
                item.count
            )

        /*
         * الفئة.
         */
        views.setTextViewText(
            R.id.dhikr_category,
            item.category
        )

        /*
         * نص الذكر.
         */
        views.setTextViewText(
            R.id.dhikr_text,
            item.text
        )

        /*
         * العداد الحالي.
         */
        views.setTextViewText(
            R.id.dhikr_count,
            current.toString()
        )

        /*
         * العدد المطلوب.
         */
        views.setTextViewText(
            R.id.dhikr_target,
            "من ${item.count}"
        )

        /*
         * شريط التقدم.
         */
        views.setProgressBar(
            R.id.dhikr_progress,
            item.count,
            current,
            false
        )

        /*
         * زر التالي.
         */
        views.setOnClickPendingIntent(
            R.id.dhikr_next,
            actionIntent(
                context,
                ACTION_NEXT,
                widgetId
            )
        )

        /*
         * زر السابق.
         */
        views.setOnClickPendingIntent(
            R.id.dhikr_previous,
            actionIntent(
                context,
                ACTION_PREVIOUS,
                widgetId
            )
        )

        /*
         * زر إعادة العداد.
         */
        views.setOnClickPendingIntent(
            R.id.dhikr_reset,
            actionIntent(
                context,
                ACTION_RESET,
                widgetId
            )
        )

        /*
         * الضغط على العداد:
         *
         * 0 → 1 → 2 → 3 → ...
         */
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

    /*
     * إنشاء أمر زيادة العداد.
     */
    private fun createIncrementIntent(
        context: Context,
        widgetId: Int
    ): PendingIntent {

        val intent =
            Intent(
                context,
                DhikrWidget::class.java
            ).apply {
                action = ACTION_INCREMENT

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

    /*
     * إنشاء PendingIntent للأزرار.
     */
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

    /*
     * تفعيل خط Zain على ويدجت الأذكار فقط.
     *
     * لا يتم تغيير خط التطبيق بالكامل.
     */
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

    /*
     * توحيد كتابة اسم الفئة.
     */
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