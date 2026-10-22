package edu.hcmute.duan.ui.main

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import edu.hcmute.duan.CalendarWallpaperRenderer
import edu.hcmute.duan.data.local.AppDatabase
import edu.hcmute.duan.data.local.HabitEntity
import edu.hcmute.duan.data.local.ReminderEntity
import edu.hcmute.duan.data.model.EventModel
import edu.hcmute.duan.data.model.WallpaperTheme
import edu.hcmute.duan.data.repository.CalendarRepository
import edu.hcmute.duan.data.repository.SettingsRepository
import edu.hcmute.duan.data.repository.WallpaperRepository
import edu.hcmute.duan.receiver.AlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

class MainViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val calendarRepository: CalendarRepository,
    private val wallpaperRepository: WallpaperRepository
) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val reminderDao = db.reminderDao()
    private val habitDao = db.habitDao()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

    fun initScreenDimensions(width: Int, height: Int) {
        if (screenWidth != width || screenHeight != height) {
            screenWidth = width
            screenHeight = height
            updatePreview()
        }
    }

    fun loadInitialData() {
        viewModelScope.launch {
            loadPrefs()
            refreshCalendarsAndEvents()
            calendarRepository.scheduleDailyCalendarUpdate()
            AlarmScheduler(getApplication()).scheduleMidnightAlarm()
            applyWallpaperToLockScreen()
        }
    }

    private fun loadPrefs() {
        val colorPast = settingsRepository.getColorPast()
        val colorFuture = settingsRepository.getColorFuture()
        val colorToday = settingsRepository.getColorToday()
        val colorText = settingsRepository.getColorText()
        val colorBg = settingsRepository.getColorBg()
        val isUsingImage = settingsRepository.isUsingImage()
        val yOffset = settingsRepository.getYOffset()
        val scale = settingsRepository.getScale()
        val isMondayFirst = settingsRepository.isMondayFirst()
        val showWeekNumbers = settingsRepository.isShowWeekNumbers()
        val isMinimalistEvents = settingsRepository.isMinimalistEvents()
        val layoutStyle = settingsRepository.getLayoutStyle()
        val fontStyle = settingsRepository.getFontStyle()
        val lang = settingsRepository.getLanguage()
        val nightMode = settingsRepository.getNightMode()

        _uiState.update { state ->
            state.copy(
                colorPast = colorPast,
                colorFuture = colorFuture,
                colorToday = colorToday,
                colorText = colorText,
                colorBg = colorBg,
                isUsingImage = isUsingImage,
                yOffset = yOffset,
                scale = scale,
                isMondayFirst = isMondayFirst,
                showWeekNumbers = showWeekNumbers,
                isMinimalistEvents = isMinimalistEvents,
                layoutStyle = layoutStyle,
                fontStyle = fontStyle,
                language = lang,
                nightMode = nightMode
            )
        }
    }

    fun onLanguageChanged(langCode: String) {
        settingsRepository.setLanguage(langCode)
        _uiState.update { it.copy(language = langCode) }
    }

    fun onNightModeChanged(mode: Int) {
        settingsRepository.setNightMode(mode)
        _uiState.update { it.copy(nightMode = mode) }
    }

    fun refreshCalendarsAndEvents(yearMonth: YearMonth = YearMonth.now()) {
        viewModelScope.launch(Dispatchers.IO) {
            val calendars = calendarRepository.getAvailableCalendars()
            val enabledMap = mutableMapOf<Long, Boolean>()
            val colorMap = mutableMapOf<Long, Int>()
            val selectedCalIds = mutableSetOf<String>()

            for (cal in calendars) {
                val isEnabled = settingsRepository.isCalendarEnabled(cal.id)
                val color = settingsRepository.getCalendarColor(cal.id)
                enabledMap[cal.id] = isEnabled
                colorMap[cal.id] = color
                if (isEnabled) {
                    selectedCalIds.add(cal.id.toString())
                }
            }

            val events = calendarRepository.getEventsForMonth(yearMonth, selectedCalIds)

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    calendars = calendars,
                    calendarEnabledMap = enabledMap,
                    calendarColorMap = colorMap,
                    events = HashMap(events),
                    refreshTrigger = System.currentTimeMillis()
                )
                updatePreview()
            }
        }
    }

    fun deleteEvent(event: EventModel, selectedDate: LocalDate) {
        viewModelScope.launch(Dispatchers.IO) {
            calendarRepository.deleteLocalEvent(event.title, event.begin)
            
            val yearMonth = YearMonth.from(selectedDate)
            val calendars = calendarRepository.getAvailableCalendars()
            val enabledMap = mutableMapOf<Long, Boolean>()
            val colorMap = mutableMapOf<Long, Int>()
            val selectedCalIds = mutableSetOf<String>()

            for (cal in calendars) {
                val isEnabled = settingsRepository.isCalendarEnabled(cal.id)
                val color = settingsRepository.getCalendarColor(cal.id)
                enabledMap[cal.id] = isEnabled
                colorMap[cal.id] = color
                if (isEnabled) {
                    selectedCalIds.add(cal.id.toString())
                }
            }

            val events = calendarRepository.getEventsForMonth(yearMonth, selectedCalIds)

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    calendars = calendars,
                    calendarEnabledMap = enabledMap,
                    calendarColorMap = colorMap,
                    events = HashMap(events),
                    refreshTrigger = System.currentTimeMillis()
                )
                updatePreview()
            }
        }
    }

    // Reminders Management
    fun loadReminders() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = reminderDao.getAllReminders()
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(reminders = list) }
            }
        }
    }

    fun addReminder(title: String, quadrant: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val q = when (quadrant) {
                "Làm ngay" -> "Q1"
                "Lên kế hoạch" -> "Q2"
                "Ủy quyền" -> "Q3"
                "Loại bỏ" -> "Q4"
                else -> "Q1"
            }
            val newEntity = ReminderEntity(
                title = title,
                timeMillis = System.currentTimeMillis(),
                priorityQuadrant = q
            )
            reminderDao.insertReminder(newEntity)
            loadReminders()
        }
    }

    // Habits Management
    fun loadHabits() {
        viewModelScope.launch(Dispatchers.IO) {
            var habits = habitDao.getAllHabits()
            if (habits.isEmpty()) {
                val h1 = HabitEntity(name = "Đi bộ sáng 30p", icon = "🚶‍♂️", streak = 3)
                val h2 = HabitEntity(name = "Uống 2L nước", icon = "💧", streak = 5)
                habitDao.insertHabit(h1)
                habitDao.insertHabit(h2)
                habits = habitDao.getAllHabits()
            }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(habits = habits) }
            }
        }
    }

    fun addHabit(name: String, icon: String = "✨") {
        viewModelScope.launch(Dispatchers.IO) {
            val newHabit = HabitEntity(name = name, icon = icon, streak = 0)
            habitDao.insertHabit(newHabit)
            loadHabits()
        }
    }

    fun toggleHabitCheckedToday(habitId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val habit = habitDao.getAllHabits().find { it.id == habitId } ?: return@launch
            val todayStr = LocalDate.now().toString()
            val isCheckedToday = habit.lastCheckedDate == todayStr

            val newStreak = if (isCheckedToday) (habit.streak - 1).coerceAtLeast(0) else habit.streak + 1
            val newLastChecked = if (isCheckedToday) "" else todayStr

            val updated = habit.copy(streak = newStreak, lastCheckedDate = newLastChecked)
            habitDao.updateHabit(updated)
            loadHabits()
        }
    }

    fun onCalendarToggleChanged(calId: Long, isChecked: Boolean) {
        settingsRepository.setCalendarEnabled(calId, isChecked)
        val updatedEnabledMap = _uiState.value.calendarEnabledMap.toMutableMap().apply {
            put(calId, isChecked)
        }
        _uiState.update { it.copy(calendarEnabledMap = updatedEnabledMap) }
        loadEventsOnly()
    }

    fun onCalendarColorChanged(calId: Long, color: Int) {
        settingsRepository.setCalendarColor(calId, color)
        val updatedColorMap = _uiState.value.calendarColorMap.toMutableMap().apply {
            put(calId, color)
        }
        _uiState.update { it.copy(calendarColorMap = updatedColorMap) }
        updatePreview()
    }

    private fun loadEventsOnly() {
        viewModelScope.launch(Dispatchers.IO) {
            val enabledMap = _uiState.value.calendarEnabledMap
            val selectedCalIds = enabledMap.filterValues { it }.keys.map { it.toString() }.toSet()
            val events = calendarRepository.getEventsForCurrentMonth(selectedCalIds)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(events = events) }
                updatePreview()
            }
        }
    }

    fun onColorPastChanged(color: Int) {
        _uiState.update { it.copy(colorPast = color) }
        saveCurrentSettings()
    }

    fun onColorFutureChanged(color: Int) {
        _uiState.update { it.copy(colorFuture = color) }
        saveCurrentSettings()
    }

    fun onColorTodayChanged(color: Int) {
        _uiState.update { it.copy(colorToday = color) }
        saveCurrentSettings()
    }

    fun onColorTextChanged(color: Int) {
        _uiState.update { it.copy(colorText = color) }
        saveCurrentSettings()
    }

    fun onColorBgChanged(color: Int) {
        _uiState.update { it.copy(colorBg = color, isUsingImage = false) }
        saveCurrentSettings()
    }

    fun onCustomImagePicked(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val width = if (screenWidth > 0) screenWidth else 1080
            val height = if (screenHeight > 0) screenHeight else 1920
            val success = settingsRepository.saveCustomImage(uri, width, height)
            withContext(Dispatchers.Main) {
                if (success) {
                    _uiState.update { it.copy(isUsingImage = true) }
                    saveCurrentSettings()
                } else {
                    _uiState.update { it.copy(userMessage = "Failed to load image") }
                }
            }
        }
    }

    fun onYOffsetChanged(value: Float) {
        _uiState.update { it.copy(yOffset = value) }
        saveCurrentSettings()
    }

    fun onScaleChanged(value: Float) {
        _uiState.update { it.copy(scale = value) }
        saveCurrentSettings()
    }

    fun onMondayFirstChanged(isChecked: Boolean) {
        _uiState.update { it.copy(isMondayFirst = isChecked) }
        saveCurrentSettings()
    }

    fun onWeekNumbersChanged(isChecked: Boolean) {
        _uiState.update { it.copy(showWeekNumbers = isChecked) }
        saveCurrentSettings()
    }

    fun onMinimalistEventsChanged(isChecked: Boolean) {
        _uiState.update { it.copy(isMinimalistEvents = isChecked) }
        saveCurrentSettings()
    }

    fun onLayoutStyleChanged(style: Int) {
        _uiState.update { it.copy(layoutStyle = style) }
        saveCurrentSettings()
    }

    fun onFontStyleChanged(font: Int) {
        _uiState.update { it.copy(fontStyle = font) }
        saveCurrentSettings()
    }

    private fun saveCurrentSettings() {
        val currentState = _uiState.value
        settingsRepository.saveSettings(
            colorPast = currentState.colorPast,
            colorFuture = currentState.colorFuture,
            colorToday = currentState.colorToday,
            colorText = currentState.colorText,
            colorBg = currentState.colorBg,
            isUsingImage = currentState.isUsingImage,
            yOffset = currentState.yOffset,
            scale = currentState.scale,
            mondayFirst = currentState.isMondayFirst,
            weekNumbers = currentState.showWeekNumbers,
            minimalistEvents = currentState.isMinimalistEvents,
            layoutStyle = currentState.layoutStyle,
            fontStyle = currentState.fontStyle
        )
        updatePreview()
    }

    private fun buildThemeConfig(): WallpaperTheme {
        val currentState = _uiState.value
        val customImage = if (currentState.isUsingImage) settingsRepository.getCustomImageBitmap() else null

        return WallpaperTheme(
            width = if (screenWidth > 0) screenWidth else 1080,
            height = if (screenHeight > 0) screenHeight else 1920,
            layoutStyle = currentState.layoutStyle,
            fontStyle = currentState.fontStyle,
            isMondayFirst = currentState.isMondayFirst,
            showWeekNumbers = currentState.showWeekNumbers,
            isMinimalist = currentState.isMinimalistEvents,
            yOffsetPercent = currentState.yOffset.toInt(),
            sizeScalePercent = currentState.scale.toInt(),
            colorBg = currentState.colorBg,
            colorText = currentState.colorText,
            colorToday = currentState.colorToday,
            colorPast = currentState.colorPast,
            colorFuture = currentState.colorFuture,
            bgImage = customImage,
            customCalColors = currentState.calendarColorMap
        )
    }

    fun updatePreview() {
        if (screenWidth == 0 || screenHeight == 0) return
        val theme = buildThemeConfig()
        val bitmap = CalendarWallpaperRenderer.createCalendarBitmap(theme, _uiState.value.events)
        _uiState.update { it.copy(previewBitmap = bitmap) }
    }

    fun applyWallpaperToLockScreen() {
        val bitmap = _uiState.value.previewBitmap ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = wallpaperRepository.setLockScreenWallpaper(bitmap)
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    userMessage = if (result.isSuccess) "Lockscreen updated successfully!" else "Failed: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun resetSettings() {
        settingsRepository.resetToDefaults()
        loadPrefs()
        refreshCalendarsAndEvents()
        _uiState.update { it.copy(userMessage = "Reset to defaults") }
    }

    fun userMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }
}

class MainViewModelFactory(
    private val application: Application,
    private val settingsRepository: SettingsRepository,
    private val calendarRepository: CalendarRepository,
    private val wallpaperRepository: WallpaperRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(
                application,
                settingsRepository,
                calendarRepository,
                wallpaperRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
