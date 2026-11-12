package com.succh.unifeed.desktop

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.succh.unifeed.MainActivity
import com.succh.unifeed.R
import com.succh.unifeed.ui.ReaderPrefs
import com.succh.unifeed.ui.theme.ThemeMode

/**
 * 桌面「最新未读」小部件：标题区打开应用，列表项打开对应文章。
 * 数据刷新走 [requestUpdate]，系统周期更新关掉（updatePeriodMillis=0）。
 */
class UnreadWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            appWidgetManager.updateAppWidget(widgetId, buildRemoteViews(context, widgetId))
            appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_list)
        }
    }

    companion object {
        fun requestUpdate(context: Context) {
            runCatching {
                val appContext = context.applicationContext
                val manager = AppWidgetManager.getInstance(appContext)
                val ids = manager.getAppWidgetIds(
                    ComponentName(appContext, UnreadWidgetProvider::class.java)
                )
                if (ids.isEmpty()) return
                for (id in ids) {
                    manager.updateAppWidget(id, buildRemoteViews(appContext, id))
                }
                manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
            }
        }

        private fun buildRemoteViews(context: Context, widgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.unread_widget)
            applyHeaderTheme(context, views)
            val openApp = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            views.setOnClickPendingIntent(
                R.id.widget_header,
                PendingIntent.getActivity(
                    context,
                    0,
                    openApp,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            val serviceIntent = Intent(context, UnreadWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_list, serviceIntent)
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)

            val itemTemplate = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            views.setPendingIntentTemplate(
                R.id.widget_list,
                PendingIntent.getActivity(
                    context,
                    1,
                    itemTemplate,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            )
            return views
        }

        /**
         * 标题条跟随所选主题：按皮肤深浅模式（跟随系统 / 手动固定）取强调色，
         * 运行时覆盖布局里的默认 @color/primary，切换主题后 requestUpdate 即生效。
         */
        private fun applyHeaderTheme(context: Context, views: RemoteViews) {
            val accent = ReaderPrefs.currentAccent(context)
            val dark = when (ReaderPrefs.currentThemeMode(context)) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.FOLLOW_SYSTEM ->
                    (context.resources.configuration.uiMode and
                        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            }
            val palette = if (dark) accent.dark else accent.light
            views.setInt(R.id.widget_header, "setBackgroundColor", palette.primary.toArgb())
            views.setTextColor(R.id.widget_header, palette.onPrimary.toArgb())
        }
    }
}
