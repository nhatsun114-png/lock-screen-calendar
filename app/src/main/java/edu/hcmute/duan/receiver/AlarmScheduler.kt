package edu.hcmute.duan.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar
import kotlin.math.abs

class AlarmScheduler(private val context: Context) {

    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleExactAlarm(
        notificationId: Int,
        eventTitle: String,
        eventTimeText: String,
        triggerAtMillis: Long
    ): Boolean {
        return try {
            if (triggerAtMillis <= System.currentTimeMillis()) {
                return false
            }

            val safeId = if (notificationId == 0) generateUniqueId(triggerAtMillis, eventTitle) else notificationId

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra(AlarmReceiver.EXTRA_EVENT_TITLE, eventTitle)
                putExtra(AlarmReceiver.EXTRA_EVENT_TIME_TEXT, eventTimeText)
                putExtra(AlarmReceiver.EXTRA_NOTIFICATION_ID, safeId)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                safeId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            var canUseExact = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                canUseExact = alarmManager.canScheduleExactAlarms()
            }

            if (canUseExact) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setExact(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    }
                } catch (se: SecurityException) {
                    se.printStackTrace()
                    // Fallback to non-exact alarm if exact alarm permission is missing/revoked
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    } else {
                        alarmManager.set(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    }
                }
            } else {
                // Non-exact alarm fallback on Android 12+ if canScheduleExactAlarms is false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun cancelAlarm(notificationId: Int) {
        try {
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun scheduleMidnightAlarm() {
        try {
            val midnight = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 1)
                set(Calendar.MILLISECOND, 0)
            }

            val intent = Intent(context, MidnightReceiver::class.java).apply {
                action = MidnightReceiver.ACTION_MIDNIGHT_UPDATE
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                999999,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    midnight.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    midnight.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        fun generateUniqueId(triggerAtMillis: Long, title: String): Int {
            val raw = (triggerAtMillis xor (triggerAtMillis ushr 16)).toInt() + title.hashCode()
            return abs(raw % 1_000_000_000)
        }
    }
}
