package com.example.astroxplore.core.database.dao

import androidx.room.*
import com.example.astroxplore.core.database.entity.GroupJoinRequestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupJoinRequestDao {
    @Query("SELECT * FROM group_join_requests WHERE groupId = :groupId AND status = 'pending' ORDER BY createdAt DESC")
    fun getPendingRequestsForGroup(groupId: String): Flow<List<GroupJoinRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: GroupJoinRequestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequests(requests: List<GroupJoinRequestEntity>)

    @Query("UPDATE group_join_requests SET status = :newStatus WHERE id = :requestId")
    suspend fun updateRequestStatus(requestId: String, newStatus: String)

    @Query("DELETE FROM group_join_requests WHERE groupId = :groupId")
    suspend fun clearGroupRequests(groupId: String)
}
