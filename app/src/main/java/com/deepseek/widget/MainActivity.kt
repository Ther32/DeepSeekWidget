package com.deepseek.widget

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.deepseek.widget.data.DeepSeekApiClient
import com.deepseek.widget.data.PreferencesManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var prefsManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefsManager = PreferencesManager(this)

        // 设置点击事件
        findViewById<android.widget.ImageButton>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<FloatingActionButton>(R.id.fab_refresh).setOnClickListener {
            refreshData()
        }

        // 加载缓存数据
        loadCachedData()
    }

    override fun onResume() {
        super.onResume()
        // 每次回到前台时刷新显示
        loadCachedData()
    }

    private fun loadCachedData() {
        lifecycleScope.launch {
            val data = WidgetCache(this@MainActivity).read()
            val apiKey = prefsManager.getApiKey()
            updateUI(data, apiKey)
        }
    }

    private fun refreshData() {
        lifecycleScope.launch {
            val apiKey = prefsManager.getApiKey()
            if (apiKey.isNullOrBlank()) {
                Toast.makeText(this@MainActivity, "请先在设置中配置 API Key", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                return@launch
            }

            Toast.makeText(this@MainActivity, "正在获取数据…", Toast.LENGTH_SHORT).show()

            // 显示加载中状态
            findViewById<android.widget.TextView>(R.id.tv_balance).text = "…"
            findViewById<android.widget.TextView>(R.id.tv_usage).text = "加载中"

            val result = withContext(Dispatchers.IO) {
                val client = DeepSeekApiClient(apiKey)
                client.fetchAllWidgetData()
            }

            // 缓存并更新 UI
            WidgetCache(this@MainActivity).write(result)
            updateUI(result, apiKey)

            if (result.error != null) {
                Toast.makeText(this@MainActivity, "获取失败: ${result.error}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "更新成功", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI(data: com.deepseek.widget.data.WidgetData, apiKey: String?) {
        val tvBalance = findViewById<android.widget.TextView>(R.id.tv_balance)
        val tvBalanceStatus = findViewById<android.widget.TextView>(R.id.tv_balance_status)
        val tvUsage = findViewById<android.widget.TextView>(R.id.tv_usage)
        val tvQuota = findViewById<android.widget.TextView>(R.id.tv_quota)
        val tvApiStatus = findViewById<android.widget.TextView>(R.id.tv_api_status)
        val tvUpdateTime = findViewById<android.widget.TextView>(R.id.tv_update_time)
        val progressUsage = findViewById<android.widget.ProgressBar>(R.id.progress_usage)

        if (data.error != null) {
            tvBalance.text = "⚠️"
            tvBalanceStatus.text = data.error.take(20)
            tvBalanceStatus.setTextColor(getColor(R.color.widget_offline))
            tvUsage.text = "获取失败"
            tvQuota.text = ""
            tvApiStatus.text = "● 错误"
            tvApiStatus.setTextColor(getColor(R.color.widget_offline))
            tvUpdateTime.text = "点击刷新重试"
            progressUsage.progress = 0
            return
        }

        if (data.balance == "--" && data.totalTokens == 0L && data.lastUpdateTime == 0L) {
            tvBalance.text = "---"
            tvBalanceStatus.text = "等待首次获取数据"
            tvBalanceStatus.setTextColor(getColor(R.color.widget_hint))
            tvUsage.text = "---"
            tvQuota.text = ""
            tvApiStatus.text = "● 未知"
            tvApiStatus.setTextColor(getColor(R.color.widget_hint))
            tvUpdateTime.text = "点击右下角刷新"
            progressUsage.progress = 0
            return
        }

        // 余额
        tvBalance.text = data.balance
        tvBalanceStatus.text = if (data.isAvailable) "● 正常" else "● 受限"
        tvBalanceStatus.setTextColor(
            if (data.isAvailable) getColor(R.color.widget_online)
            else getColor(R.color.widget_offline)
        )

        // 用量（-1 表示按量计费无配额数据）
        if (data.totalTokens < 0) {
            tvUsage.text = "按量计费"
            tvQuota.text = ""
        } else {
            tvUsage.text = formatTokens2(data.totalTokens)
            tvQuota.text = "已用"
        }
        progressUsage.progress = 0

        // API 状态
        tvApiStatus.text = if (apiKey != null) "● 已配置" else "● 未配置"
        tvApiStatus.setTextColor(
            if (apiKey != null) getColor(R.color.widget_online)
            else getColor(R.color.widget_offline)
        )

        // 更新时间
        tvUpdateTime.text = if (data.lastUpdateTime > 0) {
            val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
            sdf.format(Date(data.lastUpdateTime))
        } else {
            "--:--"
        }
    }

    private fun formatTokens2(tokens: Long): String = when {
        tokens >= 1_000_000_000 -> "${"%.2f".format(tokens / 1_000_000_000.0)}B"
        tokens >= 1_000_000 -> "${"%.2f".format(tokens / 1_000_000.0)}M"
        tokens >= 1_000 -> "${"%.2f".format(tokens / 1_000.0)}K"
        tokens > 0 -> "$tokens"
        else -> "---"
    }
}
