package com.example.astroxplore.features.groups.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val sessionReviews by viewModel.sessionReviews.collectAsState()
    val kpiStats by viewModel.kpiStats.collectAsState()
    val currentUserRole by viewModel.currentUserRole.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val currentUserId = viewModel.currentUserId
    
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Shelf", "Votes", "Sessions", "KPIs", "Admin")

    var showEditSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var showScheduleSheet by remember { mutableStateOf(false) }
    var showReviewSheet by remember { mutableStateOf(false) }
    var selectedBibcodeForSchedule by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(groupId) {
        viewModel.loadGroupData(groupId)
    }

    if (group == null && uiState !is GroupDetailsUiState.Loading) {
        LaunchedEffect(Unit) { onNavigateBack() }
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
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            group?.focusArea ?: "Active Discussion", 
                            style = MaterialTheme.typography.labelSmall, 
                            color = MaterialTheme.colorScheme.primary
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
                    IconButton(onClick = { showQrDialog = true }) {
                        Icon(Icons.Default.QrCode, contentDescription = "QR Code")
                    }
                    if (currentUserRole == "admin" || group?.ownerId == currentUserId) {
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
                0 -> ShelfTab(uiState, onPaperClick)
                1 -> {
                    if (uiState is GroupDetailsUiState.Success) {
                        val state = uiState as GroupDetailsUiState.Success
                        ConsensusVoting(
                            papers = state.groupPapers,
                            onVote = { viewModel.voteForPaper(it) },
                            onUnvote = { viewModel.unvoteForPaper(it) }
                        )
                    }
                }
                2 -> {
                    if (uiState is GroupDetailsUiState.Success) {
                        val state = uiState as GroupDetailsUiState.Success
                        SessionsTab(
                            presentations = state.presentations,
                            reviews = sessionReviews,
                            onScheduleClick = { 
                                if (state.papers.isNotEmpty()) {
                                    selectedBibcodeForSchedule = state.papers.first().bibcode
                                    showScheduleSheet = true 
                                }
                            },
                            onAddReviewClick = {
                                if (state.papers.isNotEmpty()) {
                                    showReviewSheet = true
                                }
                            },
                            onCheckIn = { presentationId ->
                                viewModel.checkInSession(presentationId)
                            }
                        )
                    }
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
                    onLeave = { viewModel.leaveGroup() },
                    onDelete = { showDeleteDialog = true },
                    onKick = { targetUserId -> viewModel.kickMember(targetUserId) },
                    onPromote = { targetUserId -> viewModel.updateMemberRole(targetUserId, "admin") },
                    onDemote = { targetUserId -> viewModel.updateMemberRole(targetUserId, "member") },
                    onEditClick = { showEditSheet = true }
                )
            }
        }
    }

    if (showQrDialog && group != null) {
        QrCodeDialog(
            displayId = group!!.displayId,
            name = group!!.name,
            onDismiss = { showQrDialog = false }
        )
    }

    if (showEditSheet && group != null) {
        EditGroupSheet(
            group = group!!,
            onDismiss = { showEditSheet = false },
            onUpdate = { n, d, f -> 
                viewModel.updateGroup(n, d, f)
                showEditSheet = false
            }
        )
    }

    if (showScheduleSheet && selectedBibcodeForSchedule != null) {
        SchedulePresentationSheet(
            bibcode = selectedBibcodeForSchedule!!,
            onDismiss = { showScheduleSheet = false },
            onSchedule = { dateTime ->
                viewModel.schedulePresentation(selectedBibcodeForSchedule!!, dateTime)
                showScheduleSheet = false
            }
        )
    }

    if (showReviewSheet && uiState is GroupDetailsUiState.Success) {
        val state = uiState as GroupDetailsUiState.Success
        AddSessionReviewSheet(
            papers = state.papers,
            onDismiss = { showReviewSheet = false },
            onSubmit = { bibcode, notes, rating ->
                viewModel.addSessionReview(bibcode, notes, rating)
                showReviewSheet = false
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Dissolve Journal Club?") },
            text = { Text("This action cannot be undone. All shared papers and discussions will be permanently deleted.") },
            confirmButton = {
                Button(
                    onClick = { 
                        viewModel.deleteGroup() 
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ShelfTab(uiState: GroupDetailsUiState, onPaperClick: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (uiState) {
            is GroupDetailsUiState.Loading -> {
                LottieLoadingView(
                    size = 150,
                    resId = R.raw.book_loader
                )
            }
            is GroupDetailsUiState.Success -> {
                if (uiState.papers.isEmpty()) {
                    EmptyShelfState()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.papers, key = { it.bibcode }) { paper ->
                            PaperCard(
                                paper = paper,
                                onTitleClick = { onPaperClick(paper.bibcode) },
                                onReadMoreClick = { onPaperClick(paper.bibcode) }
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
fun SessionsTab(
    presentations: List<PresentationModel>,
    reviews: List<SessionReviewModel>,
    onScheduleClick: () -> Unit,
    onAddReviewClick: () -> Unit,
    onCheckIn: (String) -> Unit
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
                Text("Scheduled Presentations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Button(onClick = onScheduleClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Schedule")
                }
            }
        }

        if (presentations.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Text(
                        "No talks scheduled yet. Schedule one for your next session!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(presentations) { presentation ->
                PresentationCard(presentation = presentation, onCheckIn = { onCheckIn(presentation.id ?: "") })
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Session Reviews & Notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onAddReviewClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    Icon(Icons.Default.RateReview, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Notes")
                }
            }
        }

        if (reviews.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Text(
                        "No session review notes yet. Add conclusions and notes after your discussions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(reviews) { review ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
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
                            Text(
                                text = "★".repeat(review.rating),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = review.notes,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Reviewed by ${review.reviewerId.take(8)}...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PresentationCard(presentation: PresentationModel, onCheckIn: () -> Unit) {
    var checkedIn by remember { mutableStateOf(false) }

    val date = try {
        LocalDateTime.parse(presentation.scheduledAt).format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm"))
    } catch (e: Exception) {
        presentation.scheduledAt
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(text = presentation.bibcode, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(text = "Presenter: ${presentation.presenterId.take(8)}...", style = MaterialTheme.typography.bodySmall)
            }
            FilledTonalButton(
                onClick = {
                    checkedIn = true
                    onCheckIn()
                },
                enabled = !checkedIn,
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(if (checkedIn) "Present" else "Check In")
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
            Text(
                "Group Analytics & KPIs",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh KPIs")
            }
        }

        if (!isOnline) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Live KPI metrics require an internet connection. Showing cached summary.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        val metrics = kpis ?: GroupKpiModel()

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiMetricCard(
                title = "Papers Discussed",
                value = metrics.totalPapers.toString(),
                icon = Icons.Default.MenuBook,
                modifier = Modifier.weight(1f)
            )
            KpiMetricCard(
                title = "Total Votes",
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
                title = "Active Members",
                value = metrics.totalMembers.toString(),
                icon = Icons.Default.People,
                modifier = Modifier.weight(1f)
            )
        }

        KpiMetricCard(
            title = "Session Notes & Reviews",
            value = metrics.totalReviews.toString(),
            icon = Icons.Default.RateReview,
            modifier = Modifier.fillMaxWidth()
        )
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
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
    onLeave: () -> Unit,
    onDelete: () -> Unit,
    onKick: (String) -> Unit,
    onPromote: (String) -> Unit,
    onDemote: (String) -> Unit,
    onEditClick: () -> Unit
) {
    val isAdmin = currentUserRole == "admin" || group?.ownerId == currentUserId

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Club Administration", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        if (isAdmin) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Edit Club Details", fontWeight = FontWeight.Bold)
                            Text("Update name, description, and focus area", style = MaterialTheme.typography.bodySmall)
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
            Spacer(modifier = Modifier.height(24.dp))
            if (group?.ownerId == currentUserId) {
                Button(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shape = MaterialTheme.shapes.medium
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
                    shape = MaterialTheme.shapes.medium
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
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
                    text = if (isSelf) "You (${member.userId.take(8)})" else "Researcher ${member.userId.take(8)}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = when {
                        isCurrentMemberOwner -> "Owner / Admin"
                        member.role == "admin" -> "Admin"
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
fun QrCodeDialog(
    displayId: String,
    name: String,
    onDismiss: () -> Unit
) {
    val qrBitmap = remember(displayId) { QrCodeUtils.generateQrCode(displayId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Club QR Code",
                        modifier = Modifier.size(200.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Club ID: $displayId", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text("Share this ID or QR code with colleagues to invite them.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
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
    onUpdate: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(group.name) }
    var description by remember { mutableStateOf(group.description ?: "") }
    var focusArea by remember { mutableStateOf(group.focusArea ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()).navigationBarsPadding()
        ) {
            Text("Edit Club Details", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Club Name") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = focusArea, onValueChange = { focusArea = it }, label = { Text("Focus Area") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { onUpdate(name, description, focusArea) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Update Settings")
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSessionReviewSheet(
    papers: List<PaperModel>,
    onDismiss: () -> Unit,
    onSubmit: (String, String, Int) -> Unit
) {
    var selectedBibcode by remember { mutableStateOf(papers.firstOrNull()?.bibcode ?: "") }
    var notes by remember { mutableStateOf("") }
    var rating by remember { mutableIntStateOf(5) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()).navigationBarsPadding()
        ) {
            Text("Record Session Review", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(16.dp))

            Text("Select Paper", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(8.dp))
            papers.take(5).forEach { paper ->
                FilterChip(
                    selected = selectedBibcode == paper.bibcode,
                    onClick = { selectedBibcode = paper.bibcode },
                    label = { Text(paper.title.take(30) + "...") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Discussion Rating (1-5 Stars)", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { star ->
                    IconButton(onClick = { rating = star }) {
                        Icon(
                            if (star <= rating) Icons.Default.Star else Icons.Outlined.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Key Findings & Discussion Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4
            )

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { onSubmit(selectedBibcode, notes, rating) },
                enabled = notes.isNotBlank() && selectedBibcode.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Save Session Notes")
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulePresentationSheet(
    bibcode: String,
    onDismiss: () -> Unit,
    onSchedule: (LocalDateTime) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding()
        ) {
            Text("Schedule Presentation", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(8.dp))
            Text("For paper: $bibcode", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = { onSchedule(LocalDateTime.now().plusDays(1)) },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Schedule for Tomorrow")
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun EmptyShelfState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No papers on the shelf yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Add papers from the Feed or Explore screens using the Journal Club picker.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
