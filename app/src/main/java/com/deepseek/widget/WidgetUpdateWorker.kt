package com.deepseek.widget

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.deepseek.widget.data.DeepSeekApiClient
import com.deepseek.widget.data.PreferencesManager
import com.deepseek.widget.data.WidgetData
import java.util.concurrent.TimeUnit

/**
 * 后台更新 Worker
 *
 * 被 WorkManager 调度运行，负责：
 * 1. 从 DataStore 读取 API Key
 * 2. 调用 DeepSeek API 获取余额和用量
 * 3. 缓存结果到 DataStore
 * 4. 更新所有活跃的桌面 Widget
 */
class WidgetUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DeepSeekWidget"
        private const val PERIODIC_WORK_NAME = "deepseek_periodic_update"
        private const val IMMEDIATE_WORK_NAME = "deepseek_immediate_update"

        /**
         * 调度定时更新任务
         * @param intervalMinutes 更新间隔（分钟）
         */
        fun schedulePeriodicUpdate(context: Context, intervalMinutes: Long) {
            val workRequest = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .addTag("deepseek_periodic")
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1,
                    TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )

            Log.d(TAG, "定时更新已调度，间隔: ${intervalMinutes}分钟")
        }

        /**
         * 取消定时更新
         */
        fun cancelPeriodicUpdate(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }

        /**
         * 触发一次立即更新
         */
        fun triggerImmediateUpdate(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .addTag("deepseek_immediate")
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )

            Log.d(TAG, "立即更新已触发")
        }
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "开始更新数据…")

            val prefsManager = PreferencesManager(applicationContext)

            // 1. 读取 API Key
            val apiKey = prefsManager.getApiKey()
            if (apiKey.isNullOrBlank()) {
                Log.w(TAG, "API Key 未配置，跳过更新")
                setLastError(prefsManager, "未配置 API Key")
                return Result.failure()
            }

            // 2. 调用 API
            val client = DeepSeekApiClient(apiKey)
            val widgetData = client.fetchAllWidgetData()

            // 3. 缓存结果
            prefsManager.cacheWidgetData(widgetData)

            // 4. 更新所有 Widget
            if (widgetData.error == null) {
                DeepSeekWidgetProvider.updateAllWidgets(applicationContext, widgetData)
                Log.d(TAG,
                    "更新成功: 余额=¥${widgetData.balance}, " +
                            "用量=${widgetData.totalTokens}, " +
                            "花费=¥${widgetData.totalCost}")
                Result.success()
            } else {
                Log.w(TAG, "API 返回错误: ${widgetData.error}")
                setLastError(prefsManager, widgetData.error ?: "未知错误")
                DeepSeekWidgetProvider.updateAllWidgets(applicationContext, widgetData)
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新失败", e)
            try {
                val prefsManager = PreferencesManager(applicationContext)
                setLastError(prefsManager, e.message ?: "未知错误")
                // 保留旧缓存数据，只更新错误状态
                val oldData = prefsManager.getCachedWidgetData()
                DeepSeekWidgetProvider.updateAllWidgets(
                    applicationContext,
                    oldData.copy(error = e.message ?: "未知错误")
                )
            } catch (_: Exception) {
                // 避免级联异常
            }
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun setLastError(prefsManager: PreferencesManager, error: String) {
        val oldData = prefsManager.getCachedWidgetData()
        prefsManager.cacheWidgetData(oldData.copy(error = error))
    }
}
