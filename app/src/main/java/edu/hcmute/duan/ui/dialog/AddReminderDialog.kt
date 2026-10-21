package edu.hcmute.duan.ui.dialog

import android.content.Context
import android.view.LayoutInflater
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import edu.hcmute.duan.databinding.BottomSheetAddReminderBinding

class AddReminderDialog(
    private val context: Context,
    private val defaultQuadrant: String = "Lên kế hoạch",
    private val onReminderAdded: (title: String, quadrant: String) -> Unit
) {
    private val dialog = BottomSheetDialog(context)
    private val binding = BottomSheetAddReminderBinding.inflate(LayoutInflater.from(context))

    private var selectedQuadrant = defaultQuadrant
    private val quadrantOptions = arrayOf("Làm ngay", "Lên kế hoạch", "Ủy quyền", "Loại bỏ")

    fun show() {
        try {
            dialog.setContentView(binding.root)

            selectedQuadrant = defaultQuadrant
            binding.tvEisenhowerQuadrant.text = "$selectedQuadrant ↕"

            binding.btnCloseAddReminder.setOnClickListener { dialog.dismiss() }

            binding.btnSaveReminder.setOnClickListener { saveReminder() }

            binding.tvEisenhowerQuadrant.setOnClickListener { showQuadrantSelectionDialog() }

            binding.btnAddNotification.setOnClickListener {
                Toast.makeText(context, "Đã bật thông báo nhắc nhở!", Toast.LENGTH_SHORT).show()
            }

            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showQuadrantSelectionDialog() {
        try {
            MaterialAlertDialogBuilder(context)
                .setTitle("Chọn ô Eisenhower")
                .setItems(quadrantOptions) { _, which ->
                    selectedQuadrant = quadrantOptions[which]
                    binding.tvEisenhowerQuadrant.text = "$selectedQuadrant ↕"
                }
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveReminder() {
        try {
            val title = binding.etReminderTitle.text.toString().trim()
            if (title.isEmpty()) {
                Toast.makeText(context, "Vui lòng nhập tiêu đề nhắc nhở!", Toast.LENGTH_SHORT).show()
                return
            }

            onReminderAdded(title, selectedQuadrant)
            Toast.makeText(context, "Đã lưu nhắc nhở mới vào '$selectedQuadrant'!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        } catch (e: Exception) {
            e.printStackTrace()
            dialog.dismiss()
        }
    }
}
