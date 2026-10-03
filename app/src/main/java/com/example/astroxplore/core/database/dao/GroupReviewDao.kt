package com.example.astroxplore.core.database.dao

import androidx.room.*
import com.example.astroxplore.core.database.entity.GroupReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupReviewDao {
    @Query("SELECT * FROM group_reviews WHERE groupId = :groupId ORDER BY createdAt DESC")
    fun getReviews(groupId: String): Flow<List<GroupReviewEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReview(review: GroupReviewEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviews(reviews: List<GroupReviewEntity>)

    @Query("DELETE FROM group_reviews WHERE id = :id")
    suspend fun deleteReview(id: String)

    @Query("DELETE FROM group_reviews WHERE groupId = :groupId")
    suspend fun deleteGroupReviews(groupId: String)

    @Query("SELECT * FROM group_reviews WHERE isSynced = 0")
    suspend fun getUnsyncedReviews(): List<GroupReviewEntity>

    @Query("UPDATE group_reviews SET isSynced = :isSynced WHERE id = :id")
    suspend fun updateSyncStatus(id: String, isSynced: Boolean)

    @Query("SELECT COUNT(*) FROM group_reviews WHERE groupId = :groupId")
    suspend fun getReviewCount(groupId: String): Int
}
