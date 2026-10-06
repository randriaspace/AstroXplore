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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
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

    private val _pendingRequests = MutableStateFlow<List<GroupJoinRequestModel>>(emptyList())
    val pendingRequests: StateFlow<List<GroupJoinRequestModel>> = _pendingRequests.asStateFlow()

    private val _sessionReviews = MutableStateFlow<List<SessionReviewModel>>(emptyList())
    val sessionReviews: StateFlow<List<SessionReviewModel>> = _sessionReviews.asStateFlow()

    private val _presentations = MutableStateFlow<List<PresentationModel>>(emptyList())
    val presentations: StateFlow<List<PresentationModel>> = _presentations.asStateFlow()

    private val _kpiStats = MutableStateFlow<GroupKpiModel?>(null)
    val kpiStats: StateFlow<GroupKpiModel?> = _kpiStats.asStateFlow()

    private val _currentUserRole = MutableStateFlow("member")
    val currentUserRole: StateFlow<String> = _currentUserRole.asStateFlow()

    private val _inviteToken = MutableStateFlow<String?>(null)
    val inviteToken: StateFlow<String?> = _inviteToken.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    val currentUserId get() = authRepository.currentUser?.id ?: "local_user"

    init {
        viewModelScope.launch {
            isOnline.collect { online ->
                if (online) {
                    val currentId = _currentGroup.value?.id
                    groupRepository.syncPendingMutations()
                    if (currentId != null) {
                        groupRepository.syncGroupPapers(currentId)
                        groupRepository.syncGroupMembers(currentId)
                        groupRepository.syncGroupPresentations(currentId)
                        groupRepository.syncGroupReviews(currentId)
                        loadKpis(currentId)
                    }
                }
            }
        }
    }

    fun loadGroupData(groupId: String) {
        viewModelScope.launch {
            _uiState.value = GroupDetailsUiState.Loading
            
            // 1. Observe group info
            launch {
                groupRepository.getLocalGroups().collectLatest { groups ->
                    val group = groups.find { it.id == groupId }
                    _currentGroup.value = group
                    if (group != null && (group.ownerId == currentUserId || group.ownerId.startsWith("local_"))) {
                        _currentUserRole.value = "admin"
                    }
                }
            }

            // 2. Observe papers in this group reactively
            launch {
                groupRepository.getLocalGroupPapers(groupId).collectLatest { groupPapers ->
                    refreshGroupUi(groupId, groupPapers)
                }
            }

            // 3. Observe presentations reactively
            launch {
                groupRepository.getGroupPresentationsFlow(groupId).collectLatest { presList ->
                    _presentations.value = presList
                }
            }

            // 4. Observe session reviews reactively
            launch {
                groupRepository.getSessionReviewsFlow(groupId).collectLatest { revList ->
                    _sessionReviews.value = revList
                }
            }

            // 5. Observe members reactively
            launch {
                groupRepository.getGroupMembersFlow(groupId).collectLatest { memberList ->
                    _members.value = memberList
                    val myMembership = memberList.find { it.userId == currentUserId }
                    if (_currentGroup.value?.ownerId == currentUserId) {
                        _currentUserRole.value = "admin"
                    } else if (myMembership != null) {
                        _currentUserRole.value = myMembership.role
                    }
                }
            }

            // 6. Observe pending join requests
            launch {
                groupRepository.getPendingJoinRequestsFlow(groupId).collectLatest { reqList ->
                    _pendingRequests.value = reqList
                }
            }
        }
        
        // Background sync and KPI compute
        viewModelScope.launch {
            groupRepository.syncGroupPapers(groupId)
            groupRepository.syncGroupMembers(groupId)
            groupRepository.syncGroupPresentations(groupId)
            groupRepository.syncGroupReviews(groupId)
            loadKpis(groupId)
        }

        // Live Supabase Realtime channel for instant vote / shelf updates
        viewModelScope.launch {
            groupRepository.observeRealtimeGroupUpdates(groupId) {
                loadKpis(groupId)
            }
        }
    }

    fun refreshGroupData() {
        val groupId = _currentGroup.value?.id ?: return
        viewModelScope.launch {
            groupRepository.syncGroupPapers(groupId)
            groupRepository.syncGroupMembers(groupId)
            groupRepository.syncGroupPresentations(groupId)
            groupRepository.syncGroupReviews(groupId)
            loadKpis(groupId)
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
                val group = _currentGroup.value
                _inviteToken.value = "ASTRO-${group?.displayId ?: groupId.take(6).uppercase()}-INVITE"
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
            _messages.emit("Member removed from club.")
            loadKpis(groupId)
        }
    }

    fun approveJoinRequest(requestId: String, applicantUserId: String) {
        val groupId = _currentGroup.value?.id ?: return
        viewModelScope.launch {
            groupRepository.approveJoinRequest(requestId, groupId, applicantUserId)
            _messages.emit("Join request approved!")
            loadKpis(groupId)
        }
    }

    fun declineJoinRequest(requestId: String) {
        viewModelScope.launch {
            groupRepository.declineJoinRequest(requestId)
            _messages.emit("Join request declined.")
        }
    }

    fun updateMemberRole(targetUserId: String, newRole: String) {
        val groupId = _currentGroup.value?.id ?: return
        viewModelScope.launch {
            groupRepository.updateMemberRole(groupId, targetUserId, newRole)
            _messages.emit("Role updated to ${newRole.replaceFirstChar { it.uppercase() }}.")
        }
    }

    fun addPaperToShelf(bibcode: String, title: String? = null, authors: String? = null, year: String? = null) {
        val groupId = _currentGroup.value?.id ?: return
        viewModelScope.launch {
            groupRepository.addPaperToGroup(groupId, bibcode, title, authors, year)
            _messages.emit("Added paper to club shelf!")
            loadKpis(groupId)
        }
    }

    fun addSessionReview(bibcode: String, paperTitle: String?, notes: String, rating: Int) {
        val groupId = _currentGroup.value?.id ?: return
        viewModelScope.launch {
            groupRepository.addSessionReview(groupId, bibcode, paperTitle, notes, rating)
            _messages.emit("Discussion notes & review saved!")
            loadKpis(groupId)
        }
    }

    fun checkInSession(presentationId: String) {
        val groupId = _currentGroup.value?.id ?: return
        viewModelScope.launch {
            groupRepository.checkInSession(presentationId)
            _messages.emit("Checked in to session!")
            loadKpis(groupId)
        }
    }

    private suspend fun refreshGroupUi(groupId: String, groupPapers: List<GroupPaperModel>) {
        try {
            val presentations = _presentations.value
            
            if (groupPapers.isEmpty()) {
                _uiState.value = GroupDetailsUiState.Success(emptyList(), emptyList(), presentations)
            } else {
                // If papers don't have titles cached, try fetching from paperRepository
                val needsRemoteFetch = groupPapers.any { it.title.isNullOrBlank() }
                val papers: List<PaperModel> = if (needsRemoteFetch && isOnline.value) {
                    try {
                        val bibcodes = groupPapers.map { it.bibcode }
                        val query = "bibcode:(" + bibcodes.joinToString(" OR ") + ")"
                        paperRepository.getPapersByQuery(query, pageSize = 50)
                    } catch (_: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
                
                _uiState.value = GroupDetailsUiState.Success(
                    papers = papers,
                    groupPapers = groupPapers,
                    presentations = presentations
                )
            }
            loadKpis(groupId)
        } catch (_: Exception) {
            _uiState.value = GroupDetailsUiState.Error("Failed to load group content")
        }
    }

    fun voteForPaper(groupPaperId: String) {
        val groupId = _currentGroup.value?.id ?: return
        viewModelScope.launch {
            groupRepository.voteForPaper(groupPaperId)
            loadKpis(groupId)
        }
    }

    fun unvoteForPaper(groupPaperId: String) {
        val groupId = _currentGroup.value?.id ?: return
        viewModelScope.launch {
            groupRepository.unvoteForPaper(groupPaperId)
            loadKpis(groupId)
        }
    }

    fun schedulePresentation(bibcode: String, paperTitle: String?, dateTime: LocalDateTime, location: String) {
        val groupId = _currentGroup.value?.id ?: return
        viewModelScope.launch {
            groupRepository.schedulePresentation(groupId, bibcode, paperTitle, dateTime, location)
            _messages.emit("Session scheduled for ${dateTime.toLocalDate()}!")
            loadKpis(groupId)
        }
    }

    fun updateGroup(name: String, description: String?, focusArea: String?, schedule: String, location: String) {
        val groupId = _currentGroup.value?.id ?: return
        viewModelScope.launch {
            groupRepository.updateGroup(groupId, name, description, focusArea, schedule, location)
            _messages.emit("Club settings updated.")
        }
    }

    fun archiveGroupWithPassword(password: String) {
        val groupId = _currentGroup.value?.id ?: return
        viewModelScope.launch {
            try {
                // If using online auth, reauthenticate; if local, allow password
                if (authRepository.isLoggedIn()) {
                    val reauthenticated = authRepository.reauthenticateCurrentUser(password)
                    if (!reauthenticated) {
                        _messages.emit("Password confirmation failed. Please try again.")
                        return@launch
                    }
                }
                groupRepository.archiveGroup(groupId)
                _messages.emit("Journal Club archived successfully.")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _messages.emit("Couldn't archive the club. Please try again.")
            }
        }
    }

    fun leaveGroup() {
        val groupId = _currentGroup.value?.id ?: return
        viewModelScope.launch {
            groupRepository.leaveGroup(groupId)
            _messages.emit("You left the Journal Club.")
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
