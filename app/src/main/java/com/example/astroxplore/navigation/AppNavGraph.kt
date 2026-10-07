package com.example.astroxplore.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.example.astroxplore.features.auth.ui.LoginScreen
import com.example.astroxplore.features.auth.ui.SignupScreen
import com.example.astroxplore.features.feed.ui.FeedScreen
import com.example.astroxplore.features.feed.ui.PaperDetailsScreen
import com.example.astroxplore.features.groups.ui.GroupDetailsScreen
import com.example.astroxplore.features.groups.ui.GroupsScreen
import com.example.astroxplore.features.library.ui.LibraryScreen
import com.example.astroxplore.features.onboarding.ui.OnboardingScreen
import com.example.astroxplore.features.profile.ui.EditProfileScreen
import com.example.astroxplore.features.profile.ui.InterestsScreen
import com.example.astroxplore.features.profile.ui.ProfileScreen
import com.example.astroxplore.features.search.ui.ExploreScreen
import io.github.jan.supabase.auth.status.SessionStatus

private const val screenTransitionDuration = 350
private const val tabTransitionDuration = 180
private val screenSlideSpec = tween<IntOffset>(
    durationMillis = screenTransitionDuration,
    easing = FastOutSlowInEasing
)
private val screenFadeSpec = tween<Float>(
    durationMillis = screenTransitionDuration,
    easing = FastOutSlowInEasing
)

private fun isTopLevelTab(destination: NavDestination): Boolean =
    destination.hasRoute<Screen.Feed>() ||
        destination.hasRoute<Screen.Groups>() ||
        destination.hasRoute<Screen.Explore>() ||
        destination.hasRoute<Screen.Library>() ||
        destination.hasRoute<Screen.Profile>()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    isTopLevelTab(initialState.destination) && isTopLevelTab(targetState.destination)

private fun NavDestination.isInMainGraph(): Boolean =
    hierarchy.any { it.hasRoute<Screen.MainGraph>() }

private fun NavDestination.isInAuthGraph(): Boolean =
    hierarchy.any { it.hasRoute<Screen.AuthGraph>() }

private fun NavDestination.isInOnboardingGraph(): Boolean =
    hierarchy.any { it.hasRoute<Screen.OnboardingGraph>() }

