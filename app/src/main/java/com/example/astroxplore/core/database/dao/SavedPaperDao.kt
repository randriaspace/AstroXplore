package com.example.astroxplore.core.database.dao

import androidx.room.*
import com.example.astroxplore.core.database.entity.DownloadState
import com.example.astroxplore.core.database.entity.SavedPaperEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedPaperDao {
    @Query("SELECT * FROM saved_papers ORDER BY savedAt DESC")
    fun getAllSavedPapers(): Flow<List<SavedPaperEntity>>

    @Query("SELECT * FROM saved_papers WHERE bibcode = :bibcode")
    suspend fun getPaperByBibcode(bibcode: String): SavedPaperEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePaper(paper: SavedPaperEntity)

    @Query("DELETE FROM saved_papers WHERE bibcode = :bibcode")
    suspend fun deletePaper(bibcode: String)

    @Query("SELECT EXISTS(SELECT * FROM saved_papers WHERE bibcode = :bibcode)")
    fun isPaperSaved(bibcode: String): Flow<Boolean>

    @Query("SELECT * FROM saved_papers WHERE isSynced = 0")
    suspend fun getUnsyncedPapers(): List<SavedPaperEntity>

    @Query("SELECT COUNT(*) FROM saved_papers")
    fun getSavedPaperCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(citationCount), 0) FROM saved_papers")
    fun getTotalCitationsTracked(): Flow<Int>

    @Query("UPDATE saved_papers SET downloadState = :state, downloadProgress = :progress WHERE bibcode = :bibcode")
    suspend fun updateDownloadProgress(bibcode: String, state: DownloadState, progress: Int)

    @Query("UPDATE saved_papers SET downloadState = :state, localFilePath = :filePath, fileSizeBytes = :fileSize, downloadProgress = 100 WHERE bibcode = :bibcode")
    suspend fun markDownloadComplete(bibcode: String, state: DownloadState, filePath: String, fileSize: Long)

    @Query("UPDATE saved_papers SET downloadState = 'NOT_DOWNLOADED', localFilePath = NULL, fileSizeBytes = 0, downloadProgress = 0")
    suspend fun clearAllLocalPdfs()
}
