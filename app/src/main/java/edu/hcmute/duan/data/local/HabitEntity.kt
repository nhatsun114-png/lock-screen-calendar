package edu.hcmute.duan.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val streak: Int = 0,
    val lastCheckedDate: String = "",
    val completedDatesJson: String = "[]"
)
