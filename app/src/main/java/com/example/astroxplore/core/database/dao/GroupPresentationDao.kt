package com.example.astroxplore.core.database.dao

import androidx.room.*
import com.example.astroxplore.core.database.entity.GroupPresentationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupPresentationDao {
    @Query("SELECT * FROM group_presentations WHERE groupId = :groupId ORDER BY scheduledAt ASC")
    fun getPresentations(groupId: String): Flow<List<GroupPresentationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPresentation(presentation: GroupPresentationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPresentations(presentations: List<GroupPresentationEntity>)

    @Query("UPDATE group_presentations SET isCheckedIn = :checkedIn, attendeeCount = attendeeCount + (CASE WHEN :checkedIn = 1 THEN 1 ELSE -1 END) WHERE id = :id")
    suspend fun checkIn(id: String, checkedIn: Boolean = true)

    @Query("DELETE FROM group_presentations WHERE id = :id")
    suspend fun deletePresentation(id: String)

    @Query("DELETE FROM group_presentations WHERE groupId = :groupId")
    suspend fun deleteGroupPresentations(groupId: String)

    @Query("SELECT COUNT(*) FROM group_presentations WHERE groupId = :groupId")
    suspend fun getPresentationCount(groupId: String): Int
}
