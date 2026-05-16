package com.webwatcher.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.webwatcher.R
import com.webwatcher.ui.detail.DetailActivity

object NotificationHelper {

    const val CHANNEL_ID = "web_watcher_channel"
    private const val CHANNEL_NAME = "WebWatcher 更新通知"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "監視対象URLの更新を通知します"
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    fun showChangeNotification(
        context: Context,
        targetId: Long,
        targetTitle: String,
        historyId: Long,
        notifId: Int
    ) {
        val intent = Intent(context, DetailActivity::class.java).apply {
            putExtra(DetailActivity.EXTRA_TARGET_ID, targetId)
            putExtra(DetailActivity.EXTRA_HISTORY_ID, historyId)
            putExtra(DetailActivity.EXTRA_SHOW_DIFF, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🔔 ページが更新されました")
            .setContentText(targetTitle)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("「$targetTitle」に変更が検出されました。タップして変更箇所を確認してください。"))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId, notification)
    }
}
