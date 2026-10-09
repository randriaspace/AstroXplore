package com.example.astroxplore.core.database

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences_store")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val appDatabase: AppDatabase
) {
    private object PreferencesKeys {
        val ADS_API_KEY = stringPreferencesKey("ads_api_key")
    }

    val themeMode: Flow<ThemeMode> = settingsRepository.themeMode
    val isDynamicColorEnabled: Flow<Boolean> = settingsRepository.dynamicColorEnabled

    val adsApiKey: Flow<String?> = context.userPrefsDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            preferences[PreferencesKeys.ADS_API_KEY]
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        settingsRepository.setThemeMode(mode)
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        settingsRepository.setDynamicColor(enabled)
    }

    suspend fun setAdsApiKey(key: String?) {
        context.userPrefsDataStore.edit { preferences ->
            if (key.isNullOrBlank()) {
                preferences.remove(PreferencesKeys.ADS_API_KEY)
            } else {
                preferences[PreferencesKeys.ADS_API_KEY] = key.trim()
            }
        }
    }

    suspend fun getCacheSizeMb(): String = withContext(Dispatchers.IO) {
        try {
            var sizeBytes = getFolderSize(context.cacheDir)
            context.externalCacheDir?.let {
                sizeBytes += getFolderSize(it)
            }
            val mb = sizeBytes / (1024.0 * 1024.0)
            if (mb < 0.1) "0.1 MB" else String.format(Locale.US, "%.1f MB", mb)
        } catch (_: Exception) {
            "0 MB"
        }
    }

    suspend fun getCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        try {
            var sizeBytes = getFolderSize(context.cacheDir)
            context.externalCacheDir?.let {
                sizeBytes += getFolderSize(it)
            }
            sizeBytes
        } catch (_: Exception) {
            0L
        }
    }

    suspend fun clearOfflineCache() = withContext(Dispatchers.IO) {
        try {
            appDatabase.feedPaperDao().clearFeed()
            clearFolder(context.cacheDir)
            context.externalCacheDir?.let { clearFolder(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getFolderSize(file: File): Long {
        var size: Long = 0
        if (!file.exists()) return 0
        val files = file.listFiles() ?: return 0
        for (f in files) {
            size += if (f.isDirectory) getFolderSize(f) else f.length()
        }
        return size
    }

    private fun clearFolder(file: File) {
        if (!file.exists()) return
        val files = file.listFiles() ?: return
        for (f in files) {
            if (f.isDirectory) {
                clearFolder(f)
            }
            f.delete()
        }
    }
}
