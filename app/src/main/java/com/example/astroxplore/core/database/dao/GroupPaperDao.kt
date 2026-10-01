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

    @Query("DELETE FROM group_papers WHERE groupId = :groupId AND bibcode = :bibcode")
    suspend fun deleteGroupPaper(groupId: String, bibcode: String)

    @Query("DELETE FROM group_papers WHERE groupId = :groupId")
    suspend fun deleteGroupPapers(groupId: String)

    @Query("SELECT * FROM group_papers WHERE isSynced = 0")
    suspend fun getUnsyncedGroupPapers(): List<GroupPaperEntity>
}
