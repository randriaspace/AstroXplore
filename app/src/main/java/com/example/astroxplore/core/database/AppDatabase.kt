package com.example.astroxplore.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.astroxplore.core.database.dao.*
import com.example.astroxplore.core.database.entity.*

@Database(
    entities = [
        KeywordEntity::class, 
        UserPreferenceEntity::class, 
        SavedPaperEntity::class,
        FeedPaperEntity::class,
        GroupEntity::class,
        ProfileEntity::class,
        GroupPaperEntity::class,
        PendingPaperDeletionEntity::class,
        GroupPresentationEntity::class,
        GroupReviewEntity::class,
        GroupMemberEntity::class,
        GroupJoinRequestEntity::class
    ],
    version = 13,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun keywordDao(): KeywordDao
    abstract fun savedPaperDao(): SavedPaperDao
    abstract fun feedPaperDao(): FeedPaperDao
    abstract fun groupDao(): GroupDao
    abstract fun profileDao(): ProfileDao
    abstract fun groupPaperDao(): GroupPaperDao
    abstract fun pendingPaperDeletionDao(): PendingPaperDeletionDao
    abstract fun groupPresentationDao(): GroupPresentationDao
    abstract fun groupReviewDao(): GroupReviewDao
    abstract fun groupMemberDao(): GroupMemberDao
    abstract fun groupJoinRequestDao(): GroupJoinRequestDao
}
