package edu.hcmute.duan.ui.main

import android.graphics.Bitmap
import android.graphics.Color
import androidx.appcompat.app.AppCompatDelegate
import edu.hcmute.duan.data.local.HabitEntity
import edu.hcmute.duan.data.local.ReminderEntity
import edu.hcmute.duan.data.model.CalendarModel
import edu.hcmute.duan.data.model.EventModel

data class MainUiState(
    val colorPast: Int = Color.parseColor("#555555"),
    val colorFuture: Int = Color.parseColor("#AAAAAA"),
    val colorToday: Int = Color.parseColor("#4CAF50"),
    val colorText: Int = Color.WHITE,
    val colorBg: Int = Color.BLACK,
    val isUsingImage: Boolean = false,
    val yOffset: Float = 60f,
    val scale: Float = 100f,
    val isMondayFirst: Boolean = true,
    val showWeekNumbers: Boolean = false,
    val isMinimalistEvents: Boolean = false,
    val layoutStyle: Int = 0,
    val fontStyle: Int = 0,
    val language: String = "vi",
    val nightMode: Int = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
    val calendars: List<CalendarModel> = emptyList(),
    val calendarEnabledMap: Map<Long, Boolean> = emptyMap(),
    val calendarColorMap: Map<Long, Int> = emptyMap(),
    val events: Map<Int, List<EventModel>> = emptyMap(),
    val reminders: List<ReminderEntity> = emptyList(),
    val habits: List<HabitEntity> = emptyList(),
    val previewBitmap: Bitmap? = null,
    val userMessage: String? = null,
    val isLoading: Boolean = false,
    val refreshTrigger: Long = 0L
) {
    val styleName: String
        get() = when (layoutStyle) {
            0 -> "Month agenda"
            1 -> "Split clock hole"
            2 -> "Weekly tasks"
            else -> "Month agenda"
        }
}
