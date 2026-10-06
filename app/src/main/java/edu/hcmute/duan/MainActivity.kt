package edu.hcmute.duan

import android.Manifest
import android.app.WallpaperManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import edu.hcmute.duan.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    
    private var colorPast = Color.parseColor("#555555")
    private var colorFuture = Color.parseColor("#AAAAAA")
    private var colorToday = Color.parseColor("#4CAF50")
    private var colorText = Color.parseColor("#FFFFFF")
    private var colorBg = Color.parseColor("#000000")
    private var isUsingImage = false
    
    private var events: Map<Int, List<EventModel>> = emptyMap()

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            saveImageToInternalStorage(it)
            isUsingImage = true
            savePrefs()
            updateColorUIs()
            updatePreview()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPrefs = getSharedPreferences("CalendarPrefs", MODE_PRIVATE)
        
        // Init Bottom Sheet
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenHeight = displayMetrics.heightPixels
        
        // Đặt chiều cao bảng điều khiển là 55% màn hình
        bottomSheetBehavior.maxHeight = (screenHeight * 0.55).toInt()

        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {}

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                val safeOffset = if (slideOffset < 0f) 0f else slideOffset
                
                // Thu nhỏ khung Preview xuống còn đúng 45% (vừa khít với 55% của bảng điều khiển)
                // Như vậy 45 + 55 = 100% màn hình -> Không bao giờ che lấp nhau!
                val scale = 1f - (safeOffset * 0.55f) 
                
                binding.mainContent.pivotX = binding.mainContent.width / 2f
                binding.mainContent.pivotY = 0f // Tâm thu nhỏ dính chặt mép trên cùng màn hình
                binding.mainContent.scaleX = scale
                binding.mainContent.scaleY = scale
                
                // Tắt translation vì pivotY = 0 đã tự động dồn nội dung lên trên cùng rồi
                binding.mainContent.translationY = 0f
                
                binding.btnCustomize.alpha = 1f - safeOffset
            }
        })

        binding.btnCustomize.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        loadPrefs()
        setupListeners()
        updateColorUIs()
        
        checkPermissionsAndLoadEvents()
        scheduleDailyCalendarUpdate()
    }

    private fun scheduleDailyCalendarUpdate() {
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
            
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "DailyCalendarUpdate",
            ExistingPeriodicWorkPolicy.UPDATE,
            updateRequest
        )
    }

    private fun loadPrefs() {
        colorPast = sharedPrefs.getInt("colorPast", Color.parseColor("#555555"))
        colorFuture = sharedPrefs.getInt("colorFuture", Color.parseColor("#AAAAAA"))
        colorToday = sharedPrefs.getInt("colorToday", Color.parseColor("#4CAF50"))
        colorText = sharedPrefs.getInt("colorText", Color.WHITE)
        colorBg = sharedPrefs.getInt("colorBg", Color.BLACK)
        isUsingImage = sharedPrefs.getBoolean("isUsingImage", false)
        
        binding.seekYOffset.value = try { sharedPrefs.getFloat("yOffset", 60f) } catch (e: ClassCastException) { 60f }
        binding.seekScale.value = try { sharedPrefs.getFloat("scale", 100f) } catch (e: ClassCastException) { 100f }
        
        binding.switchMondayFirst.isChecked = sharedPrefs.getBoolean("mondayFirst", true)
        binding.switchWeekNumbers.isChecked = sharedPrefs.getBoolean("weekNumbers", false)
        binding.switchMinimalist.isChecked = sharedPrefs.getBoolean("minimalistEvents", false)
        
        when (sharedPrefs.getInt("layoutStyle", 0)) {
            0 -> binding.rbStandard.isChecked = true
            1 -> binding.rbSplit.isChecked = true
            2 -> binding.rbWeekly.isChecked = true
        }

        when (sharedPrefs.getInt("fontStyle", 0)) {
            0 -> binding.toggleFont.check(binding.btnFontSans.id)
            1 -> binding.toggleFont.check(binding.btnFontSerif.id)
            2 -> binding.toggleFont.check(binding.btnFontMono.id)
        }
    }

    private fun savePrefs() {
        val layoutStyle = when {
            binding.rbStandard.isChecked -> 0
            binding.rbSplit.isChecked -> 1
            binding.rbWeekly.isChecked -> 2
            else -> 0
        }
        val fontStyle = when (binding.toggleFont.checkedButtonId) {
            binding.btnFontSans.id -> 0
            binding.btnFontSerif.id -> 1
            binding.btnFontMono.id -> 2
            else -> 0
        }
        sharedPrefs.edit().apply {
            putInt("colorPast", colorPast)
            putInt("colorFuture", colorFuture)
            putInt("colorToday", colorToday)
            putInt("colorText", colorText)
            putInt("colorBg", colorBg)
            putBoolean("isUsingImage", isUsingImage)
            putFloat("yOffset", binding.seekYOffset.value)
            putFloat("scale", binding.seekScale.value)
            putBoolean("mondayFirst", binding.switchMondayFirst.isChecked)
            putBoolean("weekNumbers", binding.switchWeekNumbers.isChecked)
            putBoolean("minimalistEvents", binding.switchMinimalist.isChecked)
            putInt("layoutStyle", layoutStyle)
            putInt("fontStyle", fontStyle)
        }.apply()
    }

    private fun setupListeners() {
        // Colors Setup
        binding.rowColorPast.setOnClickListener { showColorPicker("Past Day Color", colorPast) { c -> colorPast = c; applyColorChange() } }
        binding.rowColorFuture.setOnClickListener { showColorPicker("Future Day Color", colorFuture) { c -> colorFuture = c; applyColorChange() } }
        binding.rowColorToday.setOnClickListener { showColorPicker("Today Highlight Color", colorToday) { c -> colorToday = c; applyColorChange() } }
        binding.rowColorText.setOnClickListener { showColorPicker("Text & Numbers Color", colorText) { c -> colorText = c; applyColorChange() } }
        binding.rowColorBg.setOnClickListener { showColorPicker("Background Color", colorBg) { c -> colorBg = c; isUsingImage = false; applyColorChange() } }
        
        binding.btnPickImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        binding.btnRefreshCalendars.setOnClickListener {
            populateCalendars()
        }

        val sliderListener = object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) { savePrefs(); updatePreview() }
        }
        binding.seekYOffset.addOnSliderTouchListener(sliderListener)
        binding.seekScale.addOnSliderTouchListener(sliderListener)

        val switchListener = { _: Any, _: Any -> savePrefs(); updatePreview() }
        binding.switchMondayFirst.setOnCheckedChangeListener(switchListener)
        binding.switchWeekNumbers.setOnCheckedChangeListener(switchListener)
        binding.switchMinimalist.setOnCheckedChangeListener(switchListener)
        binding.rgLayoutStyle.setOnCheckedChangeListener { _, _ -> savePrefs(); updatePreview() }
        binding.toggleFont.addOnButtonCheckedListener { _, _, isChecked -> if(isChecked) { savePrefs(); updatePreview() } }

        binding.btnUpdateWallpaper.setOnClickListener {
            setLockScreenWallpaper()
        }
        
        binding.btnSetWallpaper.setOnClickListener {
            // Chỉ đóng BottomSheet lại (Cấu hình đã được tự động lưu mỗi khi thay đổi)
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            Toast.makeText(this, "Configuration saved! Press 'Update' to apply to Lockscreen.", Toast.LENGTH_SHORT).show()
        }
        
        binding.btnReset.setOnClickListener {
            sharedPrefs.edit().clear().apply()
            val file = File(filesDir, "custom_bg.jpg")
            if (file.exists()) file.delete()
            loadPrefs()
            updateColorUIs()
            populateCalendars()
            updatePreview()
            Toast.makeText(this, "Reset to defaults", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun applyColorChange() {
        savePrefs()
        updateColorUIs()
        updatePreview()
    }

    private fun updateColorUIs() {
        binding.ivColorPast.setCardBackgroundColor(colorPast)
        binding.ivColorFuture.setCardBackgroundColor(colorFuture)
        binding.ivColorToday.setCardBackgroundColor(colorToday)
        binding.ivColorText.setCardBackgroundColor(colorText)
        binding.ivColorBg.setCardBackgroundColor(if (isUsingImage) Color.TRANSPARENT else colorBg)
    }

    private fun showColorPicker(title: String, currentColor: Int, onColorPicked: (Int) -> Unit) {
        val colors = intArrayOf(
            0xFFF44336.toInt(), 0xFFE91E63.toInt(), 0xFF9C27B0.toInt(), 0xFF673AB7.toInt(),
            0xFF3F51B5.toInt(), 0xFF2196F3.toInt(), 0xFF03A9F4.toInt(), 0xFF00BCD4.toInt(),
            0xFF009688.toInt(), 0xFF4CAF50.toInt(), 0xFF8BC34A.toInt(), 0xFFCDDC39.toInt(),
            0xFFFFEB3B.toInt(), 0xFFFFC107.toInt(), 0xFFFF9800.toInt(), 0xFFFF5722.toInt(),
            0xFF795548.toInt(), 0xFF9E9E9E.toInt(), 0xFF607D8B.toInt(), 0xFFFFFFFF.toInt(),
            0xFF888888.toInt(), 0xFF444444.toInt(), 0xFF000000.toInt(), Color.TRANSPARENT
        )
        
        val grid = GridLayout(this).apply {
            columnCount = 6
            setPadding(32, 32, 32, 32)
        }
        
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(grid)
            .setNegativeButton("Cancel", null)
            .show()
            
        for (color in colors) {
            val card = MaterialCardView(this).apply {
                radius = 36f
                setCardBackgroundColor(color)
                strokeWidth = if (color == Color.TRANSPARENT) 4 else 0
                strokeColor = Color.DKGRAY
                val params = GridLayout.LayoutParams().apply {
                    width = 80
                    height = 80
                    setMargins(16, 16, 16, 16)
                }
                layoutParams = params
                setOnClickListener {
                    onColorPicked(color)
                    dialog.dismiss()
                }
            }
            grid.addView(card)
        }
    }

    private fun populateCalendars() {
        binding.llCalendarsContainer.removeAllViews()
        val cals = CalendarHelper.getAvailableCalendars(this)
        if (cals.isEmpty()) {
            val tv = TextView(this).apply {
                text = "No calendars found."
                setTextColor(Color.GRAY)
            }
            binding.llCalendarsContainer.addView(tv)
            return
        }
        
        for (cal in cals) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 16, 0, 16)
                gravity = Gravity.CENTER_VERTICAL
            }
            
            val cb = CheckBox(this).apply {
                text = "${cal.displayName}\n(${cal.accountName})"
                setTextColor(Color.WHITE)
                buttonTintList = ColorStateList.valueOf(Color.parseColor("#2196F3"))
                isChecked = sharedPrefs.getBoolean("cal_enabled_${cal.id}", true)
                setOnCheckedChangeListener { _, isChecked -> 
                    sharedPrefs.edit().putBoolean("cal_enabled_${cal.id}", isChecked).apply()
                    loadEvents()
                }
            }
            
            val colorView = MaterialCardView(this).apply {
                radius = 32f
                setCardBackgroundColor(sharedPrefs.getInt("cal_color_${cal.id}", Color.parseColor("#00BCD4")))
                val params = LinearLayout.LayoutParams(64, 64).apply { marginEnd = 16 }
                layoutParams = params
                setOnClickListener {
                    showColorPicker("Pick color for ${cal.displayName}", Color.CYAN) { color ->
                        setCardBackgroundColor(color)
                        sharedPrefs.edit().putInt("cal_color_${cal.id}", color).apply()
                        updatePreview()
                    }
                }
            }
            
            row.addView(cb, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(colorView)
            binding.llCalendarsContainer.addView(row)
        }
    }

    private fun saveImageToInternalStorage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            val screenWidth = displayMetrics.widthPixels
            
            val scaledBitmap = Bitmap.createScaledBitmap(
                bitmap, screenWidth, (bitmap.height * (screenWidth.toFloat() / bitmap.width)).toInt(), true
            )

            val file = File(filesDir, "custom_bg.jpg")
            val outputStream = FileOutputStream(file)
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            
            outputStream.flush()
            outputStream.close()
            inputStream?.close()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun getCustomImageBitmap(): Bitmap? {
        val file = File(filesDir, "custom_bg.jpg")
        return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    private fun checkPermissionsAndLoadEvents() {
        val permissions = arrayOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.SET_WALLPAPER
        )

        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            populateCalendars()
            loadEvents()
        } else {
            ActivityCompat.requestPermissions(this, permissions, 100)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            populateCalendars()
            loadEvents()
        } else {
            Toast.makeText(this, "Permissions required for calendar!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadEvents() {
        lifecycleScope.launch(Dispatchers.IO) {
            val selectedCals = mutableSetOf<String>()
            val cals = CalendarHelper.getAvailableCalendars(this@MainActivity)
            for (cal in cals) {
                if (sharedPrefs.getBoolean("cal_enabled_${cal.id}", true)) {
                    selectedCals.add(cal.id.toString())
                }
            }
            events = CalendarHelper.getEventsForCurrentMonth(this@MainActivity, selectedCals)
            withContext(Dispatchers.Main) {
                updatePreview()
            }
        }
    }

    private fun buildThemeConfig(): WallpaperTheme {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        
        val layoutStyle = when {
            binding.rbStandard.isChecked -> 0
            binding.rbSplit.isChecked -> 1
            binding.rbWeekly.isChecked -> 2
            else -> 0
        }
        val fontStyle = when (binding.toggleFont.checkedButtonId) {
            binding.btnFontSans.id -> 0
            binding.btnFontSerif.id -> 1
            binding.btnFontMono.id -> 2
            else -> 0
        }
        
        val customColors = mutableMapOf<Long, Int>()
        val cals = CalendarHelper.getAvailableCalendars(this)
        for (cal in cals) {
            customColors[cal.id] = sharedPrefs.getInt("cal_color_${cal.id}", Color.parseColor("#00BCD4"))
        }

        return WallpaperTheme(
            width = displayMetrics.widthPixels,
            height = displayMetrics.heightPixels,
            layoutStyle = layoutStyle,
            fontStyle = fontStyle,
            isMondayFirst = binding.switchMondayFirst.isChecked,
            showWeekNumbers = binding.switchWeekNumbers.isChecked,
            isMinimalist = binding.switchMinimalist.isChecked,
            yOffsetPercent = binding.seekYOffset.value.toInt(),
            sizeScalePercent = binding.seekScale.value.toInt(),
            colorBg = colorBg,
            colorText = colorText,
            colorToday = colorToday,
            colorPast = colorPast,
            colorFuture = colorFuture,
            bgImage = if (isUsingImage) getCustomImageBitmap() else null,
            customCalColors = customColors
        )
    }

    private fun updatePreview() {
        val theme = buildThemeConfig()
        if (theme.width == 0 || theme.height == 0) return
        
        when (theme.layoutStyle) {
            0 -> binding.tvStyleName.text = "Month agenda"
            1 -> binding.tvStyleName.text = "Split clock hole"
            2 -> binding.tvStyleName.text = "Weekly tasks"
        }
        
        val bitmap = CalendarWallpaperRenderer.createCalendarBitmap(theme, events)
        binding.ivPreview.setImageBitmap(bitmap)
    }

    private fun setLockScreenWallpaper() {
        val theme = buildThemeConfig()
        val bitmap = CalendarWallpaperRenderer.createCalendarBitmap(theme, events)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val wallpaperManager = WallpaperManager.getInstance(applicationContext)
                wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Lockscreen updated successfully!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
