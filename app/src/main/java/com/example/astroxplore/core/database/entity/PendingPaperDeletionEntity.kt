package com.example.astroxplore.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_paper_deletions")
data class PendingPaperDeletionEntity(
    @PrimaryKey
    val bibcode: String,
    val userId: String,
    val timestamp: Long = System.currentTimeMillis()
)
