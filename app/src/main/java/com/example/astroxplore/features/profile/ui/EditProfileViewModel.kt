package com.example.astroxplore.features.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.astroxplore.core.database.SettingsRepository
import com.example.astroxplore.features.auth.data.AuthRepository
import com.example.astroxplore.features.profile.data.ProfileRepository
import com.example.astroxplore.features.profile.model.ProfileModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<EditProfileUiState>(EditProfileUiState.Loading)
    val uiState = _uiState.asStateFlow()

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
        loadProfile()
    }

    private fun loadProfile() {
        val user = authRepository.currentUser
        val userId = user?.id ?: run {
            _uiState.value = EditProfileUiState.Success(
                ProfileModel(
                    id = "local_user",
                    email = "",
                    fullName = "Scholar",
                    isOnboarded = false
                )
            )
            return
        }

        viewModelScope.launch {
            // Build immediate fallback from user metadata so screen never hangs
            val metadata = user.userMetadata
            val metadataMap = metadata?.entries?.associate { it.key to it.value.jsonPrimitive.content }
            val firstNameFromAuth = metadataMap?.get("first_name") ?: ""
            val lastNameFromAuth = metadataMap?.get("last_name") ?: ""
            val fullNameFromAuth = metadataMap?.get("full_name")
                ?: listOf(firstNameFromAuth, lastNameFromAuth).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Scholar" }

            val fallback = ProfileModel(
                id = userId,
                email = user.email ?: "",
                firstName = firstNameFromAuth,
                lastName = lastNameFromAuth,
                fullName = fullNameFromAuth,
                affiliationType = "Individual",
                institution = "Independent Researcher",
                isOnboarded = false
            )

            // Emit fallback immediately to guarantee zero delay and responsive UI
            _uiState.value = EditProfileUiState.Success(fallback)

            // Then observe local profile changes (Room)
            launch {
                profileRepository.getLocalProfile(userId).collectLatest { profile ->
                    if (profile != null) {
                        _uiState.value = EditProfileUiState.Success(profile)
                    }
                }
            }

            // Sync from remote Supabase
            launch {
                try {
                    val remote = profileRepository.getProfile(userId)
                    if (remote != null) {
                        _uiState.value = EditProfileUiState.Success(remote)
                    } else {
                        // Persist fallback to local and remote if remote does not exist yet
                        profileRepository.updateProfile(fallback)
                    }
                } catch (_: Exception) {
                    profileRepository.updateProfile(fallback)
                }
            }
        }
    }

    fun updateProfile(
        firstName: String,
        lastName: String,
        affiliationType: String,
        affiliationName: String?,
        country: String,
        educationLevel: String,
        institution: String?,
        orcidId: String?
    ) {
        val user = authRepository.currentUser
        val userEmail = user?.email ?: (uiState.value as? EditProfileUiState.Success)?.profile?.email ?: ""
        val currentProfile = (uiState.value as? EditProfileUiState.Success)?.profile
            ?: ProfileModel(
                id = user?.id ?: "local_user",
                email = userEmail
            )

        val updatedProfile = currentProfile.copy(
            email = if (currentProfile.email.isNotBlank()) currentProfile.email else userEmail,
            firstName = firstName,
            lastName = lastName,
            fullName = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" "),
            affiliationType = affiliationType,
            affiliationName = affiliationName,
            country = country,
            educationLevel = educationLevel,
            institution = institution ?: affiliationName,
            orcidId = orcidId,
            isOnboarded = currentProfile.isOnboarded
        )

        _uiState.value = EditProfileUiState.Success(updatedProfile)

        viewModelScope.launch {
            profileRepository.updateProfile(updatedProfile)
            settingsRepository.setOnboardingComplete(true)
        }
    }
}

sealed interface EditProfileUiState {
    data object Loading : EditProfileUiState
    data class Success(val profile: ProfileModel) : EditProfileUiState
    data class Error(val message: String) : EditProfileUiState
}
