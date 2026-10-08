package com.example.astroxplore.features.feed.ui

import android.content.Intent
import android.content.ClipData
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.astroxplore.features.feed.model.PaperModel
import com.example.astroxplore.features.feed.ui.components.AstroAbstractView
import com.example.astroxplore.features.feed.ui.components.AstroPaperTitleText
import com.example.astroxplore.features.groups.model.GroupModel
import com.example.astroxplore.features.groups.ui.components.GroupPickerSheet
import com.example.astroxplore.core.ui.components.LottieLoadingView
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperDetailsScreen(
    bibcode: String,
    onNavigateBack: () -> Unit,
    viewModel: PaperDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isSaved by viewModel.isSaved.collectAsState()
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
                title = { Text("Publication", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                windowInsets = WindowInsets(0.dp),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState is PaperDetailsUiState.Success) {
                        IconButton(onClick = {
                            val paper = (uiState as PaperDetailsUiState.Success).paper
                            viewModel.toggleSave(paper)
                        }) {
                            Icon(
                                imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = if (isSaved) "Remove from library" else "Save to library",
                                tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    IconButton(onClick = {
                        val sendIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Check out this paper: ${bibcode}\nhttps://ui.adsabs.harvard.edu/abs/${bibcode}")
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, null)
                        context.startActivity(shareIntent)
                    }) {
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
                Surface(
                    tonalElevation = 0.dp,
                    color = MaterialTheme.colorScheme.background
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { 
                                val url = paper.pdfUrl ?: "https://ui.adsabs.harvard.edu/abs/${paper.bibcode}"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Launch, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Read PDF", fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "BibTeX: ${paper.title}")
                                    putExtra(Intent.EXTRA_TEXT, paper.toBibTeX())
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Export BibTeX"))
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Outlined.FormatQuote, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Export BibTeX", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (val state = uiState) {
                is PaperDetailsUiState.Loading -> {
                    LottieLoadingView(size = 150)
                }
                is PaperDetailsUiState.Success -> {
                    PaperDetailsContent(
                        paper = state.paper,
                        onCiteClick = {
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("BibTeX", state.paper.toBibTeX())))
                                snackbarHostState.showSnackbar("BibTeX copied to clipboard")
                            }
                        },
                        onAddToGroupClick = {
                            showGroupPicker = true
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaperDetailsContent(
    paper: PaperModel,
    onCiteClick: () -> Unit,
    onAddToGroupClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = paper.category.uppercase(),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = paper.dateDisplay,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        AstroPaperTitleText(
            title = paper.title,
            maxLines = Int.MAX_VALUE,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                lineHeight = 32.sp
            )
        )

        if (paper.authors.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = paper.authors.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetadataBadge(
                label = "CITATIONS",
                value = paper.citationCount.toString()
            )
            paper.arxivId?.let { arxivId ->
                MetadataBadge(
                    label = "ARXIV ID",
                    value = arxivId
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCiteClick,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy BibTeX", maxLines = 1)
            }
            FilledTonalButton(
                onClick = onAddToGroupClick,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Outlined.Groups, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add to group", maxLines = 1)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = "ABSTRACT",
            modifier = Modifier.padding(top = 18.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        AstroAbstractView(
            rawAbstract = paper.abstractText,
            isExpanded = true
        )

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = "PUBLICATION DETAILS",
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        MetadataRow("Published", paper.dateDisplay)
        MetadataRow("Bibcode", paper.bibcode)
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun MetadataBadge(
    label: String,
    value: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            modifier = Modifier.weight(1.4f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End
        )
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