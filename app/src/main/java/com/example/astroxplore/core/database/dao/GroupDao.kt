package com.example.astroxplore.core.database.dao

import androidx.room.*
import com.example.astroxplore.core.database.entity.GroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM journal_clubs ORDER BY createdAt DESC")
    fun getAllGroups(): Flow<List<GroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<GroupEntity>)

    @Query("DELETE FROM journal_clubs")
    suspend fun clearAll()

    @Query("DELETE FROM journal_clubs WHERE id = :groupId")
    suspend fun deleteGroup(groupId: String)

    @Query("SELECT * FROM journal_clubs WHERE isSynced = 0")
    suspend fun getUnsyncedGroups(): List<GroupEntity>
}
