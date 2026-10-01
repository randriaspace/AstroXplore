package com.example.astroxplore.features.library.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.astroxplore.core.ui.components.LottieLoadingView
import com.example.astroxplore.features.feed.ui.components.PaperCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onPaperClick: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val papers by viewModel.savedPapers.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "Personal Library", 
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    ) 
                },
                windowInsets = WindowInsets(0.dp),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.syncLibrary() },
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            if (isRefreshing && papers.isEmpty()) {
                LottieLoadingView(size = 150)
            } else if (papers.isEmpty()) {
                EmptyLibraryState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(papers, key = { it.bibcode }) { paper ->
                        PaperCard(
                            paper = paper,
                            isSaved = true,
                            onSaveClick = { viewModel.toggleSave(paper) },
                            onTitleClick = { onPaperClick(paper.bibcode) },
                            onReadMoreClick = { onPaperClick(paper.bibcode) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyLibraryState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.AutoStories,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Your academic shelf is empty",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Save papers from your feed or exploration to read them offline and sync them across devices.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
