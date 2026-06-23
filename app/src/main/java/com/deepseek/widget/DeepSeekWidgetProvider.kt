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
import com.deepseek.widget.data.PreferencesManager
import com.deepseek.widget.data.WidgetData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DeepSeek 用量监控桌面 Widget Provider
 *
 * 功能：
 * - 显示最后缓存的余额和用量数据（离线可用）
 * - 点击组件触发立即刷新
 * - 通过 WorkManager 实现定时更新
 */
class DeepSeekWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // 为每个 widget 实例应用当前缓存数据
        for (appWidgetId in appWidgetIds) {
            updateWidgetFromCache(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (ACTION_REFRESH == intent.action) {
            // 用户点击了组件 → 触发立即刷新
            triggerImmediateRefresh(context)
        }
    }

    override fun onEnabled(context: Context) {
        // 第一个 widget 被添加时触发
        triggerImmediateRefresh(context)
    }

    // ===== 内部方法 =====

    /**
     * 从 DataStore 缓存读取数据并更新 Widget
     */
    private fun updateWidgetFromCache(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val prefsManager = PreferencesManager(context)

        // 使用 runBlocking 协程构建器同步读取缓存
        // 因为 RemoteViews 更新必须在主线程同步完成
        kotlinx.coroutines.runBlocking {
            val data = prefsManager.getCachedWidgetData()
            val views = buildRemoteViews(context, data)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    /**
     * 触发一次立即刷新（通过 WorkManager）
     */
    private fun triggerImmediateRefresh(context: Context) {
        val workRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(TAG_MANUAL_REFRESH)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)

        // 立即显示"刷新中…"状态
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, DeepSeekWidgetProvider::class.java)
        )

        for (id in widgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            views.setTextViewText(R.id.text_balance, "刷新中…")
            views.setTextViewText(R.id.text_usage, "请稍候")
            views.setTextViewText(R.id.text_quota, "")
            views.setTextViewText(R.id.text_update_time, "⏳ 正在获取数据…")
            views.setTextColor(R.id.text_balance, context.getColor(R.color.widget_hint))
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    // ===== 静态方法（供 Worker 调用） =====

    companion object {
        const val ACTION_REFRESH = "com.deepseek.widget.ACTION_REFRESH"
        const val TAG_MANUAL_REFRESH = "deepseek_manual_refresh"

        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val dateTimeFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

        /**
         * 根据 WidgetData 构建 RemoteViews
         */
        fun buildRemoteViews(context: Context, data: WidgetData): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            if (data.error != null) {
                // 错误状态
                views.setTextViewText(R.id.text_balance, "⚠️")
                views.setTextViewText(R.id.text_usage, "获取失败")
                views.setTextViewText(R.id.text_cost, data.error.take(30))
                views.setTextViewText(R.id.text_update_time, "点击重试")
                views.setTextColor(R.id.text_status_indicator, context.getColor(R.color.widget_offline))
                views.setTextViewText(R.id.text_status_label, "错误")
            } else {
                // 正常数据
                views.setTextViewText(R.id.text_balance, "¥${data.balance}")
                views.setTextViewText(R.id.text_usage, formatTokens(data.totalTokens))
                val quotaText = if (data.totalTokens > 0) "${formatTokens(data.totalTokens)}" else "---"
                views.setTextViewText(R.id.text_quota, quotaText)

                val statusColor = if (data.isAvailable) {
                    context.getColor(R.color.widget_online)
                } else {
                    context.getColor(R.color.widget_offline)
                }
                views.setTextColor(R.id.text_status_indicator, statusColor)
                views.setTextViewText(R.id.text_status_label,
                    if (data.isAvailable) "正常" else "受限")

                // 更新时间
                val timeStr = if (data.lastUpdateTime > 0) {
                    val date = Date(data.lastUpdateTime)
                    val now = Date()
                    val fmt = if (date.year == now.year && date.month == now.month && date.date == now.date) {
                        timeFormat
                    } else {
                        dateTimeFormat
                    }
                    "点击刷新 · 更新于: ${fmt.format(date)}"
                } else {
                    "点击刷新获取数据"
                }
                views.setTextViewText(R.id.text_update_time, timeStr)

                // 恢复正常颜色
                views.setTextColor(R.id.text_balance, context.getColor(R.color.widget_value))
            }

            // 设置点击事件：点击整个组件触发刷新
            val refreshIntent = Intent(context, DeepSeekWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

            return views
        }

        /**
         * 更新所有活跃的 Widget 实例
         * （由 WidgetUpdateWorker 在后台调用）
         */
        fun updateAllWidgets(context: Context, data: WidgetData) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, DeepSeekWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)

            if (widgetIds.isEmpty()) return // 没有活跃的 widget

            val views = buildRemoteViews(context, data)
            for (id in widgetIds) {
                appWidgetManager.updateAppWidget(id, views)
            }
        }

        /** 格式化 token 数量为可读形式 */
        private fun formatTokens(tokens: Long): String {
            return when {
                tokens >= 1_000_000_000 -> "${"%.2f".format(tokens / 1_000_000_000.0)}B tokens"
                tokens >= 1_000_000 -> "${"%.2f".format(tokens / 1_000_000.0)}M tokens"
                tokens >= 1_000 -> "${"%.2f".format(tokens / 1_000.0)}K tokens"
                else -> "$tokens tokens"
            }
        }
    }
}
