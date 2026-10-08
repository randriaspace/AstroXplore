package com.example.astroxplore.features.search.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.astroxplore.R
import com.example.astroxplore.features.feed.model.PaperModel
import com.example.astroxplore.features.feed.ui.components.PaperCard
import com.example.astroxplore.features.feed.ui.components.PaperDetailsBottomSheet
import com.example.astroxplore.features.feed.ui.components.PaperCardSkeleton
import com.example.astroxplore.core.ui.components.LottieLoadingView
import com.example.astroxplore.core.ui.components.OfflineFallbackState

private val QUICK_DISCOVERY_TOPICS = listOf(
    "🔭 JWST" to "James Webb Space Telescope",
    "🪐 Exoplanets" to "Exoplanet atmospheres",
    "🕳️ Black Holes" to "Black hole merger",
    "🌌 Cosmology" to "Cosmological expansion",
    "⚡ Gravitational Waves" to "Gravitational wave",
    "⚛️ Dark Matter" to "Dark matter halo",
    "✨ Supernovae" to "Supernova remnant",
    "☄️ Asteroids" to "Near earth asteroids"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    autofocus: Boolean = false,
    viewModel: ExploreViewModel = hiltViewModel(),
    onPaperClick: (String) -> Unit = {},
    onLibraryClick: () -> Unit = {}
) {
    val isOnline by viewModel.isOnline.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val filter by viewModel.searchFilter.collectAsState()
    val savedPaperIds by viewModel.savedPaperIds.collectAsState()
    val suggestedKeywords by viewModel.suggestedKeywords.collectAsState()

    var showFilterSheet by remember { mutableStateOf(false) }
    var selectedPaperForDetails by remember { mutableStateOf<PaperModel?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Explore Universe",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "NASA ADS",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                windowInsets = WindowInsets(0.dp),
                actions = {
                    BadgedBox(
                        badge = {
                            if (filter.isActive()) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.offset(x = (-4).dp, y = 4.dp)
                                )
                            }
                        }
                    ) {
                        FilledTonalIconButton(
                            onClick = { showFilterSheet = true },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (filter.isActive())
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = "Advanced Filters",
                                tint = if (filter.isActive())
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Google M3 Capsule Search Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = CircleShape,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            ) {
                TextField(
                    value = query,
                    onValueChange = { viewModel.onQueryChange(it) },
                    placeholder = {
                        Text(
                            text = "Search 10M+ astrophysics papers...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onQueryChange("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Quick Topic & Filter Assist Chips Carousel
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quick Toggle: Peer-Reviewed Only
                item {
                    FilterChip(
                        selected = filter.refereedOnly,
                        onClick = { viewModel.updateFilter(filter.copy(refereedOnly = !filter.refereedOnly)) },
                        label = { Text("Peer-Reviewed") },
                        leadingIcon = {
                            if (filter.refereedOnly) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            } else {
                                Icon(Icons.Outlined.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }

                // Quick Toggle: Open Access
                item {
                    FilterChip(
                        selected = filter.isOpenAccess,
                        onClick = { viewModel.updateFilter(filter.copy(isOpenAccess = !filter.isOpenAccess)) },
                        label = { Text("Open Access") },
                        leadingIcon = {
                            if (filter.isOpenAccess) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            } else {
                                Icon(Icons.Outlined.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }

                // Curated Topic Chips
                items(QUICK_DISCOVERY_TOPICS) { (chipLabel, searchQueryValue) ->
                    val isSelected = query.equals(searchQueryValue, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (isSelected) {
                                viewModel.onQueryChange("")
                            } else {
                                viewModel.onQueryChange(searchQueryValue)
                            }
                        },
                        label = { Text(chipLabel) },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            selectedBorderColor = Color.Transparent,
                            enabled = true,
                            selected = isSelected
                        )
                    )
                }
            }

            // Results Content with Animated State Transitions
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = uiState,
                    transitionSpec = {
                        fadeIn(tween(250)) togetherWith fadeOut(tween(200))
                    },
                    label = "search_results"
                ) { state ->
                    if (!isOnline && (state is ExploreUiState.Idle || state is ExploreUiState.Error || (state is ExploreUiState.Success && state.results.isEmpty()))) {
                        OfflineFallbackState(
                            message = "Live search and discovery require an internet connection. Visit your Personal Library to read saved papers.",
                            onLibraryClick = onLibraryClick
                        )
                    } else {
                        when (state) {
                            ExploreUiState.Idle -> SearchIdleState(
                                suggestedKeywords = suggestedKeywords,
                                onKeywordClick = { viewModel.onQueryChange(it) },
                                onTopicClick = { viewModel.onQueryChange(it) }
                            )

                            ExploreUiState.Loading -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(top = 8.dp)
                                ) {
                                    repeat(3) {
                                        PaperCardSkeleton()
                                    }
                                }
                            }

                            is ExploreUiState.Success -> {
                                if (state.results.isEmpty()) {
                                    EmptyResultsState(
                                        query = query,
                                        onClearQuery = {
                                            viewModel.onQueryChange("")
                                            viewModel.clearFilters()
                                        }
                                    )
                                } else {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        // Results Metric & Sorting Row
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 20.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Description,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "${state.results.size} Papers Found",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            // Sorting Chips (Recent vs Cited)
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                SortBy.entries.forEach { sortBy ->
                                                    val isSelected = filter.sortBy == sortBy
                                                    FilterChip(
                                                        selected = isSelected,
                                                        onClick = { viewModel.setSortBy(sortBy) },
                                                        label = {
                                                            Text(
                                                                text = sortBy.displayName,
                                                                style = MaterialTheme.typography.labelSmall
                                                            )
                                                        },
                                                        leadingIcon = {
                                                            val icon = if (sortBy == SortBy.DATE_DESC)
                                                                Icons.Outlined.Schedule
                                                            else
                                                                Icons.Outlined.FormatQuote
                                                            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        },
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = FilterChipDefaults.filterChipColors(
                                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(bottom = 96.dp)
                                        ) {
                                            items(state.results, key = { it.bibcode }) { paper ->
                                                PaperCard(
                                                    paper = paper,
                                                    isSaved = savedPaperIds.contains(paper.bibcode),
                                                    onSaveClick = { viewModel.toggleSavePaper(paper) },
                                                    onTitleClick = { onPaperClick(paper.bibcode) },
                                                    onReadMoreClick = {
                                                        selectedPaperForDetails = paper
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            is ExploreUiState.Error -> ErrorState(
                                message = stringResource(state.messageResId),
                                onRetry = { viewModel.retry() }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        FilterBottomSheet(
            filter = filter,
            onDismiss = { showFilterSheet = false },
            onApply = { viewModel.updateFilter(it) },
            onClear = { viewModel.clearFilters() }
        )
    }

    if (selectedPaperForDetails != null) {
        PaperDetailsBottomSheet(
            paper = selectedPaperForDetails!!,
            showAuthorsOnly = false,
            onDismiss = { selectedPaperForDetails = null },
            onNavigateToDetails = onPaperClick
        )
    }
}

/**
 * Google Discover Style Idle Hub for Astrophysics Research
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchIdleState(
    suggestedKeywords: List<String>,
    onKeywordClick: (String) -> Unit,
    onTopicClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Cosmic Hero Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(24.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Text(
                                text = "NASA ADS Engine",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Explore the Universe",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Access over 10 million peer-reviewed papers, arXiv preprints, and astronomical catalogs with real-time academic precision.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Curated Research Pathways
        Text(
            text = "CURATED RESEARCH PATHWAYS",
            style = MaterialTheme.typography.labelLarge.copy(
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )

        Spacer(modifier = Modifier.height(12.dp))

        val pathways = listOf(
            Triple("🪐 Exoplanetary Atmospheres", "JWST transmission spectroscopy & biosignatures", "JWST exoplanet atmosphere"),
            Triple("🔭 Cosmic Dawn & First Stars", "High-redshift galaxy formation at z > 10", "cosmic dawn high redshift galaxies"),
            Triple("🕳️ Supermassive Black Holes", "Relativistic jets and Event Horizon astrophysics", "supermassive black hole event horizon"),
            Triple("⚡ Gravitational Wave Astronomy", "Multi-messenger neutron star & compact mergers", "gravitational wave neutron star merger")
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            pathways.forEach { (title, subtitle, searchVal) ->
                Card(
                    onClick = { onTopicClick(searchVal) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        if (suggestedKeywords.isNotEmpty()) {
            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "TRENDING RESEARCH TOPICS",
                style = MaterialTheme.typography.labelLarge.copy(
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestedKeywords.forEach { keyword ->
                    SuggestionChip(
                        onClick = { onKeywordClick(keyword) },
                        label = { Text(keyword) },
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.TrendingUp,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            enabled = true
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}

@Composable
fun EmptyResultsState(
    query: String,
    onClearQuery: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = CircleShape,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.SearchOff,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "No papers found",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "We couldn't find matches for \"$query\". Try loosening your filters or searching for general astronomical terms.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onClearQuery,
            shape = RoundedCornerShape(100.dp)
        ) {
            Text("Clear Search & Filters")
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Search Error",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        FilledTonalButton(onClick = onRetry) {
            Text("Try Again")
        }
    }
}

/**
 * Modern Google M3 Advanced Research Search Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterBottomSheet(
    filter: SearchFilter,
    onDismiss: () -> Unit,
    onApply: (SearchFilter) -> Unit,
    onClear: () -> Unit
) {
    var currentFilter by remember { mutableStateOf(filter) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Advanced Search",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = {
                    onClear()
                    onDismiss()
                }) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            }

            // Scrollable Filter Sections
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Section 1: Academic Rigor & Accessibility
                FilterCardContainer(title = "Academic Standards & Access") {
                    FilterSwitchRow(
                        title = "Refereed Journals Only",
                        subtitle = "Include strictly peer-reviewed papers",
                        checked = currentFilter.refereedOnly,
                        onCheckedChange = { currentFilter = currentFilter.copy(refereedOnly = it) },
                        icon = Icons.Outlined.Verified
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    FilterSwitchRow(
                        title = "Open Access Publications",
                        subtitle = "Free to read, open-distribution preprints",
                        checked = currentFilter.isOpenAccess,
                        onCheckedChange = { currentFilter = currentFilter.copy(isOpenAccess = it) },
                        icon = Icons.Outlined.LockOpen
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    FilterSwitchRow(
                        title = "Associated Data Included",
                        subtitle = "Publications with linked astronomical datasets",
                        checked = currentFilter.hasData,
                        onCheckedChange = { currentFilter = currentFilter.copy(hasData = it) },
                        icon = Icons.Outlined.Storage
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    FilterSwitchRow(
                        title = "Software & Code Linked",
                        subtitle = "Includes computational analysis or pipeline code",
                        checked = currentFilter.hasSoftware,
                        onCheckedChange = { currentFilter = currentFilter.copy(hasSoftware = it) },
                        icon = Icons.Outlined.Code
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 2: Temporal & Bibliographic
                FilterCardContainer(title = "Publication & Timeframe") {
                    Text(
                        text = "Year Range",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Year Range Assist Chips
                    val quickYears = listOf(
                        "All Time" to null,
                        "2024+" to (2024..2027),
                        "Past 5 Yrs" to (2020..2027),
                        "2010–2020" to (2010..2020)
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        quickYears.forEach { (label, range) ->
                            val isSelected = currentFilter.yearRange == range
                            FilterChip(
                                selected = isSelected,
                                onClick = { currentFilter = currentFilter.copy(yearRange = range) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilterTextField(
                            value = currentFilter.yearRange?.first?.toString() ?: "",
                            onValueChange = {
                                val v = it.toIntOrNull()
                                currentFilter = currentFilter.copy(
                                    yearRange = if (v != null) v..(currentFilter.yearRange?.last ?: 2027) else null
                                )
                            },
                            label = "From (Year)",
                            leadingIcon = Icons.Outlined.CalendarToday,
                            modifier = Modifier.weight(1f)
                        )
                        FilterTextField(
                            value = currentFilter.yearRange?.last?.toString() ?: "",
                            onValueChange = {
                                val v = it.toIntOrNull()
                                currentFilter = currentFilter.copy(
                                    yearRange = if (v != null) (currentFilter.yearRange?.first ?: 1900)..v else null
                                )
                            },
                            label = "To (Year)",
                            leadingIcon = Icons.Outlined.Event,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    FilterTextField(
                        value = currentFilter.bibstem ?: "",
                        onValueChange = { currentFilter = currentFilter.copy(bibstem = it) },
                        label = "Journal (Bibstem)",
                        placeholder = "e.g. ApJ, A&A, MNRAS, Nature",
                        leadingIcon = Icons.AutoMirrored.Outlined.MenuBook
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Popular journal shortcut chips
                    val journals = listOf("ApJ", "A&A", "MNRAS", "Nature", "Science")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        journals.forEach { j ->
                            AssistChip(
                                onClick = { currentFilter = currentFilter.copy(bibstem = j) },
                                label = { Text(j, style = MaterialTheme.typography.labelSmall) },
                                shape = RoundedCornerShape(6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilterTextField(
                            value = currentFilter.volume ?: "",
                            onValueChange = { currentFilter = currentFilter.copy(volume = it) },
                            label = "Volume",
                            modifier = Modifier.weight(1f)
                        )
                        FilterTextField(
                            value = currentFilter.page ?: "",
                            onValueChange = { currentFilter = currentFilter.copy(page = it) },
                            label = "Page",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 3: Author & Institutional Scope
                FilterCardContainer(title = "Author & Institution Scope") {
                    FilterTextField(
                        value = currentFilter.author ?: "",
                        onValueChange = { currentFilter = currentFilter.copy(author = it) },
                        label = "Author Name",
                        placeholder = "e.g. Hawking, S. or Mayor, Michel",
                        leadingIcon = Icons.Outlined.Person
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    FilterTextField(
                        value = currentFilter.firstAuthor ?: "",
                        onValueChange = { currentFilter = currentFilter.copy(firstAuthor = it) },
                        label = "First Author Only",
                        placeholder = "Lead primary researcher",
                        leadingIcon = Icons.Outlined.PersonOutline
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    FilterTextField(
                        value = currentFilter.affiliation ?: "",
                        onValueChange = { currentFilter = currentFilter.copy(affiliation = it) },
                        label = "Institution / Affiliation",
                        placeholder = "e.g. NASA, ESO, Harvard, Caltech",
                        leadingIcon = Icons.Outlined.AccountBalance
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    FilterTextField(
                        value = currentFilter.objectName ?: "",
                        onValueChange = { currentFilter = currentFilter.copy(objectName = it) },
                        label = "Astronomical Object (Simbad)",
                        placeholder = "e.g. Sagittarius A*, Trappist-1, M31",
                        leadingIcon = Icons.Outlined.Public
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 4: Identifiers & Deep Academic Parameters (Collapsible Accordion)
                var proExpanded by remember { mutableStateOf(false) }
                Card(
                    onClick = { proExpanded = !proExpanded },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Fingerprint,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Academic IDs & Pro Filters",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(
                                imageVector = if (proExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(visible = proExpanded) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    FilterTextField(
                                        value = currentFilter.arxivId ?: "",
                                        onValueChange = { currentFilter = currentFilter.copy(arxivId = it) },
                                        label = "arXiv ID",
                                        placeholder = "e.g. 2304.12345",
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterTextField(
                                        value = currentFilter.doi ?: "",
                                        onValueChange = { currentFilter = currentFilter.copy(doi = it) },
                                        label = "DOI",
                                        placeholder = "10.1088/...",
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                FilterTextField(
                                    value = currentFilter.orcid ?: "",
                                    onValueChange = { currentFilter = currentFilter.copy(orcid = it) },
                                    label = "Author ORCID",
                                    placeholder = "e.g. 0000-0002-1825-0097"
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                FilterTextField(
                                    value = currentFilter.arxivClass ?: "",
                                    onValueChange = { currentFilter = currentFilter.copy(arxivClass = it) },
                                    label = "arXiv Classification",
                                    placeholder = "astro-ph.CO, astro-ph.EP, gr-qc"
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                FilterTextField(
                                    value = currentFilter.database ?: "",
                                    onValueChange = { currentFilter = currentFilter.copy(database = it) },
                                    label = "Database",
                                    placeholder = "astronomy, physics, general"
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Sticky Bottom Execution Button
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            onApply(currentFilter)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (currentFilter.isActive()) "Apply Advanced Filters" else "Search Papers",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FilterCardContainer(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
fun FilterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = {
            if (placeholder.isNotEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        },
        leadingIcon = leadingIcon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            focusedBorderColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
fun FilterSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = if (checked)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = CircleShape,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (checked)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}
