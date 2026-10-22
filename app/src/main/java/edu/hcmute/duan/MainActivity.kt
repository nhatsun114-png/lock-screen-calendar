package edu.hcmute.duan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import edu.hcmute.duan.data.model.CalendarModel
import edu.hcmute.duan.data.model.EventModel
import edu.hcmute.duan.data.repository.CalendarRepository
import edu.hcmute.duan.data.repository.SettingsRepository
import edu.hcmute.duan.data.repository.WallpaperRepository
import edu.hcmute.duan.databinding.ActivityMainBinding
import edu.hcmute.duan.ui.customize.CustomizeActivity
import edu.hcmute.duan.ui.dialog.AddEventDialog
import edu.hcmute.duan.ui.dialog.AddReminderDialog
import edu.hcmute.duan.ui.dialog.ColorPickerDialog
import edu.hcmute.duan.ui.main.MainUiState
import edu.hcmute.duan.ui.main.MainViewModel
import edu.hcmute.duan.ui.main.MainViewModelFactory
import edu.hcmute.duan.util.LocaleHelper
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import android.app.DatePickerDialog
import android.widget.Button
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var selectedCalendarDate: LocalDate = LocalDate.now()

    private val calendarRepository by lazy { CalendarRepository(applicationContext) }

    private val viewModel: MainViewModel by lazy {
        val factory = MainViewModelFactory(
            application,
            SettingsRepository(applicationContext),
            calendarRepository,
            WallpaperRepository(applicationContext)
        )
        ViewModelProvider(this, factory)[MainViewModel::class.java]
    }

    private var isUpdatingUiFromState = false

    private val imagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { viewModel.onCustomImagePicked(it) }
        }

    override fun attachBaseContext(newBase: Context) {
        val lang = SettingsRepository(newBase).getLanguage()
        val localizedContext = LocaleHelper.setLocale(newBase, lang)
        super.attachBaseContext(localizedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val settingsRepo = SettingsRepository(applicationContext)
        AppCompatDelegate.setDefaultNightMode(settingsRepo.getNightMode())

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initDisplayMetrics()
        setupListeners()
        setupInitialDefaultData()
        observeViewModel()

        checkPermissionsAndInit()
    }

    private fun initDisplayMetrics() {
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        viewModel.initScreenDimensions(displayMetrics.widthPixels, displayMetrics.heightPixels)
    }

    private fun setupListeners() {
        // Customize Button & Preview -> Opens CustomizeActivity
        binding.ivPreview.setOnClickListener {
            startActivity(Intent(this, CustomizeActivity::class.java))
        }
        binding.btnCustomize.setOnClickListener {
            startActivity(Intent(this, CustomizeActivity::class.java))
        }
        binding.btnOpenCustomize.setOnClickListener {
            startActivity(Intent(this, CustomizeActivity::class.java))
        }

        // Bottom Navigation Bar Items
        binding.navItemWallpaper.setOnClickListener { selectTab(0) }
        binding.navItemCalendar.setOnClickListener { selectTab(1) }
        binding.navItemReminders.setOnClickListener { selectTab(2) }
        binding.navItemSettings.setOnClickListener { selectTab(3) }

        binding.chipGuide.setOnClickListener {
            Toast.makeText(this, "Mở hướng dẫn thiết lập hình nền tự động", Toast.LENGTH_SHORT).show()
        }
        binding.tvAutoUpdateGuide.setOnClickListener {
            Toast.makeText(this, "Mở hướng dẫn cập nhật hình nền tự động", Toast.LENGTH_SHORT).show()
        }

        // Add Event button in Calendar page
        binding.btnAddCalendarEvent.setOnClickListener {
            openAddEventBottomSheet()
        }

        binding.btnPrevWeek.setOnClickListener {
            selectedCalendarDate = selectedCalendarDate.minusWeeks(1)
            updateCalendarWeekStrip()
            renderTimelineEvents(viewModel.uiState.value.events)
        }

        binding.btnNextWeek.setOnClickListener {
            selectedCalendarDate = selectedCalendarDate.plusWeeks(1)
            updateCalendarWeekStrip()
            renderTimelineEvents(viewModel.uiState.value.events)
        }

        binding.btnBtnToday.setOnClickListener {
            selectedCalendarDate = LocalDate.now()
            updateCalendarWeekStrip()
            renderTimelineEvents(viewModel.uiState.value.events)
        }

        binding.llMonthHeader.setOnClickListener {
            openDatePicker()
        }

        // Add Reminder buttons in Reminders Eisenhower Matrix page
        binding.btnAddQ1.setOnClickListener { openAddReminderBottomSheet("Làm ngay") }
        binding.btnAddQ2.setOnClickListener { openAddReminderBottomSheet("Lên kế hoạch") }
        binding.btnAddQ3.setOnClickListener { openAddReminderBottomSheet("Ủy quyền") }
        binding.btnAddQ4.setOnClickListener { openAddReminderBottomSheet("Loại bỏ") }

        binding.btnTogglePastHours.setOnClickListener {
            showPastHours = !showPastHours
            updateTimelineCurrentTimeIndicator()
            binding.btnTogglePastHours.text = if (showPastHours) "∧" else "∨"
            val msg = if (showPastHours) "Đã hiện toàn bộ các giờ đã qua" else "Đã ẩn các giờ đã qua"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        binding.btnUpdateWallpaper.setOnClickListener {
            viewModel.applyWallpaperToLockScreen()
        }

        // Theme Selection (Light / Dark Mode)
        binding.rgThemeMode.setOnCheckedChangeListener { _, checkedId ->
            if (!isUpdatingUiFromState) {
                val mode = when (checkedId) {
                    binding.rbThemeLight.id -> AppCompatDelegate.MODE_NIGHT_NO
                    binding.rbThemeDark.id -> AppCompatDelegate.MODE_NIGHT_YES
                    binding.rbThemeSystem.id -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                viewModel.onNightModeChanged(mode)
                AppCompatDelegate.setDefaultNightMode(mode)
            }
        }

        // Language Selection (Việt / Eng)
        binding.rgLanguage.setOnCheckedChangeListener { _, checkedId ->
            if (!isUpdatingUiFromState) {
                val langCode = when (checkedId) {
                    binding.rbLangVi.id -> "vi"
                    binding.rbLangEn.id -> "en"
                    else -> "vi"
                }
                viewModel.onLanguageChanged(langCode)
                val locales = LocaleListCompat.forLanguageTags(langCode)
                AppCompatDelegate.setApplicationLocales(locales)
                recreate()
            }
        }
    }

    private fun setupInitialDefaultData() {
        binding.tvQ1Content.text = "• Nộp báo cáo đồ án Android\n• Gửi email cho giảng viên"
        binding.tvQ2Content.text = "• Học Jetpack Compose & MVVM\n• Đọc 2 chương sách Lập trình"
        binding.tvQ3Content.text = "• Mua thêm văn phòng phẩm\n• Trả lời tin nhắn nhóm"
        binding.tvQ4Content.text = "• Lướt mạng xã hội quá 30p"
    }

    private fun openAddEventBottomSheet() {
        try {
            AddEventDialog(this, initialDate = selectedCalendarDate) {
                viewModel.refreshCalendarsAndEvents(YearMonth.from(selectedCalendarDate))
            }.show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Không thể mở màn hình thêm sự kiện: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAddReminderBottomSheet(quadrant: String) {
        try {
            AddReminderDialog(this, quadrant) { title, selectedQuadrant ->
                viewModel.addReminder(title, selectedQuadrant)
            }.show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Không thể mở màn hình thêm nhắc nhở: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun selectTab(tabIndex: Int) {
        binding.pageWallpaper.visibility = if (tabIndex == 0) View.VISIBLE else View.GONE
        binding.pageCalendar.visibility = if (tabIndex == 1) View.VISIBLE else View.GONE
        binding.pageReminders.visibility = if (tabIndex == 2) View.VISIBLE else View.GONE
        binding.pageSettings.visibility = if (tabIndex == 3) View.VISIBLE else View.GONE

        if (tabIndex == 1) {
            scrollToCurrentHourInCenter()
        }

        val activeColor = Color.parseColor("#2196F3")
        val inactiveColor = Color.parseColor("#666666")

        fun updateNavItem(item: LinearLayout, icon: ImageView, text: TextView, isSelected: Boolean) {
            item.setBackgroundResource(if (isSelected) R.drawable.bg_nav_item_selected else 0)
            icon.setColorFilter(if (isSelected) activeColor else inactiveColor)
            text.setTextColor(if (isSelected) activeColor else inactiveColor)
            text.typeface = Typeface.create(Typeface.DEFAULT, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
        }

        updateNavItem(binding.navItemWallpaper, binding.ivNavWallpaper, binding.tvNavWallpaper, tabIndex == 0)
        updateNavItem(binding.navItemCalendar, binding.ivNavCalendar, binding.tvNavCalendar, tabIndex == 1)
        updateNavItem(binding.navItemReminders, binding.ivNavReminders, binding.tvNavReminders, tabIndex == 2)
        updateNavItem(binding.navItemSettings, binding.ivNavSettings, binding.tvNavSettings, tabIndex == 3)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUiState(state)
                }
            }
        }
    }

    private fun renderUiState(state: MainUiState) {
        isUpdatingUiFromState = true

        binding.tvStyleName.text = state.styleName

        state.previewBitmap?.let { bitmap ->
            binding.ivPreview.setImageBitmap(bitmap)
        }

        // Theme mode
        when (state.nightMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> binding.rbThemeLight.isChecked = true
            AppCompatDelegate.MODE_NIGHT_YES -> binding.rbThemeDark.isChecked = true
            else -> binding.rbThemeSystem.isChecked = true
        }

        // Language
        when (state.language) {
            "en" -> binding.rbLangEn.isChecked = true
            else -> binding.rbLangVi.isChecked = true
        }

        // Reminders
        val q1 = state.reminders.filter { it.priorityQuadrant == "Q1" }
        val q2 = state.reminders.filter { it.priorityQuadrant == "Q2" }
        val q3 = state.reminders.filter { it.priorityQuadrant == "Q3" }
        val q4 = state.reminders.filter { it.priorityQuadrant == "Q4" }

        val isVi = state.language == "vi"
        val noReminderText = if (isVi) "• Chưa có nhắc nhở" else "• No reminders"
        binding.tvQ1Content.text = if (q1.isEmpty()) noReminderText else q1.joinToString("\n") { "• ${it.title}" }
        binding.tvQ2Content.text = if (q2.isEmpty()) noReminderText else q2.joinToString("\n") { "• ${it.title}" }
        binding.tvQ3Content.text = if (q3.isEmpty()) noReminderText else q3.joinToString("\n") { "• ${it.title}" }
        binding.tvQ4Content.text = if (q4.isEmpty()) noReminderText else q4.joinToString("\n") { "• ${it.title}" }

        updateCalendarWeekStrip(state.events)
        updateTimelineCurrentTimeIndicator()
        renderTimelineEvents(state.events)

        state.userMessage?.let { message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            viewModel.userMessageShown()
        }

        isUpdatingUiFromState = false
    }

    private fun openDatePicker() {
        val year = selectedCalendarDate.year
        val month = selectedCalendarDate.monthValue - 1
        val day = selectedCalendarDate.dayOfMonth

        val dpd = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            selectedCalendarDate = LocalDate.of(selectedYear, selectedMonth + 1, selectedDay)
            updateCalendarWeekStrip()
            renderTimelineEvents(viewModel.uiState.value.events)
        }, year, month, day)

        dpd.show()
    }

    private fun updateCalendarWeekStrip(eventsMap: Map<Int, List<EventModel>> = viewModel.uiState.value.events) {
        val firstDayOfWeek = selectedCalendarDate.with(DayOfWeek.MONDAY)

        val isVi = viewModel.uiState.value.language == "vi"
        val monthPattern = if (isVi) "'Tháng' M, yyyy" else "MMMM yyyy"
        val monthFormatter = DateTimeFormatter.ofPattern(monthPattern, Locale(viewModel.uiState.value.language))
        binding.tvCalendarMonthTitle.text = selectedCalendarDate.format(monthFormatter)

        val dayNames = if (isVi) arrayOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
                       else arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

        val dayCells = arrayOf(
            Triple(binding.tvCalDayLabel1, binding.tvCalDate1, Triple(binding.dotCal1, binding.calDay1, 0)),
            Triple(binding.tvCalDayLabel2, binding.tvCalDate2, Triple(binding.dotCal2, binding.calDay2, 1)),
            Triple(binding.tvCalDayLabel3, binding.tvCalDate3, Triple(binding.dotCal3, binding.calDay3, 2)),
            Triple(binding.tvCalDayLabel4, binding.tvCalDate4, Triple(binding.dotCal4, binding.calDay4, 3)),
            Triple(binding.tvCalDayLabel5, binding.tvCalDate5, Triple(binding.dotCal5, binding.calDay5, 4)),
            Triple(binding.tvCalDayLabel6, binding.tvCalDate6, Triple(binding.dotCal6, binding.calDay6, 5)),
            Triple(binding.tvCalDayLabel7, binding.tvCalDate7, Triple(binding.dotCal7, binding.calDay7, 6))
        )

        val today = LocalDate.now()

        for ((labelTv, dateTv, triple) in dayCells) {
            val (dotView, cellLayout, index) = triple
            val date = firstDayOfWeek.plusDays(index.toLong())

            labelTv.text = dayNames[index]
            dateTv.text = date.dayOfMonth.toString()

            val isSelected = (date == selectedCalendarDate)
            val isToday = (date == today)

            if (isSelected) {
                dateTv.setBackgroundResource(R.drawable.bg_day_active_circle)
                dateTv.setTextColor(Color.WHITE)
                dateTv.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            } else if (isToday) {
                dateTv.setBackgroundResource(R.drawable.bg_today_outline)
                dateTv.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                dateTv.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            } else {
                dateTv.background = null
                dateTv.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                dateTv.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }

            val dayEvents = eventsMap[date.dayOfMonth]
            dotView.visibility = if (!dayEvents.isNullOrEmpty()) View.VISIBLE else View.INVISIBLE

            cellLayout.setOnClickListener {
                selectedCalendarDate = date
                updateCalendarWeekStrip()
                renderTimelineEvents(viewModel.uiState.value.events)
            }
        }
    }

    private var showPastHours = false

    private fun scrollToCurrentHourInCenter() {
        binding.pageCalendar.post {
            try {
                val now = Calendar.getInstance()
                val currentHour = now.get(Calendar.HOUR_OF_DAY)

                val hourRows = mapOf(
                    8 to binding.rowHour08, 9 to binding.rowHour09, 10 to binding.rowHour10, 11 to binding.rowHour11,
                    12 to binding.rowHour12, 13 to binding.rowHour13, 14 to binding.rowHour14, 15 to binding.rowHour15,
                    16 to binding.rowHour16, 17 to binding.rowHour17, 18 to binding.rowHour18, 19 to binding.rowHour19,
                    20 to binding.rowHour20, 21 to binding.rowHour21, 22 to binding.rowHour22, 23 to binding.rowHour23,
                    0 to binding.rowHour00
                )

                val targetView = hourRows[currentHour] ?: binding.rowHour12
                val scrollView = binding.pageCalendar

                val targetTop = targetView.top
                val targetHeight = targetView.height
                val scrollViewHeight = scrollView.height

                val scrollToY = targetTop - (scrollViewHeight / 2) + (targetHeight / 2)

                scrollView.smoothScrollTo(0, scrollToY.coerceAtLeast(0))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun renderTimelineEvents(events: Map<Int, List<EventModel>>) {
        val slots = mapOf(
            8 to binding.slot08, 9 to binding.slot09, 10 to binding.slot10, 11 to binding.slot11,
            12 to binding.slot12, 13 to binding.slot13, 14 to binding.slot14, 15 to binding.slot15,
            16 to binding.slot16, 17 to binding.slot17, 18 to binding.slot18, 19 to binding.slot19,
            20 to binding.slot20, 21 to binding.slot21, 22 to binding.slot22, 23 to binding.slot23,
            0 to binding.slot00
        )

        for (slot in slots.values) {
            slot.removeAllViews()
        }

        binding.llCalendarEventsContainer.removeAllViews()

        val selectedDay = selectedCalendarDate.dayOfMonth
        val dayEvents = events[selectedDay] ?: emptyList()

        val dp = resources.displayMetrics.density

        if (dayEvents.isEmpty()) {
            val isVi = viewModel.uiState.value.language == "vi"
            val emptyTv = TextView(this).apply {
                text = if (isVi) "• Chưa có sự kiện cho ngày $selectedCalendarDate" else "• No events scheduled for $selectedCalendarDate"
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                textSize = 14f
                setPadding(0, 16, 0, 16)
            }
            binding.llCalendarEventsContainer.addView(emptyTv)
        } else {
            for (evt in dayEvents) {
                val cal = Calendar.getInstance().apply { timeInMillis = evt.begin }
                val hour = cal.get(Calendar.HOUR_OF_DAY)

                fun createCard(): MaterialCardView {
                    val card = MaterialCardView(this).apply {
                        radius = 12f * dp
                        setCardBackgroundColor(if (evt.color != 0) evt.color else Color.parseColor("#2196F3"))
                        strokeWidth = 0
                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
                        }
                        layoutParams = params
                    }

                    val layout = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
                    }

                    val titleTv = TextView(this).apply {
                        text = evt.title
                        setTextColor(Color.WHITE)
                        textSize = 14f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }

                    val timeStr = if (evt.allDay) "Cả ngày" else {
                        val endCal = Calendar.getInstance().apply { timeInMillis = evt.end }
                        String.format(Locale.getDefault(), "%02d:%02d - %02d:%02d", hour, cal.get(Calendar.MINUTE), endCal.get(Calendar.HOUR_OF_DAY), endCal.get(Calendar.MINUTE))
                    }

                    val timeTv = TextView(this).apply {
                        text = timeStr
                        setTextColor(Color.parseColor("#E0E0E0"))
                        textSize = 12f
                    }

                    layout.addView(titleTv)
                    layout.addView(timeTv)
                    card.addView(layout)

                    card.isClickable = true
                    card.isFocusable = true
                    card.setOnClickListener {
                        showEventOptionsBottomSheet(evt)
                    }

                    return card
                }

                binding.llCalendarEventsContainer.addView(createCard())

                val targetSlot = slots[hour]
                if (targetSlot != null) {
                    targetSlot.addView(createCard())
                }
            }
        }
    }

    private fun showEventOptionsBottomSheet(event: EventModel) {
        val isVi = viewModel.uiState.value.language == "vi"
        val dialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_event_options, null)

        val tvTitle = sheetView.findViewById<TextView>(R.id.tvOptionTitle)
        val btnEdit = sheetView.findViewById<Button>(R.id.btnOptionEdit)
        val btnDelete = sheetView.findViewById<Button>(R.id.btnOptionDelete)

        tvTitle.text = event.title
        btnEdit.text = if (isVi) "✏️ Chỉnh sửa sự kiện" else "✏️ Edit Event"
        btnDelete.text = if (isVi) "🗑️ Xóa sự kiện" else "🗑️ Delete Event"

        btnEdit.setOnClickListener {
            dialog.dismiss()
            AddEventDialog(this, initialDate = selectedCalendarDate, eventToEdit = event) {
                viewModel.refreshCalendarsAndEvents(YearMonth.from(selectedCalendarDate))
            }.show()
        }

        btnDelete.setOnClickListener {
            dialog.dismiss()
            viewModel.deleteEvent(event, selectedCalendarDate)
            Toast.makeText(this, if (isVi) "Đã xóa sự kiện!" else "Event deleted!", Toast.LENGTH_SHORT).show()
        }

        dialog.setContentView(sheetView)
        dialog.show()
    }

    private fun updateTimelineCurrentTimeIndicator() {
        try {
            val now = Calendar.getInstance()
            val currentHour = now.get(Calendar.HOUR_OF_DAY)
            val currentMinute = now.get(Calendar.MINUTE)

            val hourContainers = mapOf(
                8 to binding.hourContainer08, 9 to binding.hourContainer09, 10 to binding.hourContainer10, 11 to binding.hourContainer11,
                12 to binding.hourContainer12, 13 to binding.hourContainer13, 14 to binding.hourContainer14, 15 to binding.hourContainer15,
                16 to binding.hourContainer16, 17 to binding.hourContainer17, 18 to binding.hourContainer18, 19 to binding.hourContainer19,
                20 to binding.hourContainer20, 21 to binding.hourContainer21, 22 to binding.hourContainer22, 23 to binding.hourContainer23,
                0 to binding.hourContainer00
            )

            val hourRows = mapOf(
                8 to binding.rowHour08, 9 to binding.rowHour09, 10 to binding.rowHour10, 11 to binding.rowHour11,
                12 to binding.rowHour12, 13 to binding.rowHour13, 14 to binding.rowHour14, 15 to binding.rowHour15,
                16 to binding.rowHour16, 17 to binding.rowHour17, 18 to binding.rowHour18, 19 to binding.rowHour19,
                20 to binding.rowHour20, 21 to binding.rowHour21, 22 to binding.rowHour22, 23 to binding.rowHour23,
                0 to binding.rowHour00
            )

            val hourTvs = mapOf(
                8 to binding.t08, 9 to binding.t09, 10 to binding.t10, 11 to binding.t11,
                12 to binding.t12, 13 to binding.t13, 14 to binding.t14, 15 to binding.t15,
                16 to binding.t16, 17 to binding.t17, 18 to binding.t18, 19 to binding.t19,
                20 to binding.t20, 21 to binding.t21, 22 to binding.t22, 23 to binding.t23,
                0 to binding.t00
            )

            val slots = mapOf(
                8 to binding.slot08, 9 to binding.slot09, 10 to binding.slot10, 11 to binding.slot11,
                12 to binding.slot12, 13 to binding.slot13, 14 to binding.slot14, 15 to binding.slot15,
                16 to binding.slot16, 17 to binding.slot17, 18 to binding.slot18, 19 to binding.slot19,
                20 to binding.slot20, 21 to binding.slot21, 22 to binding.slot22, 23 to binding.slot23,
                0 to binding.slot00
            )

            for ((hour, tv) in hourTvs) {
                val formattedHour = String.format(Locale.getDefault(), "%02d:00", hour)
                tv.text = formattedHour
                tv.setTextColor(Color.parseColor("#A0A0A0"))
                tv.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }

            for ((hour, container) in hourContainers) {
                val hasEvents = (slots[hour]?.childCount ?: 0) > 0
                if (!showPastHours && hour < currentHour && hour != currentHour && !hasEvents) {
                    container.visibility = View.GONE
                } else {
                    container.visibility = View.VISIBLE
                }
            }
            hourContainers[currentHour]?.visibility = View.VISIBLE

            val activeRow = hourRows[currentHour] ?: binding.rowHour19
            val tv = hourTvs[currentHour] ?: binding.t19

            (binding.currentTimeIndicatorLayout.parent as? ViewGroup)?.removeView(binding.currentTimeIndicatorLayout)

            val dp = resources.displayMetrics.density
            val minuteOffsetPx = ((currentMinute / 60.0) * (48 * dp)).toInt()

            val params = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                (12 * dp).toInt()
            ).apply {
                addRule(RelativeLayout.RIGHT_OF, tv.id)
                topMargin = minuteOffsetPx
            }

            activeRow.addView(binding.currentTimeIndicatorLayout, params)
            binding.currentTimeIndicatorLayout.visibility = View.VISIBLE
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var pendingPermissionCallback: (() -> Unit)? = null

    private val requestCalendarPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val readGranted = permissions[Manifest.permission.READ_CALENDAR] ?: false
            if (readGranted) {
                Toast.makeText(this, "Đã cấp quyền! Đang đồng bộ lịch từ điện thoại...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Sử dụng chế độ Lịch cá nhân cục bộ", Toast.LENGTH_SHORT).show()
            }
            pendingPermissionCallback?.invoke()
            pendingPermissionCallback = null
            viewModel.loadInitialData()
        }

    private fun checkAndRequestCalendarPermissions(onGranted: () -> Unit) {
        val permissions = mutableListOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            onGranted()
        } else {
            pendingPermissionCallback = onGranted
            requestCalendarPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun checkPermissionsAndInit() {
        checkAndRequestCalendarPermissions {
            viewModel.loadInitialData()
        }
    }
}
