package edu.hcmute.duan

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import edu.hcmute.duan.data.model.WallpaperTheme
import java.io.File

class CalendarUpdateWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            val sharedPrefs = context.getSharedPreferences("CalendarPrefs", Context.MODE_PRIVATE)

            // Read configurations
            val colorBg = sharedPrefs.getInt("colorBg", Color.BLACK)
            val colorText = sharedPrefs.getInt("colorText", Color.WHITE)
            val colorToday = sharedPrefs.getInt("colorToday", Color.parseColor("#4CAF50"))
            val colorPast = sharedPrefs.getInt("colorPast", Color.parseColor("#555555"))
            val colorFuture = sharedPrefs.getInt("colorFuture", Color.parseColor("#AAAAAA"))

            val yOffset = sharedPrefs.getFloat("yOffset", 60f).toInt()
            val scale = sharedPrefs.getFloat("scale", 100f).toInt()
            val isMondayFirst = sharedPrefs.getBoolean("mondayFirst", true)
            val showWeekNumbers = sharedPrefs.getBoolean("weekNumbers", false)
            val layoutStyle = sharedPrefs.getInt("layoutStyle", 0)
            val fontStyle = sharedPrefs.getInt("fontStyle", 0)
            val isMinimalist = sharedPrefs.getBoolean("minimalistEvents", false)
            val isUsingImage = sharedPrefs.getBoolean("isUsingImage", false)

            val calendars = CalendarHelper.getAvailableCalendars(context)
            val selectedCals = mutableSetOf<String>()
            val customCalColors = mutableMapOf<Long, Int>()

            for (cal in calendars) {
                if (sharedPrefs.getBoolean("cal_enabled_${cal.id}", true)) {
                    selectedCals.add(cal.id.toString())
                    customCalColors[cal.id] = sharedPrefs.getInt("cal_color_${cal.id}", Color.parseColor("#00BCD4"))
                }
            }

            // Fetch latest events
            val events = CalendarHelper.getEventsForCurrentMonth(context, selectedCals)

            // Load Custom Image
            var bgBitmap: Bitmap? = null
            if (isUsingImage) {
                val file = File(context.filesDir, "custom_bg.jpg")
                if (file.exists()) {
                    bgBitmap = BitmapFactory.decodeFile(file.absolutePath)
                }
            }

            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)

            val theme = WallpaperTheme(
                width = displayMetrics.widthPixels,
                height = displayMetrics.heightPixels,
                layoutStyle = layoutStyle,
                fontStyle = fontStyle,
                isMondayFirst = isMondayFirst,
                showWeekNumbers = showWeekNumbers,
                isMinimalist = isMinimalist,
                yOffsetPercent = yOffset,
                sizeScalePercent = scale,
                colorBg = colorBg,
                colorText = colorText,
                colorToday = colorToday,
                colorPast = colorPast,
                colorFuture = colorFuture,
                bgImage = bgBitmap,
                customCalColors = customCalColors
            )

            // Render and Apply
            val bitmap = CalendarWallpaperRenderer.createCalendarBitmap(theme, events)
            val wallpaperManager = WallpaperManager.getInstance(context)
            wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)

            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }
}
