package com.example.astroxplore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.astroxplore.core.database.SettingsRepository
import com.example.astroxplore.core.util.NavigationSignal
import com.example.astroxplore.features.auth.data.AuthRepository
import com.example.astroxplore.features.profile.data.ProfileRepository
import com.example.astroxplore.core.network.NetworkConnectivityObserver
import com.example.astroxplore.features.library.data.LibraryRepository
import com.example.astroxplore.features.groups.data.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val libraryRepository: LibraryRepository,
    private val groupRepository: GroupRepository,
    private val navigationSignal: NavigationSignal,
    private val networkConnectivityObserver: NetworkConnectivityObserver
) : ViewModel() {
    val sessionStatus: StateFlow<SessionStatus> = authRepository.sessionStatus
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SessionStatus.Initializing
        )

    val isOnline: StateFlow<Boolean> = networkConnectivityObserver.isConnected
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    private val _isOnboarded = MutableStateFlow<Boolean?>(null)
    val isOnboarded: StateFlow<Boolean?> = _isOnboarded.asStateFlow()

    val scrollToTopEvent = navigationSignal.scrollToTop

    init {
        viewModelScope.launch {
            authRepository.sessionStatus.collect { status ->
                if (status is SessionStatus.Authenticated) {
                    val userId = authRepository.currentUser?.id ?: ""

                    // 1. Check local DataStore and local Room DB immediately
                    val isLocalDataStoreOnboarded = settingsRepository.onboardingComplete.first()
                    val localProfile = if (userId.isNotBlank()) profileRepository.getLocalProfile(userId).firstOrNull() else null
                    val isLocallyOnboarded = isLocalDataStoreOnboarded || (localProfile?.isOnboarded == true)

                    if (isLocallyOnboarded) {
                        _isOnboarded.value = true
                        if (isOnline.value && userId.isNotBlank()) {
                            syncAll(userId)
                        }
                    } else {
                        // Check remote profile with quick timeout if online before concluding false
                        var remoteOnboarded = false
                        if (isOnline.value && userId.isNotBlank()) {
                            try {
                                val profile = kotlinx.coroutines.withTimeoutOrNull(2000L) {
                                    profileRepository.getProfile(userId)
                                }
                                remoteOnboarded = profile?.isOnboarded == true
                            } catch (_: Exception) {
                            }
                        }
                        val finalOnboarded = remoteOnboarded
                        _isOnboarded.value = finalOnboarded
                        if (finalOnboarded) {
                            settingsRepository.setOnboardingComplete(true)
                            if (isOnline.value && userId.isNotBlank()) {
                                syncAll(userId)
                            }
                        }
                    }
                } else {
                    _isOnboarded.value = null
                }
            }
        }
        
        // Also watch for connectivity changes to trigger sync
        viewModelScope.launch {
            isOnline.collect { online ->
                if (online && sessionStatus.value is SessionStatus.Authenticated) {
                    val userId = authRepository.currentUser?.id ?: ""
                    syncAll(userId)
                }
            }
        }
    }

    private fun syncAll(userId: String) {
        viewModelScope.launch {
            launch { profileRepository.syncProfile(userId) }
            launch { libraryRepository.syncLibrary() }
            launch { groupRepository.syncGroups() }
            launch { profileRepository.syncAvailableKeywords() }
        }
    }

    fun triggerScrollToTop() {
        navigationSignal.onScrollToTop()
    }
}
