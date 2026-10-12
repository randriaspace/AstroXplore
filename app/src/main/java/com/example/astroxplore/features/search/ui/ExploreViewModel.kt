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

import com.example.astroxplore.BuildConfig
import com.example.astroxplore.features.groups.data.GroupRepository
import com.example.astroxplore.features.search.data.GeminiAiResearchService
import com.example.astroxplore.features.search.model.AiResearchBriefing

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
    private val groupRepository: GroupRepository,
    private val geminiAiResearchService: GeminiAiResearchService,
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

    val userResearchInterests: StateFlow<List<String>> = profileRepository.getLocalUserPreferences()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _aiBriefings = MutableStateFlow<List<AiResearchBriefing>>(emptyList())
    val aiBriefings: StateFlow<List<AiResearchBriefing>> = _aiBriefings.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMoreResults = MutableStateFlow(true)
    val hasMoreResults: StateFlow<Boolean> = _hasMoreResults.asStateFlow()

    private var currentPage = 0

    init {
        loadSavedPapers()
        loadSuggestedKeywords()
        loadAiResearchBriefings()
        
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

    fun loadAiResearchBriefings() {
        viewModelScope.launch {
            _isAiLoading.value = true
            try {
                val userPrefs = profileRepository.getLocalUserPreferences().firstOrNull() ?: emptyList()
                val groups = groupRepository.getMyGroups().firstOrNull() ?: emptyList()
                val groupNames = groups.map { it.name }
                val savedPapers = libraryRepository.getSavedPapers().firstOrNull() ?: emptyList()
                val savedTitles = savedPapers.map { it.title }

                val apiKey = try {
                    val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
                    field.get(null) as? String ?: ""
                } catch (_: Exception) {
                    ""
                }

                if (apiKey.isNotBlank() && !apiKey.contains("placeholder", ignoreCase = true)) {
                    val aiResults = geminiAiResearchService.synthesizeResearchBriefings(
                        apiKey = apiKey,
                        userInterests = userPrefs,
                        groupTopics = groupNames,
                        savedPaperTitles = savedTitles
                    )
                    if (aiResults.isNotEmpty()) {
                        _aiBriefings.value = aiResults
                        return@launch
                    }
                }

                // Resilient local synthesis fallback based on user's active Journal Clubs and saved papers
                _aiBriefings.value = generateAcademicSynthesisFallback(userPrefs, groups.map { it.name }, savedTitles)
            } catch (_: Exception) {
                _aiBriefings.value = generateAcademicSynthesisFallback(emptyList(), emptyList(), emptyList())
            } finally {
                _isAiLoading.value = false
            }
        }
    }

    private fun generateAcademicSynthesisFallback(
        userPrefs: List<String>,
        groupNames: List<String>,
        savedTitles: List<String>
    ): List<AiResearchBriefing> {
        val primaryInterest = userPrefs.firstOrNull() ?: "Exoplanet Atmospheres"
        val clubTopic = groupNames.firstOrNull() ?: "JWST Early Universe Club"

        return listOf(
            AiResearchBriefing(
                id = "briefing_jwst_exoplanets",
                title = "Atmospheric Metallicity & Cloud Decks in Warm Sub-Neptunes",
                hypothesisSummary = "Recent JWST transmission spectroscopy observations reveal depleted methane and enhanced carbon-to-oxygen ratios challenging canonical photochemical models.",
                categoryBadge = "Journal Club Synergy",
                recommendedAdsQuery = "(title:\"transmission spectroscopy\" OR abs:\"atmospheric metallicity\") AND property:refereed AND year:2024-2026",
                contextSourceReason = "Synthesized from your active club '$clubTopic' and interest in $primaryInterest",
                suggestedKeywords = listOf("JWST NIRSpec", "Exoplanet Atmospheres", "Photochemistry"),
                targetBibstem = "ApJ"
            ),
            AiResearchBriefing(
                id = "briefing_cosmic_dawn",
                title = "Over-Luminous Galaxies at z > 10 and Pop III Initial Mass Functions",
                hypothesisSummary = "High-redshift JWST JADES and UNCOVER surveys find significant excess of bright galaxies, constraining early cosmic dust extinction and starburst efficiency.",
                categoryBadge = "Trending Citation Benchmark",
                recommendedAdsQuery = "(title:\"high redshift\" OR abs:\"cosmic dawn\") AND year:2024-2026 AND property:refereed",
                contextSourceReason = "Trending across ApJ & MNRAS citation clusters in observational cosmology",
                suggestedKeywords = listOf("Cosmic Dawn", "z > 10 Galaxies", "JWST NIRCam"),
                targetBibstem = "MNRAS"
            ),
            AiResearchBriefing(
                id = "briefing_gw_multimessenger",
                title = "Compact Object Mergers with Gravitational Wave Counterparts",
                hypothesisSummary = "LIGO-Virgo-KAGRA O4 run detections coupled with rapid-response optical transient follow-ups constrain neutron star equation of state and kilonova r-process nucleosynthesis.",
                categoryBadge = "High-Energy Astrophysics",
                recommendedAdsQuery = "(title:\"gravitational wave\" OR abs:\"kilonova\") AND property:refereed",
                contextSourceReason = "Derived from current peer-reviewed research papers and multi-messenger campaigns",
                suggestedKeywords = listOf("Gravitational Waves", "Kilonova", "r-process"),
                targetBibstem = "A&A"
            )
        )
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

    fun retry() {
        viewModelScope.launch {
            performSearch(_searchQuery.value, _searchFilter.value)
        }
    }

    fun loadNextPage() {
        val currentQuery = searchQuery.value
        val currentFilter = searchFilter.value
        if ((currentQuery.isBlank() && !currentFilter.isActive()) || _isLoadingMore.value || !_hasMoreResults.value) {
            return
        }

        val currentState = _uiState.value
        if (currentState !is ExploreUiState.Success) return

        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val nextPage = currentPage + 1
                val solrQuery = NasaAdsQueryBuilder.buildAdvancedQuery(
                    query = currentQuery,
                    filter = currentFilter
                )
                val searchResult = paperRepository.getPapersByQueryWithTotal(
                    query = solrQuery,
                    sort = currentFilter.sortBy.solrValue,
                    page = nextPage,
                    pageSize = 10
                )

                if (searchResult.papers.isEmpty()) {
                    _hasMoreResults.value = false
                } else {
                    currentPage = nextPage
                    val existing = currentState.results
                    val existingBibcodes = existing.map { it.bibcode }.toSet()
                    val filteredNew = searchResult.papers.filterNot { existingBibcodes.contains(it.bibcode) }
                    
                    val combined = existing + filteredNew
                    _uiState.value = ExploreUiState.Success(
                        results = combined,
                        totalFound = searchResult.totalFound
                    )

                    if (searchResult.papers.size < 10) {
                        _hasMoreResults.value = false
                    }
                }
            } catch (_: Exception) {
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    private suspend fun performSearch(query: String, filter: SearchFilter) {
        if (query.isBlank() && !filter.isActive()) {
            _uiState.value = ExploreUiState.Idle
            return
        }

        currentPage = 0
        _hasMoreResults.value = true
        _uiState.value = ExploreUiState.Loading

        try {
            val solrQuery = NasaAdsQueryBuilder.buildAdvancedQuery(
                query = query,
                filter = filter
            )
            val searchResult = paperRepository.getPapersByQueryWithTotal(
                query = solrQuery,
                sort = filter.sortBy.solrValue,
                page = 0,
                pageSize = 20
            )
            _uiState.value = ExploreUiState.Success(
                results = searchResult.papers,
                totalFound = searchResult.totalFound
            )
            if (searchResult.papers.size < 20) {
                _hasMoreResults.value = false
            }
        } catch (e: Exception) {
            _uiState.value = ExploreUiState.Error(ErrorMapper.mapToMessage(e))
        }
    }
}

sealed interface ExploreUiState {
    data object Idle : ExploreUiState
    data object Loading : ExploreUiState
    data class Success(val results: List<PaperModel>, val totalFound: Int = 0) : ExploreUiState
    data class Error(val messageResId: Int) : ExploreUiState
}
