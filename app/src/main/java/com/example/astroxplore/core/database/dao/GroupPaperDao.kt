package com.example.astroxplore.core.database.dao

import androidx.room.*
import com.example.astroxplore.core.database.entity.GroupPaperEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupPaperDao {
    @Query("SELECT * FROM group_papers WHERE groupId = :groupId ORDER BY addedAt DESC")
    fun getGroupPapers(groupId: String): Flow<List<GroupPaperEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupPaper(paper: GroupPaperEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupPapers(papers: List<GroupPaperEntity>)

    @Query("UPDATE group_papers SET voteCount = voteCount + 1, isVotedByMe = 1 WHERE id = :id")
    suspend fun votePaper(id: String)

    @Query("UPDATE group_papers SET voteCount = CASE WHEN voteCount > 0 THEN voteCount - 1 ELSE 0 END, isVotedByMe = 0 WHERE id = :id")
    suspend fun unvotePaper(id: String)

    @Query("DELETE FROM group_papers WHERE groupId = :groupId AND bibcode = :bibcode")
    suspend fun deleteGroupPaper(groupId: String, bibcode: String)

    @Query("DELETE FROM group_papers WHERE id = :id")
    suspend fun deleteGroupPaperById(id: String)

    @Query("DELETE FROM group_papers WHERE groupId = :groupId")
    suspend fun deleteGroupPapers(groupId: String)

    @Query("SELECT * FROM group_papers WHERE isSynced = 0")
    suspend fun getUnsyncedGroupPapers(): List<GroupPaperEntity>

    @Query("UPDATE group_papers SET isSynced = :isSynced WHERE id = :id")
    suspend fun updateSyncStatus(id: String, isSynced: Boolean)

    @Query("SELECT COUNT(*) FROM group_papers WHERE groupId = :groupId")
    suspend fun getPaperCount(groupId: String): Int

    @Query("SELECT SUM(voteCount) FROM group_papers WHERE groupId = :groupId")
    suspend fun getTotalVotes(groupId: String): Int?
}
