package com.example.astroxplore

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.astroxplore.core.database.ThemeMode
import com.example.astroxplore.features.profile.ui.ProfileViewModel
import com.example.astroxplore.navigation.RootNavHost
import com.example.astroxplore.navigation.Screen
import com.example.astroxplore.ui.theme.AstroXploreTheme
import com.example.astroxplore.core.ui.components.AstroLoadingSize
import com.example.astroxplore.core.ui.components.AstroM3LoadingIndicator
import io.github.jan.supabase.auth.status.SessionStatus

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val sessionStatus by mainViewModel.sessionStatus.collectAsState()

            // Keep splash screen visible until we have a determined session status
            splashScreen.setKeepOnScreenCondition {
                sessionStatus == SessionStatus.Initializing
            }

            val profileViewModel: ProfileViewModel = hiltViewModel()
            val settingsState by profileViewModel.uiState.collectAsState()

            val darkTheme = when (settingsState.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            AstroXploreTheme(
                darkTheme = darkTheme,
                dynamicColor = settingsState.dynamicColorEnabled,
            ) {
                val isOnboarded by mainViewModel.isOnboarded.collectAsState()
                val navController = rememberNavController()
                    AstroXploreMain(
                        sessionStatus = sessionStatus,
                        isOnboarded = isOnboarded,
                        navController = navController,
                        onScrollToTop = { mainViewModel.triggerScrollToTop() }
                    )
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun AstroXploreMain(
    sessionStatus: SessionStatus = SessionStatus.Initializing,
    isOnboarded: Boolean? = null,
    navController: NavHostController = rememberNavController(),
    onScrollToTop: () -> Unit = {}
) {
    // Definitive readiness gate: wait until session is not Initializing,
    // and if Authenticated, wait until onboarding is resolved (non-null).
    val isReady = sessionStatus !is SessionStatus.Initializing &&
        (sessionStatus is SessionStatus.NotAuthenticated || (sessionStatus is SessionStatus.Authenticated && isOnboarded != null))

    val startDestination = remember(isReady, sessionStatus, isOnboarded) {
        if (!isReady) null
        else when {
            sessionStatus is SessionStatus.NotAuthenticated -> Screen.AuthGraph
            sessionStatus is SessionStatus.Authenticated && isOnboarded == true -> Screen.MainGraph
            sessionStatus is SessionStatus.Authenticated && isOnboarded == false -> Screen.OnboardingGraph
            else -> Screen.AuthGraph
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main App Content (mounted only once startDestination is definitively known)
        if (startDestination != null) {
            RootNavHost(
                navController = navController,
                startDestination = startDestination,
                sessionStatus = sessionStatus,
                onScrollToTop = onScrollToTop
            )
        }

        // Modern Material 3 Splash Overlay - Fades out seamlessly with zero flash
        AnimatedVisibility(
            visible = startDestination == null,
            exit = fadeOut(tween(400)),
            modifier = Modifier.fillMaxSize()
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = "AstroXplore",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "AstroXplore",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Astrophysics Preprint Explorer",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(36.dp))
                        AstroM3LoadingIndicator(
                            size = AstroLoadingSize.MEDIUM,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val screen: Screen
) {
    FEED("Feed", Icons.Filled.Newspaper, Icons.Outlined.Newspaper, Screen.Feed),
    GROUPS("Groups", Icons.Filled.Groups, Icons.Outlined.Groups, Screen.Groups),
    EXPLORE("Explore", Icons.Filled.Explore, Icons.Outlined.Explore, Screen.Explore()),
    LIBRARY("Library", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder, Screen.Library),
    PROFILE("Profile", Icons.Filled.Person, Icons.Outlined.Person, Screen.Profile),
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AstroXploreTheme {
        Greeting("Android")
    }
}