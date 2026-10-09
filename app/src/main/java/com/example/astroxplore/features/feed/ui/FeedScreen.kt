/**
 * FeedScreen - Main UI screen for displaying the feed of papers in the AstroXplore app.
 *
 * This composable displays a list of papers using a LazyColumn, supports pull-to-refresh,
 * infinite scroll loading, offline state handling, and various interactive elements such as
 * paper cards, search, and group picker. It interacts with FeedViewModel via Hilt dependency injection
 * to obtain feed papers, UI state, and error handling.
 *
 * Key Features:
 * - Displays papers with PaperCard composable, includes save and click actions.
 * - Handles loading states, error states, and empty results.
 * - Supports infinite scroll via LazyColumn's scroll state.
 * - Provides top bar with branding, notifications, and profile access.
 * - Includes quick filter tabs for user interests.
 * - Integrates with GroupPickerSheet for grouping papers.
 *
 * @author AstroXplore Team
 * @since 1.0
 */
package com.example.astroxplore.features.feed.ui



import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.astroxplore.R
import com.example.astroxplore.features.feed.model.PaperModel
import com.example.astroxplore.core.ui.animation.expressiveBounce
import com.example.astroxplore.core.ui.components.AstroM3DockedLinearProgress
import com.example.astroxplore.core.ui.components.OfflineFallbackState
import com.example.astroxplore.features.feed.ui.components.PaperCard
import com.example.astroxplore.features.feed.ui.components.PaperCardSkeleton
import com.example.astroxplore.features.groups.ui.components.GroupPickerSheet
import com.example.astroxplore.features.profile.model.ProfileModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel(),
    onSearchClick: () -> Unit = {},
    onPaperClick: (String) -> Unit = {},
    onLibraryClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
) {
    val papers by viewModel.feedPapers.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val error by viewModel.error.collectAsState()
    val savedPaperIds by viewModel.savedPaperIds.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val userInterests by viewModel.userInterests.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val userGroups by viewModel.userGroups.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Infinite Scroll detection
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf false
            
            lastVisibleItem.index >= (listState.layoutInfo.totalItemsCount - 5)
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && !isRefreshing && !isLoadingMore) {
            viewModel.loadNextPage()
        }
    }
    
    var showGroupPicker by remember { mutableStateOf(false) }
    var selectedPaperForGroup by remember { mutableStateOf<PaperModel?>(null) }
    
    val filterTabs = remember(userInterests) {
        listOf("For You") + userInterests
    }

    LaunchedEffect(Unit) {
        listState.scrollToItem(0)
        viewModel.scrollToTopEvent.collectLatest {
            listState.animateScrollToItem(0)
        }
    }
    
    val firstItemIndex = remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val firstItemScrollOffset = remember { derivedStateOf { listState.firstVisibleItemScrollOffset } }
    
    val isHeaderVisible = remember { 
        derivedStateOf { firstItemIndex.value == 0 && firstItemScrollOffset.value < 100 } 
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent,
            topBar = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                    tonalElevation = 2.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AnimatedVisibility(
                            visible = isHeaderVisible.value,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            BrandingRow(
                                profile = userProfile,
                                onNotifClick = {},
                                onProfileClick = onProfileClick
                            )
                        }

                        QuickFilterTabs(
                            tabs = filterTabs,
                            selectedTab = selectedCategory,
                            onTabSelected = { viewModel.selectCategory(it) }
                        )

                        AstroM3DockedLinearProgress(visible = isRefreshing)
                    }
                }
            }
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item {
                        SearchBar(
                            onSearchClick = onSearchClick,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }

                    if (!isOnline && papers.isEmpty()) {
                        item { OfflineFeedState(onLibraryClick = onLibraryClick) }
                    } else if (isRefreshing && papers.isEmpty()) {
                        items(5) { PaperCardSkeleton() }
                    } else if (error != null && papers.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                Text(text = stringResource(error!!), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (!isRefreshing && papers.isEmpty()) {
                        item {
                            EmptyResultsState(category = selectedCategory)
                        }
                    } else {
                        items(papers, key = { it.bibcode }) { paper ->
                            PaperCard(
                                paper = paper,
                                isSaved = savedPaperIds.contains(paper.bibcode),
                                onSaveClick = { viewModel.toggleSavePaper(paper) },
                                onTitleClick = { onPaperClick(paper.bibcode) },
                                onReadMoreClick = { onPaperClick(paper.bibcode) },
                                onMoreClick = {
                                    selectedPaperForGroup = paper
                                    showGroupPicker = true
                                }
                            )
                        }
                        
                        if (isLoadingMore) {
                            items(3) { PaperCardSkeleton() }
                        } else if (papers.isNotEmpty()) {
                            item { EndOfFeedMessage() }
                        }
                    }
                }
            }
        }

        if (showGroupPicker && selectedPaperForGroup != null) {
            GroupPickerSheet(
                groups = userGroups,
                onDismiss = { showGroupPicker = false },
                onGroupSelected = { groupId ->
                    viewModel.addPaperToGroup(groupId, selectedPaperForGroup!!.bibcode)
                    showGroupPicker = false
                    scope.launch {
                        snackbarHostState.showSnackbar("Added to Journal Club")
                    }
                }
            )
        }
    }
}

@Composable
fun BrandingRow(
    profile: ProfileModel?,
    onNotifClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Discover",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNotifClick) {
                Icon(Icons.Outlined.Notifications, contentDescription = "Notifications")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Surface(
                modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onProfileClick),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                if (profile?.firstName != null) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = profile.firstName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile",
                        modifier = Modifier.padding(6.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickFilterTabs(
    tabs: List<String>,
    selectedTab: String,
    onTabSelected: (String) -> Unit
) {
    PrimaryScrollableTabRow(
        selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        edgePadding = 16.dp,
        divider = {}
    ) {
        tabs.forEach { tab ->
            val isSelected = tab == selectedTab
            Tab(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                text = {
                    Text(
                        text = tab,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
    }
}

@Composable
fun SearchBar(onSearchClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onSearchClick,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = CircleShape,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .expressiveBounce(onClick = onSearchClick),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Search 10M+ astrophysics papers...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun EndOfFeedMessage() {
    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "You're all caught up!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun OfflineFeedState(onLibraryClick: () -> Unit) {
    OfflineFallbackState(
        message = "Discovery requires an internet connection. Visit your Library to read saved papers.",
        onLibraryClick = onLibraryClick
    )
}

@Composable
fun EmptyResultsState(category: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No papers found for \"$category\"",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Try adjusting your interests or check back later for new publications.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}