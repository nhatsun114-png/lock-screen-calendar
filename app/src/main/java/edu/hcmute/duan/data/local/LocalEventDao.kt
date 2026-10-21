package edu.hcmute.duan.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LocalEventDao {
    @Query("SELECT * FROM local_events")
    fun getAllEvents(): List<LocalEventEntity>

    @Query("SELECT * FROM local_events")
    fun getAllEventsSync(): List<LocalEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertEvent(event: LocalEventEntity): Long

    @Query("DELETE FROM local_events WHERE title = :title AND beginMillis = :begin")
    fun deleteEventByTitleAndBegin(title: String, begin: Long): Int

    @Query("UPDATE local_events SET title = :newTitle WHERE title = :oldTitle AND beginMillis = :oldBegin")
    fun updateEventTitle(oldTitle: String, oldBegin: Long, newTitle: String): Int
}
