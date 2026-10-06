package com.example.astroxplore.features.profile.data

import com.example.astroxplore.core.database.dao.GroupDao
import com.example.astroxplore.core.database.dao.KeywordDao
import com.example.astroxplore.core.database.dao.ProfileDao
import com.example.astroxplore.core.database.dao.SavedPaperDao
import com.example.astroxplore.core.database.entity.KeywordEntity
import com.example.astroxplore.core.database.entity.UserPreferenceEntity
import com.example.astroxplore.core.database.entity.toEntity
import com.example.astroxplore.features.profile.data.remote.dto.UserProfileDto
import com.example.astroxplore.features.profile.data.remote.dto.toDto
import com.example.astroxplore.features.profile.model.KeywordModel
import com.example.astroxplore.features.profile.model.ProfileModel
import com.example.astroxplore.features.profile.model.UserPreference
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val profileDao: ProfileDao,
    private val keywordDao: KeywordDao,
    private val savedPaperDao: SavedPaperDao,
    private val groupDao: GroupDao
) {
    fun getLocalProfile(userId: String): Flow<ProfileModel?> = profileDao.getProfile(userId).map { 
        it?.toDomainModel()
    }

    suspend fun getProfile(userId: String): ProfileModel? = withContext(Dispatchers.IO) {
        try {
            val remoteProfile = supabaseClient.postgrest["profiles"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingleOrNull<ProfileModel>()
            
            if (remoteProfile != null) {
                profileDao.insertProfile(remoteProfile.toEntity(isSynced = true))
            }
            remoteProfile
        } catch (e: Exception) {
            e.printStackTrace()
            profileDao.getProfileSync(userId)?.toDomainModel()
        }
    }

    suspend fun getUserProfileDto(userId: String): UserProfileDto? = withContext(Dispatchers.IO) {
        try {
            val remoteDto = supabaseClient.postgrest["profiles"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingleOrNull<UserProfileDto>()

            if (remoteDto != null) {
                return@withContext remoteDto
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback to local profile model converted to DTO
        val local = profileDao.getProfileSync(userId)?.toDomainModel()
        val localTopics = keywordDao.getUserPreferences().firstOrNull()?.map { it.keyword } ?: emptyList()
        local?.toDto(selectedTopics = localTopics)
    }

    suspend fun updateSelectedTopics(userId: String, topics: List<String>) = withContext(Dispatchers.IO) {
        try {
            supabaseClient.postgrest["profiles"].update({
                set("selected_topics", topics)
            }) {
                filter {
                    eq("id", userId)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        syncUserPreferences(userId, topics)
    }

    fun getSavedPapersCount(): Flow<Int> = savedPaperDao.getSavedPaperCount()

    fun getActiveClubsCount(): Flow<Int> = groupDao.getMyGroupCount()

    fun getCitationsTrackedCount(): Flow<Int> = savedPaperDao.getTotalCitationsTracked()

    suspend fun updateProfile(profile: ProfileModel) = withContext(Dispatchers.IO) {
        profileDao.insertProfile(profile.toEntity(isSynced = false))
        
        try {
            supabaseClient.postgrest["profiles"].upsert(profile)
            profileDao.insertProfile(profile.toEntity(isSynced = true))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun syncProfile(userId: String) = withContext(Dispatchers.IO) {
        try {
            val unsynced = profileDao.getProfileSync(userId)
            if (unsynced != null && !unsynced.isSynced) {
                updateProfile(unsynced.toDomainModel())
            } else {
                getProfile(userId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getLocalKeywords(): Flow<List<String>> = keywordDao.getAllKeywords().map { entities ->
        entities.map { it.name }
    }

    suspend fun searchKeywordsOnline(query: String): List<String> = withContext(Dispatchers.IO) {
        try {
            supabaseClient.postgrest["keywords"]
                .select(columns = Columns.ALL) {
                    filter {
                        ilike("name", "%$query%")
                    }
                    limit(20)
                }
                .decodeList<KeywordModel>()
                .map { it.name }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getRandomKeywordsOnline(limit: Int = 20): List<String> = withContext(Dispatchers.IO) {
        try {
            supabaseClient.postgrest["keywords"]
                .select(columns = Columns.ALL) {
                    limit(100) 
                }
                .decodeList<KeywordModel>()
                .shuffled()
                .take(limit)
                .map { it.name }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun syncAvailableKeywords() = withContext(Dispatchers.IO) {
        try {
            val remoteKeywords = supabaseClient.postgrest["keywords"]
                .select(columns = Columns.ALL)
                .decodeList<KeywordModel>()
            
            if (remoteKeywords.isNotEmpty()) {
                keywordDao.clearKeywords()
                keywordDao.insertKeywords(remoteKeywords.map { KeywordEntity(it.name, it.category) })
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getUserPreferences(userId: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val remotePrefs = supabaseClient.postgrest["user_preferences"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<UserPreference>()
                .map { it.keywordTag }
            
            if (remotePrefs.isNotEmpty()) {
                keywordDao.syncUserPreferences(remotePrefs.map { UserPreferenceEntity(it) })
            }
            remotePrefs
        } catch (e: Exception) {
            e.printStackTrace()
            keywordDao.getUserPreferences().first().map { it.keyword }
        }
    }

    fun getLocalUserPreferences(): Flow<List<String>> = keywordDao.getUserPreferences().map { entities ->
        entities.map { it.keyword }
    }

    suspend fun syncUserPreferences(userId: String, keywordTags: List<String>) = withContext(Dispatchers.IO) {
        try {
            val profile = getProfile(userId)
            if (profile == null) {
                val userEmail = supabaseClient.auth.currentUserOrNull()?.email ?: ""
                supabaseClient.postgrest["profiles"].upsert(mapOf(
                    "id" to userId,
                    "email" to userEmail,
                    "is_onboarded" to true
                ))
            } else {
                supabaseClient.postgrest["profiles"].update({
                    set("is_onboarded", true)
                }) {
                    filter {
                        eq("id", userId)
                    }
                }
            }

            supabaseClient.postgrest["user_preferences"].delete {
                filter {
                    eq("user_id", userId)
                }
            }
            
            val preferences = keywordTags.map { tag ->
                UserPreference(userId = userId, keywordTag = tag)
            }
            if (preferences.isNotEmpty()) {
                supabaseClient.postgrest["user_preferences"].insert(preferences)
            }

            keywordDao.syncUserPreferences(keywordTags.map { UserPreferenceEntity(it) })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
