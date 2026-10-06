package com.example.astroxplore.core.database.dao

import androidx.room.*
import com.example.astroxplore.core.database.entity.GroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM journal_clubs ORDER BY createdAt DESC")
    fun getAllGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM journal_clubs WHERE isMember = 1 ORDER BY createdAt DESC")
    fun getMyGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM journal_clubs WHERE isMember = 0 ORDER BY memberCount DESC, createdAt DESC")
    fun getExploreGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM journal_clubs WHERE id = :groupId")
    suspend fun getGroupById(groupId: String): GroupEntity?

    @Query("SELECT * FROM journal_clubs WHERE displayId = :displayId LIMIT 1")
    suspend fun getGroupByDisplayId(displayId: String): GroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<GroupEntity>)

    @Query("UPDATE journal_clubs SET isMember = :isMember, memberCount = memberCount + (CASE WHEN :isMember = 1 THEN 1 ELSE -1 END) WHERE id = :groupId")
    suspend fun updateMembership(groupId: String, isMember: Boolean)

    @Query("UPDATE journal_clubs SET name = :name, description = :description, focusArea = :focusArea, meetingSchedule = :schedule, meetingLocation = :location WHERE id = :groupId")
    suspend fun updateGroupDetails(groupId: String, name: String, description: String?, focusArea: String?, schedule: String?, location: String?)

    @Query("DELETE FROM journal_clubs")
    suspend fun clearAll()

    @Query("DELETE FROM journal_clubs WHERE id = :groupId")
    suspend fun deleteGroup(groupId: String)

    @Query("SELECT * FROM journal_clubs WHERE isSynced = 0")
    suspend fun getUnsyncedGroups(): List<GroupEntity>

    @Query("UPDATE journal_clubs SET isSynced = :isSynced WHERE id = :groupId")
    suspend fun updateSyncStatus(groupId: String, isSynced: Boolean)

    @Query("SELECT COUNT(*) FROM journal_clubs")
    suspend fun getGroupCount(): Int

    @Query("SELECT COUNT(*) FROM journal_clubs WHERE isMember = 1")
    fun getMyGroupCount(): Flow<Int>

    @Query("DELETE FROM journal_clubs WHERE isSynced = 1")
    suspend fun deleteAllSyncedGroups()
}
