package com.example.astroxplore.core.database.dao

import androidx.room.*
import com.example.astroxplore.core.database.entity.PendingPaperDeletionEntity

@Dao
interface PendingPaperDeletionDao {
    @Query("SELECT * FROM pending_paper_deletions WHERE userId = :userId")
    suspend fun getPendingDeletions(userId: String): List<PendingPaperDeletionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingDeletion(item: PendingPaperDeletionEntity)

    @Query("DELETE FROM pending_paper_deletions WHERE bibcode = :bibcode")
    suspend fun removePendingDeletion(bibcode: String)
}
