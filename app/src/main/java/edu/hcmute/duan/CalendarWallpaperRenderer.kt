package edu.hcmute.duan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import edu.hcmute.duan.data.model.EventModel
import edu.hcmute.duan.data.model.WallpaperTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Calendar
import java.util.Locale

object CalendarWallpaperRenderer {

    fun createCalendarBitmap(
        theme: WallpaperTheme,
        events: Map<Int, List<EventModel>>
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(theme.width, theme.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        if (theme.bgImage != null) {
            val srcRect = Rect(0, 0, theme.bgImage.width, theme.bgImage.height)
            val dstRect = Rect(0, 0, theme.width, theme.height)
            canvas.drawBitmap(theme.bgImage, srcRect, dstRect, null)
            val overlayPaint = Paint().apply { color = Color.parseColor("#99000000") }
            canvas.drawRect(0f, 0f, theme.width.toFloat(), theme.height.toFloat(), overlayPaint)
        } else {
            canvas.drawColor(theme.colorBg)
        }

        val baseTypeface = when (theme.fontStyle) {
            1 -> Typeface.SERIF
            2 -> Typeface.MONOSPACE
            else -> Typeface.SANS_SERIF
        }

        if (theme.layoutStyle == 2) {
            drawWeeklyAndTasksLayout(canvas, theme, baseTypeface, events)
        } else {
            drawMonthlyLayout(canvas, theme, baseTypeface, theme.layoutStyle == 1, events)
        }

        return bitmap
    }

    private fun getTodayLocalDate(): LocalDate {
        val cal = Calendar.getInstance()
        return LocalDate.of(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun drawWeeklyAndTasksLayout(
        canvas: Canvas,
        theme: WallpaperTheme,
        baseTypeface: Typeface,
        events: Map<Int, List<EventModel>>
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val today = getTodayLocalDate()
        val yearMonth = YearMonth.of(today.year, today.month)
        
        val baseTextSize = theme.width * 0.04f * (theme.sizeScalePercent / 100f)
        val headerTextSize = theme.width * 0.08f * (theme.sizeScalePercent / 100f)
        
        val margin = theme.width * 0.05f
        var currentY = theme.height * (theme.yOffsetPercent / 100f)

        // 1. Draw Month Name
        paint.color = theme.colorText
        paint.textSize = headerTextSize
        paint.typeface = Typeface.create(baseTypeface, Typeface.BOLD)
        val monthName = yearMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        canvas.drawText("$monthName ${yearMonth.year}", margin, currentY, paint)
        
        currentY += headerTextSize * 1.5f

        // Calculate Week Range
        val currentDayOfWeek = today.dayOfWeek.value // 1 (Mon) to 7 (Sun)
        val offsetToStart = if (theme.isMondayFirst) currentDayOfWeek - 1 else if (currentDayOfWeek == 7) 0 else currentDayOfWeek
        val startOfWeek = today.minusDays(offsetToStart.toLong())

        // 2. Draw Weekdays Header & Dates
        val weekDays = if (theme.isMondayFirst) listOf("M", "T", "W", "T", "F", "S", "S") else listOf("S", "M", "T", "W", "T", "F", "S")
        val gridWidth = theme.width - (2 * margin)
        val columnWidth = gridWidth / 7
        
        var currentX = margin
        for (i in 0..6) {
            val dateOfColumn = startOfWeek.plusDays(i.toLong())
            val isToday = dateOfColumn == today
            val isPast = dateOfColumn.isBefore(today)

            // Draw Day Letter
            paint.color = theme.colorText
            paint.alpha = if (isPast) 120 else 255
            paint.textSize = baseTextSize * 0.8f
            paint.typeface = Typeface.create(baseTypeface, Typeface.NORMAL)
            canvas.drawText(weekDays[i], currentX + columnWidth/2f - paint.measureText(weekDays[i])/2f, currentY, paint)
            paint.alpha = 255
            
            // Draw Highlight for Today
            if (isToday) {
                paint.color = theme.colorToday
                val rect = RectF(currentX + 8f, currentY + 16f, currentX + columnWidth - 8f, currentY + 16f + baseTextSize * 2f)
                canvas.drawRoundRect(rect, 16f, 16f, paint)
            }
            
            // Draw Date Number
            paint.color = if (isToday) (if (isDark(theme.colorToday)) Color.WHITE else Color.BLACK) else if (isPast) theme.colorPast else theme.colorFuture
            paint.textSize = baseTextSize * 1.2f
            paint.typeface = if (isToday) Typeface.create(baseTypeface, Typeface.BOLD) else Typeface.create(baseTypeface, Typeface.NORMAL)
            val dateStr = dateOfColumn.dayOfMonth.toString()
            canvas.drawText(dateStr, currentX + columnWidth/2f - paint.measureText(dateStr)/2f, currentY + 16f + baseTextSize * 1.4f, paint)
            
            currentX += columnWidth
        }

        currentY += baseTextSize * 4f

        // 3. Draw "Today's Tasks" Header
        paint.color = theme.colorText
        paint.textSize = baseTextSize * 1.2f
        paint.typeface = Typeface.create(baseTypeface, Typeface.BOLD)
        canvas.drawText("TODAY'S TASKS", margin, currentY, paint)
        
        currentY += baseTextSize * 1.5f

        // 4. Draw Today's Events
        val todayEvents = events[today.dayOfMonth] ?: emptyList()
        
        if (todayEvents.isEmpty()) {
            paint.color = theme.colorPast
            paint.textSize = baseTextSize
            paint.typeface = Typeface.create(baseTypeface, Typeface.ITALIC)
            canvas.drawText("No tasks for today. Enjoy your day!", margin, currentY + baseTextSize, paint)
        } else {
            for (event in todayEvents) {
                val eventColor = theme.customCalColors[event.calendarId] ?: event.color
            
                // Draw pill background
                paint.color = eventColor
                paint.alpha = 60
                val rect = RectF(margin, currentY, theme.width - margin, currentY + baseTextSize * 2.5f)
                canvas.drawRoundRect(rect, 24f, 24f, paint)
                
                // Draw color strip on the left
                paint.alpha = 255
                val stripRect = RectF(margin, currentY, margin + 12f, currentY + baseTextSize * 2.5f)
                canvas.drawRoundRect(stripRect, 12f, 12f, paint)
                
                // Draw Event Title
                paint.color = theme.colorText
                paint.textSize = baseTextSize * 0.9f
                paint.typeface = Typeface.create(baseTypeface, Typeface.BOLD)
                val titleStr = if (event.title.length > 30) event.title.take(28) + ".." else event.title
                canvas.drawText(titleStr, margin + 32f, currentY + baseTextSize * 1.1f, paint)
                
                // Draw Time
                paint.color = theme.colorFuture
                paint.textSize = baseTextSize * 0.7f
                paint.typeface = Typeface.create(baseTypeface, Typeface.NORMAL)
                val timeStr = if (event.allDay) "All day" else "Scheduled"
                canvas.drawText(timeStr, margin + 32f, currentY + baseTextSize * 2.0f, paint)
                
                currentY += baseTextSize * 3f
            }
        }
    }

    private fun drawMonthlyLayout(
        canvas: Canvas,
        theme: WallpaperTheme,
        baseTypeface: Typeface,
        isSplitLayout: Boolean,
        events: Map<Int, List<EventModel>>
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val today = getTodayLocalDate()
        val yearMonth = YearMonth.of(today.year, today.month)
        
        val baseTextSize = theme.width * 0.04f * (theme.sizeScalePercent / 100f)
        val headerTextSize = theme.width * 0.08f * (theme.sizeScalePercent / 100f)
        
        val margin = theme.width * 0.05f
        val startY = theme.height * (theme.yOffsetPercent / 100f)
        var currentY = startY

        // Calculate Layout Widths
        val gapWidth = if (isSplitLayout) theme.width * 0.35f else 0f
        val gridWidth = theme.width - (2 * margin) - gapWidth
        
        val columnCount = if (theme.showWeekNumbers) 8 else 7
        val columnWidth = gridWidth / columnCount

        // 1. Draw Month Name
        paint.color = theme.colorText
        paint.textSize = headerTextSize
        paint.typeface = Typeface.create(baseTypeface, Typeface.BOLD)
        val monthName = yearMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        
        if (isSplitLayout) {
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("$monthName ${yearMonth.year}", theme.width / 2f, currentY + baseTextSize, paint)
            paint.textAlign = Paint.Align.LEFT
        } else {
            canvas.drawText("$monthName ${yearMonth.year}", margin, currentY, paint)
        }
        
        currentY += headerTextSize * 1.5f

        // 2. Draw Weekdays Header
        val weekDays = if (theme.isMondayFirst) listOf("M", "T", "W", "T", "F", "S", "S") else listOf("S", "M", "T", "W", "T", "F", "S")
        
        paint.color = theme.colorText
        paint.textSize = baseTextSize
        paint.typeface = Typeface.create(baseTypeface, Typeface.NORMAL)
        
        var currentX = margin
        if (theme.showWeekNumbers) {
            paint.color = theme.colorPast
            canvas.drawText("#", currentX + columnWidth/2f - paint.measureText("#")/2f, currentY, paint)
            currentX += columnWidth
            paint.color = theme.colorText
        }

        for ((index, dayName) in weekDays.withIndex()) {
            if (isSplitLayout && index == 3) {
                currentX += gapWidth
            }
            canvas.drawText(dayName, currentX + columnWidth/2f - paint.measureText(dayName)/2f, currentY, paint)
            currentX += columnWidth
        }

        currentY += baseTextSize * 1.5f

        // 3. Draw Grid
        val daysInMonth = yearMonth.lengthOfMonth()
        val firstDayOfWeek = yearMonth.atDay(1).dayOfWeek.value // 1 (Mon) to 7 (Sun)
        
        var startOffset = firstDayOfWeek
        if (theme.isMondayFirst) {
            startOffset = firstDayOfWeek - 1
        } else {
            startOffset = if (firstDayOfWeek == 7) 0 else firstDayOfWeek
        }

        val rowHeight = theme.width * 0.15f * (theme.sizeScalePercent / 100f)
        
        var dayCounter = 1
        var weekNumber = yearMonth.atDay(1).get(WeekFields.of(Locale.getDefault()).weekOfYear())

        for (row in 0..5) {
            currentX = margin
            
            if (theme.showWeekNumbers) {
                if (dayCounter <= daysInMonth || row == 0) {
                    paint.color = theme.colorPast
                    val wnStr = weekNumber.toString()
                    canvas.drawText(wnStr, currentX + columnWidth/2f - paint.measureText(wnStr)/2f, currentY + baseTextSize, paint)
                    weekNumber++
                }
                currentX += columnWidth
            }

            for (col in 0..6) {
                if (isSplitLayout && col == 3) {
                    currentX += gapWidth
                }

                if ((row == 0 && col < startOffset) || dayCounter > daysInMonth) {
                    currentX += columnWidth
                    continue
                }

                val dateCounter = yearMonth.atDay(dayCounter)
                val isToday = dateCounter == today
                val isPast = dateCounter.isBefore(today)

                if (isToday) {
                    paint.color = theme.colorToday
                    val rect = RectF(currentX, currentY - baseTextSize, currentX + columnWidth - 4f, currentY + rowHeight - 4f)
                    canvas.drawRoundRect(rect, 16f, 16f, paint)
                }

                paint.color = if (isToday) (if (isDark(theme.colorToday)) Color.WHITE else Color.BLACK) else if (isPast) theme.colorPast else theme.colorFuture
                paint.typeface = if (isToday) Typeface.create(baseTypeface, Typeface.BOLD) else Typeface.create(baseTypeface, Typeface.NORMAL)
                canvas.drawText(dayCounter.toString(), currentX + 8f, currentY + baseTextSize, paint)

                val dayEvents = events[dayCounter]
                if (dayEvents != null) {
                    var eventY = currentY + baseTextSize + 10f
                    val maxEvents = minOf(dayEvents.size, 2)
                    
                    if (theme.isMinimalist) {
                        val dotRadius = baseTextSize * 0.2f
                        var dotX = currentX + 8f + dotRadius
                        for (i in 0 until minOf(dayEvents.size, 4)) {
                            val eventColor = theme.customCalColors[dayEvents[i].calendarId] ?: dayEvents[i].color
                            paint.color = eventColor
                            paint.alpha = if (isPast) 120 else 255
                            canvas.drawCircle(dotX, eventY + 4f, dotRadius, paint)
                            dotX += dotRadius * 3f
                        }
                        paint.alpha = 255
                    } else {
                        for (i in 0 until maxEvents) {
                            val event = dayEvents[i]
                            val eventColor = theme.customCalColors[event.calendarId] ?: event.color
                            paint.color = eventColor
                            paint.alpha = if (isPast) 80 else 180
                            val pillRect = RectF(currentX + 4f, eventY, currentX + columnWidth - 8f, eventY + baseTextSize * 0.8f)
                            canvas.drawRoundRect(pillRect, 8f, 8f, paint)
                            
                            paint.color = if (isDark(eventColor)) Color.WHITE else Color.BLACK
                            paint.alpha = if (isPast) 120 else 255
                            paint.textSize = baseTextSize * 0.6f
                            val titleStr = if (event.title.length > 8) event.title.take(6) + ".." else event.title
                            canvas.drawText(titleStr, currentX + 8f, eventY + baseTextSize * 0.6f, paint)
                            
                            eventY += baseTextSize * 0.9f
                        }
                        paint.alpha = 255
                        if (dayEvents.size > 2) {
                            paint.color = theme.colorPast
                            canvas.drawText("+${dayEvents.size - 2} more", currentX + 8f, eventY + baseTextSize * 0.5f, paint)
                        }
                    }
                }
                paint.textSize = baseTextSize // reset
                currentX += columnWidth
                dayCounter++
            }
            currentY += rowHeight
            if (dayCounter > daysInMonth) break
        }
    }
    
    private fun isDark(color: Int): Boolean {
        val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return darkness >= 0.5
    }
}
