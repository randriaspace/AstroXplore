package com.example.astroxplore.features.feed.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.astroxplore.core.database.entity.DownloadState
import com.example.astroxplore.core.ui.components.*
import com.example.astroxplore.features.feed.model.PaperModel
import com.example.astroxplore.features.feed.ui.components.AstroAbstractView
import com.example.astroxplore.features.feed.ui.components.AstroPaperTitleText
import com.example.astroxplore.features.groups.ui.components.GroupPickerSheet
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
                PrimaryActionDock(
                    onReadPdfClick = {
                        val localFile = if (!localFilePath.isNullOrBlank()) {
                            File(localFilePath!!)
                        } else {
                            File(context.filesDir, "pdfs/${paper.bibcode}.pdf")
                        }

                        if (localFile.exists()) {
                            onReadPdfClick(paper.bibcode, localFile.absolutePath, paper.title)
                        } else if (!paper.pdfUrl.isNullOrBlank()) {
                            viewModel.downloadAndOpenPdf(context, paper, onReadPdfClick)
                        } else {
                            val url = "https://ui.adsabs.harvard.edu/abs/${paper.bibcode}"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }
                    },
                    onSecondaryActionClick = { showGroupPicker = true },
                    downloadState = downloadState,
                    downloadProgress = downloadProgress,
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
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LottieLoadingView(size = 140)
                    }
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
 * Tabbed Paper Details reading container.
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

@Composable
private fun HeroPublicationHeaderCard(paper: PaperModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hero_publication_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header: Date & Category & arXiv Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = paper.dateDisplay,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!paper.arxivId.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "arXiv:${paper.arxivId}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (paper.category.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = paper.category.uppercase(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Title with LaTeX support
            AstroPaperTitleText(
                title = paper.title,
                maxLines = Int.MAX_VALUE,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    lineHeight = 28.sp
                )
            )

            // Author collaboration list with monogram chips
            if (paper.authors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                AuthorChipsFlow(
                    authors = paper.authors,
                    initialVisibleCount = 3
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewTabContent(paper: PaperModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Quick Metrics Badges
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetadataMetricBadge(
                label = "Citations",
                value = "${paper.citationCount}",
                icon = Icons.Outlined.FormatQuote
            )
            paper.arxivId?.let { arxiv ->
                MetadataMetricBadge(
                    label = "arXiv ID",
                    value = arxiv,
                    icon = Icons.Outlined.Tag
                )
            }
            if (paper.bibcode.isNotBlank()) {
                MetadataMetricBadge(
                    label = "Bibcode",
                    value = paper.bibcode.take(12),
                    icon = Icons.Outlined.Bookmark
                )
            }
        }

        // Abstract Section
        CollapsibleSection(
            title = "Abstract",
            icon = Icons.Outlined.MenuBook,
            initiallyExpanded = true
        ) {
            AstroAbstractView(
                rawAbstract = paper.abstractText,
                isExpanded = true
            )
        }
    }
}

@Composable
private fun MetricsTabContent(
    paper: PaperModel,
    onOpenAds: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Impact Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "CITATION IMPACT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${paper.citationCount}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "tracked citations",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
        }

        // Publication Metadata Section
        CollapsibleSection(
            title = "Publication Metadata",
            icon = Icons.Outlined.Info,
            initiallyExpanded = true
        ) {
            PublicationDetailRow(label = "Published Date", value = paper.dateDisplay)
            PublicationDetailRow(label = "Bibcode", value = paper.bibcode)
            paper.arxivId?.let { arxiv ->
                PublicationDetailRow(label = "arXiv Identifier", value = arxiv)
            }
            if (paper.category.isNotBlank()) {
                PublicationDetailRow(label = "Primary Category", value = paper.category)
            }
            paper.rawPubDate?.let { pubDate ->
                PublicationDetailRow(label = "Raw Release Stamp", value = pubDate)
            }
        }

        // External ADS Portal Link Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "NASA ADS Abstract Service",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "View peer reviews and citation tree on ADS",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalIconButton(onClick = onOpenAds) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Launch,
                        contentDescription = "Open NASA ADS"
                    )
                }
            }
        }
    }
}

@Composable
private fun BibTeXTabContent(
    bibtex: String,
    onCopyBibTeX: () -> Unit,
    onShareBibTeX: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BibTeXCodeBlock(
            bibtexCode = bibtex,
            onCopyClick = onCopyBibTeX,
            onShareClick = onShareBibTeX
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.FormatQuote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Standard BibTeX record generated for use with Overleaf, LaTeX, Zotero, or Mendeley.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun PaperModel.toBibTeX(): String = buildString {
    append("@ARTICLE{").append(bibcode).append(",\n")
    if (authors.isNotEmpty()) {
        append("  author = {").append(authors.joinToString(" and ")).append("},\n")
    }
    append("  title = {").append(title).append("},\n")
    rawPubDate?.take(4)?.toIntOrNull()?.let { year ->
        append("  year = {").append(year).append("},\n")
    }
    append("  bibcode = {").append(bibcode).append("}\n}")
}