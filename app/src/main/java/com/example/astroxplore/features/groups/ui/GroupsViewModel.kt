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

    // Offline-First: Reactively observe joined clubs from Room
    val myGroups: StateFlow<List<GroupModel>> = groupRepository.getMyGroups()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Reactively observe discoverable public clubs from Room
    val exploreGroups: StateFlow<List<GroupModel>> = groupRepository.getExploreGroups()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val uiState: StateFlow<GroupsUiState> = combine(myGroups, exploreGroups) { my, explore ->
        GroupsUiState.Success(myClubs = my, exploreClubs = explore)
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
                    groupRepository.syncPendingMutations()
                }
            }
        }
    }

    fun syncGroups() {
        viewModelScope.launch {
            _isRefreshing.value = true
            groupRepository.syncPendingMutations()
            _isRefreshing.value = false
        }
    }

    fun createGroup(
        name: String,
        description: String?,
        focusArea: String?,
        schedule: String = "Weekly on Thursdays",
        location: String = ""
    ) {
        viewModelScope.launch {
            try {
                val created = groupRepository.createGroup(name, description, focusArea, schedule, location)
                _messages.emit("Created \"${created.name}\" successfully!")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _messages.emit("Couldn't create the club. Saved locally.")
            }
        }
    }

    fun joinGroup(displayId: String) {
        viewModelScope.launch {
            val success = groupRepository.joinGroup(displayId)
            _joinStatus.emit(success)
            if (success) {
                _messages.emit("Joined club successfully!")
            } else {
                _messages.emit("Couldn't join club. Please check the club code.")
            }
        }
    }

    fun joinPublicClub(groupId: String, clubName: String) {
        viewModelScope.launch {
            val success = groupRepository.joinPublicClub(groupId)
            if (success) {
                _messages.emit("Joined \"$clubName\"! Check My Clubs.")
            }
        }
    }
}

sealed interface GroupsUiState {
    data object Loading : GroupsUiState
    data class Success(
        val myClubs: List<GroupModel>,
        val exploreClubs: List<GroupModel>
    ) : GroupsUiState
    data class Error(val message: String) : GroupsUiState
}
