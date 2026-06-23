package com.deepseek.widget

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.deepseek.widget.data.DeepSeekApiClient
import com.deepseek.widget.data.PreferencesManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefsManager: PreferencesManager
    private lateinit var inputApiKey: TextInputEditText
    private lateinit var inputInterval: MaterialAutoCompleteTextView
    private lateinit var textStatus: android.widget.TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefsManager = PreferencesManager(this)
        inputApiKey = findViewById(R.id.input_api_key)
        inputInterval = findViewById(R.id.input_interval)
        textStatus = findViewById(R.id.text_status)

        findViewById<android.widget.ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }

        findViewById<MaterialButton>(R.id.btn_test).setOnClickListener {
            testConnection()
        }

        findViewById<MaterialButton>(R.id.btn_save).setOnClickListener {
            saveSettings()
        }

        setupIntervalDropdown()
        loadExistingConfig()
    }

    private fun setupIntervalDropdown() {
        val labels = resources.getStringArray(R.array.interval_labels)
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, labels)
        inputInterval.setAdapter(adapter)
        inputInterval.setText(labels[2], false) // 默认 1 小时
    }

    private fun loadExistingConfig() {
        lifecycleScope.launch {
            val apiKey = prefsManager.getApiKey()
            if (!apiKey.isNullOrBlank()) {
                inputApiKey.setText(apiKey)
            }

            val intervalMinutes = prefsManager.getUpdateInterval()
            val labels = resources.getStringArray(R.array.interval_labels)
            val values = resources.getStringArray(R.array.interval_values)
            val index = values.indexOf(intervalMinutes.toString())
            if (index >= 0) {
                inputInterval.setText(labels[index], false)
            }
        }
    }

    private fun testConnection() {
        val apiKey = inputApiKey.text?.toString()?.trim() ?: ""
        if (apiKey.isBlank()) {
            showStatus("请先输入 API Key", isError = true)
            return
        }

        showStatus("⏳ 连接测试中…", isError = false)
        textStatus.visibility = android.view.View.VISIBLE

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val client = DeepSeekApiClient(apiKey)
                client.getBalance()
            }
            result.fold(
                onSuccess = { resp ->
                    val info = resp.balanceInfos.firstOrNull()
                    val balance = info?.totalBalance ?: "?"
                    showStatus("✅ 连接成功! 余额: ¥$balance", isError = false)
                },
                onFailure = { error ->
                    showStatus("❌ 连接失败: ${error.message?.take(40) ?: "未知"}", isError = true)
                }
            )
        }
    }

    private fun saveSettings() {
        val apiKey = inputApiKey.text?.toString()?.trim() ?: ""
        if (apiKey.isBlank()) {
            showStatus("请先输入 API Key", isError = true)
            Toast.makeText(this, "请先输入 API Key", Toast.LENGTH_SHORT).show()
            return
        }

        val labels = resources.getStringArray(R.array.interval_labels)
        val values = resources.getStringArray(R.array.interval_values)
        val selectedLabel = inputInterval.text.toString()
        val index = labels.indexOf(selectedLabel)
        val intervalMinutes = if (index >= 0) {
            values[index].toLong()
        } else {
            PreferencesManager.DEFAULT_INTERVAL_MINUTES
        }

        showStatus("⏳ 保存中…", isError = false)

        lifecycleScope.launch {
            prefsManager.saveApiKey(apiKey)
            prefsManager.saveUpdateInterval(intervalMinutes)
            WidgetUpdateWorker.schedulePeriodicUpdate(this@SettingsActivity, intervalMinutes)
            WidgetUpdateWorker.triggerImmediateUpdate(this@SettingsActivity)

            Toast.makeText(this@SettingsActivity, "设置已保存", Toast.LENGTH_SHORT).show()
            showStatus("✅ 已保存", isError = false)
            finish()
        }
    }

    private fun showStatus(message: String, isError: Boolean) {
        textStatus.visibility = android.view.View.VISIBLE
        textStatus.text = message
        textStatus.setTextColor(
            if (isError) getColor(R.color.widget_offline)
            else getColor(android.R.color.holo_green_dark)
        )
    }
}
