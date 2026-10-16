package com.example.astroxplore.features.groups.ui

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.astroxplore.core.ui.components.AstroLoadingSize
import com.example.astroxplore.core.ui.components.AstroM3LoadingIndicator
import com.example.astroxplore.features.groups.model.GroupModel
import com.example.astroxplore.features.groups.ui.components.AstroClubTheme
import com.example.astroxplore.features.groups.ui.components.AstroClubThemes
import com.example.astroxplore.features.groups.ui.components.QrCodeScannerSheet
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    onGroupClick: (String) -> Unit,
    onLibraryClick: () -> Unit = {},
    viewModel: GroupsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var showCreateSheet by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        viewModel.joinStatus.collectLatest { success ->
            if (success) {
                snackbarHostState.showSnackbar("Successfully joined the club!")
            } else {
                snackbarHostState.showSnackbar("Couldn't join. Please verify the club code or QR code.")
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    if (showQrScanner) {
        QrCodeScannerSheet(
            onDismiss = { showQrScanner = false },
            onCodeScanned = { code ->
                showQrScanner = false
                viewModel.joinGroup(code)
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Journal Clubs",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        )
                    }
                },
                windowInsets = WindowInsets(0.dp),
                actions = {
                    IconButton(onClick = { showQrScanner = true }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR Code")
                    }
                    IconButton(onClick = { showJoinDialog = true }) {
                        Icon(Icons.Outlined.GroupAdd, contentDescription = "Join Club by Code")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Start a Club", fontWeight = FontWeight.Bold) },
                shape = MaterialTheme.shapes.large
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.syncGroups() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is GroupsUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AstroM3LoadingIndicator(
                            size = AstroLoadingSize.LARGE,
                            label = "Loading private journal clubs..."
                        )
                    }
                }
                is GroupsUiState.Success -> {
                    if (state.clubs.isEmpty()) {
                        EmptyClubsState(
                            onCreateClick = { showCreateSheet = true },
                            onJoinClick = { showJoinDialog = true },
                            onScanQrClick = { showQrScanner = true }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                PrivateClubsHeaderBanner(
                                    clubCount = state.clubs.size
                                )
                            }

                            items(state.clubs, key = { it.id }) { group ->
                                ClubCard(
                                    group = group,
                                    onClick = { onGroupClick(group.id) },
                                    onCopyCode = { code ->
                                        clipboardManager.setText(AnnotatedString(code))
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Club code copied: $code")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                is GroupsUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        CreateClubSheet(
            isOnline = isOnline,
            onDismiss = { showCreateSheet = false },
            onCreate = { name, desc, focus, sched, loc ->
                viewModel.createGroup(name, desc, focus, sched, loc) {
                    showCreateSheet = false
                }
            }
        )
    }

    if (showJoinDialog) {
        JoinClubDialog(
            onDismiss = { showJoinDialog = false },
            onScanQr = {
                showJoinDialog = false
                showQrScanner = true
            },
            onJoin = { code ->
                viewModel.joinGroup(code)
                showJoinDialog = false
            }
        )
    }
}

@Composable
fun PrivateClubsHeaderBanner(
    clubCount: Int
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Groups,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    "My Research Circles",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "$clubCount active private ${if (clubCount == 1) "circle" else "circles"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ClubCard(
    group: GroupModel,
    onClick: () -> Unit,
    onCopyCode: (String) -> Unit
) {
    val theme = AstroClubThemes.getThemeForGroup(group)
    val cleanFocus = AstroClubThemes.cleanFocusArea(group.focusArea)

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, theme.primaryColor.copy(alpha = 0.28f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Themed Avatar + Club Name + Focus + Code
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Curated Theme Avatar
                    Surface(
                        shape = CircleShape,
                        color = theme.containerColor,
                        border = BorderStroke(1.5.dp, theme.primaryColor.copy(alpha = 0.4f)),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                theme.icon,
                                contentDescription = theme.iconName,
                                tint = theme.primaryColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = theme.containerColor
                            ) {
                                Text(
                                    text = cleanFocus,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.onContainerColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Unique Club Code Badge with Copy Action
                Surface(
                    onClick = { onCopyCode(group.displayId) },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = theme.primaryColor,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = group.displayId,
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.primaryColor,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy Code",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }

            if (!group.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = group.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Meeting Status & Cadence Highlight Pill
            if (group.meetingSchedule.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = theme.containerColor,
                    border = BorderStroke(1.dp, theme.primaryColor.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = theme.primaryColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = group.meetingSchedule,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = theme.onContainerColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Venue / Video Link
            if (group.meetingLocation.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Outlined.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = group.meetingLocation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Activity Stats & Open Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.People,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${group.memberCount} ${if (group.memberCount == 1) "member" else "members"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Shelf",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Private",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Open Club", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun EmptyClubsState(
    onCreateClick: () -> Unit,
    onJoinClick: () -> Unit,
    onScanQrClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            modifier = Modifier.size(88.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "Private Journal Clubs",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Journal clubs in AstroXplore are private research groups. Create a club with custom meeting days and frequency, or join using an invite code or QR code from your colleagues.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onCreateClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start a Club", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onJoinClick,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Outlined.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Join by Code", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            FilledTonalButton(
                onClick = onScanQrClick,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Scan QR", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Multi-Step Progressive Group Creation Wizard
 * Step 0: Identity & Theme (Name, Description, Space Icon, Accent Palette, Focus Area)
 * Step 1: Meeting Cadence & Schedule (Weekly multi-day or Monthly weekday/date + Time)
 * Step 2: Venue & Review (Location link, full summary confirmation)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateClubSheet(
    isOnline: Boolean = true,
    onDismiss: () -> Unit,
    onCreate: (name: String, desc: String?, focus: String?, schedule: String, loc: String) -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }

    // Step 0: Immersive Identity & Theme State
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var focusArea by remember { mutableStateOf("") }
    var selectedIconName by remember { mutableStateOf("Rocket") }

    val currentAccentColor = MaterialTheme.colorScheme.primary
    val currentContainerColor = MaterialTheme.colorScheme.primaryContainer
    val currentIcon = AstroClubThemes.availableIcons[selectedIconName] ?: Icons.Default.RocketLaunch

    val quickTopics = listOf("Exoplanets", "JWST Galaxies", "Gravitational Waves", "Black Holes", "Cosmology", "Fast Radio Bursts")

    // Step 1: Meeting Scheduler State
    val frequencies = listOf("Weekly", "Monthly")
    var selectedFrequency by remember { mutableStateOf("Weekly") }

    val daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    var selectedDays by remember { mutableStateOf(setOf("Thu")) }

    var monthlyMode by remember { mutableStateOf("By Weekday") }
    val weekOrdinals = listOf("1st", "2nd", "3rd", "4th", "Last")
    var selectedOrdinal by remember { mutableStateOf("1st") }
    var selectedMonthlyDay by remember { mutableStateOf("Thu") }

    val monthlyPresetDates = listOf("1st", "10th", "15th", "20th", "25th", "Last day")
    var selectedMonthlyDate by remember { mutableStateOf("1st") }
    var isCustomMonthlyDate by remember { mutableStateOf(false) }
    var customDayNumber by remember { mutableStateOf("1") }

    val presetTimes = listOf("9:00 AM", "11:00 AM", "2:00 PM", "4:00 PM", "6:00 PM")
    var selectedTime by remember { mutableStateOf("4:00 PM") }
    var isCustomTime by remember { mutableStateOf(false) }
    var customTimeInput by remember { mutableStateOf("") }

    // Dynamic schedule preview
    val formattedSchedule = remember(
        selectedFrequency,
        selectedDays,
        monthlyMode,
        selectedOrdinal,
        selectedMonthlyDay,
        selectedMonthlyDate,
        isCustomMonthlyDate,
        customDayNumber,
        selectedTime,
        isCustomTime,
        customTimeInput
    ) {
        val timeStr = if (isCustomTime && customTimeInput.isNotBlank()) customTimeInput.trim() else selectedTime
        if (selectedFrequency == "Weekly") {
            val daysStr = if (selectedDays.isEmpty()) {
                "Select days"
            } else {
                daysOfWeek.filter { selectedDays.contains(it) }.joinToString(", ")
            }
            "Weekly on $daysStr at $timeStr"
        } else {
            if (monthlyMode == "By Weekday") {
                "Monthly on the $selectedOrdinal $selectedMonthlyDay at $timeStr"
            } else {
                val dateStr = if (isCustomMonthlyDate && customDayNumber.isNotBlank()) {
                    val num = customDayNumber.toIntOrNull()?.coerceIn(1, 31) ?: 1
                    val suffix = when {
                        num in 11..13 -> "th"
                        num % 10 == 1 -> "st"
                        num % 10 == 2 -> "nd"
                        num % 10 == 3 -> "rd"
                        else -> "th"
                    }
                    "$num$suffix"
                } else {
                    selectedMonthlyDate
                }
                "Monthly on the $dateStr at $timeStr"
            }
        }
    }

    val isStep1Valid = if (selectedFrequency == "Weekly") {
        selectedDays.isNotEmpty()
    } else {
        true
    }

    // Step 2: Venue & Confirmation State
    var location by remember { mutableStateOf("Virtual Seminar Room") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Stepper Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = currentContainerColor,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                currentIcon,
                                contentDescription = null,
                                tint = currentAccentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = when (currentStep) {
                                0 -> "Step 1 of 3: Immersive Identity"
                                1 -> "Step 2 of 3: Meeting Cadence"
                                else -> "Step 3 of 3: Venue & Review"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = when (currentStep) {
                                0 -> "Personalized icon, accent color, and focus"
                                1 -> "Meeting recurrence and schedule logic"
                                else -> "Venue link and final confirmation"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Progressive step progress bar
            LinearProgressIndicator(
                progress = { (currentStep + 1) / 3f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = currentAccentColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            // Animated Step Content
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally(animationSpec = tween(220)) { width -> width } + fadeIn()).togetherWith(
                            slideOutHorizontally(animationSpec = tween(220)) { width -> -width } + fadeOut()
                        )
                    } else {
                        (slideInHorizontally(animationSpec = tween(220)) { width -> -width } + fadeIn()).togetherWith(
                            slideOutHorizontally(animationSpec = tween(220)) { width -> width } + fadeOut()
                        )
                    }
                },
                label = "WizardStepTransition"
            ) { step ->
                when (step) {
                    // -------------------------------------------------------------
                    // STEP 0: Immersive Identity & Personalized Icon & Color
                    // -------------------------------------------------------------
                    0 -> {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Journal Club Name *") },
                                placeholder = { Text("e.g. JWST High-Redshift Syndicate") },
                                leadingIcon = {
                                    Icon(currentIcon, contentDescription = null, tint = currentAccentColor)
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )



                            // Personalized Modern Icon Selection from M Icons
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Personalized Club Icon (Material Icons):",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val iconEntries = AstroClubThemes.availableIcons.entries.toList()
                                        iconEntries.chunked(4).forEach { rowItems ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                rowItems.forEach { (iconName, iconVector) ->
                                                    val isSelected = selectedIconName == iconName
                                                    Surface(
                                                        onClick = { selectedIconName = iconName },
                                                        shape = RoundedCornerShape(12.dp),
                                                        color = if (isSelected) currentContainerColor else MaterialTheme.colorScheme.surface,
                                                        border = BorderStroke(
                                                            width = if (isSelected) 1.5.dp else 1.dp,
                                                            color = if (isSelected) currentAccentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                                        ),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(48.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                iconVector,
                                                                contentDescription = iconName,
                                                                tint = if (isSelected) currentAccentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                repeat(4 - rowItems.size) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Research Focus Area
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "Research Focus Area:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(quickTopics) { topic ->
                                        FilterChip(
                                            selected = focusArea == topic,
                                            onClick = { focusArea = if (focusArea == topic) "" else topic },
                                            label = { Text(topic, fontSize = 12.sp) }
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = focusArea,
                                    onValueChange = { focusArea = it },
                                    placeholder = { Text("Or custom focus (e.g. Magnetars & FRBs)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                label = { Text("Club Description (Optional)") },
                                placeholder = { Text("Overview of meeting topics, paper criteria, or presentation guidelines...") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = { currentStep = 1 },
                                enabled = name.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Continue to Schedule", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // -------------------------------------------------------------
                    // STEP 1: Meeting Cadence & Schedule
                    // -------------------------------------------------------------
                    1 -> {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, currentAccentColor.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // 1. Frequency
                                    Text(
                                        "Meeting Frequency:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        frequencies.forEach { freq ->
                                            FilterChip(
                                                selected = selectedFrequency == freq,
                                                onClick = { selectedFrequency = freq },
                                                label = { Text(freq, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                            )
                                        }
                                    }

                                    // 2. Logic parameters based on frequency
                                    if (selectedFrequency == "Weekly") {
                                        Text(
                                            "Days of the Week (Multi-select):",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            daysOfWeek.forEach { day ->
                                                val isSelected = selectedDays.contains(day)
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = {
                                                        selectedDays = if (isSelected) {
                                                            if (selectedDays.size > 1) selectedDays - day else selectedDays
                                                        } else {
                                                            selectedDays + day
                                                        }
                                                    },
                                                    label = {
                                                        Text(
                                                            day,
                                                            fontSize = 11.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    },
                                                    modifier = Modifier.height(34.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        // Monthly
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                "Monthly Recurrence Pattern:",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                listOf("By Weekday", "By Date").forEach { mode ->
                                                    FilterChip(
                                                        selected = monthlyMode == mode,
                                                        onClick = { monthlyMode = mode },
                                                        label = { Text(mode, fontSize = 11.sp) }
                                                    )
                                                }
                                            }

                                            if (monthlyMode == "By Weekday") {
                                                Text(
                                                    "Week of the Month:",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    items(weekOrdinals) { ord ->
                                                        FilterChip(
                                                            selected = selectedOrdinal == ord,
                                                            onClick = { selectedOrdinal = ord },
                                                            label = { Text(ord, fontSize = 11.sp) }
                                                        )
                                                    }
                                                }

                                                Text(
                                                    "Day of the Week:",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    daysOfWeek.forEach { day ->
                                                        FilterChip(
                                                            selected = selectedMonthlyDay == day,
                                                            onClick = { selectedMonthlyDay = day },
                                                            label = { Text(day, fontSize = 11.sp) },
                                                            modifier = Modifier.height(34.dp)
                                                        )
                                                    }
                                                }
                                            } else {
                                                Text(
                                                    "Day of Month Preset:",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    items(monthlyPresetDates) { pDate ->
                                                        FilterChip(
                                                            selected = !isCustomMonthlyDate && selectedMonthlyDate == pDate,
                                                            onClick = {
                                                                isCustomMonthlyDate = false
                                                                selectedMonthlyDate = pDate
                                                            },
                                                            label = { Text(pDate, fontSize = 11.sp) }
                                                        )
                                                    }
                                                    item {
                                                        FilterChip(
                                                            selected = isCustomMonthlyDate,
                                                            onClick = { isCustomMonthlyDate = true },
                                                            label = { Text("Custom Day", fontSize = 11.sp) }
                                                        )
                                                    }
                                                }

                                                AnimatedVisibility(visible = isCustomMonthlyDate) {
                                                    OutlinedTextField(
                                                        value = customDayNumber,
                                                        onValueChange = { customDayNumber = it.filter { ch -> ch.isDigit() }.take(2) },
                                                        label = { Text("Calendar Day (1 - 31)") },
                                                        singleLine = true,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // 3. Meeting Time
                                    Text(
                                        "Meeting Time:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(presetTimes) { time ->
                                            FilterChip(
                                                selected = !isCustomTime && selectedTime == time,
                                                onClick = {
                                                    isCustomTime = false
                                                    selectedTime = time
                                                },
                                                label = { Text(time, fontSize = 11.sp) }
                                            )
                                        }
                                        item {
                                            FilterChip(
                                                selected = isCustomTime,
                                                onClick = { isCustomTime = true },
                                                label = { Text("Custom", fontSize = 11.sp) }
                                            )
                                        }
                                    }

                                    AnimatedVisibility(visible = isCustomTime) {
                                        OutlinedTextField(
                                            value = customTimeInput,
                                            onValueChange = { customTimeInput = it },
                                            label = { Text("Custom Meeting Time (e.g. 3:30 PM UTC)") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    // Live Cadence Preview Card
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = currentContainerColor,
                                        border = BorderStroke(1.dp, currentAccentColor.copy(alpha = 0.35f))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.CalendarToday,
                                                contentDescription = null,
                                                tint = currentAccentColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    "Formatted Schedule Preview:",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = currentAccentColor.copy(alpha = 0.8f)
                                                )
                                                Text(
                                                    text = formattedSchedule,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = currentAccentColor
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { currentStep = 0 },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Back")
                                }

                                Button(
                                    onClick = { currentStep = 2 },
                                    enabled = isStep1Valid,
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .height(50.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = currentAccentColor)
                                ) {
                                    Text("Continue to Venue", fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // -------------------------------------------------------------
                    // STEP 2: Venue & Final Confirmation
                    // -------------------------------------------------------------
                    2 -> {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            OutlinedTextField(
                                value = location,
                                onValueChange = { location = it },
                                label = { Text("Meeting Venue or Video Link") },
                                placeholder = { Text("e.g. Google Meet / Seminar Room 302") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = currentAccentColor)
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Comprehensive Summary Review Card
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = currentContainerColor.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.5.dp, currentAccentColor.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        "Club Summary Review",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = currentAccentColor
                                    )

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = CircleShape,
                                            color = currentContainerColor,
                                            border = BorderStroke(1.5.dp, currentAccentColor),
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    currentIcon,
                                                    contentDescription = null,
                                                    tint = currentAccentColor,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = if (focusArea.isNotBlank()) focusArea else "Astrophysics Discussion",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = currentAccentColor,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = currentAccentColor.copy(alpha = 0.2f))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = currentAccentColor, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(formattedSchedule, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = currentAccentColor, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(location.ifBlank { "Virtual Seminar Room" }, style = MaterialTheme.typography.bodySmall)
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Private Circle • Unique invite code will be generated", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { currentStep = 1 },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Back")
                                }

                                if (!isOnline) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.CloudOff,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                            Text(
                                                "You are currently offline. Group creation requires an active internet connection to synchronize with Supabase.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        val taggedFocusArea = buildString {
                                            append(if (focusArea.isNotBlank()) focusArea.trim() else "Astrophysics")
                                            append(" [icon:$selectedIconName]")
                                        }
                                        onCreate(name, description, taggedFocusArea, formattedSchedule, location)
                                    },
                                    enabled = name.isNotBlank() && isStep1Valid && isOnline,
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .height(52.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = currentAccentColor)
                                ) {
                                    Text(
                                        if (isOnline) "Create Journal Club" else "Offline (Online Required)",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun JoinClubDialog(
    onDismiss: () -> Unit,
    onScanQr: () -> Unit = {},
    onJoin: (String) -> Unit
) {
    var codeInput by remember { mutableStateOf("") }
    val sampleCodes = listOf("JWST01", "EXOATM02", "GWASTRO03", "ASTRO8X2")

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Outlined.GroupAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        title = {
            Text("Join Private Journal Club", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Enter the unique 6 to 8-character Club Code provided by the club founder, or scan their QR code.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = onScanQr,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan Club QR Code", fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it.uppercase() },
                    label = { Text("Club Code (e.g. ASTRO8X2)") },
                    placeholder = { Text("ASTRO8X2") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (codeInput.isNotBlank()) {
                            IconButton(onClick = { codeInput = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                )

                Text("Recent / Sample Codes:", style = MaterialTheme.typography.labelSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(sampleCodes) { sample ->
                        SuggestionChip(
                            onClick = { codeInput = sample },
                            label = { Text(sample, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onJoin(codeInput.trim()) },
                enabled = codeInput.isNotBlank()
            ) {
                Text("Join Club", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
