package edu.hcmute.duan.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import edu.hcmute.duan.data.repository.CalendarRepository

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            try {
                val calendarRepository = CalendarRepository(context.applicationContext)
                calendarRepository.scheduleDailyCalendarUpdate()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
