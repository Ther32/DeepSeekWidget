package com.deepseek.widget.data

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * DeepSeek API 客户端
 *
 * 用于查询用户的余额和月度用量。
 * API 文档参考: https://platform.deepseek.com/api-docs
 */
class DeepSeekApiClient(private val apiKey: String) {

    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val BASE_URL = "https://api.deepseek.com"

        /** 格式化日期用于 API 参数 */
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }

    /**
     * 查询账户余额
     * GET https://api.deepseek.com/balance
     */
    fun getBalance(): Result<BalanceResponse> {
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/balance")
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                    return Result.failure(
                        ApiException(response.code, body ?: "Empty response")
                    )
                }
                val balanceResponse = gson.fromJson(body, BalanceResponse::class.java)
                Result.success(balanceResponse)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 查询指定日期范围的用量
     * GET https://api.deepseek.com/user/usage
     * @param startDate 开始日期 yyyy-MM-dd
     * @param endDate 结束日期 yyyy-MM-dd
     */
    fun getUsage(startDate: String, endDate: String): Result<UsageResponse> {
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/user/usage?start_date=$startDate&end_date=$endDate")
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                    return Result.failure(
                        ApiException(response.code, body ?: "Empty response")
                    )
                }
                // DeepSeek 某些端点可能返回空 data，兼容处理
                val usageResponse = gson.fromJson(body, UsageResponse::class.java)
                Result.success(usageResponse)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 查询当月的用量
     */
    fun getCurrentMonthUsage(): Result<UsageResponse> {
        val now = Date()
        val endDate = dateFormat.format(now)

        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
        val startDate = dateFormat.format(calendar.time)

        return getUsage(startDate, endDate)
    }

    /**
     * 一次调用完成所有数据获取（余额 + 月度用量）
     * @return WidgetData 包含余额和用量信息，error 字段在失败时设置
     */
    fun fetchAllWidgetData(): WidgetData {
        // 获取余额
        val (balanceStr, currency, isAvailable) = getBalance().fold(
            onSuccess = { resp ->
                val info = resp.balanceInfos.firstOrNull()
                Triple(
                    info?.totalBalance ?: "--",
                    info?.currency ?: "CNY",
                    resp.isAvailable
                )
            },
            onFailure = { error ->
                return WidgetData(error = "余额查询失败: ${error.message}")
            }
        )

        // 获取月度用量
        val (totalTokens, totalCost) = getCurrentMonthUsage().fold(
            onSuccess = { resp ->
                Pair(
                    resp.total?.totalTokens ?: 0,
                    resp.total?.totalCost ?: "--"
                )
            },
            onFailure = { error ->
                return WidgetData(error = "用量查询失败: ${error.message}")
            }
        )

        return WidgetData(
            balance = balanceStr,
            currency = currency,
            isAvailable = isAvailable,
            totalTokens = totalTokens,
            totalCost = totalCost,
            lastUpdateTime = System.currentTimeMillis()
        )
    }
}

/** API 响应异常 */
class ApiException(val code: Int, message: String) : IOException("HTTP $code: $message")
