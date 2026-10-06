package com.example.astroxplore.features.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.astroxplore.features.auth.data.AuthRepository
import com.example.astroxplore.features.profile.data.ProfileRepository
import com.example.astroxplore.features.profile.model.ProfileModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository
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
        val userId = authRepository.currentUser?.id ?: return
        viewModelScope.launch {
            profileRepository.getLocalProfile(userId).collectLatest { profile ->
                if (profile != null) {
                    _uiState.value = EditProfileUiState.Success(profile)
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
        val currentProfile = (uiState.value as? EditProfileUiState.Success)?.profile ?: return
        val updatedProfile = currentProfile.copy(
            firstName = firstName,
            lastName = lastName,
            fullName = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" "),
            affiliationType = affiliationType,
            affiliationName = affiliationName,
            country = country,
            educationLevel = educationLevel,
            institution = institution ?: affiliationName,
            orcidId = orcidId
        )

        viewModelScope.launch {
            profileRepository.updateProfile(updatedProfile)
        }
    }
}

sealed interface EditProfileUiState {
    data object Loading : EditProfileUiState
    data class Success(val profile: ProfileModel) : EditProfileUiState
    data class Error(val message: String) : EditProfileUiState
}
