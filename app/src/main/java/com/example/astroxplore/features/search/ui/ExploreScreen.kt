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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
                    Text(
                        "Explore Universe", 
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    ) 
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
                        IconButton(onClick = { showFilterSheet = true }) {
                            Icon(
                                Icons.Outlined.Tune, 
                                contentDescription = "Advanced Filters",
                                tint = if (filter.isActive()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Premium Search Field
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = MaterialTheme.shapes.extraLarge,
                border = BorderStroke(
                    1.dp, 
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                )
            ) {
                TextField(
                    value = query,
                    onValueChange = { viewModel.onQueryChange(it) },
                    placeholder = { 
                        Text(
                            "Title, author, bibcode...", 
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        ) 
                    },
                    leadingIcon = { 
                        Icon(
                            Icons.Default.Search, 
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        ) 
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(20.dp))
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

            // Results Content
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = uiState,
                    transitionSpec = {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                    },
                    label = "search_results"
                ) { state ->
                    if (!isOnline && (state is ExploreUiState.Idle || state is ExploreUiState.Error || (state is ExploreUiState.Success && state.results.isEmpty()))) {
                        OfflineFallbackState(
                            message = "Discovery and search require an internet connection. Visit your Library to read saved papers.",
                            onLibraryClick = onLibraryClick
                        )
                    } else {
                        when (state) {
                            ExploreUiState.Idle -> SearchIdleState(
                                suggestedKeywords = suggestedKeywords,
                                onKeywordClick = { viewModel.onQueryChange(it) }
                            )
                            ExploreUiState.Loading -> {
                                LottieLoadingView(size = 150)
                            }
                            is ExploreUiState.Success -> {
                                if (state.results.isEmpty()) {
                                    EmptyResultsState()
                                } else {
                                Column {
                                    // Merged Sorting & Count Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // Count
                                        Text(
                                            text = "${state.results.size} Results",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                            fontWeight = FontWeight.Bold
                                        )

                                        // Sorting Chips
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            SortBy.entries.forEach { sortBy ->
                                                val isSelected = filter.sortBy == sortBy
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = { viewModel.setSortBy(sortBy) },
                                                    label = { 
                                                        Text(
                                                            text = sortBy.displayName,
                                                            style = MaterialTheme.typography.labelMedium
                                                        ) 
                                                    },
                                                    shape = MaterialTheme.shapes.medium,
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                    ),
                                                    border = FilterChipDefaults.filterChipBorder(
                                                        borderColor = Color.Transparent,
                                                        selectedBorderColor = Color.Transparent,
                                                        enabled = true,
                                                        selected = isSelected
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(bottom = 24.dp)
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
                        is ExploreUiState.Error -> ErrorState(stringResource(state.messageResId))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchIdleState(
    suggestedKeywords: List<String>,
    onKeywordClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
            shape = CircleShape,
            modifier = Modifier.size(120.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                LottieLoadingView(
                    size = 100,
                    resId = R.raw.book_loader
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            "Ready to explore the Cosmos?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "Search through 10M+ curated astrophysics papers using full academic parameters.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        
        if (suggestedKeywords.isNotEmpty()) {
            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                "TRENDING TOPICS",
                style = MaterialTheme.typography.labelLarge.copy(
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Black
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                suggestedKeywords.forEach { keyword ->
                    SuggestionChip(
                        onClick = { onKeywordClick(keyword) },
                        label = { Text(keyword) },
                        shape = MaterialTheme.shapes.medium
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyResultsState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Outlined.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No papers found",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Try adjusting your filters or query",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun ErrorState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = message, color = MaterialTheme.colorScheme.error)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Advanced Research", 
                    style = MaterialTheme.typography.headlineSmall, 
                    fontWeight = FontWeight.ExtraBold
                )
                TextButton(onClick = { 
                    onClear()
                    onDismiss()
                }) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            }

            // 1. Academic Standards
            FilterSectionTitle("Academic Standards")
            FilterSwitchRow(
                title = "Refereed Only",
                subtitle = "Include only peer-reviewed journals",
                checked = currentFilter.refereedOnly,
                onCheckedChange = { currentFilter = currentFilter.copy(refereedOnly = it) },
                icon = Icons.Outlined.Verified
            )
            FilterSwitchRow(
                title = "Open Access",
                subtitle = "Include only free to read publications",
                checked = currentFilter.isOpenAccess,
                onCheckedChange = { currentFilter = currentFilter.copy(isOpenAccess = it) },
                icon = Icons.Outlined.LockOpen
            )
            FilterSwitchRow(
                title = "Has Data",
                subtitle = "Papers with associated datasets",
                checked = currentFilter.hasData,
                onCheckedChange = { currentFilter = currentFilter.copy(hasData = it) },
                icon = Icons.Outlined.Storage
            )
            FilterSwitchRow(
                title = "Has Software",
                subtitle = "Publications with associated code",
                checked = currentFilter.hasSoftware,
                onCheckedChange = { currentFilter = currentFilter.copy(hasSoftware = it) },
                icon = Icons.Outlined.Code
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Temporal & Bibliographic
            FilterSectionTitle("Temporal & Bibliographic")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FilterTextField(
                    value = currentFilter.yearRange?.first?.toString() ?: "",
                    onValueChange = { val v = it.toIntOrNull(); currentFilter = currentFilter.copy(yearRange = if(v != null) v..(currentFilter.yearRange?.last ?: 2027) else null) },
                    label = "From (Year)",
                    modifier = Modifier.weight(1f)
                )
                FilterTextField(
                    value = currentFilter.yearRange?.last?.toString() ?: "",
                    onValueChange = { val v = it.toIntOrNull(); currentFilter = currentFilter.copy(yearRange = if(v != null) (currentFilter.yearRange?.first ?: 1900)..v else null) },
                    label = "To (Year)",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            FilterTextField(
                value = currentFilter.bibstem ?: "",
                onValueChange = { currentFilter = currentFilter.copy(bibstem = it) },
                label = "Journal/Publication (Bibstem)",
                placeholder = "e.g. ApJ, A&A, Nature"
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
            Spacer(modifier = Modifier.height(12.dp))
            FilterTextField(
                value = currentFilter.bibcodePrefix ?: "",
                onValueChange = { currentFilter = currentFilter.copy(bibcodePrefix = it) },
                label = "Bibcode Prefix",
                placeholder = "e.g. 2026ApJ"
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Research Specifics
            FilterSectionTitle("Research Specifics")
            FilterTextField(
                value = currentFilter.fullText ?: "",
                onValueChange = { currentFilter = currentFilter.copy(fullText = it) },
                label = "Full Text Search",
                placeholder = "Search keywords in paper body"
            )
            Spacer(modifier = Modifier.height(12.dp))
            FilterTextField(
                value = currentFilter.author ?: "",
                onValueChange = { currentFilter = currentFilter.copy(author = it) },
                label = "Full Author Name",
                placeholder = "e.g. Hawking, Stephen"
            )
            Spacer(modifier = Modifier.height(12.dp))
            FilterTextField(
                value = currentFilter.firstAuthor ?: "",
                onValueChange = { currentFilter = currentFilter.copy(firstAuthor = it) },
                label = "First Author Only",
                placeholder = "Primary researcher"
            )
            Spacer(modifier = Modifier.height(12.dp))
            FilterTextField(
                value = currentFilter.affiliation ?: "",
                onValueChange = { currentFilter = currentFilter.copy(affiliation = it) },
                label = "Institution / Affiliation",
                placeholder = "e.g. NASA, CERN, MIT"
            )
            Spacer(modifier = Modifier.height(12.dp))
            FilterTextField(
                value = currentFilter.objectName ?: "",
                onValueChange = { currentFilter = currentFilter.copy(objectName = it) },
                label = "Astronomical Object (Simbad)",
                placeholder = "e.g. M31, Sgr A*"
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 4. Academic Identifiers
            FilterSectionTitle("Academic Identifiers")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FilterTextField(
                    value = currentFilter.arxivId ?: "",
                    onValueChange = { currentFilter = currentFilter.copy(arxivId = it) },
                    label = "arXiv ID",
                    modifier = Modifier.weight(1f)
                )
                FilterTextField(
                    value = currentFilter.doi ?: "",
                    onValueChange = { currentFilter = currentFilter.copy(doi = it) },
                    label = "DOI",
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

            Spacer(modifier = Modifier.height(24.dp))

            // 5. Elite Pro Parameters
            var proExpanded by remember { mutableStateOf(false) }
            Surface(
                onClick = { proExpanded = !proExpanded },
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                shape = MaterialTheme.shapes.large
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoGraph, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Elite Pro Parameters", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Icon(if(proExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }

            if (proExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                FilterTextField(
                    value = currentFilter.arxivClass ?: "",
                    onValueChange = { currentFilter = currentFilter.copy(arxivClass = it) },
                    label = "arXiv Class",
                    placeholder = "e.g. astro-ph.CO, gr-qc"
                )
                Spacer(modifier = Modifier.height(12.dp))
                FilterTextField(
                    value = currentFilter.bibGroup ?: "",
                    onValueChange = { currentFilter = currentFilter.copy(bibGroup = it) },
                    label = "Bib Group",
                    placeholder = "e.g. ARI, CfA"
                )
                Spacer(modifier = Modifier.height(12.dp))
                FilterTextField(
                    value = currentFilter.database ?: "",
                    onValueChange = { currentFilter = currentFilter.copy(database = it) },
                    label = "Database",
                    placeholder = "astronomy, physics, general"
                )
                Spacer(modifier = Modifier.height(12.dp))
                FilterTextField(
                    value = currentFilter.docType ?: "",
                    onValueChange = { currentFilter = currentFilter.copy(docType = it) },
                    label = "Document Type",
                    placeholder = "article, book, newsletter"
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = {
                    onApply(currentFilter)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = MaterialTheme.shapes.extraLarge,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text("Execute Advanced Search", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun FilterSectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge.copy(
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Black
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.padding(vertical = 12.dp)
    )
}

@Composable
fun FilterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = ""
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))) },
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = OutlinedTextFieldDefaults.colors(
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
    Surface(
        onClick = { onCheckedChange(!checked) },
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon, 
                    contentDescription = null, 
                    tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
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
}
