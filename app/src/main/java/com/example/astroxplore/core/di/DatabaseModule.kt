package com.example.astroxplore.core.di

import android.content.Context
import androidx.room.Room
import com.example.astroxplore.core.database.AppDatabase
import com.example.astroxplore.core.database.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "astro_xplore_db"
        )
        .fallbackToDestructiveMigration(dropAllTables = true) // For development simplicity
        .build()
    }

    @Provides
    @Singleton
    fun provideKeywordDao(database: AppDatabase): KeywordDao {
        return database.keywordDao()
    }

    @Provides
    @Singleton
    fun provideSavedPaperDao(database: AppDatabase): SavedPaperDao {
        return database.savedPaperDao()
    }

    @Provides
    @Singleton
    fun provideFeedPaperDao(database: AppDatabase): FeedPaperDao {
        return database.feedPaperDao()
    }

    @Provides
    @Singleton
    fun provideGroupDao(database: AppDatabase): GroupDao {
        return database.groupDao()
    }

    @Provides
    @Singleton
    fun provideProfileDao(database: AppDatabase): ProfileDao {
        return database.profileDao()
    }

    @Provides
    @Singleton
    fun provideGroupPaperDao(database: AppDatabase): GroupPaperDao {
        return database.groupPaperDao()
    }

    @Provides
    @Singleton
    fun providePendingPaperDeletionDao(database: AppDatabase): PendingPaperDeletionDao {
        return database.pendingPaperDeletionDao()
    }

    @Provides
    @Singleton
    fun provideGroupPresentationDao(database: AppDatabase): GroupPresentationDao {
        return database.groupPresentationDao()
    }

    @Provides
    @Singleton
    fun provideGroupReviewDao(database: AppDatabase): GroupReviewDao {
        return database.groupReviewDao()
    }

    @Provides
    @Singleton
    fun provideGroupMemberDao(database: AppDatabase): GroupMemberDao {
        return database.groupMemberDao()
    }
}
