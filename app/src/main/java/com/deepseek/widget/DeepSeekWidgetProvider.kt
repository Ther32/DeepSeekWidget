package com.deepseek.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.deepseek.widget.data.WidgetData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DeepSeek 用量监控桌面 Widget Provider
 *
 * 使用 SharedPreferences 做同步读取缓存，避免 DataStore 的异步问题。
 * Widget 初始化必须同步完成，不能依赖协程。
 */
class DeepSeekWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val cache = WidgetCache(context)
        for (appWidgetId in appWidgetIds) {
            try {
                val data = cache.read()
                val views = buildRemoteViews(context, data)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                // 异常降级：显示友好提示，不崩溃
                val views = RemoteViews(context.packageName, R.layout.widget_layout)
                views.setTextViewText(R.id.widget_title, "DeepSeek Monitor")
                views.setTextViewText(R.id.text_balance, "点击配置")
                views.setTextViewText(R.id.text_usage, "请先打开 App 设置 API Key")
                views.setTextViewText(R.id.text_quota, "")
                views.setTextViewText(R.id.text_update_time, "点击进入设置")
                views.setTextColor(R.id.text_status_indicator, context.getColor(R.color.widget_offline))
                views.setTextViewText(R.id.text_status_label, "未配置")

                // 点击打开配置页
                val configIntent = Intent(context, DeepSeekWidgetConfigActivity::class.java)
                val pi = PendingIntent.getActivity(
                    context, appWidgetId, configIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_container, pi)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (ACTION_REFRESH == intent.action) {
            triggerImmediateRefresh(context)
        }
    }

    override fun onEnabled(context: Context) {
        triggerImmediateRefresh(context)
    }

    /**
     * 触发一次立即刷新（WorkManager）
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

        // 显示"刷新中…"
        val am = AppWidgetManager.getInstance(context)
        val ids = am.getAppWidgetIds(
            ComponentName(context, DeepSeekWidgetProvider::class.java)
        )
        for (id in ids) {
            val v = RemoteViews(context.packageName, R.layout.widget_layout)
            v.setTextViewText(R.id.text_balance, "刷新中…")
            v.setTextViewText(R.id.text_usage, "请稍候")
            v.setTextViewText(R.id.text_quota, "")
            v.setTextViewText(R.id.text_update_time, "⏳ 正在获取数据…")
            am.updateAppWidget(id, v)
        }
    }

    // ===== 静态方法 =====

    companion object {
        const val ACTION_REFRESH = "com.deepseek.widget.ACTION_REFRESH"
        const val TAG_MANUAL_REFRESH = "deepseek_manual_refresh"

        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val dateTimeFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

        /**
         * 构建 RemoteViews（供 Worker 和 Provider 共用）
         */
        fun buildRemoteViews(context: Context, data: WidgetData): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            if (data.balance == "--" && data.totalTokens == 0L && data.lastUpdateTime == 0L && data.error == null) {
                // 从未更新过 → 显示"等待中"
                views.setTextViewText(R.id.text_balance, "等待中…")
                views.setTextViewText(R.id.text_usage, "正在首次获取数据")
                views.setTextViewText(R.id.text_quota, "")
                views.setTextViewText(R.id.text_update_time, "网络连接后将自动更新")
                views.setTextColor(R.id.text_status_indicator, context.getColor(R.color.widget_hint))
                views.setTextViewText(R.id.text_status_label, "初始化")
            } else if (data.error != null) {
                views.setTextViewText(R.id.text_balance, "⚠️")
                views.setTextViewText(R.id.text_usage, "获取失败")
                views.setTextViewText(R.id.text_quota, data.error.take(30))
                views.setTextViewText(R.id.text_update_time, "点击重试")
                views.setTextColor(R.id.text_status_indicator, context.getColor(R.color.widget_offline))
                views.setTextViewText(R.id.text_status_label, "错误")
            } else {
                views.setTextViewText(R.id.text_balance, "¥${data.balance}")
                views.setTextViewText(R.id.text_usage, formatTokens(data.totalTokens))
                views.setTextViewText(R.id.text_quota, formatTokens(data.totalTokens))

                val color = if (data.isAvailable) context.getColor(R.color.widget_online)
                else context.getColor(R.color.widget_offline)
                views.setTextColor(R.id.text_status_indicator, color)
                views.setTextViewText(R.id.text_status_label, if (data.isAvailable) "正常" else "受限")

                val timeStr = if (data.lastUpdateTime > 0) {
                    val date = Date(data.lastUpdateTime)
                    val now = Date()
                    val fmt = if (date.year == now.year && date.month == now.month && date.date == now.date)
                        timeFormat else dateTimeFormat
                    "点击刷新 · 更新于: ${fmt.format(date)}"
                } else {
                    "点击刷新获取数据"
                }
                views.setTextViewText(R.id.text_update_time, timeStr)
                views.setTextColor(R.id.text_balance, context.getColor(R.color.widget_value))
            }

            // 点击 → 刷新
            val intent = Intent(context, DeepSeekWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val pi = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pi)
            return views
        }

        /**
         * 更新所有活跃 Widget（Worker 调用）
         */
        fun updateAllWidgets(context: Context, data: WidgetData) {
            // 先写缓存
            WidgetCache(context).write(data)

            val am = AppWidgetManager.getInstance(context)
            val ids = am.getAppWidgetIds(
                ComponentName(context, DeepSeekWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return

            val views = buildRemoteViews(context, data)
            for (id in ids) am.updateAppWidget(id, views)
        }

        private fun formatTokens(tokens: Long): String = when {
            tokens >= 1_000_000_000 -> "${"%.2f".format(tokens / 1_000_000_000.0)}B tokens"
            tokens >= 1_000_000 -> "${"%.2f".format(tokens / 1_000_000.0)}M tokens"
            tokens >= 1_000 -> "${"%.2f".format(tokens / 1_000.0)}K tokens"
            tokens > 0 -> "$tokens tokens"
            else -> "---"
        }
    }
}

/**
 * 同步 Widget 缓存（SharedPreferences）
 * 专供 Widget Provider 在主线程同步读取使用
 */
class WidgetCache(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("widget_cache", Context.MODE_PRIVATE)

    fun read(): WidgetData = try {
        WidgetData(
            balance = prefs.getString("balance", "--") ?: "--",
            currency = prefs.getString("currency", "CNY") ?: "CNY",
            isAvailable = prefs.getBoolean("is_available", false),
            totalTokens = prefs.getLong("total_tokens", 0),
            totalCost = prefs.getString("total_cost", "--") ?: "--",
            lastUpdateTime = prefs.getLong("update_time", 0),
            error = prefs.getString("error", null)
        )
    } catch (e: Exception) {
        WidgetData()
    }

    fun write(data: WidgetData) {
        prefs.edit()
            .putString("balance", data.balance)
            .putString("currency", data.currency)
            .putBoolean("is_available", data.isAvailable)
            .putLong("total_tokens", data.totalTokens)
            .putString("total_cost", data.totalCost)
            .putLong("update_time", data.lastUpdateTime)
            .apply {
                if (data.error != null) putString("error", data.error)
                else remove("error")
            }.apply()
    }
}
