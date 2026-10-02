package com.example.astroxplore.features.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.astroxplore.core.util.ErrorMapper
import com.example.astroxplore.features.auth.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SignupViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _formState = MutableStateFlow(SignupFormState())
    val formState = _formState.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    private val _uiState = MutableStateFlow<SignupUiState>(SignupUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun updateFullName(fullName: String) {
        _formState.update { it.copy(fullName = fullName) }
    }

    fun updateEmail(email: String) {
        _formState.update { it.copy(email = email) }
    }

    fun updatePassword(password: String) {
        _formState.update { it.copy(password = password) }
    }

    fun togglePasswordVisibility() {
        _formState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun setTermsAccepted(accepted: Boolean) {
        _formState.update { it.copy(termsAccepted = accepted) }
    }

    fun dismissResult() {
        if (_uiState.value is SignupUiState.Success) _uiState.value = SignupUiState.Idle
    }

    fun signupFromForm() {
        val form = formState.value
        if (!form.termsAccepted) return
        val nameParts = form.fullName.trim().split(Regex("\\s+"), limit = 2)
        signup(
            email = form.email.trim(),
            password = form.password,
            firstName = nameParts.firstOrNull().orEmpty(),
            lastName = nameParts.getOrNull(1).orEmpty()
        )
    }

    fun signup(
        email: String,
        password: String,
        firstName: String,
        lastName: String
    ) {
        viewModelScope.launch {
            _uiState.value = SignupUiState.Loading
            try {
                val needsConfirmation = authRepository.register(
                    email, 
                    password, 
                    firstName, 
                    lastName
                )
                _uiState.value = SignupUiState.Success(needsConfirmation)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = SignupUiState.Error(ErrorMapper.mapToMessage(e))
            }
        }
    }
}

data class SignupFormState(
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val termsAccepted: Boolean = false
)

sealed interface SignupUiState {
    data object Idle : SignupUiState
    data object Loading : SignupUiState
    data class Success(val needsEmailConfirmation: Boolean) : SignupUiState
    data class Error(val messageResId: Int) : SignupUiState
}
