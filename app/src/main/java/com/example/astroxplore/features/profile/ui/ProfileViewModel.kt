package com.example.astroxplore.features.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.astroxplore.core.database.ThemeMode
import com.example.astroxplore.core.database.UserPreferencesRepository
import com.example.astroxplore.features.auth.data.AuthRepository
import com.example.astroxplore.features.profile.data.ProfileRepository
import com.example.astroxplore.features.profile.data.remote.dto.UserProfileDto
import com.example.astroxplore.features.profile.data.remote.dto.toDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val isLoading: Boolean = false,
    val userProfile: UserProfileDto? = null,
    val savedPapersCount: Int = 0,
    val activeClubsCount: Int = 0,
    val citationsTracked: Int = 0,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isDynamicColorEnabled: Boolean = true,
    val dynamicColorEnabled: Boolean = isDynamicColorEnabled,
    val adsApiKey: String? = null,
    val cacheSizeMb: String = "0 MB",
    val showLogoutDialog: Boolean = false,
    val showApiKeyModal: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _userProfile = MutableStateFlow<UserProfileDto?>(null)
    private val _isLoading = MutableStateFlow(true)
    private val _cacheSize = MutableStateFlow("0 MB")
    
    private val _showLogoutDialog = MutableStateFlow(false)
    private val _showApiKeyModal = MutableStateFlow(false)

    val uiState: StateFlow<ProfileUiState> = combine(
        combine(
            _isLoading,
            _userProfile,
            profileRepository.getSavedPapersCount(),
            profileRepository.getActiveClubsCount(),
            profileRepository.getCitationsTrackedCount()
        ) { loading, profile, savedCount, clubsCount, citations ->
            Tuple5(loading, profile, savedCount, clubsCount, citations)
        },
        combine(
            userPreferencesRepository.themeMode,
            userPreferencesRepository.isDynamicColorEnabled,
            userPreferencesRepository.adsApiKey,
            _cacheSize
        ) { themeMode, dynamicColor, adsKey, cacheSize ->
            Tuple4(themeMode, dynamicColor, adsKey, cacheSize)
        },
        combine(
            _showLogoutDialog,
            _showApiKeyModal
        ) { logout, apiKey ->
            Pair(logout, apiKey)
        }
    ) { (loading, profile, savedCount, clubsCount, citations), (themeMode, dynamicColor, adsKey, cacheSize), (logout, apiKey) ->
        ProfileUiState(
            isLoading = loading,
            userProfile = profile,
            savedPapersCount = savedCount,
            activeClubsCount = clubsCount,
            citationsTracked = citations,
            themeMode = themeMode,
            isDynamicColorEnabled = dynamicColor,
            dynamicColorEnabled = dynamicColor,
            adsApiKey = adsKey,
            cacheSizeMb = cacheSize,
            showLogoutDialog = logout,
            showApiKeyModal = apiKey
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProfileUiState(isLoading = true)
    )

    init {
        loadData()
    }

    fun loadData() {
        val user = authRepository.currentUser
        val userId = user?.id ?: return
        viewModelScope.launch {
            _isLoading.value = true
            
            // Observe local profile changes
            launch {
                profileRepository.getLocalProfile(userId).collectLatest { localProfile ->
                    if (localProfile != null) {
                        val topics = profileRepository.getLocalUserPreferences().firstOrNull() ?: emptyList()
                        _userProfile.value = localProfile.toDto(selectedTopics = topics)
                    }
                }
            }

            // Sync from remote Supabase profile
            launch {
                val remoteDto = profileRepository.getUserProfileDto(userId)
                if (remoteDto != null) {
                    _userProfile.value = remoteDto
                }
            }

            // Calculate cache size
            updateCacheSize()

            _isLoading.value = false
        }
    }

    fun updateSelectedTopics(topics: List<String>) {
        val userId = authRepository.currentUser?.id ?: return
        viewModelScope.launch {
            val current = _userProfile.value
            if (current != null) {
                _userProfile.value = current.copy(selectedTopics = topics)
            }
            profileRepository.updateSelectedTopics(userId, topics)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            userPreferencesRepository.setThemeMode(mode)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDynamicColor(enabled)
        }
    }

    fun setAdsApiKey(apiKey: String?) {
        viewModelScope.launch {
            userPreferencesRepository.setAdsApiKey(apiKey)
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            userPreferencesRepository.clearOfflineCache()
            updateCacheSize()
        }
    }

    private suspend fun updateCacheSize() {
        _cacheSize.value = userPreferencesRepository.getCacheSizeMb()
    }

    fun setShowLogoutDialog(show: Boolean) {
        _showLogoutDialog.value = show
    }

    fun setShowApiKeyModal(show: Boolean) {
        _showApiKeyModal.value = show
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }
}

private data class Tuple5<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
