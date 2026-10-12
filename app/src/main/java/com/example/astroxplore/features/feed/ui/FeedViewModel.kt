package com.example.astroxplore.features.feed.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.astroxplore.core.network.NasaAdsQueryBuilder
import com.example.astroxplore.core.network.NetworkConnectivityObserver
import com.example.astroxplore.core.util.ErrorMapper
import com.example.astroxplore.core.util.NavigationSignal
import com.example.astroxplore.features.auth.data.AuthRepository
import com.example.astroxplore.features.feed.data.PaperRepository
import com.example.astroxplore.features.feed.model.PaperModel
import com.example.astroxplore.features.groups.data.GroupRepository
import com.example.astroxplore.features.groups.model.GroupModel
import com.example.astroxplore.features.library.data.LibraryRepository
import com.example.astroxplore.features.profile.data.ProfileRepository
import com.example.astroxplore.features.profile.model.ProfileModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FeedViewModel @Inject constructor(
    private val paperRepository: PaperRepository,
    private val groupRepository: GroupRepository,
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val libraryRepository: LibraryRepository,
    private val navigationSignal: NavigationSignal,
    private val networkConnectivityObserver: NetworkConnectivityObserver
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _isInitialLoading = MutableStateFlow(true)
    val isInitialLoading = _isInitialLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private val _error = MutableStateFlow<Int?>(null)
    val error = _error.asStateFlow()

    private val _selectedCategory = MutableStateFlow("For You")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    val scrollToTopEvent = navigationSignal.scrollToTop

    private val _savedPaperIds = MutableStateFlow<Set<String>>(emptySet())
    val savedPaperIds: StateFlow<Set<String>> = _savedPaperIds.asStateFlow()

    val isOnline: StateFlow<Boolean> = networkConnectivityObserver.isConnected
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val userGroups: StateFlow<List<GroupModel>> = groupRepository.getLocalGroups()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val userProfile: StateFlow<ProfileModel?> = flow {
        val userId = authRepository.currentUser?.id
        if (userId != null) {
            emitAll(profileRepository.getLocalProfile(userId))
        } else {
            emit(null)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val userInterests: StateFlow<List<String>> = profileRepository.getLocalUserPreferences()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var currentPage = 0
    private var isEndReached = false

    // In-memory list to support infinite scroll smoothly
    private val _pagedPapers = MutableStateFlow<List<PaperModel>>(emptyList())
    
    // Combine local DB and Paged results
    val feedPapers: StateFlow<List<PaperModel>> = combine(
        paperRepository.getCachedFeed(),
        _pagedPapers,
        _selectedCategory
    ) { cached, paged, category ->
        if (paged.isNotEmpty()) {
            paged
        } else if (category == "For You") {
            cached
        } else {
            paged
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(30000),
        initialValue = emptyList()
    )

    init {
        loadSavedPapers()
        
        // Initial load
        refresh()
    }

    private fun loadSavedPapers() {
        viewModelScope.launch {
            libraryRepository.getSavedPapers().collect { papers ->
                _savedPaperIds.value = papers.map { it.bibcode }.toSet()
            }
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            _error.value = null
            currentPage = 0
            isEndReached = false
            try {
                val query = buildQuery(_selectedCategory.value)
                val freshPapers = paperRepository.getPapersByQuery(query, page = 0)
                _pagedPapers.value = freshPapers
                
                // Cache papers for offline resilience & app-switch persistence
                val currentCat = _selectedCategory.value
                paperRepository.cacheCategoryPapers(currentCat, freshPapers)
                if (currentCat == "For You") {
                    paperRepository.refreshFeed(query)
                }
            } catch (e: Exception) {
                // Keep existing in-memory papers or load cached fallback if empty
                if (_pagedPapers.value.isEmpty()) {
                    val currentCat = _selectedCategory.value
                    val fallback = if (currentCat == "For You") {
                        paperRepository.getCachedFeed().firstOrNull() ?: emptyList()
                    } else {
                        paperRepository.getCachedFeedByCategory(currentCat).firstOrNull() ?: emptyList()
                    }
                    if (fallback.isNotEmpty()) {
                        _pagedPapers.value = fallback
                    }
                }
                _error.value = ErrorMapper.mapToMessage(e)
            } finally {
                _isRefreshing.value = false
                _isInitialLoading.value = false
            }
        }
    }

    fun loadNextPage() {
        if (_isLoadingMore.value || isEndReached || !isOnline.value) return
        
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                currentPage++
                val query = buildQuery(_selectedCategory.value)
                val newPapers = paperRepository.getPapersByQuery(query, page = currentPage)
                
                if (newPapers.isEmpty()) {
                    isEndReached = true
                } else {
                    val combined = _pagedPapers.value + newPapers
                    _pagedPapers.value = combined
                    paperRepository.cacheCategoryPapers(_selectedCategory.value, combined)
                }
            } catch (e: Exception) {
                currentPage-- // Reset page on error to allow retry
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun selectCategory(category: String) {
        if (_selectedCategory.value == category) return
        _selectedCategory.value = category
        currentPage = 0
        isEndReached = false
        viewModelScope.launch {
            // First check if we have cached papers for this category so feed doesn't jump
            val cached = if (category == "For You") {
                paperRepository.getCachedFeed().firstOrNull() ?: emptyList()
            } else {
                paperRepository.getCachedFeedByCategory(category).firstOrNull() ?: emptyList()
            }
            if (cached.isNotEmpty()) {
                _pagedPapers.value = cached
            }
            refresh()
        }
    }

    fun toggleSavePaper(paper: PaperModel) {
        viewModelScope.launch {
            libraryRepository.toggleSave(paper)
        }
    }

    fun addPaperToGroup(groupId: String, bibcode: String) {
        viewModelScope.launch {
            groupRepository.addPaperToGroup(groupId, bibcode)
        }
    }

    private suspend fun buildQuery(category: String): String {
        return when (category) {
            "For You" -> {
                val userId = authRepository.currentUser?.id
                val prefs = userId?.let { profileRepository.getUserPreferences(it) } ?: emptyList()
                NasaAdsQueryBuilder.buildPreferenceQuery(prefs)
            }
            "Published" -> {
                val userId = authRepository.currentUser?.id
                val prefs = userId?.let { profileRepository.getUserPreferences(it) } ?: emptyList()
                val base = if (prefs.isNotEmpty()) NasaAdsQueryBuilder.buildPreferenceQuery(prefs) else "database:astronomy"
                "($base) AND property:refereed"
            }
            "Under Review" -> {
                val userId = authRepository.currentUser?.id
                val prefs = userId?.let { profileRepository.getUserPreferences(it) } ?: emptyList()
                val base = if (prefs.isNotEmpty()) NasaAdsQueryBuilder.buildPreferenceQuery(prefs) else "database:astronomy"
                "($base) AND (property:not_refereed OR doctype:eprint)"
            }
            else -> {
                // High-fidelity broad search
                val cleanCategory = category.trim()
                "($cleanCategory) AND database:astronomy"
            }
        }
    }
}
