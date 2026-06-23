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
 * DeepSeek API 用量响应
 * GET https://api.deepseek.com/user/usage?start_date=...&end_date=...
 */
data class UsageResponse(
    val data: List<UsageData>,
    val total: UsageTotal?
)

data class UsageData(
    val date: String,
    @SerializedName("total_input_tokens")
    val totalInputTokens: Long,
    @SerializedName("total_output_tokens")
    val totalOutputTokens: Long,
    @SerializedName("total_tokens")
    val totalTokens: Long,
    @SerializedName("total_cost")
    val totalCost: String,
    val currency: String
)

data class UsageTotal(
    @SerializedName("total_input_tokens")
    val totalInputTokens: Long,
    @SerializedName("total_output_tokens")
    val totalOutputTokens: Long,
    @SerializedName("total_tokens")
    val totalTokens: Long,
    @SerializedName("total_cost")
    val totalCost: String
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
