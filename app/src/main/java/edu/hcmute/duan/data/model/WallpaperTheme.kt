package edu.hcmute.duan.data.model

import android.graphics.Bitmap

data class WallpaperTheme(
    val width: Int,
    val height: Int,
    val layoutStyle: Int,
    val fontStyle: Int,
    val isMondayFirst: Boolean,
    val showWeekNumbers: Boolean,
    val isMinimalist: Boolean,
    val yOffsetPercent: Int,
    val sizeScalePercent: Int,
    val colorBg: Int,
    val colorText: Int,
    val colorToday: Int,
    val colorPast: Int,
    val colorFuture: Int,
    val bgImage: Bitmap?,
    val customCalColors: Map<Long, Int>
)
