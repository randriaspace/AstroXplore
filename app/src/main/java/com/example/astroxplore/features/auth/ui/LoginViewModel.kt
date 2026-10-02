package com.example.astroxplore.features.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.astroxplore.R
import com.example.astroxplore.core.network.NetworkConnectivityObserver
import com.example.astroxplore.core.util.ErrorMapper
import com.example.astroxplore.features.auth.data.AuthRepository
import com.example.astroxplore.features.profile.data.ProfileRepository
import com.example.astroxplore.features.profile.model.ProfileModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val networkConnectivityObserver: NetworkConnectivityObserver
) : ViewModel() {

    private val _formState = MutableStateFlow(LoginFormState())
    val formState = _formState.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState = _uiState.asStateFlow()

    val isOnline: StateFlow<Boolean> = networkConnectivityObserver.isConnected
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    fun updateEmail(email: String) {
        _formState.update { it.copy(email = email) }
    }

    fun updatePassword(password: String) {
        _formState.update { it.copy(password = password) }
    }

    fun togglePasswordVisibility() {
        _formState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun toggleRememberMe() {
        _formState.update { it.copy(rememberMe = !it.rememberMe) }
    }

    fun requestPasswordReset() {
        val email = formState.value.email.trim()
        if (email.isBlank()) {
            viewModelScope.launch { _messages.emit("Enter your email address first.") }
            return
        }
        viewModelScope.launch {
            try {
                authRepository.requestPasswordReset(email)
                _messages.emit("If an account exists for that email, a reset link is on its way.")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _messages.emit("Couldn't request a password reset. Check your connection and try again.")
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                authRepository.login(email, password)
                val user = authRepository.currentUser
                if (user != null) {
                    val metadata = user.userMetadata
                    val metadataMap = metadata?.entries?.associate { it.key to it.value.jsonPrimitive.content }
                    val firstNameFromMetadata = metadataMap?.get("first_name")?.trim()
                    val lastNameFromMetadata = metadataMap?.get("last_name")?.trim()
                    val fullNameFromMetadata = metadataMap?.get("full_name")?.trim()

                    var profile = profileRepository.getProfile(user.id)

                    if (profile == null) {
                        val newProfile = AuthRepository.buildProfileFromUserData(
                            userId = user.id,
                            email = user.email ?: email,
                            metadata = metadataMap,
                            isOnboarded = false
                        )
                        profileRepository.updateProfile(newProfile)
                        profile = newProfile
                    } else if (profile.fullName.isNullOrBlank() || profile.firstName.isNullOrBlank() || profile.lastName.isNullOrBlank()) {
                        val syncedProfile = profile.copy(
                            firstName = profile.firstName ?: firstNameFromMetadata,
                            lastName = profile.lastName ?: lastNameFromMetadata,
                            fullName = profile.fullName ?: fullNameFromMetadata
                        )
                        profileRepository.updateProfile(syncedProfile)
                        profile = syncedProfile
                    }

                    _uiState.value = LoginUiState.Success(isOnboarded = profile.isOnboarded)
                } else {
                    _uiState.value = LoginUiState.Error(R.string.error_unknown)
                }
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(ErrorMapper.mapToMessage(e))
            }
        }
    }
}

data class LoginFormState(
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val rememberMe: Boolean = false
)

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Success(val isOnboarded: Boolean) : LoginUiState
    data class Error(val messageResId: Int) : LoginUiState
}
