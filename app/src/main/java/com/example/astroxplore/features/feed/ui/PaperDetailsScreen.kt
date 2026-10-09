package com.example.astroxplore.features.feed.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.astroxplore.core.ui.components.AstroDetailsSkeleton
import com.example.astroxplore.core.ui.components.AstroTabItem
import com.example.astroxplore.core.ui.components.AstroTabRow
import com.example.astroxplore.core.ui.components.PrimaryActionDock
import com.example.astroxplore.features.feed.model.PaperModel
import com.example.astroxplore.features.feed.ui.components.*
import com.example.astroxplore.features.groups.ui.components.GroupPickerSheet
import com.example.astroxplore.core.database.entity.DownloadState
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperDetailsScreen(
    bibcode: String,
    onNavigateBack: () -> Unit,
    onReadPdfClick: (bibcode: String, pdfFilePath: String, paperTitle: String) -> Unit = { _, _, _ -> },
    viewModel: PaperDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isSaved by viewModel.isSaved.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val localFilePath by viewModel.localFilePath.collectAsState()
    val userGroups by viewModel.userGroups.collectAsState()

    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showGroupPicker by remember { mutableStateOf(false) }
    var userInitiatedDownload by remember { mutableStateOf(false) }

    BackHandler {
        if (showGroupPicker) {
            showGroupPicker = false
        } else {
            onNavigateBack()
        }
    }

    LaunchedEffect(bibcode) {
        viewModel.loadPaper(bibcode)
    }

    // Automatically open the PDF reader once download completes
    LaunchedEffect(downloadState, localFilePath) {
        if (userInitiatedDownload && downloadState == DownloadState.DOWNLOADED && uiState is PaperDetailsUiState.Success) {
            val paper = (uiState as PaperDetailsUiState.Success).paper
            val targetFile = if (!localFilePath.isNullOrBlank()) {
                File(localFilePath!!)
            } else {
                File(context.filesDir, "pdfs/${paper.bibcode}.pdf")
            }
            if (targetFile.exists()) {
                userInitiatedDownload = false
                onReadPdfClick(paper.bibcode, targetFile.absolutePath, paper.title)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Publication",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                windowInsets = WindowInsets(0.dp),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("paper_details_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState is PaperDetailsUiState.Success) {
                        val paper = (uiState as PaperDetailsUiState.Success).paper
                        IconButton(
                            onClick = { viewModel.toggleSave(paper) },
                            modifier = Modifier.testTag("paper_details_bookmark_button")
                        ) {
                            Icon(
                                imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = if (isSaved) "Remove from library" else "Save to library",
                                tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Check out this paper: $bibcode\nhttps://ui.adsabs.harvard.edu/abs/$bibcode"
                                )
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, null))
                        },
                        modifier = Modifier.testTag("paper_details_share_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            if (uiState is PaperDetailsUiState.Success) {
                val paper = (uiState as PaperDetailsUiState.Success).paper
                val hasPdfUrl = !paper.pdfUrl.isNullOrBlank()

                PrimaryActionDock(
                    onReadPdfClick = {
                        val localFile = if (!localFilePath.isNullOrBlank()) {
                            File(localFilePath!!)
                        } else {
                            File(context.filesDir, "pdfs/${paper.bibcode}.pdf")
                        }

                        if (localFile.exists()) {
                            onReadPdfClick(paper.bibcode, localFile.absolutePath, paper.title)
                        } else if (hasPdfUrl) {
                            userInitiatedDownload = true
                            viewModel.downloadAndOpenPdf(context, paper, onReadPdfClick)
                        } else {
                            // Direct user to external publisher / NASA ADS link
                            val url = "https://ui.adsabs.harvard.edu/abs/${paper.bibcode}"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }
                    },
                    onSecondaryActionClick = { showGroupPicker = true },
                    downloadState = downloadState,
                    downloadProgress = downloadProgress,
                    hasPdfUrl = hasPdfUrl,
                    secondaryButtonText = "Journal Club",
                    secondaryButtonIcon = Icons.Outlined.Groups
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is PaperDetailsUiState.Loading -> {
                    AstroDetailsSkeleton()
                }
                is PaperDetailsUiState.Success -> {
                    PaperDetailsMainContent(
                        paper = state.paper,
                        onCopyBibTeX = { bibtex ->
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("BibTeX", bibtex)))
                                snackbarHostState.showSnackbar("BibTeX copied to clipboard")
                            }
                        },
                        onShareBibTeX = { bibtex ->
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "BibTeX: ${state.paper.title}")
                                putExtra(Intent.EXTRA_TEXT, bibtex)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Export BibTeX"))
                        },
                        onOpenAds = {
                            val url = "https://ui.adsabs.harvard.edu/abs/${state.paper.bibcode}"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    )
                }
                is PaperDetailsUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        if (showGroupPicker) {
            GroupPickerSheet(
                groups = userGroups,
                onDismiss = { showGroupPicker = false },
                onGroupSelected = { groupId ->
                    viewModel.addPaperToGroup(groupId, bibcode)
                    showGroupPicker = false
                    scope.launch {
                        snackbarHostState.showSnackbar("Added to Journal Club")
                    }
                }
            )
        }
    }
}

/**
 * Tabbed Paper Details reading container with smooth fluid tab transitions.
 */
@Composable
private fun PaperDetailsMainContent(
    paper: PaperModel,
    onCopyBibTeX: (String) -> Unit,
    onShareBibTeX: (String) -> Unit,
    onOpenAds: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val bibtexString = remember(paper) { paper.toBibTeX() }

    val tabs = remember(paper.citationCount) {
        listOf(
            AstroTabItem(title = "Overview", icon = Icons.Outlined.Description),
            AstroTabItem(title = "Metrics", icon = Icons.Outlined.Analytics, badgeCount = paper.citationCount),
            AstroTabItem(title = "BibTeX", icon = Icons.Outlined.FormatQuote)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Publication Card
        HeroPublicationHeaderCard(paper = paper)

        // Google-style Segmented Tab Pill Row
        AstroTabRow(
            tabs = tabs,
            selectedTabIndex = selectedTab,
            onTabSelected = { selectedTab = it }
        )

        // Animated Tab Body
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
            },
            label = "paper_details_tab_content"
        ) { tabIndex ->
            when (tabIndex) {
                0 -> OverviewTabContent(paper = paper)
                1 -> MetricsTabContent(paper = paper, onOpenAds = onOpenAds)
                2 -> BibTeXTabContent(
                    bibtex = bibtexString,
                    onCopyBibTeX = { onCopyBibTeX(bibtexString) },
                    onShareBibTeX = { onShareBibTeX(bibtexString) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}