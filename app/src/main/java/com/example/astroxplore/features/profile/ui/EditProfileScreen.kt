package com.example.astroxplore.features.profile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.astroxplore.core.ui.components.AstroLoadingSize
import com.example.astroxplore.core.ui.components.AstroM3LoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onNavigateBack: () -> Unit,
    viewModel: EditProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    BackHandler(onBack = onNavigateBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Scholar Profile", fontWeight = FontWeight.Bold) },
                windowInsets = WindowInsets(0.dp),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        when (val state = uiState) {
            is EditProfileUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    AstroM3LoadingIndicator(
                        size = AstroLoadingSize.LARGE,
                        label = "Loading profile..."
                    )
                }
            }
            is EditProfileUiState.Success -> {
                val profile = state.profile
                
                var firstName by remember(profile.id) { mutableStateOf(profile.firstName ?: "") }
                var lastName by remember(profile.id) { mutableStateOf(profile.lastName ?: "") }
                var affiliationType by remember(profile.id) { mutableStateOf(profile.affiliationType ?: "Individual") }
                var affiliationName by remember(profile.id) { mutableStateOf(profile.affiliationName ?: profile.institution ?: "") }
                var country by remember(profile.id) { mutableStateOf(profile.country ?: "") }
                var educationLevel by remember(profile.id) { mutableStateOf(profile.educationLevel ?: "") }
                var orcidId by remember(profile.id) { mutableStateOf(profile.orcidId ?: "") }

                var hasUserEdited by remember { mutableStateOf(false) }

                // Sync whenever profile emits from local Room or remote Supabase if user hasn't overwritten
                LaunchedEffect(profile) {
                    if (!hasUserEdited) {
                        firstName = profile.firstName ?: ""
                        lastName = profile.lastName ?: ""
                        affiliationType = profile.affiliationType ?: "Individual"
                        affiliationName = profile.affiliationName ?: profile.institution ?: ""
                        country = profile.country ?: ""
                        educationLevel = profile.educationLevel ?: ""
                        orcidId = profile.orcidId ?: ""
                    }
                }

                // Debounced auto-save on field change to prevent any data loss
                LaunchedEffect(firstName, lastName, affiliationType, affiliationName, country, educationLevel, orcidId) {
                    if (hasUserEdited) {
                        kotlinx.coroutines.delay(800L)
                        viewModel.updateProfile(
                            firstName = firstName,
                            lastName = lastName,
                            affiliationType = affiliationType,
                            affiliationName = affiliationName,
                            country = country,
                            educationLevel = educationLevel,
                            institution = affiliationName,
                            orcidId = orcidId
                        )
                    }
                }

                val saveAndExit: () -> Unit = {
                    viewModel.updateProfile(
                        firstName = firstName,
                        lastName = lastName,
                        affiliationType = affiliationType,
                        affiliationName = affiliationName,
                        country = country,
                        educationLevel = educationLevel,
                        institution = affiliationName,
                        orcidId = orcidId,
                        onSuccess = onNavigateBack
                    )
                }

                BackHandler(onBack = saveAndExit)

                var showAffiliationPicker by remember { mutableStateOf(false) }
                var showCountryPicker by remember { mutableStateOf(false) }
                var showEducationPicker by remember { mutableStateOf(false) }

                if (showAffiliationPicker) {
                    PickerBottomSheet(
                        title = "Affiliation Type",
                        options = viewModel.affiliationTypes,
                        selectedOption = affiliationType,
                        showSearch = false,
                        onOptionSelected = { 
                            affiliationType = it
                            hasUserEdited = true
                        },
                        onDismiss = { showAffiliationPicker = false }
                    )
                }

                if (showCountryPicker) {
                    PickerBottomSheet(
                        title = "Country",
                        options = viewModel.countries,
                        selectedOption = country,
                        showSearch = true,
                        onOptionSelected = { 
                            country = it
                            hasUserEdited = true
                        },
                        onDismiss = { showCountryPicker = false }
                    )
                }

                if (showEducationPicker) {
                    PickerBottomSheet(
                        title = "Education Level",
                        options = viewModel.educationLevels,
                        selectedOption = educationLevel,
                        showSearch = false,
                        onOptionSelected = { 
                            educationLevel = it
                            hasUserEdited = true
                        },
                        onDismiss = { showEducationPicker = false }
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .imePadding()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Immersive Header Hero Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ),
                        shape = MaterialTheme.shapes.extraLarge,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    val initials = buildString {
                                        if (firstName.isNotBlank()) append(firstName.first().uppercase())
                                        if (lastName.isNotBlank()) append(lastName.first().uppercase())
                                        if (this.isEmpty()) append("AS")
                                    }
                                    Text(
                                        text = initials,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = if (firstName.isNotBlank() || lastName.isNotBlank()) "$firstName $lastName".trim() else "Astrophysics Scholar",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (affiliationName.isNotBlank()) affiliationName else "Researcher Profile",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Group 1: Public Identity
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "PUBLIC IDENTITY",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedTextField(
                                value = firstName,
                                onValueChange = { 
                                    firstName = it 
                                    hasUserEdited = true
                                },
                                label = { Text("First Name") },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                                shape = MaterialTheme.shapes.large,
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = lastName,
                                onValueChange = { 
                                    lastName = it 
                                    hasUserEdited = true
                                },
                                label = { Text("Last Name") },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                                shape = MaterialTheme.shapes.large,
                                singleLine = true
                            )
                        }
                    }

                    // Group 2: Academic Background
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "ACADEMIC BACKGROUND",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )

                            // Affiliation Type Picker Field
                            Box(modifier = Modifier.fillMaxWidth().clickable { showAffiliationPicker = true }) {
                                OutlinedTextField(
                                    value = affiliationType,
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = false,
                                    label = { Text("Affiliation Type") },
                                    leadingIcon = { Icon(Icons.Outlined.Business, contentDescription = null) },
                                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.large,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }

                            if (affiliationType != "Individual" && affiliationType.isNotEmpty()) {
                                OutlinedTextField(
                                    value = affiliationName,
                                    onValueChange = { 
                                        affiliationName = it 
                                        hasUserEdited = true
                                    },
                                    label = { Text("Institution / Organization Name") },
                                    modifier = Modifier.fillMaxWidth(),
                                    leadingIcon = { Icon(Icons.Outlined.Business, contentDescription = null) },
                                    shape = MaterialTheme.shapes.large,
                                    singleLine = true
                                )
                            }

                            // Country Picker Field
                            Box(modifier = Modifier.fillMaxWidth().clickable { showCountryPicker = true }) {
                                OutlinedTextField(
                                    value = country,
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = false,
                                    label = { Text("Country") },
                                    leadingIcon = { Icon(Icons.Outlined.Public, contentDescription = null) },
                                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.large,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }

                            // Education Level Picker Field
                            Box(modifier = Modifier.fillMaxWidth().clickable { showEducationPicker = true }) {
                                OutlinedTextField(
                                    value = educationLevel,
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = false,
                                    label = { Text("Education Level") },
                                    leadingIcon = { Icon(Icons.Outlined.School, contentDescription = null) },
                                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.large,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }

                    // Group 3: Research Credentials
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "RESEARCH CREDENTIALS",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedTextField(
                                value = orcidId,
                                onValueChange = { 
                                    orcidId = it 
                                    hasUserEdited = true
                                },
                                label = { Text("ORCID iD") },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = { Icon(Icons.Outlined.Badge, contentDescription = null) },
                                shape = MaterialTheme.shapes.large,
                                placeholder = { Text("0000-0000-0000-0000") },
                                singleLine = true
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = saveAndExit,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Changes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
            is EditProfileUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Error loading profile. Please try again.", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerBottomSheet(
    title: String,
    options: List<String>,
    selectedOption: String,
    showSearch: Boolean = false,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredOptions = remember(searchQuery, options) {
        if (searchQuery.isBlank()) options
        else options.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            if (showSearch) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredOptions) { option ->
                    val isSelected = option == selectedOption
                    Surface(
                        onClick = {
                            onOptionSelected(option)
                            onDismiss()
                        },
                        shape = MaterialTheme.shapes.medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
