package com.example.astroxplore.features.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.astroxplore.features.auth.data.AuthRepository
import com.example.astroxplore.features.profile.data.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class InterestsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedInterests = MutableStateFlow<List<String>>(emptyList())
    val selectedInterests: StateFlow<List<String>> = _selectedInterests.asStateFlow()

    val filteredKeywords: StateFlow<List<String>> = _searchQuery
        .debounce(300.milliseconds)
        .flatMapLatest { query ->
            flow {
                if (query.isBlank()) {
                    emit(profileRepository.getRandomKeywordsOnline(20))
                } else {
                    emit(profileRepository.searchKeywordsOnline(query))
                }
            }
        }
        .combine(_selectedInterests) { online, selected ->
            // Combine online results with selected ones to make sure selected are always available
            (selected + online).distinct().sorted()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _saveStatus = MutableStateFlow<SaveStatus>(SaveStatus.Idle)
    val saveStatus: StateFlow<SaveStatus> = _saveStatus.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val user = authRepository.currentUser
            if (user != null) {
                val prefs = profileRepository.getUserPreferences(user.id)
                _selectedInterests.value = prefs
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleInterest(keyword: String) {
        val current = _selectedInterests.value.toMutableList()
        if (current.contains(keyword)) {
            current.remove(keyword)
        } else {
            if (current.size < 6) {
                current.add(keyword)
            }
        }
        _selectedInterests.value = current
    }

    fun saveInterests() {
        val user = authRepository.currentUser ?: return
        val current = _selectedInterests.value
        if (current.size !in 3..6) return

        viewModelScope.launch {
            _saveStatus.value = SaveStatus.Loading
            try {
                profileRepository.syncUserPreferences(user.id, current)
                _saveStatus.value = SaveStatus.Success
            } catch (e: Exception) {
                _saveStatus.value = SaveStatus.Error(e.localizedMessage ?: "Unknown error occurred")
            }
        }
    }
}

sealed interface SaveStatus {
    object Idle : SaveStatus
    object Loading : SaveStatus
    object Success : SaveStatus
    data class Error(val message: String) : SaveStatus
}
