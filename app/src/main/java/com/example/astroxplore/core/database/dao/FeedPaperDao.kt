package com.example.astroxplore.core.database.dao

import androidx.room.*
import com.example.astroxplore.core.database.entity.FeedPaperEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedPaperDao {
    @Query("SELECT * FROM feed_papers ORDER BY cachedAt DESC")
    fun getCachedFeed(): Flow<List<FeedPaperEntity>>

    @Query("SELECT * FROM feed_papers WHERE category = :category ORDER BY cachedAt DESC")
    fun getCachedFeedByCategory(category: String): Flow<List<FeedPaperEntity>>

    @Query("SELECT * FROM feed_papers WHERE bibcode = :bibcode LIMIT 1")
    suspend fun getPaperByBibcode(bibcode: String): FeedPaperEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPapers(papers: List<FeedPaperEntity>)

    @Query("DELETE FROM feed_papers")
    suspend fun clearFeed()

    @Query("DELETE FROM feed_papers WHERE category = :category")
    suspend fun clearFeedByCategory(category: String)

    @Transaction
    suspend fun refreshFeed(papers: List<FeedPaperEntity>) {
        clearFeed()
        insertPapers(papers)
    }

    @Transaction
    suspend fun refreshCategory(category: String, papers: List<FeedPaperEntity>) {
        clearFeedByCategory(category)
        insertPapers(papers)
    }
}
