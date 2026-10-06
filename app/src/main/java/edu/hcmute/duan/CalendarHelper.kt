package edu.hcmute.duan

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import java.time.Instant
import java.time.ZoneId
import java.time.YearMonth

object CalendarHelper {

    fun getAvailableCalendars(context: Context): List<CalendarModel> {
        val calendars = mutableListOf<CalendarModel>()
        val uri = CalendarContract.Calendars.CONTENT_URI
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
        )
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID)
            val accountNameIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
            val displayNameIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIdx)
                val accountName = cursor.getString(accountNameIdx) ?: "Unknown"
                val displayName = cursor.getString(displayNameIdx) ?: "Unknown"
                calendars.add(CalendarModel(id, accountName, displayName))
            }
        }
        return calendars
    }

    fun getEventsForCurrentMonth(context: Context, selectedCalIds: Set<String>): Map<Int, List<EventModel>> {
        val eventsMap = mutableMapOf<Int, MutableList<EventModel>>()
        if (selectedCalIds.isEmpty()) return eventsMap

        val yearMonth = YearMonth.now()
        val zoneId = ZoneId.systemDefault()
        
        val startMillis = yearMonth.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMillis = yearMonth.atEndOfMonth().atTime(23, 59, 59).atZone(zoneId).toInstant().toEpochMilli()

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startMillis)
        ContentUris.appendId(builder, endMillis)
        val uri = builder.build()

        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.EVENT_COLOR,
            CalendarContract.Instances.CALENDAR_ID
        )

        val placeholders = selectedCalIds.joinToString(",") { "?" }
        val selection = "${CalendarContract.Instances.CALENDAR_ID} IN ($placeholders)"
        val selectionArgs = selectedCalIds.toTypedArray()

        val cursor: Cursor? = context.contentResolver.query(uri, projection, selection, selectionArgs, "${CalendarContract.Instances.BEGIN} ASC")

        cursor?.use {
            val titleIdx = it.getColumnIndex(CalendarContract.Instances.TITLE)
            val beginIdx = it.getColumnIndex(CalendarContract.Instances.BEGIN)
            val endIdx = it.getColumnIndex(CalendarContract.Instances.END)
            val allDayIdx = it.getColumnIndex(CalendarContract.Instances.ALL_DAY)
            val displayColorIdx = it.getColumnIndex(CalendarContract.Instances.DISPLAY_COLOR)
            val eventColorIdx = it.getColumnIndex(CalendarContract.Instances.EVENT_COLOR)
            val calIdIdx = it.getColumnIndex(CalendarContract.Instances.CALENDAR_ID)

            while (it.moveToNext()) {
                val title = it.getString(titleIdx) ?: "No Title"
                val begin = it.getLong(beginIdx)
                val end = it.getLong(endIdx)
                val allDay = it.getInt(allDayIdx) == 1
                val calId = it.getLong(calIdIdx)
                
                var color = 0
                if (displayColorIdx != -1) color = it.getInt(displayColorIdx)
                if (color == 0 && eventColorIdx != -1) color = it.getInt(eventColorIdx)
                if (color == 0) color = 0xFF00BCD4.toInt() // Default Cyan

                val event = EventModel(title, begin, end, allDay, color, calId)
                
                val eventZdt = Instant.ofEpochMilli(begin).atZone(zoneId)
                if (eventZdt.year == yearMonth.year && eventZdt.month == yearMonth.month) {
                    val day = eventZdt.dayOfMonth
                    if (!eventsMap.containsKey(day)) {
                        eventsMap[day] = mutableListOf()
                    }
                    eventsMap[day]?.add(event)
                }
            }
        }
        return eventsMap
    }
}
