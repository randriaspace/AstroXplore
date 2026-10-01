package com.example.astroxplore.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.astroxplore.core.database.dao.FeedPaperDao
import com.example.astroxplore.core.database.dao.GroupDao
import com.example.astroxplore.core.database.dao.GroupPaperDao
import com.example.astroxplore.core.database.dao.KeywordDao
import com.example.astroxplore.core.database.dao.ProfileDao
import com.example.astroxplore.core.database.dao.PendingPaperDeletionDao
import com.example.astroxplore.core.database.dao.SavedPaperDao
import com.example.astroxplore.core.database.entity.FeedPaperEntity
import com.example.astroxplore.core.database.entity.GroupEntity
import com.example.astroxplore.core.database.entity.GroupPaperEntity
import com.example.astroxplore.core.database.entity.KeywordEntity
import com.example.astroxplore.core.database.entity.PendingPaperDeletionEntity
import com.example.astroxplore.core.database.entity.ProfileEntity
import com.example.astroxplore.core.database.entity.SavedPaperEntity
import com.example.astroxplore.core.database.entity.UserPreferenceEntity

@Database(
    entities = [
        KeywordEntity::class, 
        UserPreferenceEntity::class, 
        SavedPaperEntity::class,
        FeedPaperEntity::class,
        GroupEntity::class,
        ProfileEntity::class,
        GroupPaperEntity::class,
        PendingPaperDeletionEntity::class
    ],
    version = 8,
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
}
