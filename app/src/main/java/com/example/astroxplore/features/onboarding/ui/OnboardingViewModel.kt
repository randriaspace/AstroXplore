package com.example.astroxplore.features.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.astroxplore.R
import com.example.astroxplore.core.database.SettingsRepository
import com.example.astroxplore.features.auth.data.AuthRepository
import com.example.astroxplore.features.profile.data.ProfileRepository
import com.example.astroxplore.features.profile.model.ProfileModel
import com.example.astroxplore.core.util.ErrorMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<Int?>(null)
    val error = _error.asStateFlow()

    // Search and Interests logic matching InterestsViewModel
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedInterests = MutableStateFlow<List<String>>(emptyList())
    val selectedInterests = _selectedInterests.asStateFlow()

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
            (selected + online).distinct().sorted()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val countries = Locale.getISOCountries().map {
        val locale = Locale.Builder().setRegion(it).build()
        locale.displayCountry
    }.sorted()

    val educationLevels = listOf(
        "High School",
        "Undergraduate - 1st Year",
        "Undergraduate - 2nd Year",
        "Undergraduate - 3rd Year",
        "Undergraduate - 4th Year",
        "Master's Degree",
        "PhD Candidate",
        "Postdoctoral Researcher",
        "Professor / Professional Researcher"
    )

    val affiliationTypes = listOf(
        "University",
        "Organization",
        "Company",
        "Institution",
        "Individual"
    )

    init {
        viewModelScope.launch {
            profileRepository.syncAvailableKeywords()
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

    fun completeOnboarding(
        affiliationType: String,
        affiliationName: String?,
        country: String,
        educationLevel: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val user = authRepository.currentUser ?: run {
                _error.value = R.string.error_session_expired
                _isLoading.value = false
                return@launch
            }
            try {
                // Update profile with professional details
                val currentProfile = profileRepository.getProfile(user.id)
                val updatedProfile = (currentProfile ?: ProfileModel(id = user.id, email = user.email ?: "")).copy(
                    affiliationType = affiliationType,
                    affiliationName = affiliationName,
                    country = country,
                    educationLevel = educationLevel,
                    isOnboarded = true
                )
                profileRepository.updateProfile(updatedProfile)
                
                // Sync interests
                profileRepository.syncUserPreferences(user.id, _selectedInterests.value)
                
                settingsRepository.setOnboardingComplete(true)
                onSuccess()
            } catch (e: Exception) {
                _error.value = ErrorMapper.mapToMessage(e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
