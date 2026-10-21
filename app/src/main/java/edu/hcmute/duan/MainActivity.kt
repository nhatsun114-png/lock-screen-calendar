package edu.hcmute.duan

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import java.util.Calendar
import java.util.Locale
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import edu.hcmute.duan.data.model.CalendarModel
import edu.hcmute.duan.data.repository.CalendarRepository
import edu.hcmute.duan.data.repository.SettingsRepository
import edu.hcmute.duan.data.repository.WallpaperRepository
import edu.hcmute.duan.databinding.ActivityMainBinding
import edu.hcmute.duan.ui.dialog.AddEventDialog
import edu.hcmute.duan.ui.dialog.AddReminderDialog
import edu.hcmute.duan.ui.dialog.ColorPickerDialog
import edu.hcmute.duan.ui.main.MainUiState
import edu.hcmute.duan.ui.main.MainViewModel
import edu.hcmute.duan.ui.main.MainViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    private val viewModel: MainViewModel by lazy {
        val factory = MainViewModelFactory(
            SettingsRepository(applicationContext),
            CalendarRepository(applicationContext),
            WallpaperRepository(applicationContext)
        )
        ViewModelProvider(this, factory)[MainViewModel::class.java]
    }

    private var isUpdatingUiFromState = false

    private val imagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { viewModel.onCustomImagePicked(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initDisplayMetrics()
        initBottomSheet()
        setupListeners()
        setupHeatmapGrids()
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

    private fun initBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenHeight = displayMetrics.heightPixels

        bottomSheetBehavior.maxHeight = (screenHeight * 0.55).toInt()

        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {}

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                val safeOffset = if (slideOffset < 0f) 0f else slideOffset
                val scale = 1f - (safeOffset * 0.55f)

                binding.mainContent.pivotX = binding.mainContent.width / 2f
                binding.mainContent.pivotY = 0f
                binding.mainContent.scaleX = scale
                binding.mainContent.scaleY = scale
                binding.mainContent.translationY = 0f

                binding.btnCustomize.alpha = 1f - safeOffset
            }
        })

        binding.btnCustomize.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun setupListeners() {
        // Bottom Navigation Bar Items
        binding.navItemWallpaper.setOnClickListener { selectTab(0) }
        binding.navItemCalendar.setOnClickListener { selectTab(1) }
        binding.navItemReminders.setOnClickListener { selectTab(2) }
        binding.navItemHabits.setOnClickListener { selectTab(3) }
        binding.navItemSettings.setOnClickListener { selectTab(4) }

        binding.chipGuide.setOnClickListener {
            Toast.makeText(this, "Mở hướng dẫn thiết lập hình nền tự động", Toast.LENGTH_SHORT).show()
        }
        binding.tvAutoUpdateGuide.setOnClickListener {
            Toast.makeText(this, "Mở hướng dẫn cập nhật hình nền tự động", Toast.LENGTH_SHORT).show()
        }

        // Add Event button in Calendar page
        binding.btnAddCalendarEvent.setOnClickListener {
            checkAndRequestCalendarPermissions {
                openAddEventBottomSheet()
            }
        }

        // Add Reminder buttons in Reminders Eisenhower Matrix page
        binding.btnAddQ1.setOnClickListener { openAddReminderBottomSheet("Làm ngay") }
        binding.btnAddQ2.setOnClickListener { openAddReminderBottomSheet("Lên kế hoạch") }
        binding.btnAddQ3.setOnClickListener { openAddReminderBottomSheet("Ủy quyền") }
        binding.btnAddQ4.setOnClickListener { openAddReminderBottomSheet("Loại bỏ") }

        // Habits page listeners
        binding.btnHabitHistory.setOnClickListener {
            Toast.makeText(this, "Lịch sử hoàn thành thói quen", Toast.LENGTH_SHORT).show()
        }

        binding.btnAddHabit.setOnClickListener {
            val input = android.widget.EditText(this).apply {
                hint = "Nhập tên thói quen mới"
                setPadding(48, 32, 48, 32)
            }
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Tạo thói quen mới")
                .setView(input)
                .setPositiveButton("Lưu") { dialog, _ ->
                    val habitName = input.text.toString().trim()
                    if (habitName.isNotEmpty()) {
                        Toast.makeText(this, "Đã tạo thói quen mới: $habitName", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Vui lòng nhập tên thói quen!", Toast.LENGTH_SHORT).show()
                    }
                    dialog.dismiss()
                }
                .setNegativeButton("Hủy", null)
                .show()
        }

        binding.chipPro.setOnClickListener {
            Toast.makeText(this, "Tính năng PRO: Mở khóa thêm không giới hạn thói quen!", Toast.LENGTH_SHORT).show()
        }

        binding.btnTabWeek.setOnClickListener {
            binding.btnTabWeek.setBackgroundResource(R.drawable.bg_segmented_item_selected)
            binding.btnTabWeek.setTextColor(Color.BLACK)
            binding.btnTabWeek.setTypeface(Typeface.DEFAULT, Typeface.BOLD)

            binding.btnTabYear.setBackgroundResource(0)
            binding.btnTabYear.setTextColor(Color.parseColor("#666666"))
            binding.btnTabYear.setTypeface(Typeface.DEFAULT, Typeface.NORMAL)

            binding.rowHabit1Week.visibility = View.VISIBLE
            binding.rowHabit1Year.visibility = View.GONE
            binding.rowHabit2Week.visibility = View.VISIBLE
            binding.rowHabit2Year.visibility = View.GONE

            binding.btnCheckHabit1.visibility = View.VISIBLE
            binding.btnCheckHabit2.visibility = View.VISIBLE
            binding.tvHabit1FlameBadge.visibility = View.GONE
            binding.tvHabit2FlameBadge.visibility = View.GONE
            binding.tvHabit1Streak.text = "0d"
            binding.tvHabit2Streak.text = "0d"
        }

        binding.btnTabYear.setOnClickListener {
            binding.btnTabYear.setBackgroundResource(R.drawable.bg_segmented_item_selected)
            binding.btnTabYear.setTextColor(Color.BLACK)
            binding.btnTabYear.setTypeface(Typeface.DEFAULT, Typeface.BOLD)

            binding.btnTabWeek.setBackgroundResource(0)
            binding.btnTabWeek.setTextColor(Color.parseColor("#666666"))
            binding.btnTabWeek.setTypeface(Typeface.DEFAULT, Typeface.NORMAL)

            binding.rowHabit1Week.visibility = View.GONE
            binding.rowHabit1Year.visibility = View.VISIBLE
            binding.rowHabit2Week.visibility = View.GONE
            binding.rowHabit2Year.visibility = View.VISIBLE

            binding.btnCheckHabit1.visibility = View.GONE
            binding.btnCheckHabit2.visibility = View.GONE
            binding.tvHabit1FlameBadge.visibility = View.VISIBLE
            binding.tvHabit2FlameBadge.visibility = View.VISIBLE
            binding.tvHabit1Streak.text = "0d · 17:29"
            binding.tvHabit2Streak.text = "0d · 17:29"
        }

        var isHabit1Completed = false
        binding.btnCheckHabit1.setOnClickListener {
            isHabit1Completed = !isHabit1Completed
            if (isHabit1Completed) {
                binding.tvHabit1Streak.text = "1d"
                binding.btnCheckHabit1.setBackgroundColor(Color.parseColor("#4CAF50"))
                binding.btnCheckHabit1.setTextColor(Color.WHITE)
                Toast.makeText(this, "Đã hoàn thành: Đi bộ sáng! 🎉", Toast.LENGTH_SHORT).show()
            } else {
                binding.tvHabit1Streak.text = "0d"
                binding.btnCheckHabit1.setBackgroundColor(Color.parseColor("#C8E6C9"))
                binding.btnCheckHabit1.setTextColor(Color.parseColor("#4CAF50"))
            }
        }

        var isHabit2Completed = false
        binding.btnCheckHabit2.setOnClickListener {
            isHabit2Completed = !isHabit2Completed
            if (isHabit2Completed) {
                binding.tvHabit2Streak.text = "1d"
                binding.btnCheckHabit2.setBackgroundColor(Color.parseColor("#0288D1"))
                binding.btnCheckHabit2.setTextColor(Color.WHITE)
                Toast.makeText(this, "Đã hoàn thành: Uống nước! 💧", Toast.LENGTH_SHORT).show()
            } else {
                binding.tvHabit2Streak.text = "0d"
                binding.btnCheckHabit2.setBackgroundColor(Color.parseColor("#B3E5FC"))
                binding.btnCheckHabit2.setTextColor(Color.parseColor("#0288D1"))
            }
        }

        binding.btnTogglePastHours.setOnClickListener {
            showPastHours = !showPastHours
            updateTimelineCurrentTimeIndicator()
            binding.btnTogglePastHours.text = if (showPastHours) "∧" else "∨"
            val msg = if (showPastHours) "Đã hiện toàn bộ các giờ đã qua" else "Đã ẩn các giờ đã qua"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        binding.rowColorPast.setOnClickListener {
            ColorPickerDialog.show(this, "Past Day Color", viewModel.uiState.value.colorPast) {
                viewModel.onColorPastChanged(it)
            }
        }
        binding.rowColorFuture.setOnClickListener {
            ColorPickerDialog.show(this, "Future Day Color", viewModel.uiState.value.colorFuture) {
                viewModel.onColorFutureChanged(it)
            }
        }
        binding.rowColorToday.setOnClickListener {
            ColorPickerDialog.show(this, "Today Highlight Color", viewModel.uiState.value.colorToday) {
                viewModel.onColorTodayChanged(it)
            }
        }
        binding.rowColorText.setOnClickListener {
            ColorPickerDialog.show(this, "Text & Numbers Color", viewModel.uiState.value.colorText) {
                viewModel.onColorTextChanged(it)
            }
        }
        binding.rowColorBg.setOnClickListener {
            ColorPickerDialog.show(this, "Background Color", viewModel.uiState.value.colorBg) {
                viewModel.onColorBgChanged(it)
            }
        }

        binding.btnPickImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        binding.btnRefreshCalendars.setOnClickListener {
            checkAndRequestCalendarPermissions {
                viewModel.refreshCalendarsAndEvents()
            }
        }

        val sliderListener = object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                if (slider.id == binding.seekYOffset.id) {
                    viewModel.onYOffsetChanged(slider.value)
                } else if (slider.id == binding.seekScale.id) {
                    viewModel.onScaleChanged(slider.value)
                }
            }
        }
        binding.seekYOffset.addOnSliderTouchListener(sliderListener)
        binding.seekScale.addOnSliderTouchListener(sliderListener)

        binding.switchMondayFirst.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUiFromState) viewModel.onMondayFirstChanged(isChecked)
        }
        binding.switchWeekNumbers.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUiFromState) viewModel.onWeekNumbersChanged(isChecked)
        }
        binding.switchMinimalist.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUiFromState) viewModel.onMinimalistEventsChanged(isChecked)
        }

        binding.rgLayoutStyle.setOnCheckedChangeListener { _, checkedId ->
            if (!isUpdatingUiFromState) {
                val layoutStyle = when (checkedId) {
                    binding.rbStandard.id -> 0
                    binding.rbSplit.id -> 1
                    binding.rbWeekly.id -> 2
                    else -> 0
                }
                viewModel.onLayoutStyleChanged(layoutStyle)
            }
        }

        binding.toggleFont.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isUpdatingUiFromState && isChecked) {
                val fontStyle = when (checkedId) {
                    binding.btnFontSans.id -> 0
                    binding.btnFontSerif.id -> 1
                    binding.btnFontMono.id -> 2
                    else -> 0
                }
                viewModel.onFontStyleChanged(fontStyle)
            }
        }

        binding.btnUpdateWallpaper.setOnClickListener {
            viewModel.applyWallpaperToLockScreen()
        }

        binding.btnSetWallpaper.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            Toast.makeText(this, "Configuration saved! Press 'Update' to apply to Lockscreen.", Toast.LENGTH_SHORT).show()
        }

        binding.btnReset.setOnClickListener {
            viewModel.resetSettings()
        }
    }

    private fun setupHeatmapGrids() {
        val dp = resources.displayMetrics.density

        fun createDotDrawable(color: Int): android.graphics.drawable.GradientDrawable {
            return android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setColor(color)
                cornerRadius = 2.5f * dp
            }
        }

        fun populateGrid(grid: android.widget.GridLayout, activeColor: Int, lightBgColor: Int) {
            grid.removeAllViews()
            grid.rowCount = 7
            grid.columnCount = 24
            for (row in 0..6) {
                for (col in 0..23) {
                    val dot = View(this).apply {
                        val params = android.widget.GridLayout.LayoutParams().apply {
                            width = (9 * dp).toInt()
                            height = (9 * dp).toInt()
                            setMargins((1.5f * dp).toInt(), (1.5f * dp).toInt(), (1.5f * dp).toInt(), (1.5f * dp).toInt())
                        }
                        layoutParams = params
                        val isCompleted = (col == 23 && row == 2)
                        background = createDotDrawable(if (isCompleted) activeColor else lightBgColor)
                    }
                    grid.addView(dot)
                }
            }
        }

        populateGrid(binding.gridHeatmap1, Color.parseColor("#00E676"), Color.parseColor("#C8E6C9"))
        populateGrid(binding.gridHeatmap2, Color.parseColor("#0288D1"), Color.parseColor("#B3E5FC"))
    }

    private fun setupInitialDefaultData() {
        // Default Eisenhower Reminders Data
        binding.tvQ1Content.text = "• Nộp báo cáo đồ án Android\n• Gửi email cho giảng viên"
        binding.tvQ2Content.text = "• Học Jetpack Compose & MVVM\n• Đọc 2 chương sách Lập trình"
        binding.tvQ3Content.text = "• Mua thêm văn phòng phẩm\n• Trả lời tin nhắn nhóm"
        binding.tvQ4Content.text = "• Lướt mạng xã hội quá 30p"

        // Default Habit Streaks
        binding.tvHabit1Streak.text = "3d"
        binding.tvHabit2Streak.text = "5d"
    }

    private fun openAddEventBottomSheet() {
        try {
            AddEventDialog(this) {
                viewModel.refreshCalendarsAndEvents()
            }.show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Không thể mở màn hình thêm sự kiện: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAddReminderBottomSheet(quadrant: String) {
        try {
            AddReminderDialog(this, quadrant) { title, selectedQuadrant ->
                when (selectedQuadrant) {
                    "Làm ngay" -> binding.tvQ1Content.text = "• $title"
                    "Lên kế hoạch" -> binding.tvQ2Content.text = "• $title"
                    "Ủy quyền" -> binding.tvQ3Content.text = "• $title"
                    "Loại bỏ" -> binding.tvQ4Content.text = "• $title"
                }
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
        binding.pageHabits.visibility = if (tabIndex == 3) View.VISIBLE else View.GONE
        binding.pageSettings.visibility = if (tabIndex == 4) View.VISIBLE else View.GONE

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
        updateNavItem(binding.navItemHabits, binding.ivNavHabits, binding.tvNavHabits, tabIndex == 3)
        updateNavItem(binding.navItemSettings, binding.ivNavSettings, binding.tvNavSettings, tabIndex == 4)
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

        binding.ivColorPast.setCardBackgroundColor(state.colorPast)
        binding.ivColorFuture.setCardBackgroundColor(state.colorFuture)
        binding.ivColorToday.setCardBackgroundColor(state.colorToday)
        binding.ivColorText.setCardBackgroundColor(state.colorText)
        binding.ivColorBg.setCardBackgroundColor(if (state.isUsingImage) Color.TRANSPARENT else state.colorBg)

        binding.seekYOffset.value = state.yOffset.coerceIn(binding.seekYOffset.valueFrom, binding.seekYOffset.valueTo)
        binding.seekScale.value = state.scale.coerceIn(binding.seekScale.valueFrom, binding.seekScale.valueTo)

        binding.switchMondayFirst.isChecked = state.isMondayFirst
        binding.switchWeekNumbers.isChecked = state.showWeekNumbers
        binding.switchMinimalist.isChecked = state.isMinimalistEvents

        when (state.layoutStyle) {
            0 -> binding.rbStandard.isChecked = true
            1 -> binding.rbSplit.isChecked = true
            2 -> binding.rbWeekly.isChecked = true
        }

        when (state.fontStyle) {
            0 -> binding.toggleFont.check(binding.btnFontSans.id)
            1 -> binding.toggleFont.check(binding.btnFontSerif.id)
            2 -> binding.toggleFont.check(binding.btnFontMono.id)
        }

        renderCalendarsList(state.calendars, state.calendarEnabledMap, state.calendarColorMap)

        val currentMonth = java.time.YearMonth.now().monthValue
        binding.tvCalendarMonthTitle.text = "Tháng $currentMonth"

        updateTimelineCurrentTimeIndicator()
        renderTimelineEvents(state.events)

        state.userMessage?.let { message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            viewModel.userMessageShown()
        }

        isUpdatingUiFromState = false
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

                val activeRow = hourRows[currentHour] ?: binding.rowHour19
                val rowTop = activeRow.top

                val scrollViewHeight = binding.pageCalendar.height
                val rowHeight = activeRow.height

                val targetScrollY = (rowTop - (scrollViewHeight / 2) + (rowHeight / 2)).coerceAtLeast(0)

                binding.pageCalendar.smoothScrollTo(0, targetScrollY)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
                if (!showPastHours && hour < currentHour && !hasEvents) {
                    container.visibility = View.GONE
                } else {
                    container.visibility = View.VISIBLE
                }
            }

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

    private fun renderCalendarsList(
        calendars: List<CalendarModel>,
        enabledMap: Map<Long, Boolean>,
        colorMap: Map<Long, Int>
    ) {
        binding.llCalendarsContainer.removeAllViews()
        if (calendars.isEmpty()) {
            val tv = TextView(this).apply {
                text = "No calendars found."
                setTextColor(Color.GRAY)
            }
            binding.llCalendarsContainer.addView(tv)
            return
        }

        for (cal in calendars) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 16, 0, 16)
                gravity = Gravity.CENTER_VERTICAL
            }

            val cb = CheckBox(this).apply {
                text = "${cal.displayName}\n(${cal.accountName})"
                setTextColor(Color.WHITE)
                buttonTintList = ColorStateList.valueOf(Color.parseColor("#2196F3"))
                isChecked = enabledMap[cal.id] ?: true
                setOnCheckedChangeListener { _, isChecked ->
                    if (!isUpdatingUiFromState) {
                        viewModel.onCalendarToggleChanged(cal.id, isChecked)
                    }
                }
            }

            val calColor = colorMap[cal.id] ?: Color.parseColor("#00BCD4")
            val colorView = MaterialCardView(this).apply {
                radius = 32f
                setCardBackgroundColor(calColor)
                val params = LinearLayout.LayoutParams(64, 64).apply { marginEnd = 16 }
                layoutParams = params
                setOnClickListener {
                    ColorPickerDialog.show(this@MainActivity, "Pick color for ${cal.displayName}", calColor) { color ->
                        viewModel.onCalendarColorChanged(cal.id, color)
                    }
                }
            }

            row.addView(cb, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(colorView)
            binding.llCalendarsContainer.addView(row)
        }
    }

    private fun renderTimelineEvents(eventsMap: Map<Int, List<edu.hcmute.duan.data.model.EventModel>>) {
        binding.llCalendarEventsContainer.removeAllViews()

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

        val todayDay = java.time.LocalDate.now().dayOfMonth
        val todayEvents = eventsMap[todayDay] ?: emptyList()

        if (todayEvents.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "Hôm nay chưa có sự kiện nào. Bấm + để thêm!"
                setTextColor(Color.parseColor("#888888"))
                textSize = 14f
                setPadding(0, 8, 0, 8)
            }
            binding.llCalendarEventsContainer.addView(emptyTv)
            return
        }

        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val cal = Calendar.getInstance()

        for (event in todayEvents) {
            cal.timeInMillis = event.begin
            val startHour = cal.get(Calendar.HOUR_OF_DAY)

            val card = MaterialCardView(this).apply {
                radius = 28f
                setCardBackgroundColor(event.color)
                strokeWidth = 0
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 4
                    bottomMargin = 8
                }
                layoutParams = params
            }

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 18, 24, 18)
            }

            val titleTv = TextView(this).apply {
                text = event.title
                setTextColor(Color.WHITE)
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val timeStr = if (event.allDay) "🕒 Cả ngày" else "🕒 ${timeFormat.format(java.util.Date(event.begin))} - ${timeFormat.format(java.util.Date(event.end))}"
            val timeTv = TextView(this).apply {
                text = timeStr
                setTextColor(Color.parseColor("#F0F0F0"))
                textSize = 13f
                setPadding(0, 4, 0, 0)
            }

            container.addView(titleTv)
            container.addView(timeTv)
            card.addView(container)

            val targetSlot = slots[startHour]
            if (targetSlot != null) {
                targetSlot.addView(card)
            } else {
                binding.llCalendarEventsContainer.addView(card)
            }
        }
    }

    private val requestCalendarPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val readGranted = permissions[Manifest.permission.READ_CALENDAR] ?: false
            if (readGranted) {
                Toast.makeText(this, "Đã cấp quyền! Đang đồng bộ lịch từ điện thoại...", Toast.LENGTH_SHORT).show()
                viewModel.loadInitialData()
            } else {
                Toast.makeText(this, "Vui lòng cấp quyền Lịch trong Cài đặt máy để lấy các sự kiện của bạn", Toast.LENGTH_LONG).show()
            }
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
            requestCalendarPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun checkPermissionsAndInit() {
        checkAndRequestCalendarPermissions {
            viewModel.loadInitialData()
        }
    }
}
