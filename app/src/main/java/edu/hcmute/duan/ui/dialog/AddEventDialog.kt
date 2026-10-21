package edu.hcmute.duan.ui.dialog

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import edu.hcmute.duan.data.model.CalendarModel
import edu.hcmute.duan.data.model.EventModel
import edu.hcmute.duan.data.repository.CalendarRepository
import edu.hcmute.duan.databinding.BottomSheetAddEventBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddEventDialog(
    private val context: Context,
    private val eventToEdit: EventModel? = null,
    private val onEventAdded: () -> Unit
) {
    private val dialog = BottomSheetDialog(context)
    private val binding = BottomSheetAddEventBinding.inflate(LayoutInflater.from(context))

    private val calendarRepository = CalendarRepository(context.applicationContext)
    private var availableCalendars: List<CalendarModel> = emptyList()
    private var selectedCalendarId: Long = 1L
    private var selectedColor: Int = Color.parseColor("#D81B60")

    private val startCalendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
    }

    private val endCalendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 10)
        set(Calendar.MINUTE, 0)
    }

    private val dateFormat = SimpleDateFormat("'ngày' dd 'thg' MM, yyyy", Locale("vi", "VN"))
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun show() {
        try {
            dialog.setContentView(binding.root)

            loadCalendars()
            prefillEventToEdit()
            updateDateTimeDisplays()

            binding.btnCloseAddEvent.setOnClickListener { dialog.dismiss() }

            binding.btnSaveEvent.setOnClickListener { saveEvent() }

            binding.switchAllDay.setOnCheckedChangeListener { _, isChecked ->
                binding.tvStartTime.visibility = if (isChecked) View.GONE else View.VISIBLE
                binding.tvEndTime.visibility = if (isChecked) View.GONE else View.VISIBLE
            }

            binding.tvStartDate.setOnClickListener { showDatePicker(true) }
            binding.tvStartTime.setOnClickListener { showTimePicker(true) }
            binding.tvEndDate.setOnClickListener { showDatePicker(false) }
            binding.tvEndTime.setOnClickListener { showTimePicker(false) }

            binding.tvSelectCalendarName.setOnClickListener { showCalendarSelectionDialog() }
            binding.tvReminderTime.setOnClickListener { showReminderSelectionDialog() }
            binding.tvRepeatOptions.setOnClickListener { showRepeatSelectionDialog() }

            binding.btnSelectColor.setOnClickListener {
                try {
                    ColorPickerDialog.show(context, "Biểu tượng & Màu", selectedColor) { color ->
                        selectedColor = color
                        binding.btnSelectColor.setCardBackgroundColor(color)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Lỗi khi mở giao diện: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun prefillEventToEdit() {
        if (eventToEdit == null) return
        try {
            binding.etEventTitle.setText(eventToEdit.title)
            startCalendar.timeInMillis = eventToEdit.begin
            endCalendar.timeInMillis = eventToEdit.end

            binding.switchAllDay.isChecked = eventToEdit.allDay
            binding.tvStartTime.visibility = if (eventToEdit.allDay) View.GONE else View.VISIBLE
            binding.tvEndTime.visibility = if (eventToEdit.allDay) View.GONE else View.VISIBLE

            selectedColor = eventToEdit.color
            binding.btnSelectColor.setCardBackgroundColor(selectedColor)
            selectedCalendarId = eventToEdit.calendarId
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadCalendars() {
        try {
            availableCalendars = calendarRepository.getAvailableCalendars()
            if (availableCalendars.isNotEmpty()) {
                selectedCalendarId = availableCalendars.first().id
                binding.tvSelectCalendarName.text = "${availableCalendars.first().displayName} ↕"
            } else {
                binding.tvSelectCalendarName.text = "Lịch mặc định ↕"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            binding.tvSelectCalendarName.text = "Lịch mặc định ↕"
        }
    }

    private fun updateDateTimeDisplays() {
        try {
            binding.tvStartDate.text = dateFormat.format(startCalendar.time)
            binding.tvStartTime.text = timeFormat.format(startCalendar.time)

            binding.tvEndDate.text = dateFormat.format(endCalendar.time)
            binding.tvEndTime.text = timeFormat.format(endCalendar.time)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showDatePicker(isStart: Boolean) {
        try {
            val targetCal = if (isStart) startCalendar else endCalendar
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    targetCal.set(Calendar.YEAR, year)
                    targetCal.set(Calendar.MONTH, month)
                    targetCal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    if (isStart && endCalendar.before(startCalendar)) {
                        endCalendar.timeInMillis = startCalendar.timeInMillis + 3600000
                    }
                    updateDateTimeDisplays()
                },
                targetCal.get(Calendar.YEAR),
                targetCal.get(Calendar.MONTH),
                targetCal.get(Calendar.DAY_OF_MONTH)
            ).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showTimePicker(isStart: Boolean) {
        try {
            val targetCal = if (isStart) startCalendar else endCalendar
            TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    targetCal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    targetCal.set(Calendar.MINUTE, minute)
                    if (isStart && endCalendar.before(startCalendar)) {
                        endCalendar.timeInMillis = startCalendar.timeInMillis + 3600000
                    }
                    updateDateTimeDisplays()
                },
                targetCal.get(Calendar.HOUR_OF_DAY),
                targetCal.get(Calendar.MINUTE),
                true
            ).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showCalendarSelectionDialog() {
        try {
            if (availableCalendars.isEmpty()) return
            val items = availableCalendars.map { "${it.displayName} (${it.accountName})" }.toTypedArray()
            MaterialAlertDialogBuilder(context)
                .setTitle("Chọn Lịch")
                .setItems(items) { _, which ->
                    val chosen = availableCalendars[which]
                    selectedCalendarId = chosen.id
                    binding.tvSelectCalendarName.text = "${chosen.displayName} ↕"
                }
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var selectedReminderOption: String = "Tại thời điểm diễn ra"

    private fun showReminderSelectionDialog() {
        try {
            val options = arrayOf(
                "Không",
                "Tại thời điểm diễn ra",
                "Trước 5 phút",
                "Trước 15 phút",
                "Trước 30 phút",
                "Trước 1 giờ",
                "Trước 1 ngày"
            )
            MaterialAlertDialogBuilder(context)
                .setTitle("Nhắc nhở")
                .setItems(options) { _, which ->
                    selectedReminderOption = options[which]
                    binding.tvReminderTime.text = "${options[which]} ↕"
                }
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showRepeatSelectionDialog() {
        try {
            val options = arrayOf(
                "Không",
                "Hàng ngày",
                "Hàng tuần",
                "Hàng tháng",
                "Hàng năm"
            )
            MaterialAlertDialogBuilder(context)
                .setTitle("Lặp lại")
                .setItems(options) { _, which ->
                    binding.tvRepeatOptions.text = "${options[which]} ↕"
                }
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveEvent() {
        try {
            val title = binding.etEventTitle.text.toString().trim()
            if (title.isEmpty()) {
                Toast.makeText(context, "Vui lòng nhập tiêu đề!", Toast.LENGTH_SHORT).show()
                return
            }

            val location = binding.etEventLocation.text.toString().trim()
            val notes = binding.etEventNotes.text.toString().trim()
            val isAllDay = binding.switchAllDay.isChecked

            // If editing an existing event, remove the old instance
            if (eventToEdit != null) {
                calendarRepository.deleteLocalEvent(eventToEdit.title, eventToEdit.begin)
            }

            val success = calendarRepository.addEventToCalendar(
                title = title,
                beginMillis = startCalendar.timeInMillis,
                endMillis = endCalendar.timeInMillis,
                allDay = isAllDay,
                calendarId = selectedCalendarId,
                color = selectedColor,
                location = if (location.isNotEmpty()) location else null,
                notes = if (notes.isNotEmpty()) notes else null
            )

            // Schedule exact AlarmManager alarm based on selected reminder time offset
            try {
                val reminderOffsetMillis = when (selectedReminderOption) {
                    "Trước 5 phút" -> 5 * 60 * 1000L
                    "Trước 15 phút" -> 15 * 60 * 1000L
                    "Trước 30 phút" -> 30 * 60 * 1000L
                    "Trước 1 giờ" -> 60 * 60 * 1000L
                    "Trước 1 ngày" -> 24 * 60 * 60 * 1000L
                    else -> 0L
                }

                val triggerTimeMillis = (startCalendar.timeInMillis - reminderOffsetMillis).coerceAtLeast(System.currentTimeMillis() + 1000L)
                val alarmScheduler = edu.hcmute.duan.receiver.AlarmScheduler(context)
                val notificationId = (triggerTimeMillis / 1000).toInt()
                val timeText = timeFormat.format(startCalendar.time)
                val eventLabel = if (reminderOffsetMillis > 0) "$title ($selectedReminderOption)" else title

                alarmScheduler.scheduleExactAlarm(
                    notificationId = notificationId,
                    eventTitle = eventLabel,
                    eventTimeText = timeText,
                    triggerAtMillis = triggerTimeMillis
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val toastMsg = if (eventToEdit != null) "Đã cập nhật sự kiện!" else "Đã thêm sự kiện mới & đặt báo thức!"
            Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
            onEventAdded()
            dialog.dismiss()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Đã lưu sự kiện!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
    }
}
