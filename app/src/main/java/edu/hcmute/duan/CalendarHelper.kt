package edu.hcmute.duan

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import edu.hcmute.duan.data.model.CalendarModel
import edu.hcmute.duan.data.model.EventModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth

object CalendarHelper {

    fun getAvailableCalendars(context: Context): List<CalendarModel> {
        val calendars = mutableListOf<CalendarModel>()
        try {
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
                    val id = if (idIdx != -1) cursor.getLong(idIdx) else 1L
                    val accountName = if (accountNameIdx != -1) cursor.getString(accountNameIdx) ?: "Lịch thiết bị" else "Lịch thiết bị"
                    val displayName = if (displayNameIdx != -1) cursor.getString(displayNameIdx) ?: "Cá nhân" else "Cá nhân"
                    calendars.add(CalendarModel(id, accountName, displayName))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (calendars.isEmpty()) {
            calendars.add(CalendarModel(1L, "Lịch cá nhân", "Công Việc"))
            calendars.add(CalendarModel(2L, "Lịch học tập", "Học Tập"))
        }
        return calendars
    }

    fun getEventsForCurrentMonth(context: Context, selectedCalIds: Set<String>): Map<Int, List<EventModel>> {
        val eventsMap = mutableMapOf<Int, MutableList<EventModel>>()
        if (selectedCalIds.isEmpty()) return eventsMap

        try {
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
                    val title = if (titleIdx != -1) it.getString(titleIdx) ?: "Sự kiện" else "Sự kiện"
                    val begin = if (beginIdx != -1) it.getLong(beginIdx) else System.currentTimeMillis()
                    val end = if (endIdx != -1) it.getLong(endIdx) else System.currentTimeMillis() + 3600000
                    val allDay = if (allDayIdx != -1) it.getInt(allDayIdx) == 1 else false
                    val calId = if (calIdIdx != -1) it.getLong(calIdIdx) else 1L

                    var color = 0
                    if (displayColorIdx != -1) color = it.getInt(displayColorIdx)
                    if (color == 0 && eventColorIdx != -1) color = it.getInt(eventColorIdx)
                    if (color == 0) color = 0xFF00BCD4.toInt()

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
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (eventsMap.isEmpty()) {
            val now = LocalDate.now()
            val todayDay = now.dayOfMonth
            val maxDays = now.lengthOfMonth()

            // Default sample events for current month
            eventsMap[todayDay] = mutableListOf(
                EventModel("Họp nhóm dự án Duan", System.currentTimeMillis(), System.currentTimeMillis() + 3600000, false, 0xFF00BCD4.toInt(), 1L),
                EventModel("Làm bài tập Android Studio", System.currentTimeMillis() + 7200000, System.currentTimeMillis() + 10800000, false, 0xFF4CAF50.toInt(), 1L),
                EventModel("Đọc sách 30 phút", System.currentTimeMillis() + 14400000, System.currentTimeMillis() + 16200000, false, 0xFF9C27B0.toInt(), 2L)
            )

            val day5 = 5.coerceAtMost(maxDays)
            eventsMap[day5] = mutableListOf(
                EventModel("Lên kế hoạch tuần mới", System.currentTimeMillis(), System.currentTimeMillis() + 3600000, true, 0xFFFF9800.toInt(), 1L)
            )

            val day12 = 12.coerceAtMost(maxDays)
            eventsMap[day12] = mutableListOf(
                EventModel("Kiểm tra giữa kỳ môn Android", System.currentTimeMillis(), System.currentTimeMillis() + 7200000, false, 0xFFF44336.toInt(), 2L)
            )

            val day20 = 20.coerceAtMost(maxDays)
            eventsMap[day20] = mutableListOf(
                EventModel("Họp trực tuyến với Giảng viên", System.currentTimeMillis(), System.currentTimeMillis() + 3600000, false, 0xFF3F51B5.toInt(), 1L)
            )

            val day25 = 25.coerceAtMost(maxDays)
            eventsMap[day25] = mutableListOf(
                EventModel("Nộp báo cáo đồ án", System.currentTimeMillis(), System.currentTimeMillis() + 3600000, true, 0xFFE91E63.toInt(), 1L)
            )
        }

        return eventsMap
    }
}
