package com.deepseek.widget.data

import com.google.gson.annotations.SerializedName

/**
 * DeepSeek API 余额响应
 * GET https://api.deepseek.com/balance
 */
data class BalanceResponse(
    @SerializedName("balance_infos")
    val balanceInfos: List<BalanceInfo>,
    @SerializedName("is_available")
    val isAvailable: Boolean
)

data class BalanceInfo(
    @SerializedName("total_balance")
    val totalBalance: String,
    @SerializedName("topped_up_balance")
    val toppedUpBalance: String,
    @SerializedName("granted_balance")
    val grantedBalance: String,
    val currency: String
)

/**
 * DeepSeek API 用量/配额响应
 * GET https://api.deepseek.com/v1/usage
 * 返回当前配额周期内的用量统计
 */
data class UsageResponse(
    @SerializedName("used_tokens")
    val usedTokens: Long = 0,
    @SerializedName("total_tokens")
    val totalTokens: Long = 0,
    @SerializedName("reset_at")
    val resetAt: String? = null
)

/**
 * 组件显示用的缓存数据
 */
data class WidgetData(
    val balance: String = "--",
    val currency: String = "CNY",
    val isAvailable: Boolean = false,
    val totalTokens: Long = 0,
    val totalCost: String = "--",
    val lastUpdateTime: Long = 0L,
    val error: String? = null
) {
    companion object {
        val EMPTY = WidgetData()
    }
}
