package edu.hcmute.duan.ui.dialog

import android.content.Context
import android.graphics.Color
import android.widget.GridLayout
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object ColorPickerDialog {

    fun show(context: Context, title: String, currentColor: Int, onColorPicked: (Int) -> Unit) {
        val colors = intArrayOf(
            0xFFF44336.toInt(), 0xFFE91E63.toInt(), 0xFF9C27B0.toInt(), 0xFF673AB7.toInt(),
            0xFF3F51B5.toInt(), 0xFF2196F3.toInt(), 0xFF03A9F4.toInt(), 0xFF00BCD4.toInt(),
            0xFF009688.toInt(), 0xFF4CAF50.toInt(), 0xFF8BC34A.toInt(), 0xFFCDDC39.toInt(),
            0xFFFFEB3B.toInt(), 0xFFFFC107.toInt(), 0xFFFF9800.toInt(), 0xFFFF5722.toInt(),
            0xFF795548.toInt(), 0xFF9E9E9E.toInt(), 0xFF607D8B.toInt(), 0xFFFFFFFF.toInt(),
            0xFF888888.toInt(), 0xFF444444.toInt(), 0xFF000000.toInt(), Color.TRANSPARENT
        )

        val grid = GridLayout(context).apply {
            columnCount = 6
            setPadding(32, 32, 32, 32)
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(grid)
            .setNegativeButton("Cancel", null)
            .show()

        for (color in colors) {
            val card = MaterialCardView(context).apply {
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
}
