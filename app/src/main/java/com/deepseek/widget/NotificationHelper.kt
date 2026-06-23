package com.deepseek.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * 通知栏管理器 — 在通知栏显示 DeepSeek 余额和用量
 */
object NotificationHelper {

    private const val CHANNEL_ID = "deepseek_monitor"
    private const val NOTIFICATION_ID = 1001

    fun createChannel(context: Context) {
        val name = context.getString(R.string.notif_channel_name)
        val desc = context.getString(R.string.notif_channel_desc)
        val importance = NotificationManager.IMPORTANCE_LOW // 低优先级，不弹声音
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = desc
            setShowBadge(false)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun showNotification(context: Context) {
        createChannel(context)

        val data = WidgetCache(context).read()
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = context.getString(R.string.notif_title)
        val content = if (data.error != null) {
            context.getString(R.string.notif_error, data.error.take(20))
        } else if (data.balance == "--") {
            context.getString(R.string.notif_loading)
        } else {
            "${context.getString(R.string.notif_balance, data.balance)}  |  ${context.getString(R.string.notif_usage, formatTokens3(data.totalTokens))}"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_graphic)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(content)
            )
            .setContentIntent(pendingIntent)
            .setOngoing(true) // 不可滑动删除
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // 没有通知权限
        }
    }

    fun cancelNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun formatTokens3(tokens: Long): String = when {
        tokens >= 1_000_000_000 -> "${"%.2f".format(tokens / 1_000_000_000.0)}B"
        tokens >= 1_000_000 -> "${"%.2f".format(tokens / 1_000_000.0)}M"
        tokens >= 1_000 -> "${"%.2f".format(tokens / 1_000.0)}K"
        tokens > 0 -> "$tokens"
        else -> "0"
    }
}
