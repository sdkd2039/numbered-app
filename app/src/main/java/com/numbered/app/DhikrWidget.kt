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
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { widgetId -> render(context, widgetId, DhikrRepository.loadCached(context)) }
        DhikrRepository.refresh(context) { data ->
            val managerAfterRefresh = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, DhikrWidget::class.java)
            managerAfterRefresh.getAppWidgetIds(component).forEach { widgetId ->
                render(context, widgetId, data)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        when (intent.action) {
            ACTION_PREVIOUS, ACTION_NEXT, ACTION_GROUP_PREVIOUS, ACTION_GROUP_NEXT -> {
                val data = DhikrRepository.loadCached(context)
                if (data.isNotEmpty()) {
                    move(context, widgetId, data, intent.action!!)
                    render(context, widgetId, data)
                }
            }
        }
    }

    companion object {
        private const val PREFS = "dhikr_widget_state"
        private const val KEY_GROUP = "group_"
        private const val KEY_INDEX = "index_"

        const val ACTION_PREVIOUS = "com.numbered.app.action.DHIKR_PREVIOUS"
        const val ACTION_NEXT = "com.numbered.app.action.DHIKR_NEXT"
        const val ACTION_GROUP_PREVIOUS = "com.numbered.app.action.DHIKR_GROUP_PREVIOUS"
        const val ACTION_GROUP_NEXT = "com.numbered.app.action.DHIKR_GROUP_NEXT"

        private fun render(context: Context, widgetId: Int, data: List<DhikrEntry>) {
            val views = RemoteViews(context.packageName, R.layout.widget_dhikr)
            val groups = data.map { it.group }.distinct()
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val groupIndex = prefs.getInt(KEY_GROUP + widgetId, 0)
                .coerceIn(0, (groups.size - 1).coerceAtLeast(0))
            val entries = data.filter { it.group == groups.getOrNull(groupIndex) }
            val entryIndex = prefs.getInt(KEY_INDEX + widgetId, 0)
                .coerceIn(0, (entries.size - 1).coerceAtLeast(0))
            val entry = entries.getOrNull(entryIndex)

            views.setTextViewText(R.id.dhikr_group, entry?.group ?: "الذكر")
            views.setTextViewText(R.id.dhikr_text, entry?.text ?: "لا توجد بيانات محفوظة")
            views.setTextViewText(
                R.id.dhikr_count_label,
                entry?.count?.takeIf { it.isNotBlank() }?.let { "عدد المرات: $it" } ?: ""
            )
            views.setTextViewText(
                R.id.dhikr_counter,
                entry?.count?.takeIf { it.isNotBlank() } ?: ""
            )
            views.setTextViewText(
                R.id.dhikr_progress,
                if (entry != null) "${entryIndex + 1} / ${entries.size}" else ""
            )

            val hasData = entry != null
            val hasGroups = groups.size > 1
            views.setViewVisibility(R.id.dhikr_counter_card, if (hasData && !entry!!.count.isNullOrBlank()) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.dhikr_count_label, if (hasData && !entry!!.count.isNullOrBlank()) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.dhikr_group_previous, if (hasGroups) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.dhikr_group_next, if (hasGroups) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.dhikr_progress, if (hasData) View.VISIBLE else View.GONE)

            bind(views, context, widgetId, R.id.dhikr_previous, ACTION_PREVIOUS)
            bind(views, context, widgetId, R.id.dhikr_next, ACTION_NEXT)
            bind(views, context, widgetId, R.id.dhikr_group_previous, ACTION_GROUP_PREVIOUS)
            bind(views, context, widgetId, R.id.dhikr_group_next, ACTION_GROUP_NEXT)

            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launchIntent ->
                views.setOnClickPendingIntent(
                    R.id.dhikr_root,
                    PendingIntent.getActivity(
                        context,
                        widgetId,
                        launchIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
            AppWidgetManager.getInstance(context).updateAppWidget(widgetId, views)
        }

        private fun bind(
            views: RemoteViews,
            context: Context,
            widgetId: Int,
            viewId: Int,
            action: String
        ) {
            val intent = Intent(context, DhikrWidget::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            views.setOnClickPendingIntent(
                viewId,
                PendingIntent.getBroadcast(
                    context,
                    widgetId * 10 + viewId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }

        private fun move(context: Context, widgetId: Int, data: List<DhikrEntry>, action: String) {
            val groups = data.map { it.group }.distinct()
            if (groups.isEmpty()) return
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            var groupIndex = prefs.getInt(KEY_GROUP + widgetId, 0).coerceIn(0, groups.lastIndex)
            var entryIndex = prefs.getInt(KEY_INDEX + widgetId, 0)

            if (action == ACTION_GROUP_PREVIOUS || action == ACTION_GROUP_NEXT) {
                val delta = if (action == ACTION_GROUP_NEXT) 1 else -1
                groupIndex = (groupIndex + delta + groups.size) % groups.size
                entryIndex = 0
            } else {
                val entries = data.filter { it.group == groups[groupIndex] }
                if (entries.isEmpty()) return
                val delta = if (action == ACTION_NEXT) 1 else -1
                entryIndex = (entryIndex + delta + entries.size) % entries.size
            }

            prefs.edit()
                .putInt(KEY_GROUP + widgetId, groupIndex)
                .putInt(KEY_INDEX + widgetId, entryIndex)
                .apply()
        }
    }
}
