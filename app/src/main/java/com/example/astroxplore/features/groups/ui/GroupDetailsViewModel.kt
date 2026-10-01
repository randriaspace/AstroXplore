package com.example.astroxplore.features.groups.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.astroxplore.core.network.NetworkConnectivityObserver
import com.example.astroxplore.features.auth.data.AuthRepository
import com.example.astroxplore.features.feed.data.PaperRepository
import com.example.astroxplore.features.feed.model.PaperModel
import com.example.astroxplore.features.groups.data.GroupRepository
import com.example.astroxplore.features.groups.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class GroupDetailsViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val paperRepository: PaperRepository,
    private val authRepository: AuthRepository,
    networkConnectivityObserver: NetworkConnectivityObserver
) : ViewModel() {

    val isOnline: StateFlow<Boolean> = networkConnectivityObserver.isConnected
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    private val _uiState = MutableStateFlow<GroupDetailsUiState>(GroupDetailsUiState.Loading)
    val uiState: StateFlow<GroupDetailsUiState> = _uiState.asStateFlow()

    private val _currentGroup = MutableStateFlow<GroupModel?>(null)
    val currentGroup = _currentGroup.asStateFlow()

    private val _members = MutableStateFlow<List<GroupMemberModel>>(emptyList())
    val members: StateFlow<List<GroupMemberModel>> = _members.asStateFlow()

    private val _sessionReviews = MutableStateFlow<List<SessionReviewModel>>(emptyList())
    val sessionReviews: StateFlow<List<SessionReviewModel>> = _sessionReviews.asStateFlow()

    private val _kpiStats = MutableStateFlow<GroupKpiModel?>(null)
    val kpiStats: StateFlow<GroupKpiModel?> = _kpiStats.asStateFlow()

    private val _currentUserRole = MutableStateFlow("member")
    val currentUserRole: StateFlow<String> = _currentUserRole.asStateFlow()

    private val _inviteToken = MutableStateFlow<String?>(null)
    val inviteToken: StateFlow<String?> = _inviteToken.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    val currentUserId get() = authRepository.currentUser?.id

    fun loadGroupData(groupId: String) {
        viewModelScope.launch {
            _uiState.value = GroupDetailsUiState.Loading
            
            // Sync specific group info from local
            groupRepository.getLocalGroups().collectLatest { groups ->
                val group = groups.find { it.id == groupId }
                _currentGroup.value = group
                if (group != null && group.ownerId == currentUserId) {
                    _currentUserRole.value = "admin"
                }
            }
        }

        viewModelScope.launch {
            // Observe papers in this group reactively
            groupRepository.getLocalGroupPapers(groupId).collectLatest { groupPapers ->
                refreshGroupUi(groupId, groupPapers)
            }
        }
        
        // Background sync
        viewModelScope.launch {
            groupRepository.syncGroupPapers(groupId)
            loadGroupMembers(groupId)
            loadSessionReviews(groupId)
            loadKpis(groupId)
        }
    }

    fun loadGroupMembers(groupId: String) {
        viewModelScope.launch {
            val fetchedMembers = groupRepository.getGroupMembers(groupId)
            _members.value = fetchedMembers
            val myMembership = fetchedMembers.find { it.userId == currentUserId }
            if (_currentGroup.value?.ownerId == currentUserId) {
                _currentUserRole.value = "admin"
            } else if (myMembership != null) {
                _currentUserRole.value = myMembership.role
            }
        }
    }

    fun loadSessionReviews(groupId: String) {
        viewModelScope.launch {
            _sessionReviews.value = groupRepository.getSessionReviews(groupId)
        }
    }

    fun loadKpis(groupId: String) {
        viewModelScope.launch {
            _kpiStats.value = groupRepository.getGroupKpis(groupId)
        }
    }

    fun createInvite() {
        val groupId = _currentGroup.value?.id ?: return
        viewModelScope.launch {
            try {
                _inviteToken.value = groupRepository.createGroupInvite(groupId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _messages.emit("Couldn't create an invite. Check your connection and permissions.")
            }
        }
    }

    fun dismissInvite() {
        _inviteToken.value = null
    }

    fun kickMember(targetUserId: String) {
        val groupId = _currentGroup.value?.id ?: return
        viewModelScope.launch {
            groupRepository.kickMember(groupId, targetUserId)
            loadGroupMembers(groupId)
            loadKpis(groupId)
        }
    }

    fun updateMemberRole(targetUserId: String, newRole: String) {
        val groupId = _currentGroup.value?.id ?: return
        viewModelScope.launch {
            groupRepository.updateMemberRole(groupId, targetUserId, newRole)
            loadGroupMembers(groupId)
        }
    }

    fun addSessionReview(bibcode: String, notes: String, rating: Int) {
        val groupId = _currentGroup.value?.id ?: return
        viewModelScope.launch {
            groupRepository.addSessionReview(groupId, bibcode, notes, rating)
            loadSessionReviews(groupId)
            loadKpis(groupId)
        }
    }

    fun checkInSession(presentationId: String) {
        val groupId = _currentGroup.value?.id ?: return
        viewModelScope.launch {
            groupRepository.checkInSession(presentationId)
            loadKpis(groupId)
        }
    }

    private suspend fun refreshGroupUi(groupId: String, groupPapers: List<GroupPaperModel>) {
        try {
            val presentations = groupRepository.getGroupPresentations(groupId)
            
            if (groupPapers.isEmpty()) {
                _uiState.value = GroupDetailsUiState.Success(emptyList(), emptyList(), presentations)
            } else {
                val bibcodes = groupPapers.map { it.bibcode }
                val query = "bibcode:(" + bibcodes.joinToString(" OR ") + ")"
                val papers = paperRepository.getPapersByQuery(query, pageSize = 50)
                
                _uiState.value = GroupDetailsUiState.Success(
                    papers = papers,
                    groupPapers = groupPapers,
                    presentations = presentations
                )
            }
        } catch (e: Exception) {
            _uiState.value = GroupDetailsUiState.Error("Failed to load group content")
        }
    }

    private fun refreshGroupContent(groupId: String) {
        viewModelScope.launch {
            groupRepository.syncGroupPapers(groupId)
            loadKpis(groupId)
        }
    }

    fun voteForPaper(groupPaperId: String) {
        val groupId = _currentGroup.value?.id ?: return
        viewModelScope.launch {
            groupRepository.voteForPaper(groupPaperId)
            refreshGroupContent(groupId)
        }
    }

    fun unvoteForPaper(groupPaperId: String) {
        val groupId = _currentGroup.value?.id ?: return
        viewModelScope.launch {
            groupRepository.unvoteForPaper(groupPaperId)
            refreshGroupContent(groupId)
        }
    }

    fun schedulePresentation(bibcode: String, dateTime: LocalDateTime) {
        val groupId = _currentGroup.value?.id ?: return
        viewModelScope.launch {
            groupRepository.schedulePresentation(groupId, bibcode, dateTime)
            refreshGroupContent(groupId)
        }
    }

    fun updateGroup(name: String, description: String?, focusArea: String?) {
        val groupId = _currentGroup.value?.id ?: return
        viewModelScope.launch {
            groupRepository.updateGroup(groupId, name, description, focusArea)
        }
    }

    fun archiveGroup() {
        val groupId = _currentGroup.value?.id ?: return
        viewModelScope.launch {
            groupRepository.archiveGroup(groupId)
        }
    }

    fun leaveGroup() {
        val groupId = _currentGroup.value?.id ?: return
        viewModelScope.launch {
            groupRepository.leaveGroup(groupId)
        }
    }
}

sealed interface GroupDetailsUiState {
    data object Loading : GroupDetailsUiState
    data class Success(
        val papers: List<PaperModel>,
        val groupPapers: List<GroupPaperModel>,
        val presentations: List<PresentationModel>
    ) : GroupDetailsUiState
    data class Error(val message: String) : GroupDetailsUiState
}
