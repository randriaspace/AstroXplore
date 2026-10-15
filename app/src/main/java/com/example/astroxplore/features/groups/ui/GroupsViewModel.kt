package com.example.astroxplore.features.groups.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.astroxplore.core.network.NetworkConnectivityObserver
import com.example.astroxplore.features.groups.data.GroupRepository
import com.example.astroxplore.features.groups.model.GroupModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    networkConnectivityObserver: NetworkConnectivityObserver
) : ViewModel() {

    val isOnline: StateFlow<Boolean> = networkConnectivityObserver.isConnected
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _joinStatus = MutableSharedFlow<Boolean>()
    val joinStatus = _joinStatus.asSharedFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    // Offline-First: Reactively observe joined private clubs from Room
    val myClubs: StateFlow<List<GroupModel>> = groupRepository.getMyGroups()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val uiState: StateFlow<GroupsUiState> = myClubs.map<List<GroupModel>, GroupsUiState> { clubs ->
        GroupsUiState.Success(clubs = clubs)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GroupsUiState.Loading
    )

    init {
        viewModelScope.launch {
            syncGroups()
        }
        viewModelScope.launch {
            isOnline.collect { online ->
                if (online) {
                    syncGroups()
                }
            }
        }
    }

    fun syncGroups() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                groupRepository.syncPendingMutations()
                groupRepository.syncGroups()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                e.printStackTrace()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun createGroup(
        name: String,
        description: String?,
        focusArea: String?,
        schedule: String = "Weekly on Thursdays",
        location: String = "",
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val created = groupRepository.createGroup(name, description, focusArea, schedule, location)
                _messages.emit("Created \"${created.name}\" successfully!")
                syncGroups()
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                _messages.emit(e.message ?: "Could not create club. Please check internet connection.")
            }
        }
    }

    fun joinGroup(displayId: String) {
        viewModelScope.launch {
            try {
                val success = groupRepository.joinGroup(displayId)
                _joinStatus.emit(success)
                if (success) {
                    _messages.emit("Joined club successfully! Welcome aboard.")
                    syncGroups()
                } else {
                    _messages.emit("Couldn't find club with this code. Please check the code or QR scanner.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _joinStatus.emit(false)
                _messages.emit(e.message ?: "Failed to join club. Please check internet connection.")
            }
        }
    }
}

sealed interface GroupsUiState {
    data object Loading : GroupsUiState
    data class Success(val clubs: List<GroupModel>) : GroupsUiState
    data class Error(val message: String) : GroupsUiState
}
