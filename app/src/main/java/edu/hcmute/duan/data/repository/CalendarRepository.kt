package edu.hcmute.duan.data.repository

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import edu.hcmute.duan.CalendarHelper
import edu.hcmute.duan.CalendarUpdateWorker
import edu.hcmute.duan.data.model.CalendarModel
import edu.hcmute.duan.data.model.EventModel
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class CalendarRepository(private val context: Context) {

    fun getAvailableCalendars(): List<CalendarModel> {
        return CalendarHelper.getAvailableCalendars(context)
    }

    fun getEventsForCurrentMonth(selectedCalIds: Set<String>): Map<Int, List<EventModel>> {
        return CalendarHelper.getEventsForCurrentMonth(context, selectedCalIds)
    }

    fun addEventToCalendar(
        title: String,
        beginMillis: Long,
        endMillis: Long,
        allDay: Boolean,
        calendarId: Long,
        location: String?,
        notes: String?
    ): Boolean {
        return try {
            val values = ContentValues().apply {
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DTSTART, beginMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                if (!location.isNullOrBlank()) put(CalendarContract.Events.EVENT_LOCATION, location)
                if (!notes.isNullOrBlank()) put(CalendarContract.Events.DESCRIPTION, notes)
            }
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            uri != null
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun scheduleDailyCalendarUpdate() {
        val currentDate = Calendar.getInstance()
        val dueDate = Calendar.getInstance()

        dueDate.set(Calendar.HOUR_OF_DAY, 0)
        dueDate.set(Calendar.MINUTE, 5)
        dueDate.set(Calendar.SECOND, 0)

        if (dueDate.before(currentDate)) {
            dueDate.add(Calendar.HOUR_OF_DAY, 24)
        }

        val timeDiff = dueDate.timeInMillis - currentDate.timeInMillis
        val updateRequest = PeriodicWorkRequestBuilder<CalendarUpdateWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "DailyCalendarUpdate",
            ExistingPeriodicWorkPolicy.UPDATE,
            updateRequest
        )
    }
}
