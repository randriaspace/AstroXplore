package com.example.astroxplore.features.onboarding.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.astroxplore.R
import com.example.astroxplore.core.ui.components.AstroLoadingSize
import com.example.astroxplore.core.ui.components.AstroM3LoadingIndicator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var currentStep by remember { mutableIntStateOf(1) }
    var showSuccess by remember { mutableStateOf(false) }

    // Step Professional State
    var affiliationType by remember { mutableStateOf("") }
    var affiliationName by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var educationLevel by remember { mutableStateOf("") }

    // Step Interests
    val selectedInterests by viewModel.selectedInterests.collectAsState()

    if (showSuccess) {
        SuccessImmersive(onOnboardingComplete)
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Immersive Background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )

            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    Column {
                        TopAppBar(
                            title = { 
                                Text(
                                    when(currentStep) {
                                        1 -> "Professional Background"
                                        else -> "Research Interests"
                                    }, 
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                ) 
                            },
                            navigationIcon = {
                                if (currentStep > 1) {
                                    IconButton(onClick = { currentStep-- }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack, 
                                            contentDescription = "Back",
                                            tint = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        )
                        
                        val progress by animateFloatAsState(
                            targetValue = currentStep / 2f,
                            animationSpec = spring(stiffness = Spring.StiffnessLow),
                            label = "progress"
                        )
                        
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).clip(MaterialTheme.shapes.small),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                bottomBar = {
                    Box(modifier = Modifier.padding(24.dp).navigationBarsPadding()) {
                        when (currentStep) {
                            1 -> {
                                Button(
                                    onClick = { currentStep = 2 },
                                    modifier = Modifier.fillMaxWidth().height(64.dp),
                                    enabled = affiliationType.isNotEmpty() && country.isNotEmpty() && educationLevel.isNotEmpty(),
                                    shape = MaterialTheme.shapes.extraLarge
                                ) {
                                    Text("Next: Select Interests", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                            2 -> {
                                Button(
                                    onClick = { 
                                        viewModel.completeOnboarding(
                                            affiliationType = affiliationType,
                                            affiliationName = affiliationName,
                                            country = country,
                                            educationLevel = educationLevel,
                                            onSuccess = { showSuccess = true }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth().height(64.dp),
                                    enabled = selectedInterests.size in 3..6 && !isLoading,
                                    shape = MaterialTheme.shapes.extraLarge
                                ) {
                                    Text(stringResource(R.string.get_started), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                        } else {
                            slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                        }
                    },
                    label = "stepTransition"
                ) { step ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (error != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(error!!),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(12.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        when (step) {
                            1 -> {
                                ProfessionalStep(
                                    affiliationType = affiliationType,
                                    onAffiliationTypeChange = { affiliationType = it },
                                    affiliationName = affiliationName,
                                    onAffiliationNameChange = { affiliationName = it },
                                    country = country,
                                    onCountryChange = { country = it },
                                    educationLevel = educationLevel,
                                    onEducationLevelChange = { educationLevel = it },
                                    viewModel = viewModel
                                )
                            }
                            2 -> {
                                InterestsStep(
                                    selectedInterests = selectedInterests,
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            AstroM3LoadingIndicator(
                size = AstroLoadingSize.LARGE,
                label = "Configuring your research feed..."
            )
        }
    }
}

@Composable
fun ProfessionalStep(
    affiliationType: String,
    onAffiliationTypeChange: (String) -> Unit,
    affiliationName: String,
    onAffiliationNameChange: (String) -> Unit,
    country: String,
    onCountryChange: (String) -> Unit,
    educationLevel: String,
    onEducationLevelChange: (String) -> Unit,
    viewModel: OnboardingViewModel
) {
    var showAffiliationPicker by remember { mutableStateOf(false) }
    var showCountryPicker by remember { mutableStateOf(false) }
    var showEducationPicker by remember { mutableStateOf(false) }

    if (showAffiliationPicker) {
        PickerBottomSheet(
            title = stringResource(R.string.affiliation_type),
            options = viewModel.affiliationTypes,
            selectedOption = affiliationType,
            showSearch = false,
            onOptionSelected = onAffiliationTypeChange,
            onDismiss = { showAffiliationPicker = false }
        )
    }

    if (showCountryPicker) {
        PickerBottomSheet(
            title = stringResource(R.string.country),
            options = viewModel.countries,
            selectedOption = country,
            showSearch = true,
            onOptionSelected = onCountryChange,
            onDismiss = { showCountryPicker = false }
        )
    }

    if (showEducationPicker) {
        PickerBottomSheet(
            title = stringResource(R.string.education_level),
            options = viewModel.educationLevels,
            selectedOption = educationLevel,
            showSearch = false,
            onOptionSelected = onEducationLevelChange,
            onDismiss = { showEducationPicker = false }
        )
    }

    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = "Tell us about your research background",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(32.dp))

        // Affiliation Selector Field
        Box(modifier = Modifier.fillMaxWidth().clickable { showAffiliationPicker = true }) {
            OutlinedTextField(
                value = affiliationType,
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text(stringResource(R.string.affiliation_type)) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        if (affiliationType != "Individual" && affiliationType.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = affiliationName,
                onValueChange = onAffiliationNameChange,
                label = { Text(stringResource(R.string.affiliation_name)) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Country Selector Field
        Box(modifier = Modifier.fillMaxWidth().clickable { showCountryPicker = true }) {
            OutlinedTextField(
                value = country,
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text(stringResource(R.string.country)) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Education Level Selector Field
        Box(modifier = Modifier.fillMaxWidth().clickable { showEducationPicker = true }) {
            OutlinedTextField(
                value = educationLevel,
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text(stringResource(R.string.education_level)) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
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
                .padding(bottom = 24.dp)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterestsStep(
    selectedInterests: List<String>,
    viewModel: OnboardingViewModel
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filteredKeywords by viewModel.filteredKeywords.collectAsState()

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Select 3-6 topics to personalize your academic feed.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search 100+ keywords...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            filteredKeywords.forEach { topic ->
                val isSelected = selectedInterests.contains(topic)
                
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.toggleInterest(topic) },
                    label = { 
                        Text(
                            text = topic, 
                            modifier = Modifier.padding(vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge
                        ) 
                    },
                    shape = MaterialTheme.shapes.large,
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
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "${selectedInterests.size} / 6 selected",
            style = MaterialTheme.typography.labelLarge,
            color = if (selectedInterests.size in 3..6) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun SuccessImmersive(onComplete: () -> Unit) {
    val scale = remember { Animatable(0.5f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            )
        }
        launch {
            alpha.animateTo(targetValue = 1f, animationSpec = tween(600))
        }
        delay(2000)
        onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale.value)
                    .alpha(alpha.value),
                tint = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Welcome to the Crew!",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.alpha(alpha.value)
            )
            Text(
                text = "Your personalized feed is ready.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                modifier = Modifier.alpha(alpha.value)
            )
        }
    }
}
