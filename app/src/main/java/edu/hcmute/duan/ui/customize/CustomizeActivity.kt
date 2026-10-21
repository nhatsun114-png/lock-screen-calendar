package edu.hcmute.duan.ui.customize

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import edu.hcmute.duan.data.repository.CalendarRepository
import edu.hcmute.duan.data.repository.SettingsRepository
import edu.hcmute.duan.data.repository.WallpaperRepository
import edu.hcmute.duan.databinding.ActivityCustomizeBinding
import edu.hcmute.duan.ui.dialog.ColorPickerDialog
import edu.hcmute.duan.ui.main.MainUiState
import edu.hcmute.duan.ui.main.MainViewModel
import edu.hcmute.duan.ui.main.MainViewModelFactory
import edu.hcmute.duan.util.LocaleHelper
import kotlinx.coroutines.launch

class CustomizeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomizeBinding

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
        super.onCreate(savedInstanceState)
        binding = ActivityCustomizeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarCustomize.setNavigationOnClickListener { finish() }

        initDisplayMetrics()
        setupListeners()
        observeViewModel()
        viewModel.loadInitialData()
    }

    private fun initDisplayMetrics() {
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        viewModel.initScreenDimensions(displayMetrics.widthPixels, displayMetrics.heightPixels)
    }

    private fun setupListeners() {
        binding.rowColorPast.setOnClickListener {
            ColorPickerDialog.show(this, "Màu ngày đã qua", viewModel.uiState.value.colorPast) {
                viewModel.onColorPastChanged(it)
            }
        }
        binding.rowColorFuture.setOnClickListener {
            ColorPickerDialog.show(this, "Màu ngày sắp tới", viewModel.uiState.value.colorFuture) {
                viewModel.onColorFutureChanged(it)
            }
        }
        binding.rowColorToday.setOnClickListener {
            ColorPickerDialog.show(this, "Màu hôm nay", viewModel.uiState.value.colorToday) {
                viewModel.onColorTodayChanged(it)
            }
        }
        binding.rowColorText.setOnClickListener {
            ColorPickerDialog.show(this, "Màu chữ & số", viewModel.uiState.value.colorText) {
                viewModel.onColorTextChanged(it)
            }
        }
        binding.rowColorBg.setOnClickListener {
            ColorPickerDialog.show(this, "Màu nền", viewModel.uiState.value.colorBg) {
                viewModel.onColorBgChanged(it)
            }
        }

        binding.btnPickImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        binding.seekYOffset.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.onYOffsetChanged(value)
        }
        binding.seekScale.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.onScaleChanged(value)
        }

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
                updateFontButtonColors(checkedId)
                val fontStyle = when (checkedId) {
                    binding.btnFontSans.id -> 0
                    binding.btnFontSerif.id -> 1
                    binding.btnFontMono.id -> 2
                    else -> 0
                }
                viewModel.onFontStyleChanged(fontStyle)
            }
        }

        binding.btnApplyWallpaper.setOnClickListener {
            viewModel.applyWallpaperToLockScreen()
            Toast.makeText(this, "Đã cập nhật hình nền khóa!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateFontButtonColors(checkedId: Int) {
        binding.btnFontSans.setTextColor(if (checkedId == binding.btnFontSans.id) Color.parseColor("#3F51B5") else Color.BLACK)
        binding.btnFontSerif.setTextColor(if (checkedId == binding.btnFontSerif.id) Color.parseColor("#3F51B5") else Color.BLACK)
        binding.btnFontMono.setTextColor(if (checkedId == binding.btnFontMono.id) Color.parseColor("#3F51B5") else Color.BLACK)
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

        state.previewBitmap?.let { bitmap ->
            binding.ivCustomizePreview.setImageBitmap(bitmap)
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
        updateFontButtonColors(binding.toggleFont.checkedButtonId)

        state.userMessage?.let { message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            viewModel.userMessageShown()
        }

        isUpdatingUiFromState = false
    }
}
