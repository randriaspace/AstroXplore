package com.example.astroxplore.features.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.astroxplore.core.util.ErrorMapper
import com.example.astroxplore.features.auth.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SignupViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SignupUiState>(SignupUiState.Idle)
    val uiState = _uiState.asStateFlow()

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
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = SignupUiState.Error(ErrorMapper.mapToMessage(e))
            }
        }
    }
}

sealed interface SignupUiState {
    data object Idle : SignupUiState
    data object Loading : SignupUiState
    data class Success(val needsEmailConfirmation: Boolean) : SignupUiState
    data class Error(val messageResId: Int) : SignupUiState
}
