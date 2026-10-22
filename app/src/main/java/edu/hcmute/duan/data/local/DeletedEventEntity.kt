package edu.hcmute.duan.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deleted_events")
data class DeletedEventEntity(
    @PrimaryKey val title: String
)
