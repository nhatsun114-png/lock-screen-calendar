package edu.hcmute.duan.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_events")
data class LocalEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val beginMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val color: Int,
    val calendarId: Long
)
