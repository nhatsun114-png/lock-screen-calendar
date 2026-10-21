package edu.hcmute.duan.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

class SettingsRepository(private val context: Context) {

    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences("CalendarPrefs", Context.MODE_PRIVATE)

    fun getColorPast(): Int = sharedPrefs.getInt("colorPast", Color.parseColor("#555555"))
    fun getColorFuture(): Int = sharedPrefs.getInt("colorFuture", Color.parseColor("#AAAAAA"))
    fun getColorToday(): Int = sharedPrefs.getInt("colorToday", Color.parseColor("#4CAF50"))
    fun getColorText(): Int = sharedPrefs.getInt("colorText", Color.WHITE)
    fun getColorBg(): Int = sharedPrefs.getInt("colorBg", Color.BLACK)
    fun isUsingImage(): Boolean = sharedPrefs.getBoolean("isUsingImage", false)

    fun getYOffset(): Float = try { sharedPrefs.getFloat("yOffset", 60f) } catch (e: ClassCastException) { 60f }
    fun getScale(): Float = try { sharedPrefs.getFloat("scale", 100f) } catch (e: ClassCastException) { 100f }

    fun isMondayFirst(): Boolean = sharedPrefs.getBoolean("mondayFirst", true)
    fun isShowWeekNumbers(): Boolean = sharedPrefs.getBoolean("weekNumbers", false)
    fun isMinimalistEvents(): Boolean = sharedPrefs.getBoolean("minimalistEvents", false)

    fun getLayoutStyle(): Int = sharedPrefs.getInt("layoutStyle", 0)
    fun getFontStyle(): Int = sharedPrefs.getInt("fontStyle", 0)

    fun isCalendarEnabled(calId: Long): Boolean =
        sharedPrefs.getBoolean("cal_enabled_$calId", true)

    fun setCalendarEnabled(calId: Long, enabled: Boolean) {
        sharedPrefs.edit().putBoolean("cal_enabled_$calId", enabled).apply()
    }

    fun getCalendarColor(calId: Long): Int =
        sharedPrefs.getInt("cal_color_$calId", Color.parseColor("#00BCD4"))

    fun setCalendarColor(calId: Long, color: Int) {
        sharedPrefs.edit().putInt("cal_color_$calId", color).apply()
    }

    fun saveSettings(
        colorPast: Int,
        colorFuture: Int,
        colorToday: Int,
        colorText: Int,
        colorBg: Int,
        isUsingImage: Boolean,
        yOffset: Float,
        scale: Float,
        mondayFirst: Boolean,
        weekNumbers: Boolean,
        minimalistEvents: Boolean,
        layoutStyle: Int,
        fontStyle: Int
    ) {
        sharedPrefs.edit().apply {
            putInt("colorPast", colorPast)
            putInt("colorFuture", colorFuture)
            putInt("colorToday", colorToday)
            putInt("colorText", colorText)
            putInt("colorBg", colorBg)
            putBoolean("isUsingImage", isUsingImage)
            putFloat("yOffset", yOffset)
            putFloat("scale", scale)
            putBoolean("mondayFirst", mondayFirst)
            putBoolean("weekNumbers", weekNumbers)
            putBoolean("minimalistEvents", minimalistEvents)
            putInt("layoutStyle", layoutStyle)
            putInt("fontStyle", fontStyle)
        }.apply()
    }

    fun saveCustomImage(uri: Uri, screenWidth: Int): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return false
            val bitmap = BitmapFactory.decodeStream(inputStream) ?: return false

            val scaledBitmap = Bitmap.createScaledBitmap(
                bitmap,
                screenWidth,
                (bitmap.height * (screenWidth.toFloat() / bitmap.width)).toInt(),
                true
            )

            val file = File(context.filesDir, "custom_bg.jpg")
            val outputStream = FileOutputStream(file)
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)

            outputStream.flush()
            outputStream.close()
            inputStream.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getCustomImageBitmap(): Bitmap? {
        val file = File(context.filesDir, "custom_bg.jpg")
        return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    fun resetToDefaults() {
        sharedPrefs.edit().clear().apply()
        val file = File(context.filesDir, "custom_bg.jpg")
        if (file.exists()) file.delete()
    }
}
