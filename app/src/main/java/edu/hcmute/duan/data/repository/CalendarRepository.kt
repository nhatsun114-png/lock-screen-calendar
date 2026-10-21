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
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

class CalendarRepository(private val context: Context) {

    fun getAvailableCalendars(): List<CalendarModel> {
        return CalendarHelper.getAvailableCalendars(context)
    }

    fun getEventsForCurrentMonth(selectedCalIds: Set<String>): Map<Int, List<EventModel>> {
        val systemEventsMap = CalendarHelper.getEventsForCurrentMonth(context, selectedCalIds).toMutableMap()
        val localEvents = loadLocalEvents()

        val zoneId = ZoneId.systemDefault()
        val currentYearMonth = java.time.YearMonth.now()

        for (evt in localEvents) {
            val zdt = Instant.ofEpochMilli(evt.begin).atZone(zoneId)
            if (zdt.year == currentYearMonth.year && zdt.month == currentYearMonth.month) {
                val day = zdt.dayOfMonth
                val list = systemEventsMap[day]?.toMutableList() ?: mutableListOf()
                list.add(evt)
                systemEventsMap[day] = list
            }
        }

        return systemEventsMap
    }

    fun addEventToCalendar(
        title: String,
        beginMillis: Long,
        endMillis: Long,
        allDay: Boolean,
        calendarId: Long,
        color: Int = 0xFFAB47BC.toInt(),
        location: String? = null,
        notes: String? = null
    ): Boolean {
        val newEvent = EventModel(
            title = title,
            begin = beginMillis,
            end = endMillis,
            allDay = allDay,
            color = color,
            calendarId = calendarId
        )

        // Always save locally so event instantly appears on calendar timeline
        saveLocalEvent(newEvent)

        try {
            val values = ContentValues().apply {
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DTSTART, beginMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.EVENT_COLOR, color)
                if (!location.isNullOrBlank()) put(CalendarContract.Events.EVENT_LOCATION, location)
                if (!notes.isNullOrBlank()) put(CalendarContract.Events.DESCRIPTION, notes)
            }
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return true
    }

    private fun saveLocalEvent(event: EventModel) {
        try {
            val existing = loadLocalEvents().toMutableList()
            existing.add(event)

            val prefs = context.getSharedPreferences("local_user_events_prefs", Context.MODE_PRIVATE)
            val jsonArray = JSONArray()
            for (evt in existing) {
                val obj = JSONObject().apply {
                    put("title", evt.title)
                    put("begin", evt.begin)
                    put("end", evt.end)
                    put("allDay", evt.allDay)
                    put("color", evt.color)
                    put("calendarId", evt.calendarId)
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString("local_events_json", jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadLocalEvents(): List<EventModel> {
        val list = mutableListOf<EventModel>()
        try {
            val prefs = context.getSharedPreferences("local_user_events_prefs", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("local_events_json", null) ?: return emptyList()
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    EventModel(
                        title = obj.getString("title"),
                        begin = obj.getLong("begin"),
                        end = obj.getLong("end"),
                        allDay = obj.getBoolean("allDay"),
                        color = obj.getInt("color"),
                        calendarId = obj.getLong("calendarId")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
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
