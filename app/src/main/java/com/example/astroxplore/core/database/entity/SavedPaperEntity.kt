package com.example.astroxplore.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED
}

@Entity(tableName = "saved_papers")
data class SavedPaperEntity(
    @PrimaryKey
    val bibcode: String,
    val title: String,
    val authors: String, // Stored as comma-separated
    val abstractText: String,
    val category: String,
    val dateDisplay: String,
    val citationCount: Int,
    val savedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = true,
    val pdfUrl: String? = null,
    val localFilePath: String? = null,
    val downloadState: DownloadState = DownloadState.NOT_DOWNLOADED,
    val downloadProgress: Int = 0,
    val fileSizeBytes: Long = 0L
)
