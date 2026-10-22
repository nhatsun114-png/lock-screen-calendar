package edu.hcmute.duan.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DeletedEventDao {
    @Query("SELECT title FROM deleted_events")
    fun getAllDeletedTitlesSync(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDeletedTitle(deletedEvent: DeletedEventEntity)

    @Query("DELETE FROM deleted_events WHERE title = :title")
    fun removeDeletedTitle(title: String)
}
