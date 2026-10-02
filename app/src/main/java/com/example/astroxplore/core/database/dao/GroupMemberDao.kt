package com.example.astroxplore.core.database.dao

import androidx.room.*
import com.example.astroxplore.core.database.entity.GroupMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupMemberDao {
    @Query("SELECT * FROM group_members WHERE groupId = :groupId ORDER BY CASE role WHEN 'admin' THEN 1 WHEN 'moderator' THEN 2 ELSE 3 END, joinedAt ASC")
    fun getMembers(groupId: String): Flow<List<GroupMemberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: GroupMemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<GroupMemberEntity>)

    @Query("UPDATE group_members SET role = :newRole WHERE groupId = :groupId AND userId = :userId")
    suspend fun updateRole(groupId: String, userId: String, newRole: String)

    @Query("DELETE FROM group_members WHERE groupId = :groupId AND userId = :userId")
    suspend fun removeMember(groupId: String, userId: String)

    @Query("DELETE FROM group_members WHERE groupId = :groupId")
    suspend fun deleteGroupMembers(groupId: String)

    @Query("SELECT COUNT(*) FROM group_members WHERE groupId = :groupId")
    suspend fun getMemberCount(groupId: String): Int
}
