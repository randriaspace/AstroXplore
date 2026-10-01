package com.example.astroxplore.features.search.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.astroxplore.core.network.NasaAdsQueryBuilder
import com.example.astroxplore.core.network.NetworkConnectivityObserver
import com.example.astroxplore.core.util.ErrorMapper
import com.example.astroxplore.features.feed.data.PaperRepository
import com.example.astroxplore.features.feed.model.PaperModel
import com.example.astroxplore.features.library.data.LibraryRepository
import com.example.astroxplore.features.profile.data.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

enum class SortBy(val solrValue: String, val displayName: String) {
    DATE_DESC("date desc", "Recent"),
    CITATION_DESC("citation_count desc", "Cited")
}

data class SearchFilter(
    val yearRange: IntRange? = null,
    val refereedOnly: Boolean = false,
    val bibstem: String? = null,
    val author: String? = null,
    val abstractOnly: String? = null,
    val titleOnly: String? = null,
    val orcid: String? = null,
    val firstAuthor: String? = null,
    val affiliation: String? = null,
    val objectName: String? = null,
    val arxivId: String? = null,
    val doi: String? = null,
    val citationCountMin: Int? = null,
    val isOpenAccess: Boolean = false,
    val hasData: Boolean = false,
    val hasSoftware: Boolean = false,
    val fullText: String? = null,
    val volume: String? = null,
    val page: String? = null,
    val bibcodePrefix: String? = null,
    // Pro Fields
    val arxivClass: String? = null,
    val authorCountRange: IntRange? = null,
    val bibGroup: String? = null,
    val database: String? = null,
    val docType: String? = null,
    // Sorting
    val sortBy: SortBy = SortBy.DATE_DESC
) {
    fun isActive(): Boolean = yearRange != null || refereedOnly || !bibstem.isNullOrBlank() || 
            !author.isNullOrBlank() || !abstractOnly.isNullOrBlank() || !titleOnly.isNullOrBlank() || 
            !orcid.isNullOrBlank() || !firstAuthor.isNullOrBlank() || !affiliation.isNullOrBlank() || 
            !objectName.isNullOrBlank() || !arxivId.isNullOrBlank() || !doi.isNullOrBlank() || 
            citationCountMin != null || isOpenAccess || hasData || hasSoftware || !fullText.isNullOrBlank() ||
            !volume.isNullOrBlank() || !page.isNullOrBlank() || !bibcodePrefix.isNullOrBlank() ||
            !arxivClass.isNullOrBlank() || authorCountRange != null || !bibGroup.isNullOrBlank() || 
            !database.isNullOrBlank() || !docType.isNullOrBlank()
}

@OptIn(FlowPreview::class)
@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val paperRepository: PaperRepository,
    private val libraryRepository: LibraryRepository,
    private val profileRepository: ProfileRepository,
    networkConnectivityObserver: NetworkConnectivityObserver
) : ViewModel() {

    val isOnline: StateFlow<Boolean> = networkConnectivityObserver.isConnected
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    private val _uiState = MutableStateFlow<ExploreUiState>(ExploreUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchFilter = MutableStateFlow(SearchFilter())
    val searchFilter = _searchFilter.asStateFlow()

    private val _savedPaperIds = MutableStateFlow<Set<String>>(emptySet())
    val savedPaperIds: StateFlow<Set<String>> = _savedPaperIds.asStateFlow()

    private val _suggestedKeywords = MutableStateFlow<List<String>>(emptyList())
    val suggestedKeywords = _suggestedKeywords.asStateFlow()

    init {
        loadSavedPapers()
        loadSuggestedKeywords()
        
        _searchQuery
            .combine(_searchFilter) { query, filter -> query to filter }
            .debounce(500.milliseconds)
            .onEach { (query, filter) -> performSearch(query, filter) }
            .launchIn(viewModelScope)
    }

    private fun loadSavedPapers() {
        viewModelScope.launch {
            libraryRepository.getSavedPapers().collect { papers ->
                _savedPaperIds.value = papers.map { it.bibcode }.toSet()
            }
        }
    }

    private fun loadSuggestedKeywords() {
        viewModelScope.launch {
            _suggestedKeywords.value = profileRepository.getRandomKeywordsOnline(10)
        }
    }

    fun toggleSavePaper(paper: PaperModel) {
        viewModelScope.launch {
            libraryRepository.toggleSave(paper)
        }
    }

    fun onQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun updateFilter(filter: SearchFilter) {
        _searchFilter.value = filter
    }

    fun setSortBy(sortBy: SortBy) {
        _searchFilter.value = _searchFilter.value.copy(sortBy = sortBy)
    }

    fun clearFilters() {
        _searchFilter.value = SearchFilter()
    }

    private suspend fun performSearch(query: String, filter: SearchFilter) {
        if (query.isBlank() && !filter.isActive()) {
            _uiState.value = ExploreUiState.Idle
            return
        }

        _uiState.value = ExploreUiState.Loading
        try {
            val solrQuery = NasaAdsQueryBuilder.buildAdvancedQuery(
                query = query,
                filter = filter
            )
            val results = paperRepository.getPapersByQuery(
                query = solrQuery,
                sort = filter.sortBy.solrValue
            )
            _uiState.value = ExploreUiState.Success(results)
        } catch (e: Exception) {
            _uiState.value = ExploreUiState.Error(ErrorMapper.mapToMessage(e))
        }
    }
}

sealed interface ExploreUiState {
    data object Idle : ExploreUiState
    data object Loading : ExploreUiState
    data class Success(val results: List<PaperModel>) : ExploreUiState
    data class Error(val messageResId: Int) : ExploreUiState
}
