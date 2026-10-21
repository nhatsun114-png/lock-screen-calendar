package edu.hcmute.duan.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits")
    fun getAllHabits(): List<HabitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertHabit(habit: HabitEntity): Long

    @Update
    fun updateHabit(habit: HabitEntity): Int

    @Query("DELETE FROM habits WHERE id = :id")
    fun deleteHabitById(id: Long): Int
}
