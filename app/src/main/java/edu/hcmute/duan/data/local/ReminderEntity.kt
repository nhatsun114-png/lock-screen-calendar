package edu.hcmute.duan.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val timeMillis: Long,
    val priorityQuadrant: String, // "Q1", "Q2", "Q3", "Q4"
    val isCompleted: Boolean = false
)