@Composable
fun RootNavHost(
    navController: NavHostController,
    startDestination: Screen,
    sessionStatus: SessionStatus,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomNav = currentDestination?.let(::isTopLevelTab) == true

    LaunchedEffect(sessionStatus, currentDestination) {
        if (sessionStatus is SessionStatus.NotAuthenticated &&
            currentDestination != null &&
            !currentDestination.isInAuthGraph()
        ) {
            navController.navigate(Screen.AuthGraph) {
                when {
                    currentDestination.isInMainGraph() ->
                        popUpTo<Screen.MainGraph> { inclusive = true }
                    currentDestination.isInOnboardingGraph() ->
                        popUpTo<Screen.OnboardingGraph> { inclusive = true }
                }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { if (showBottomNav) BottomNavigationBar(navController = navController) },
        modifier = modifier
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            enterTransition = {
                if (isTabSwitch()) {
                    fadeIn(tween(tabTransitionDuration, easing = FastOutSlowInEasing))
                } else {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = screenSlideSpec
                    ) + fadeIn(animationSpec = screenFadeSpec)
                }
            },
            exitTransition = {
                if (isTabSwitch()) {
                    fadeOut(tween(tabTransitionDuration, easing = FastOutSlowInEasing))
                } else {
                    slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = screenSlideSpec
                    ) + fadeOut(animationSpec = screenFadeSpec)
                }
            },
            popEnterTransition = {
                if (isTabSwitch()) {
                    fadeIn(tween(tabTransitionDuration, easing = FastOutSlowInEasing))
                } else {
                    slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = screenSlideSpec
                    ) + fadeIn(animationSpec = screenFadeSpec)
                }
            },
            popExitTransition = {
                if (isTabSwitch()) {
                    fadeOut(tween(tabTransitionDuration, easing = FastOutSlowInEasing))
                } else {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = screenSlideSpec
                    ) + fadeOut(animationSpec = screenFadeSpec)
                }
            }
        ) {
            navigation<Screen.AuthGraph>(startDestination = Screen.Login) {
                composable<Screen.Login> {
                    LoginScreen(
                        onLoginSuccess = { isOnboarded ->
                            if (isOnboarded) {
                                navController.navigate(Screen.MainGraph) {
                                    popUpTo<Screen.AuthGraph> { inclusive = true }
                                    launchSingleTop = true
                                }
                            } else {
                                navController.navigate(Screen.OnboardingGraph) {
                                    popUpTo<Screen.AuthGraph> { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        },
                        onNavigateToSignup = { navController.navigate(Screen.Signup) },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable<Screen.Signup> {
                    SignupScreen(
                        onSignupSuccess = {
                            navController.navigate(Screen.OnboardingGraph) {
                                popUpTo<Screen.AuthGraph> { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToLogin = { navController.popBackStack() }
                    )
                }
            }

            navigation<Screen.OnboardingGraph>(startDestination = Screen.Onboarding) {
                composable<Screen.Onboarding> {
                    OnboardingScreen(onOnboardingComplete = {
                        navController.navigate(Screen.MainGraph) {
                            popUpTo<Screen.OnboardingGraph> { inclusive = true }
                            launchSingleTop = true
                        }
                    })
                }
            }

            navigation<Screen.MainGraph>(startDestination = Screen.Feed) {
                composable<Screen.Feed> {
                    FeedScreen(
                        onSearchClick = { navController.navigate(Screen.Explore(autofocus = true)) },
                        onPaperClick = { bibcode -> navController.navigate(Screen.PaperDetails(bibcode)) },
                        onLibraryClick = { navController.navigate(Screen.Library) },
                        onProfileClick = { navController.navigate(Screen.Profile) }
                    )
                }

                composable<Screen.Groups> {
                    GroupsScreen(
                        onGroupClick = { groupId -> navController.navigate(Screen.GroupDetails(groupId)) },
                        onLibraryClick = { navController.navigate(Screen.Library) }
                    )
                }

                composable<Screen.GroupDetails> { backStackEntry ->
                    val details = backStackEntry.toRoute<Screen.GroupDetails>()
                    GroupDetailsScreen(
                        groupId = details.groupId,
                        onNavigateBack = { navController.popBackStack() },
                        onPaperClick = { bibcode -> navController.navigate(Screen.PaperDetails(bibcode)) }
                    )
                }

                composable<Screen.Explore> { backStackEntry ->
                    val route = backStackEntry.toRoute<Screen.Explore>()
                    ExploreScreen(
                        autofocus = route.autofocus,
                        onPaperClick = { bibcode -> navController.navigate(Screen.PaperDetails(bibcode)) },
                        onLibraryClick = { navController.navigate(Screen.Library) }
                    )
                }

                composable<Screen.Library> {
                    LibraryScreen(onPaperClick = { bibcode -> navController.navigate(Screen.PaperDetails(bibcode)) })
                }

                composable<Screen.Profile> {
                    ProfileScreen(
                        onNavigateToInterests = { navController.navigate(Screen.Interests) },
                        onNavigateToEditProfile = { navController.navigate(Screen.EditProfile) }
                    )
                }

                composable<Screen.Interests> {
                    InterestsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onPreferencesUpdated = {
                            navController.navigate(Screen.Feed) {
                                popUpTo<Screen.MainGraph> { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable<Screen.EditProfile> {
                    EditProfileScreen(onNavigateBack = { navController.popBackStack() })
                }

                composable<Screen.PaperDetails> { backStackEntry ->
                    val details = backStackEntry.toRoute<Screen.PaperDetails>()
                    PaperDetailsScreen(
                        bibcode = details.bibcode,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

/**
 * Bottom Navigation Bar Component
 */
@Composable
fun BottomNavigationBar(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val items = listOf(
        NavBarItem("feed", "Feed", Icons.Filled.Newspaper, Icons.Outlined.Newspaper),
        NavBarItem("groups", "Groups", Icons.Filled.Groups, Icons.Outlined.Groups),
        NavBarItem("explore", "Explore", Icons.Filled.Explore, Icons.Outlined.Explore),
        NavBarItem("library", "Library", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder),
        NavBarItem("profile", "Profile", Icons.Filled.Person, Icons.Outlined.Person)
    )

    val currentTab = when {
        currentDestination?.hasRoute<Screen.Feed>() == true -> "feed"
        currentDestination?.hasRoute<Screen.Groups>() == true -> "groups"
        currentDestination?.hasRoute<Screen.Explore>() == true -> "explore"
        currentDestination?.hasRoute<Screen.Library>() == true -> "library"
        currentDestination?.hasRoute<Screen.Profile>() == true -> "profile"
        else -> "feed"
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = currentTab == item.route
                NavigationBarItem(
                    modifier = Modifier.weight(1f),
                    icon = {
                        AnimatedContent(
                            targetState = isSelected,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                                        scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90)))
                                    .togetherWith(fadeOut(animationSpec = tween(90)))
                            },
                            label = "icon_transition"
                        ) { selected ->
                            Icon(
                                imageVector = if (selected) item.filledIcon else item.outlinedIcon,
                                contentDescription = item.label
                            )
                        }
                    },
                    label = {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                        )
                    },
                    selected = isSelected,
                    onClick = {
                        val tabOptions: NavOptionsBuilder.() -> Unit = {
                            popUpTo<Screen.MainGraph> { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                        when (item.route) {
                            "feed" -> navController.navigate(Screen.Feed, tabOptions)
                            "groups" -> navController.navigate(Screen.Groups, tabOptions)
                            "explore" -> navController.navigate(Screen.Explore(), tabOptions)
                            "library" -> navController.navigate(Screen.Library, tabOptions)
                            "profile" -> navController.navigate(Screen.Profile, tabOptions)
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    )
                )
            }
        }
    }
}

// Nav bar item data class
data class NavBarItem(
    val route: String,
    val label: String,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector
)