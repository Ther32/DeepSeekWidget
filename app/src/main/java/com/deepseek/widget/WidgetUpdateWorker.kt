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
import java.util.concurrent.TimeUnit

class WidgetUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DeepSeekWidget"
        private const val PERIODIC_WORK_NAME = "deepseek_periodic_update"
        private const val IMMEDIATE_WORK_NAME = "deepseek_immediate_update"

        fun schedulePeriodicUpdate(context: Context, intervalMinutes: Long) {
            val workRequest = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .addTag("deepseek_periodic")
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
            Log.d(TAG, "定时更新已调度，间隔: ${intervalMinutes}分钟")
        }

        fun cancelPeriodicUpdate(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }

        fun triggerImmediateUpdate(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .addTag("deepseek_immediate")
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
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
                WidgetCache(applicationContext).write(
                    com.deepseek.widget.data.WidgetData(error = "未配置 API Key")
                )
                return Result.failure()
            }

            // 2. 调用 API
            val client = DeepSeekApiClient(apiKey)
            val data = client.fetchAllWidgetData()

            // 3. 写入缓存（SharedPreferences）
            WidgetCache(applicationContext).write(data)

            // 4. 更新 Widget（如果系统支持）
            DeepSeekWidgetProvider.updateAllWidgets(applicationContext, data)

            if (data.error == null) {
                Log.d(TAG, "更新成功: 余额=¥${data.balance}, 用量=${data.totalTokens}")
                Result.success()
            } else {
                Log.w(TAG, "API 错误: ${data.error}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新失败", e)
            WidgetCache(applicationContext).write(
                com.deepseek.widget.data.WidgetData(error = e.message ?: "未知错误")
            )
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
