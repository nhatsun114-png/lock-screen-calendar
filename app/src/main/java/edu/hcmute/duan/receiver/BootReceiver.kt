package edu.hcmute.duan.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import edu.hcmute.duan.data.local.AppDatabase
import edu.hcmute.duan.data.repository.CalendarRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            val appContext = context.applicationContext

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 1. Reschedule WorkManager & Midnight Alarm
                    val calendarRepository = CalendarRepository(appContext)
                    calendarRepository.scheduleDailyCalendarUpdate()

                    val alarmScheduler = AlarmScheduler(appContext)
                    alarmScheduler.scheduleMidnightAlarm()
                    val now = System.currentTimeMillis()
                    val sdf = SimpleDateFormat("HH:mm, dd/MM", Locale.getDefault())

                    // Reschedule Reminders
                    val reminderDao = AppDatabase.getDatabase(appContext).reminderDao()
                    val reminders = reminderDao.getAllReminders()
                    for (reminder in reminders) {
                        if (!reminder.isCompleted && reminder.timeMillis > now) {
                            val timeStr = sdf.format(Date(reminder.timeMillis))
                            val safeId = AlarmScheduler.generateUniqueId(reminder.timeMillis, reminder.title)
                            alarmScheduler.scheduleExactAlarm(
                                notificationId = safeId,
                                eventTitle = reminder.title,
                                eventTimeText = timeStr,
                                triggerAtMillis = reminder.timeMillis
                            )
                        }
                    }

                    // Reschedule Local Events
                    val localEventDao = AppDatabase.getDatabase(appContext).localEventDao()
                    val localEvents = localEventDao.getAllEvents()
                    for (event in localEvents) {
                        if (event.beginMillis > now) {
                            val timeStr = sdf.format(Date(event.beginMillis))
                            val safeId = AlarmScheduler.generateUniqueId(event.beginMillis, event.title)
                            alarmScheduler.scheduleExactAlarm(
                                notificationId = safeId,
                                eventTitle = event.title,
                                eventTimeText = timeStr,
                                triggerAtMillis = event.beginMillis
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
