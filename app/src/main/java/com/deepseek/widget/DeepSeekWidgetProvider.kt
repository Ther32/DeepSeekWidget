package com.deepseek.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.deepseek.widget.data.WidgetData

/**
 * 桌面 Widget Provider（副功能，部分 ROM 可能不支持）
 */
class DeepSeekWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            try {
                val data = WidgetCache(context).read()
                val views = buildViews(context, data)
                appWidgetManager.updateAppWidget(id, views)
            } catch (e: Exception) {
                val views = RemoteViews(context.packageName, R.layout.widget_layout)
                views.setTextViewText(R.id.widget_title, "DeepSeek Monitor")
                views.setTextViewText(R.id.text_balance, "请打开 App")
                views.setTextViewText(R.id.text_usage, "在 App 中设置 API Key")
                views.setTextViewText(R.id.text_quota, "")
                views.setTextViewText(R.id.text_update_time, "点击打开 App")
                views.setTextColor(R.id.text_status_indicator, context.getColor(R.color.widget_offline))
                views.setTextViewText(R.id.text_status_label, "未配置")

                val pi = PendingIntent.getActivity(
                    context, id,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_container, pi)
                appWidgetManager.updateAppWidget(id, views)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (ACTION_REFRESH == intent.action) {
            triggerRefresh(context)
        }
    }

    private fun triggerRefresh(context: Context) {
        val work = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(TAG_MANUAL_REFRESH)
            .build()
        WorkManager.getInstance(context).enqueue(work)
    }

    companion object {
        const val ACTION_REFRESH = "com.deepseek.widget.ACTION_REFRESH"
        const val TAG_MANUAL_REFRESH = "deepseek_manual_refresh"

        fun updateAllWidgets(context: Context, data: WidgetData) {
            WidgetCache(context).write(data)

            val am = AppWidgetManager.getInstance(context)
            val ids = am.getAppWidgetIds(ComponentName(context, DeepSeekWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val views = buildViews(context, data)
            for (id in ids) am.updateAppWidget(id, views)
        }

        private fun buildViews(context: Context, data: WidgetData): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            when {
                data.balance == "--" && data.totalTokens == 0L && data.lastUpdateTime == 0L && data.error == null -> {
                    views.setTextViewText(R.id.text_balance, "等待中…")
                    views.setTextViewText(R.id.text_usage, "正在首次获取数据")
                    views.setTextViewText(R.id.text_quota, "")
                    views.setTextViewText(R.id.text_update_time, "网络连接后自动更新")
                    views.setTextColor(R.id.text_status_indicator, context.getColor(R.color.widget_hint))
                    views.setTextViewText(R.id.text_status_label, "初始化")
                }
                data.error != null -> {
                    views.setTextViewText(R.id.text_balance, "⚠️")
                    views.setTextViewText(R.id.text_usage, "获取失败")
                    views.setTextViewText(R.id.text_quota, data.error.take(30))
                    views.setTextViewText(R.id.text_update_time, "点击重试")
                    views.setTextColor(R.id.text_status_indicator, context.getColor(R.color.widget_offline))
                    views.setTextViewText(R.id.text_status_label, "错误")
                }
                else -> {
                    views.setTextViewText(R.id.text_balance, "¥${data.balance}")
                    views.setTextViewText(R.id.text_usage, formatToken(data.totalTokens))
                    views.setTextViewText(R.id.text_quota, formatToken(data.totalTokens))
                    val c = if (data.isAvailable) context.getColor(R.color.widget_online) else context.getColor(R.color.widget_offline)
                    views.setTextColor(R.id.text_status_indicator, c)
                    views.setTextViewText(R.id.text_status_label, if (data.isAvailable) "正常" else "受限")
                    views.setTextViewText(R.id.text_update_time, "点击刷新")
                    views.setTextColor(R.id.text_balance, context.getColor(R.color.widget_value))
                }
            }

            val intent = Intent(context, DeepSeekWidgetProvider::class.java).apply { action = ACTION_REFRESH }
            val pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_container, pi)
            return views
        }

        private fun formatToken(t: Long): String = when {
            t >= 1_000_000_000 -> "${"%.2f".format(t / 1_000_000_000.0)}B"
            t >= 1_000_000 -> "${"%.2f".format(t / 1_000_000.0)}M"
            t >= 1_000 -> "${"%.2f".format(t / 1_000.0)}K"
            t > 0 -> "$t"
            else -> "---"
        }
    }
}
