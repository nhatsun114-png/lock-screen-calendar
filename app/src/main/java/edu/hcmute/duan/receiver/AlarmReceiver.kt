package edu.hcmute.duan.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import edu.hcmute.duan.MainActivity
import edu.hcmute.duan.R

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventTitle = intent.getStringExtra(EXTRA_EVENT_TITLE) ?: "Sự kiện lịch"
        val eventTimeText = intent.getStringExtra(EXTRA_EVENT_TIME_TEXT) ?: "Sắp diễn ra"
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, System.currentTimeMillis().toInt())

        showNotification(context, notificationId, eventTitle, eventTimeText)
    }

    private fun showNotification(
        context: Context,
        notificationId: Int,
        title: String,
        timeText: String
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = "calendar_alarm_exact_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val channel = NotificationChannel(
                channelId,
                "Báo thức & Nhắc hẹn Lịch",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Kênh phát thông báo nhắc hẹn chính xác theo thời gian đã đặt"
                enableVibration(true)
                setSound(soundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🔔 Nhắc hẹn: $title")
            .setContentText("Sự kiện diễn ra lúc $timeText. Bấm để xem chi tiết!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    companion object {
        const val EXTRA_EVENT_TITLE = "extra_event_title"
        const val EXTRA_EVENT_TIME_TEXT = "extra_event_time_text"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
