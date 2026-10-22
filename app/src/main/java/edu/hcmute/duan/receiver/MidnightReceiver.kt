package edu.hcmute.duan.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import edu.hcmute.duan.CalendarUpdateWorker

class MidnightReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_DATE_CHANGED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == ACTION_MIDNIGHT_UPDATE) {

            val appContext = context.applicationContext
            try {
                val updateWork = OneTimeWorkRequestBuilder<CalendarUpdateWorker>().build()
                WorkManager.getInstance(appContext).enqueue(updateWork)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                AlarmScheduler(appContext).scheduleMidnightAlarm()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        const val ACTION_MIDNIGHT_UPDATE = "edu.hcmute.duan.ACTION_MIDNIGHT_UPDATE"
    }
}
