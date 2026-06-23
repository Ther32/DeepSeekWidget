package com.deepseek.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.deepseek.widget.data.DeepSeekApiClient
import com.deepseek.widget.data.PreferencesManager
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DeepSeek 监控组件配置界面
 *
 * 双重用途：
 * 1. 从启动器打开 → 修改设置（启动器入口）
 * 2. 从桌面添加 Widget → 配置流程（自动识别 widgetId）
 */
class DeepSeekWidgetConfigActivity : AppCompatActivity() {

    // 配置参数
    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var prefsManager: PreferencesManager

    // UI 组件
    private lateinit var inputApiKey: TextInputEditText
    private lateinit var inputInterval: MaterialAutoCompleteTextView
    private lateinit var textStatus: android.widget.TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        prefsManager = PreferencesManager(this)

        // 检测是否是 Widget 配置流程
        widgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // 如果是从 Widget 配置进入但缺少 widgetId，需要设为有效值
        // 否则系统不会添加 Widget
        if (intent?.action == "android.appwidget.action.APPWIDGET_CONFIGURE" &&
            widgetId == AppWidgetManager.INVALID_APPWIDGET_ID
        ) {
            // 缺少必要参数，通知系统失败
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        // 初始化 UI
        initViews()

        // 加载已有配置
        loadExistingConfig()
    }

    private fun initViews() {
        inputApiKey = findViewById(R.id.input_api_key)
        inputInterval = findViewById(R.id.input_interval)
        textStatus = findViewById(R.id.text_status)

        val btnTest = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_test)
        val btnSave = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_save)

        // 初始化更新间隔下拉选择器
        setupIntervalDropdown()

        // 测试连接按钮
        btnTest.setOnClickListener { testConnection() }

        // 保存按钮
        btnSave.setOnClickListener { saveAndFinish() }
    }

    private fun setupIntervalDropdown() {
        val labels = resources.getStringArray(R.array.interval_labels)
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, labels)
        inputInterval.setAdapter(adapter)
        inputInterval.setText(labels[2], false) // 默认选中 "1 小时" (索引2)
    }

    /**
     * 加载已保存的配置（如果有的话）
     */
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

    /**
     * 测试 API 连接
     */
    private fun testConnection() {
        val apiKey = inputApiKey.text?.toString()?.trim() ?: ""
        if (apiKey.isBlank()) {
            showStatus(getString(R.string.no_api_key), isError = true)
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
                onSuccess = { balanceResp ->
                    val info = balanceResp.balanceInfos.firstOrNull()
                    val balance = info?.totalBalance ?: "?"
                    val avail = if (balanceResp.isAvailable) "可用" else "受限"
                    showStatus("✅ 连接成功! 余额: ¥$balance ($avail)", isError = false)
                },
                onFailure = { error ->
                    showStatus(
                        getString(R.string.test_failed, error.message?.take(40) ?: "未知错误"),
                        isError = true
                    )
                }
            )
        }
    }

    /**
     * 保存并完成配置
     */
    private fun saveAndFinish() {
        val apiKey = inputApiKey.text?.toString()?.trim() ?: ""
        if (apiKey.isBlank()) {
            showStatus(getString(R.string.no_api_key), isError = true)
            Toast.makeText(this, getString(R.string.no_api_key), Toast.LENGTH_SHORT).show()
            return
        }

        // 获取选中间隔
        val labels = resources.getStringArray(R.array.interval_labels)
        val values = resources.getStringArray(R.array.interval_values)
        val selectedLabel = inputInterval.text.toString()
        val index = labels.indexOf(selectedLabel)
        val intervalMinutes = if (index >= 0) {
            values[index].toLong()
        } else {
            PreferencesManager.DEFAULT_INTERVAL_MINUTES
        }

        showStatus("⏳ 保存设置中…", isError = false)

        lifecycleScope.launch {
            // 1. 保存配置
            prefsManager.saveApiKey(apiKey)
            prefsManager.saveUpdateInterval(intervalMinutes)

            // 2. 调度定时更新
            WidgetUpdateWorker.schedulePeriodicUpdate(this@DeepSeekWidgetConfigActivity, intervalMinutes)

            // 3. 触发一次立即更新
            WidgetUpdateWorker.triggerImmediateUpdate(this@DeepSeekWidgetConfigActivity)

            // 4. 如果是 Widget 配置流程，设置结果
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val resultIntent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                setResult(Activity.RESULT_OK, resultIntent)
            } else {
                // 从启动器打开的，设置取消结果
                setResult(Activity.RESULT_OK)
                Toast.makeText(
                    this@DeepSeekWidgetConfigActivity,
                    getString(R.string.saved_success),
                    Toast.LENGTH_SHORT
                ).show()
            }

            finish()
        }
    }

    /**
     * 显示状态信息
     */
    private fun showStatus(message: String, isError: Boolean) {
        textStatus.visibility = android.view.View.VISIBLE
        textStatus.text = message
        textStatus.setTextColor(
            if (isError) getColor(R.color.widget_offline)
            else getColor(R.color.deepseek_primary)
        )
    }

    /**
     * 处理系统返回按钮：如果是在 Widget 配置中，需要返回取消结果
     */
    override fun onBackPressed() {
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(Activity.RESULT_CANCELED)
        }
        super.onBackPressed()
    }
}
