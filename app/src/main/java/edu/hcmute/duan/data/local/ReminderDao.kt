package edu.hcmute.duan.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY timeMillis ASC")
    fun getAllReminders(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE priorityQuadrant = :quadrant ORDER BY timeMillis ASC")
    fun getRemindersByQuadrant(quadrant: String): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertReminder(reminder: ReminderEntity): Long

    @Update
    fun updateReminder(reminder: ReminderEntity): Int

    @Query("DELETE FROM reminders WHERE id = :id")
    fun deleteReminderById(id: Long): Int
}
