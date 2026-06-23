package com.deepseek.widget

import android.content.Context
import android.content.SharedPreferences
import com.deepseek.widget.data.WidgetData

/**
 * 同步 Widget / Dashboard 缓存（SharedPreferences）
 *
 * 专供 Widget Provider 和 MainActivity 在主线程同步读取使用。
 * 后台 Worker 写入，前台 UI 读取，无协程依赖。
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
