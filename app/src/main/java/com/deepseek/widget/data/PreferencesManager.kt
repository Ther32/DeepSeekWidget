package com.deepseek.widget.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** DataStore 单例扩展属性 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "deepseek_widget_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_UPDATE_INTERVAL = longPreferencesKey("update_interval_minutes")
        private val KEY_NOTIFICATION_ENABLED = booleanPreferencesKey("notification_enabled")
        // 缓存余额和用量数据，离线时可显示最后已知值
        private val KEY_CACHED_BALANCE = stringPreferencesKey("cached_balance")
        private val KEY_CACHED_CURRENCY = stringPreferencesKey("cached_currency")
        private val KEY_CACHED_IS_AVAILABLE = stringPreferencesKey("cached_is_available")
        private val KEY_CACHED_TOTAL_TOKENS = longPreferencesKey("cached_total_tokens")
        private val KEY_CACHED_TOTAL_COST = stringPreferencesKey("cached_total_cost")
        private val KEY_CACHED_UPDATE_TIME = longPreferencesKey("cached_update_time")
        private val KEY_CACHED_ERROR = stringPreferencesKey("cached_error")

        const val DEFAULT_INTERVAL_MINUTES = 60L
    }

    // ===== API Key =====

    /** 异步获取 API Key */
    suspend fun getApiKey(): String? {
        return context.dataStore.data.first()[KEY_API_KEY]
    }

    /** 保存 API Key */
    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_API_KEY] = key
        }
    }

    /** 观察 API Key 变化 */
    val apiKeyFlow = context.dataStore.data.map { it[KEY_API_KEY] }

    // ===== 更新间隔 =====

    /** 获取更新间隔（分钟） */
    suspend fun getUpdateInterval(): Long {
        return context.dataStore.data.first()[KEY_UPDATE_INTERVAL] ?: DEFAULT_INTERVAL_MINUTES
    }

    /** 保存更新间隔 */
    suspend fun saveUpdateInterval(minutes: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_UPDATE_INTERVAL] = minutes
        }
    }

    /** 观察间隔变化 */
    val updateIntervalFlow = context.dataStore.data.map {
        it[KEY_UPDATE_INTERVAL] ?: DEFAULT_INTERVAL_MINUTES
    }

    // ===== 缓存数据（供 Widget 离线显示） =====

    /** 获取缓存的 Widget 数据 */
    suspend fun getCachedWidgetData(): WidgetData {
        return context.dataStore.data.first().let { prefs ->
            WidgetData(
                balance = prefs[KEY_CACHED_BALANCE] ?: "--",
                currency = prefs[KEY_CACHED_CURRENCY] ?: "CNY",
                isAvailable = prefs[KEY_CACHED_IS_AVAILABLE]?.toBoolean() ?: false,
                totalTokens = prefs[KEY_CACHED_TOTAL_TOKENS] ?: 0,
                totalCost = prefs[KEY_CACHED_TOTAL_COST] ?: "--",
                lastUpdateTime = prefs[KEY_CACHED_UPDATE_TIME] ?: 0L,
                error = prefs[KEY_CACHED_ERROR]
            )
        }
    }

    /** 缓存 Widget 数据 */
    suspend fun cacheWidgetData(data: WidgetData) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CACHED_BALANCE] = data.balance
            prefs[KEY_CACHED_CURRENCY] = data.currency
            prefs[KEY_CACHED_IS_AVAILABLE] = data.isAvailable.toString()
            prefs[KEY_CACHED_TOTAL_TOKENS] = data.totalTokens
            prefs[KEY_CACHED_TOTAL_COST] = data.totalCost
            prefs[KEY_CACHED_UPDATE_TIME] = data.lastUpdateTime
            if (data.error != null) {
                prefs[KEY_CACHED_ERROR] = data.error
            } else {
                prefs.remove(KEY_CACHED_ERROR)
            }
        }
    }

    // ===== 通知栏开关 =====

    /** 获取通知栏开关状态（同步，因为从 UI 线程调用） */
    fun getNotificationEnabled(): Boolean {
        return kotlinx.coroutines.runBlocking {
            context.dataStore.data.first()[KEY_NOTIFICATION_ENABLED] ?: false
        }
    }

    /** 设置通知栏开关 */
    suspend fun setNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NOTIFICATION_ENABLED] = enabled
        }
    }

    /** 清空所有配置（重置） */
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
