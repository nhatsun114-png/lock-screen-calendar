package edu.hcmute.duan.data.repository

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import edu.hcmute.duan.CalendarHelper
import edu.hcmute.duan.CalendarUpdateWorker
import edu.hcmute.duan.data.local.AppDatabase
import edu.hcmute.duan.data.local.DeletedEventEntity
import edu.hcmute.duan.data.local.LocalEventEntity
import edu.hcmute.duan.data.model.CalendarModel
import edu.hcmute.duan.data.model.EventModel
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import java.text.Normalizer
import java.time.LocalDate
import java.time.YearMonth

class CalendarRepository(private val context: Context) {

    private val localEventDao = AppDatabase.getDatabase(context).localEventDao()
    private val deletedEventDao = AppDatabase.getDatabase(context).deletedEventDao()

    init {
        migrateSharedPreferencesToRoom()
    }

    private fun migrateSharedPreferencesToRoom() {
        try {
            val prefs = context.getSharedPreferences("local_user_events_prefs", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("local_events_json", null) ?: return
            val jsonArray = JSONArray(jsonStr)
            if (jsonArray.length() > 0) {
                runBlocking {
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        localEventDao.insertEvent(
                            LocalEventEntity(
                                title = obj.getString("title"),
                                beginMillis = obj.getLong("begin"),
                                endMillis = obj.getLong("end"),
                                allDay = obj.getBoolean("allDay"),
                                color = obj.getInt("color"),
                                calendarId = obj.getLong("calendarId")
                            )
                        )
                    }
                }
                prefs.edit().remove("local_events_json").apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getAvailableCalendars(): List<CalendarModel> {
        return CalendarHelper.getAvailableCalendars(context)
    }

    private fun seedInitialEventsIfEmpty() {
        val prefs = context.getSharedPreferences("calendar_app_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("has_seeded_initial_events_v3", false)) {
            return
        }

        try {
            val existing = localEventDao.getAllEventsSync()
            if (existing.isEmpty()) {
                val cal = Calendar.getInstance()
                val currentYear = cal.get(Calendar.YEAR)
                val currentMonth = cal.get(Calendar.MONTH)
                val todayDay = cal.get(Calendar.DAY_OF_MONTH)
                val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

                fun makeMillis(dayOfMonth: Int, hour: Int, minute: Int): Long {
                    val c = Calendar.getInstance().apply {
                        set(Calendar.YEAR, currentYear)
                        set(Calendar.MONTH, currentMonth)
                        set(Calendar.DAY_OF_MONTH, dayOfMonth.coerceAtMost(maxDays))
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    return c.timeInMillis
                }

                val samples = listOf(
                    LocalEventEntity(
                        title = "Họp nhóm dự án Duan",
                        beginMillis = makeMillis(todayDay, 9, 0),
                        endMillis = makeMillis(todayDay, 10, 0),
                        allDay = false,
                        color = 0xFF00BCD4.toInt(),
                        calendarId = 1L
                    ),
                    LocalEventEntity(
                        title = "Làm bài tập Android Studio",
                        beginMillis = makeMillis(todayDay, 11, 0),
                        endMillis = makeMillis(todayDay, 12, 30),
                        allDay = false,
                        color = 0xFF4CAF50.toInt(),
                        calendarId = 1L
                    ),
                    LocalEventEntity(
                        title = "Đọc sách 30 phút",
                        beginMillis = makeMillis(todayDay, 14, 0),
                        endMillis = makeMillis(todayDay, 14, 30),
                        allDay = false,
                        color = 0xFF9C27B0.toInt(),
                        calendarId = 2L
                    ),
                    LocalEventEntity(
                        title = "Họp trực tuyến với Giảng viên",
                        beginMillis = makeMillis(20, 10, 0),
                        endMillis = makeMillis(20, 11, 0),
                        allDay = false,
                        color = 0xFF3F51B5.toInt(),
                        calendarId = 1L
                    ),
                    LocalEventEntity(
                        title = "Nộp báo cáo đồ án",
                        beginMillis = makeMillis(25, 8, 0),
                        endMillis = makeMillis(25, 17, 0),
                        allDay = true,
                        color = 0xFFE91E63.toInt(),
                        calendarId = 1L
                    )
                )

                runBlocking {
                    for (s in samples) {
                        localEventDao.insertEvent(s)
                    }
                }
            }
            prefs.edit().putBoolean("has_seeded_initial_events_v3", true).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun normalizeTitle(title: String): String {
        val cleaned = title.replace("\u00A0", " ").trim().lowercase()
        return Normalizer.normalize(cleaned, Normalizer.Form.NFC)
    }

    fun getEventsForMonth(yearMonth: YearMonth = YearMonth.now(), selectedCalIds: Set<String>): Map<Int, List<EventModel>> {
        seedInitialEventsIfEmpty()

        val eventsMap = mutableMapOf<Int, MutableList<EventModel>>()

        val deletedTitles = deletedEventDao.getAllDeletedTitlesSync().map { normalizeTitle(it) }.filter { it.isNotEmpty() }.toSet()

        fun isDeleted(title: String): Boolean {
            val norm = normalizeTitle(title)
            if (norm.isEmpty()) return false
            return deletedTitles.contains(norm)
        }

        // 1. Read local events from Room Database
        val localEvents = loadLocalEvents()
        val cal = Calendar.getInstance()
        Log.d("CalendarRepo", "getEventsForMonth yearMonth=$yearMonth, localEventsCount=${localEvents.size}, deletedTitles=$deletedTitles")

        for (evt in localEvents) {
            Log.d("CalendarRepo", "  localEvt: title='${evt.title}', begin=${evt.begin}, isDeleted=${isDeleted(evt.title)}")
            if (isDeleted(evt.title)) continue

            cal.timeInMillis = evt.begin
            val evtYear = cal.get(Calendar.YEAR)
            val evtMonth = cal.get(Calendar.MONTH) + 1
            val evtDay = cal.get(Calendar.DAY_OF_MONTH)

            if (evtYear == yearMonth.year && evtMonth == yearMonth.monthValue) {
                val list = eventsMap[evtDay] ?: mutableListOf()
                if (list.none { normalizeTitle(it.title) == normalizeTitle(evt.title) }) {
                    list.add(evt)
                }
                eventsMap[evtDay] = list
            }
        }

        // 2. Read system calendar events if available (skipping duplicates & deleted)
        try {
            val sysEvents = CalendarHelper.getEventsForMonth(context, yearMonth, selectedCalIds)
            for ((day, sysList) in sysEvents) {
                val dayList = eventsMap[day] ?: mutableListOf()
                for (sysEvt in sysList) {
                    if (isDeleted(sysEvt.title)) continue

                    if (dayList.none { normalizeTitle(it.title) == normalizeTitle(sysEvt.title) }) {
                        dayList.add(sysEvt)
                    }
                }
                if (dayList.isNotEmpty()) {
                    eventsMap[day] = dayList
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return eventsMap
    }

    fun getEventsForCurrentMonth(selectedCalIds: Set<String>): Map<Int, List<EventModel>> {
        return getEventsForMonth(YearMonth.now(), selectedCalIds)
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
            deletedEventDao.removeDeletedTitle(normalizeTitle(event.title))
            localEventDao.insertEvent(
                LocalEventEntity(
                    title = event.title,
                    beginMillis = event.begin,
                    endMillis = event.end,
                    allDay = event.allDay,
                    color = event.color,
                    calendarId = event.calendarId
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteLocalEvent(eventTitle: String, eventBegin: Long) {
        val normTitle = normalizeTitle(eventTitle)
        try {
            localEventDao.deleteEventByTitle(eventTitle)
            localEventDao.deleteEventByTitle(normTitle)
            deletedEventDao.insertDeletedTitle(DeletedEventEntity(normTitle))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val selection = "${CalendarContract.Events.TITLE} = ?"
            val selectionArgs = arrayOf(eventTitle)
            context.contentResolver.delete(CalendarContract.Events.CONTENT_URI, selection, selectionArgs)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateLocalEvent(oldTitle: String, oldBegin: Long, newTitle: String) {
        try {
            runBlocking {
                localEventDao.updateEventTitle(oldTitle, oldBegin, newTitle)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadLocalEvents(): List<EventModel> {
        return try {
            val entities = localEventDao.getAllEventsSync()
            entities.map {
                EventModel(
                    title = it.title,
                    begin = it.beginMillis,
                    end = it.endMillis,
                    allDay = it.allDay,
                    color = it.color,
                    calendarId = it.calendarId
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
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

        // Use ExistingPeriodicWorkPolicy.KEEP so timer is NOT reset on app open (TC-CORE-01 fix)
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "DailyCalendarUpdate",
            ExistingPeriodicWorkPolicy.KEEP,
            updateRequest
        )
    }
}
