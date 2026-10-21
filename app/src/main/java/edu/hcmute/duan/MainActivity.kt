package edu.hcmute.duan

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
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

        state.userMessage?.let { message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            viewModel.userMessageShown()
        }

        isUpdatingUiFromState = false
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
        val permissions = arrayOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )
        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            onGranted()
        } else {
            requestCalendarPermissionLauncher.launch(permissions)
        }
    }

    private fun checkPermissionsAndInit() {
        checkAndRequestCalendarPermissions {
            viewModel.loadInitialData()
        }
    }
}
