package com.example.astroxplore.features.groups.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import android.content.ClipboardManager
import android.content.ClipData
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.astroxplore.R
import com.example.astroxplore.core.ui.components.LottieLoadingView
import com.example.astroxplore.core.util.QrCodeUtils
import com.example.astroxplore.features.feed.model.PaperModel
import com.example.astroxplore.features.feed.ui.components.PaperCard
import com.example.astroxplore.features.groups.model.*
import com.example.astroxplore.features.groups.ui.components.ConsensusVoting
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailsScreen(
    groupId: String,
    onNavigateBack: () -> Unit,
    onPaperClick: (String) -> Unit,
    viewModel: GroupDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val group by viewModel.currentGroup.collectAsState()
    val members by viewModel.members.collectAsState()
    val pendingRequests by viewModel.pendingRequests.collectAsState()
    val presentations by viewModel.presentations.collectAsState()
    val sessionReviews by viewModel.sessionReviews.collectAsState()
    val kpiStats by viewModel.kpiStats.collectAsState()
    val currentUserRole by viewModel.currentUserRole.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val currentUserId = viewModel.currentUserId
    val context = LocalContext.current
    
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Shelf", "Votes", "Sessions", "KPIs", "Admin")

    var showEditSheet by remember { mutableStateOf(false) }
    var showAddPaperSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var archivePassword by remember { mutableStateOf("") }
    var showScheduleSheet by remember { mutableStateOf(false) }
    var showReviewSheet by remember { mutableStateOf(false) }
    var selectedBibcodeForSchedule by remember { mutableStateOf<String?>(null) }
    var selectedTitleForSchedule by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val inviteToken by viewModel.inviteToken.collectAsState()

    BackHandler {
        when {
            showEditSheet -> showEditSheet = false
            showAddPaperSheet -> showAddPaperSheet = false
            showDeleteDialog -> showDeleteDialog = false
            showScheduleSheet -> showScheduleSheet = false
            showReviewSheet -> showReviewSheet = false
            inviteToken != null -> viewModel.dismissInvite()
            selectedTabIndex != 0 -> selectedTabIndex = 0
            else -> onNavigateBack()
        }
    }

    LaunchedEffect(groupId) {
        viewModel.loadGroupData(groupId)
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            group?.name ?: "Journal Club", 
                            style = MaterialTheme.typography.titleMedium, 
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            group?.focusArea ?: "Active Astrophysics Discussion", 
                            style = MaterialTheme.typography.labelSmall, 
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                windowInsets = WindowInsets(0.dp),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::createInvite) {
                        Icon(Icons.Default.QrCode, contentDescription = "Create club invite")
                    }
                    if (currentUserRole == "admin" || group?.ownerId == currentUserId || group?.ownerId?.startsWith("local_") == true) {
                        IconButton(onClick = { showEditSheet = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Club")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Meeting schedule sub-banner with Calendar Sync
            if (group != null) {
                val context = LocalContext.current
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clickable {
                                val intent = Intent(Intent.ACTION_INSERT).apply {
                                    data = android.provider.CalendarContract.Events.CONTENT_URI
                                    putExtra(android.provider.CalendarContract.Events.TITLE, "Journal Club: ${group!!.name}")
                                    putExtra(android.provider.CalendarContract.Events.DESCRIPTION, "Upcoming Journal Club session.\nSchedule: ${group!!.meetingSchedule}")
                                    putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION, group!!.meetingLocation)
                                    putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, System.currentTimeMillis() + 86400000L)
                                    putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, System.currentTimeMillis() + 86400000L + 7200000L)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = group!!.meetingSchedule,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = group!!.meetingLocation,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Sync",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.Transparent,
                divider = {},
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { 
                            Text(
                                title, 
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium
                            ) 
                        }
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> ShelfTab(
                    uiState = uiState,
                    onPaperClick = onPaperClick,
                    onAddPaperClick = { showAddPaperSheet = true },
                    onNominateClick = { bibcode, title ->
                        selectedBibcodeForSchedule = bibcode
                        selectedTitleForSchedule = title
                        showScheduleSheet = true
                    }
                )
                1 -> {
                    when (val state = uiState) {
                        is GroupDetailsUiState.Success -> {
                            ConsensusVoting(
                                papers = state.groupPapers,
                                onVote = { viewModel.voteForPaper(it) },
                                onUnvote = { viewModel.unvoteForPaper(it) },
                                onPaperClick = onPaperClick,
                                onNominateForTalk = { nominated ->
                                    selectedBibcodeForSchedule = nominated.bibcode
                                    selectedTitleForSchedule = nominated.title
                                    showScheduleSheet = true
                                }
                            )
                        }
                        is GroupDetailsUiState.Loading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                LottieLoadingView(size = 150, resId = R.raw.book_loader)
                            }
                        }
                        is GroupDetailsUiState.Error -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(state.message, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                2 -> {
                    SessionsTab(
                        presentations = presentations,
                        reviews = sessionReviews,
                        onScheduleClick = { 
                            if (uiState is GroupDetailsUiState.Success) {
                                val state = uiState as GroupDetailsUiState.Success
                                val first = state.groupPapers.firstOrNull()
                                selectedBibcodeForSchedule = first?.bibcode ?: "2024ApJ...001...01A"
                                selectedTitleForSchedule = first?.title ?: "Astrophysics Preprint"
                            } else {
                                selectedBibcodeForSchedule = "2024ApJ...001...01A"
                                selectedTitleForSchedule = "Astrophysics Preprint"
                            }
                            showScheduleSheet = true 
                        },
                        onAddReviewClick = { showReviewSheet = true },
                        onCheckIn = { presentationId -> viewModel.checkInSession(presentationId) },
                        onPaperClick = onPaperClick
                    )
                }
                3 -> KpiTab(
                    kpis = kpiStats,
                    isOnline = isOnline,
                    onRefresh = { viewModel.loadKpis(groupId) }
                )
                4 -> AdminTab(
                    group = group,
                    currentUserId = currentUserId,
                    currentUserRole = currentUserRole,
                    members = members,
                    pendingRequests = pendingRequests,
                    onApproveJoinRequest = { reqId, userId -> viewModel.approveJoinRequest(reqId, userId) },
                    onDeclineJoinRequest = { reqId -> viewModel.declineJoinRequest(reqId) },
                    onLeave = { 
                        viewModel.leaveGroup()
                        onNavigateBack()
                    },
                    onDelete = { showDeleteDialog = true },
                    onKick = { targetUserId -> viewModel.kickMember(targetUserId) },
                    onPromote = { targetUserId -> viewModel.updateMemberRole(targetUserId, "admin") },
                    onDemote = { targetUserId -> viewModel.updateMemberRole(targetUserId, "member") },
                    onEditClick = { showEditSheet = true },
                    onShareInvite = {
                        val sendIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Join our ${group?.name ?: "Astrophysics"} Journal Club on AstroXplore with code: ${group?.displayId}")
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, null)
                        context.startActivity(shareIntent)
                    }
                )
            }
        }
    }

    if (inviteToken != null && group != null) {
        GroupInviteDialog(
            inviteToken = inviteToken!!,
            name = group!!.name,
            displayId = group!!.displayId,
            onDismiss = viewModel::dismissInvite
        )
    }

    if (showAddPaperSheet) {
        AddPaperToShelfSheet(
            onDismiss = { showAddPaperSheet = false },
            onAdd = { bibcode, title, authors, year ->
                viewModel.addPaperToShelf(bibcode, title, authors, year)
                showAddPaperSheet = false
            }
        )
    }

    if (showEditSheet && group != null) {
        EditGroupSheet(
            group = group!!,
            onDismiss = { showEditSheet = false },
            onUpdate = { n, d, f, s, l -> 
                viewModel.updateGroup(n, d, f, s, l)
                showEditSheet = false
            }
        )
    }

    if (showScheduleSheet && selectedBibcodeForSchedule != null) {
        SchedulePresentationSheet(
            bibcode = selectedBibcodeForSchedule!!,
            paperTitle = selectedTitleForSchedule,
            onDismiss = { showScheduleSheet = false },
            onSchedule = { dateTime, loc ->
                viewModel.schedulePresentation(selectedBibcodeForSchedule!!, selectedTitleForSchedule, dateTime, loc)
                showScheduleSheet = false
            }
        )
    }

    if (showReviewSheet) {
        val currentPapers = (uiState as? GroupDetailsUiState.Success)?.groupPapers ?: emptyList()
        AddSessionReviewSheet(
            papers = currentPapers,
            onDismiss = { showReviewSheet = false },
            onSubmit = { bibcode, pTitle, notes, rating ->
                viewModel.addSessionReview(bibcode, pTitle, notes, rating)
                showReviewSheet = false
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { 
                archivePassword = ""
                showDeleteDialog = false 
            },
            title = { Text("Archive Journal Club?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("This archives the journal club and removes it from active member listings. Enter confirmation password.")
                    OutlinedTextField(
                        value = archivePassword,
                        onValueChange = { archivePassword = it },
                        label = { Text("Confirm password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (archivePassword.isNotBlank()) {
                            viewModel.archiveGroupWithPassword(archivePassword)
                            archivePassword = ""
                            showDeleteDialog = false
                            onNavigateBack()
                        }
                    },
                    enabled = archivePassword.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Archive Club")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    archivePassword = ""
                    showDeleteDialog = false
                }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ShelfTab(
    uiState: GroupDetailsUiState,
    onPaperClick: (String) -> Unit,
    onAddPaperClick: () -> Unit,
    onNominateClick: (String, String?) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (uiState) {
            is GroupDetailsUiState.Loading -> {
                LottieLoadingView(size = 150, resId = R.raw.book_loader)
            }
            is GroupDetailsUiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Club Paper Shelf (${uiState.groupPapers.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Curated research preprints for this journal club",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = onAddPaperClick,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Paper", fontSize = 12.sp)
                            }
                        }
                    }

                    if (uiState.groupPapers.isEmpty()) {
                        item {
                            EmptyShelfCard(onAddClick = onAddPaperClick)
                        }
                    } else {
                        items(uiState.groupPapers, key = { it.id ?: it.bibcode }) { paper ->
                            ShelfPaperCard(
                                paper = paper,
                                onReadClick = { onPaperClick(paper.bibcode) },
                                onNominateClick = { onNominateClick(paper.bibcode, paper.title) }
                            )
                        }
                    }
                }
            }
            is GroupDetailsUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(uiState.message, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun ShelfPaperCard(
    paper: GroupPaperModel,
    onReadClick: () -> Unit,
    onNominateClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = paper.bibcode,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "${paper.voteCount} Votes",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = paper.title ?: "Preprint ${paper.bibcode}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!paper.authors.isNullOrBlank() || !paper.year.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = listOfNotNull(paper.authors, paper.year).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onNominateClick) {
                    Icon(Icons.Outlined.Event, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nominate for Talk", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onReadClick,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Read Abstract", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun EmptyShelfCard(onAddClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            )
            Text(
                "No Papers on the Shelf Yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Add preprints to your club shelf so members can read, discuss, and vote on what to present next.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Paper to Shelf")
            }
        }
    }
}

@Composable
fun SessionsTab(
    presentations: List<PresentationModel>,
    reviews: List<SessionReviewModel>,
    onScheduleClick: () -> Unit,
    onAddReviewClick: () -> Unit,
    onCheckIn: (String) -> Unit,
    onPaperClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Scheduled Seminar Sessions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Upcoming presentations & virtual journal meetings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = onScheduleClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Schedule", fontSize = 12.sp)
                }
            }
        }

        if (presentations.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "No talks scheduled yet. Schedule the next paper discussion!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(presentations, key = { it.id ?: it.bibcode }) { presentation ->
                ModernPresentationCard(
                    presentation = presentation,
                    onCheckIn = { onCheckIn(presentation.id ?: "") },
                    onPaperClick = { onPaperClick(presentation.bibcode) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Session Reviews & Takeaways", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Meeting outcomes, ratings, and action points", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = onAddReviewClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.RateReview, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Notes", fontSize = 12.sp)
                }
            }
        }

        if (reviews.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "No review notes recorded yet. Add summary conclusions after your session.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(reviews, key = { it.id ?: it.notes }) { review ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = review.bibcode,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Row {
                                repeat(5) { starIndex ->
                                    Icon(
                                        imageVector = if (starIndex < review.rating) Icons.Default.Star else Icons.Outlined.Star,
                                        contentDescription = null,
                                        tint = if (starIndex < review.rating) Color(0xFFFFB300) else MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        if (!review.paperTitle.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = review.paperTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = review.notes,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "By ${review.reviewerName ?: "Club Member"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = review.createdAt ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModernPresentationCard(
    presentation: PresentationModel,
    onCheckIn: () -> Unit,
    onPaperClick: () -> Unit
) {
    var checkedIn by remember(presentation.isCheckedIn) { mutableStateOf(presentation.isCheckedIn) }

    val formattedDate = try {
        LocalDateTime.parse(presentation.scheduledAt).format(DateTimeFormatter.ofPattern("EEE, MMM dd • HH:mm 'UTC'"))
    } catch (_: Exception) {
        presentation.scheduledAt
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "${presentation.attendeeCount + if (checkedIn && !presentation.isCheckedIn) 1 else 0} Attending",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = presentation.paperTitle ?: "Paper: ${presentation.bibcode}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Presenter: ${presentation.presenterName ?: "Club Member"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = presentation.meetingLocation ?: "Virtual Seminar Room",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onPaperClick) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Read Preprint", fontSize = 12.sp)
                }

                FilledTonalButton(
                    onClick = {
                        checkedIn = !checkedIn
                        if (checkedIn) onCheckIn()
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (checkedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (checkedIn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (checkedIn) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (checkedIn) "I'm Attending" else "RSVP / Check In", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun KpiTab(
    kpis: GroupKpiModel?,
    isOnline: Boolean,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Club Velocity & Analytics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Participation telemetry and discussion milestones", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh KPIs")
            }
        }

        val metrics = kpis ?: GroupKpiModel(totalPapers = 3, totalVotes = 19, totalPresentations = 2, totalMembers = 4, totalReviews = 2)

        // Highlight Metric Banner
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${metrics.participationScore}%",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Participation Score", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text("High member consensus and attendance engagement across recent preprint reviews.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiMetricCard(
                title = "Papers Discussed",
                value = metrics.totalPapers.toString(),
                icon = Icons.AutoMirrored.Filled.MenuBook,
                modifier = Modifier.weight(1f)
            )
            KpiMetricCard(
                title = "Consensus Votes",
                value = metrics.totalVotes.toString(),
                icon = Icons.Default.ThumbUp,
                modifier = Modifier.weight(1f)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiMetricCard(
                title = "Talks Scheduled",
                value = metrics.totalPresentations.toString(),
                icon = Icons.Default.Event,
                modifier = Modifier.weight(1f)
            )
            KpiMetricCard(
                title = "Active Researchers",
                value = metrics.totalMembers.toString(),
                icon = Icons.Default.People,
                modifier = Modifier.weight(1f)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiMetricCard(
                title = "Meeting Streak",
                value = "${metrics.meetingStreak} Weeks",
                icon = Icons.Default.LocalFireDepartment,
                modifier = Modifier.weight(1f)
            )
            KpiMetricCard(
                title = "Review Notes",
                value = metrics.totalReviews.toString(),
                icon = Icons.Default.RateReview,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun KpiMetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun AdminTab(
    group: GroupModel?,
    currentUserId: String?,
    currentUserRole: String,
    members: List<GroupMemberModel>,
    pendingRequests: List<GroupJoinRequestModel> = emptyList(),
    onApproveJoinRequest: (String, String) -> Unit = { _, _ -> },
    onDeclineJoinRequest: (String) -> Unit = {},
    onLeave: () -> Unit,
    onDelete: () -> Unit,
    onKick: (String) -> Unit,
    onPromote: (String) -> Unit,
    onDemote: (String) -> Unit,
    onEditClick: () -> Unit,
    onShareInvite: () -> Unit
) {
    val isAdmin = currentUserRole == "admin" || group?.ownerId == currentUserId || group?.ownerId?.startsWith("local_") == true

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Club Administration & Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        // Invite & Share Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Club Invite Code", fontWeight = FontWeight.Bold)
                            Text("Share with colleagues to join this club", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledTonalButton(onClick = onShareInvite, shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = group?.displayId ?: "ASTRO-CLUB",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val context = LocalContext.current
                            TextButton(onClick = {
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Club Code", group?.displayId ?: ""))
                            }) {
                                Text("Copy Code", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        if (isAdmin && pendingRequests.isNotEmpty()) {
            item {
                Text("Pending Join Requests (${pendingRequests.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
            }

            items(pendingRequests) { request ->
                PendingRequestItem(
                    request = request,
                    onApprove = { onApproveJoinRequest(request.id ?: "", request.userId) },
                    onDecline = { onDeclineJoinRequest(request.id ?: "") }
                )
            }
        }

        if (isAdmin) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Edit Club Metadata", fontWeight = FontWeight.Bold)
                            Text("Update focus area, cadence, and seminar venue", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = onEditClick) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                    }
                }
            }
        }

        item {
            Text("Member Roster (${members.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        if (members.isEmpty()) {
            item {
                Text("No roster available.", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            items(members) { member ->
                MemberRosterItem(
                    member = member,
                    isCurrentMemberOwner = member.userId == group?.ownerId,
                    isCurrentUserAdmin = isAdmin,
                    isSelf = member.userId == currentUserId,
                    onKick = { onKick(member.userId) },
                    onPromote = { onPromote(member.userId) },
                    onDemote = { onDemote(member.userId) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            if (group?.ownerId == currentUserId || group?.ownerId?.startsWith("local_") == true) {
                Button(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Dissolve Journal Club")
                }
            } else {
                Button(
                    onClick = onLeave,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Leave Journal Club")
                }
            }
        }
    }
}

@Composable
fun PendingRequestItem(
    request: GroupJoinRequestModel,
    onApprove: () -> Unit,
    onDecline: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.HourglassTop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = request.userName ?: "Applicant Scholar",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Requested to join",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onApprove) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Accept", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDecline) {
                    Icon(Icons.Default.Cancel, contentDescription = "Decline", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun MemberRosterItem(
    member: GroupMemberModel,
    isCurrentMemberOwner: Boolean,
    isCurrentUserAdmin: Boolean,
    isSelf: Boolean,
    onKick: () -> Unit,
    onPromote: () -> Unit,
    onDemote: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.userName ?: if (isSelf) "You" else "Researcher",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = when {
                        isCurrentMemberOwner -> "Founder / Admin"
                        member.role == "admin" -> "Admin"
                        member.role == "moderator" -> "Moderator"
                        else -> "Member"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (isCurrentUserAdmin && !isSelf && !isCurrentMemberOwner) {
                if (member.role == "admin") {
                    TextButton(onClick = onDemote) { Text("Demote", style = MaterialTheme.typography.labelSmall) }
                } else {
                    TextButton(onClick = onPromote) { Text("Promote", style = MaterialTheme.typography.labelSmall) }
                }
                IconButton(onClick = onKick) {
                    Icon(Icons.Default.PersonRemove, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun GroupInviteDialog(
    inviteToken: String,
    name: String,
    displayId: String,
    onDismiss: () -> Unit
) {
    val qrBitmap = remember(inviteToken) { QrCodeUtils.generateQrCode(inviteToken) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Club invitation QR code",
                        modifier = Modifier.size(180.dp).clip(RoundedCornerShape(12.dp))
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Club Code: $displayId",
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                SelectionContainer {
                    Text(
                        text = inviteToken,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(ClipData.newPlainText("Club Code", displayId))
                    }) {
                        Text("Copy Code")
                    }
                    Button(onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Join our $name Journal Club with code: $displayId")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, null))
                    }) {
                        Text("Share")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditGroupSheet(
    group: GroupModel,
    onDismiss: () -> Unit,
    onUpdate: (String, String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(group.name) }
    var description by remember { mutableStateOf(group.description ?: "") }
    var focusArea by remember { mutableStateOf(group.focusArea ?: "") }
    var schedule by remember { mutableStateOf(group.meetingSchedule) }
    var location by remember { mutableStateOf(group.meetingLocation) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Edit Club Details", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Club Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = focusArea, onValueChange = { focusArea = it }, label = { Text("Focus Area") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = schedule, onValueChange = { schedule = it }, label = { Text("Meeting Schedule") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Meeting Venue or Link") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            
            Button(
                onClick = { onUpdate(name, description, focusArea, schedule, location) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Update Settings", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPaperToShelfSheet(
    onDismiss: () -> Unit,
    onAdd: (bibcode: String, title: String, authors: String, year: String) -> Unit
) {
    var bibcode by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var authors by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("2024") }

    val presetPapers = listOf(
        Triple("2024arXiv240105234G", "Discovery of an earth-sized exoplanet in the habitable zone of an M dwarf", "Gillon, M. et al."),
        Triple("2024Natur.626...89A", "High-energy gamma-ray flare from a supermassive black hole jet", "Aharonian, F. et al."),
        Triple("2024ApJ...961..102R", "NIRSpec observation of interstellar dust attenuation at cosmic dawn", "Robertson, B. et al.")
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Add Paper to Shelf", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text("Add an astrophysics preprint to the journal club reading queue.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text("Quick Presets:", style = MaterialTheme.typography.labelSmall)
            presetPapers.forEach { (b, t, a) ->
                SuggestionChip(
                    onClick = {
                        bibcode = b
                        title = t
                        authors = a
                        year = "2024"
                    },
                    label = { Text(t.take(38) + "...", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = bibcode,
                onValueChange = { bibcode = it },
                label = { Text("Bibcode or arXiv ID *") },
                placeholder = { Text("e.g. 2024ApJ...960...12W") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Paper Title *") },
                placeholder = { Text("e.g. Spectroscopic verification of galaxies at cosmic dawn") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = authors,
                    onValueChange = { authors = it },
                    label = { Text("Authors") },
                    placeholder = { Text("e.g. Smith, J. et al.") },
                    modifier = Modifier.weight(2f)
                )
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it },
                    label = { Text("Year") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = { onAdd(bibcode.trim(), title.trim(), authors.trim(), year.trim()) },
                enabled = bibcode.isNotBlank() && title.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Add to Club Shelf", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSessionReviewSheet(
    papers: List<GroupPaperModel>,
    onDismiss: () -> Unit,
    onSubmit: (bibcode: String, paperTitle: String?, notes: String, rating: Int) -> Unit
) {
    var selectedPaper by remember { mutableStateOf(papers.firstOrNull()) }
    var notes by remember { mutableStateOf("") }
    var rating by remember { mutableIntStateOf(5) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Record Session Review", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text("Summarize key findings, consensus conclusions, and rating.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (papers.isNotEmpty()) {
                Text("Paper Discussed:", style = MaterialTheme.typography.labelMedium)
                papers.take(4).forEach { paper ->
                    FilterChip(
                        selected = selectedPaper?.bibcode == paper.bibcode,
                        onClick = { selectedPaper = paper },
                        label = { Text((paper.title ?: paper.bibcode).take(36) + "...", fontSize = 12.sp) }
                    )
                }
            }

            Text("Discussion Rating (1-5 Stars):", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { star ->
                    IconButton(onClick = { rating = star }) {
                        Icon(
                            imageVector = if (star <= rating) Icons.Default.Star else Icons.Outlined.Star,
                            contentDescription = null,
                            tint = if (star <= rating) Color(0xFFFFB300) else MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Key Findings, Consensus & Notes *") },
                placeholder = { Text("What did the journal club conclude about the methodology, systematic errors, or astrophysical implications?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4
            )

            Button(
                onClick = { 
                    val bib = selectedPaper?.bibcode ?: "2024ApJ...001...01A"
                    val pTitle = selectedPaper?.title ?: "Seminar Paper"
                    onSubmit(bib, pTitle, notes, rating) 
                },
                enabled = notes.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Session Review", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulePresentationSheet(
    bibcode: String,
    paperTitle: String?,
    onDismiss: () -> Unit,
    onSchedule: (LocalDateTime, String) -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis() + 2 * 24 * 60 * 60 * 1000L
    )
    var location by remember { mutableStateOf("Google Meet: meet.google.com/ast-jnl-club") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Schedule Seminar Talk", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(
                text = paperTitle ?: "Paper: $bibcode",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            DatePicker(
                state = datePickerState,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Meeting Venue or Link") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val selectedDateMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                    val selectedDate = java.time.Instant.ofEpochMilli(selectedDateMillis)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                    val selectedDateTime = LocalDateTime.of(selectedDate, java.time.LocalTime.of(17, 0))
                    onSchedule(selectedDateTime, location.trim())
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Confirm Seminar Date", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
