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
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
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
     * 查询本月计费用量（OpenAI 兼容接口）
     * GET https://api.deepseek.com/dashboard/billing/usage?start_date=...&end_date=...
     * 返回本月总消费金额和每日明细
     */
    fun getCurrentMonthBilling(): Result<BillingUsageResponse> {
        val now = Date()
        val endDate = dateFormat.format(now)
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        val startDate = dateFormat.format(cal.time)

        return try {
            val request = Request.Builder()
                .url("$BASE_URL/dashboard/billing/usage?start_date=$startDate&end_date=$endDate")
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
                val billingResp = gson.fromJson(body, BillingUsageResponse::class.java)
                Result.success(billingResp)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 一次调用完成所有数据获取（余额 + 月度用量 + 费用）
     * @return WidgetData 包含余额和用量信息
     *
     * 获取策略：
     * 1. 先获取余额（必需）
     * 2. 尝试 OpenAI 兼容的 billing 接口获取本月费用
     * 3. 尝试 /v1/usage 获取 token 配额（仅配额制用户）
     */
    fun fetchAllWidgetData(): WidgetData {
        // 1. 获取余额（必需，失败则整体失败）
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

        // 2. 获取本月计费用量（OpenAI 兼容 billing 接口）
        var totalCost = "--"
        val billingResult = getCurrentMonthBilling()
        if (billingResult.isSuccess) {
            val usage = billingResult.getOrNull()?.totalUsage ?: 0.0
            if (usage > 0) {
                // totalUsage 可能是元或分，根据实际返回调整
                totalCost = if (usage > 100) {
                    // 可能是分，转换为元
                    "%.2f".format(usage / 100)
                } else {
                    "%.2f".format(usage)
                }
            }
        }

        // 3. 尝试获取 token 用量（仅配额制用户有数据）
        val usedTokens = getUsage().fold(
            onSuccess = { resp -> resp.usedTokens },
            onFailure = { -1L }
        )

        return WidgetData(
            balance = balanceStr,
            currency = currency,
            isAvailable = isAvailable,
            totalTokens = usedTokens,
            totalCost = totalCost,
            lastUpdateTime = System.currentTimeMillis()
        )
    }
}

/** API 响应异常 */
class ApiException(val code: Int, message: String) : IOException("HTTP $code: $message")
