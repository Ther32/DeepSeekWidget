package com.deepseek.widget.data

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
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
    }

    /**
     * 查询账户余额
     * GET https://api.deepseek.com/user/balance
     */
    fun getBalance(): Result<BalanceResponse> {
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/user/balance")
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
     * 查询 API 用量/配额
     * GET https://api.deepseek.com/v1/usage
     * 返回当前配额周期内的已用 tokens 和总配额
     */
    fun getUsage(): Result<UsageResponse> {
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/v1/usage")
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
                val usageResponse = gson.fromJson(body, UsageResponse::class.java)
                Result.success(usageResponse)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
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

        // 获取用量/配额（仅配额制用户有数据，按量付费用户会失败或返回0）
        val usedTokens = getUsage().fold(
            onSuccess = { resp -> resp.usedTokens },
            onFailure = { -1L } // -1 表示"不可用/按量计费"
        )

        return WidgetData(
            balance = balanceStr,
            currency = currency,
            isAvailable = isAvailable,
            totalTokens = usedTokens,
            totalCost = "--",
            lastUpdateTime = System.currentTimeMillis()
        )
    }
}

/** API 响应异常 */
class ApiException(val code: Int, message: String) : IOException("HTTP $code: $message")
