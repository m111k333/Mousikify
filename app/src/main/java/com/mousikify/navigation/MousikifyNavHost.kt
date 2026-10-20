package com.mousikify.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mousikify.core.designsystem.theme.SpotifyBackground
import com.mousikify.core.designsystem.theme.SpotifyCardBackground
import com.mousikify.core.designsystem.theme.SpotifyGreen
import com.mousikify.core.designsystem.theme.SpotifyTextPrimary
import com.mousikify.core.designsystem.theme.SpotifyTextSecondary
import com.mousikify.feature.equalizer.EqualizerRoute
import com.mousikify.feature.home.DiagnosticsRoute
import com.mousikify.feature.home.HomeRoute
import com.mousikify.feature.library.LibraryRoute
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mousikify.feature.player.FullScreenPlayerOverlay
import com.mousikify.feature.player.MiniPlayerBar
import com.mousikify.feature.search.SearchRoute

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : Screen("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    data object Search : Screen("search", "Search", Icons.Filled.Search, Icons.Outlined.Search)
    data object Library : Screen("library", "Your Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic)
    data object Equalizer : Screen("equalizer", "Equalizer", Icons.Filled.Home, Icons.Outlined.Home)
    data object Diagnostics : Screen("diagnostics", "Diagnostics", Icons.Filled.Home, Icons.Outlined.Home)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Search,
    Screen.Library
)

@Composable
fun MainAppContainer(
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in bottomNavItems.map { it.route }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var homeResetTrigger by remember { mutableStateOf(0L) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = SpotifyBackground,
            bottomBar = {
                if (showBottomBar) {
                    Column {
                        // MiniPlayer attached directly above bottom navigation bar
                        MiniPlayerBar(
                            onExpand = { isPlayerExpanded = true },
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        NavigationBar(
                            containerColor = SpotifyBackground.copy(alpha = 0.95f),
                            contentColor = SpotifyTextPrimary
                        ) {
                        bottomNavItems.forEach { screen ->
                            val selected = currentRoute == screen.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (screen == Screen.Home) {
                                        homeResetTrigger = System.currentTimeMillis()
                                        if (currentRoute != screen.route) {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = false
                                                }
                                                launchSingleTop = true
                                                restoreState = false
                                            }
                                        }
                                    } else {
                                        if (currentRoute != screen.route) {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                        contentDescription = screen.title
                                    )
                                },
                                label = { Text(screen.title) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = SpotifyGreen,
                                    selectedTextColor = SpotifyGreen,
                                    unselectedIconColor = SpotifyTextSecondary,
                                    unselectedTextColor = SpotifyTextSecondary,
                                    indicatorColor = Color.Transparent
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (showBottomBar) innerPadding.calculateBottomPadding() else 0.dp),
            enterTransition = { fadeIn(animationSpec = tween(180)) },
            exitTransition = { fadeOut(animationSpec = tween(140)) },
            popEnterTransition = { fadeIn(animationSpec = tween(180)) },
            popExitTransition = { fadeOut(animationSpec = tween(140)) }
        ) {
            composable(Screen.Home.route) {
                HomeRoute(
                    onNavigateToEqualizer = { navController.navigate(Screen.Equalizer.route) },
                    onNavigateToDiagnostics = { navController.navigate(Screen.Diagnostics.route) },
                    homeResetTrigger = homeResetTrigger
                )
            }
            composable(Screen.Search.route) {
                SearchRoute()
            }
            composable(Screen.Library.route) {
                LibraryRoute()
            }
            composable(Screen.Equalizer.route) {
                EqualizerRoute(
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(Screen.Diagnostics.route) {
                DiagnosticsRoute(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }

        // Full Screen Player Overlay on top of everything!
        FullScreenPlayerOverlay(
            isExpanded = isPlayerExpanded,
            onCollapse = { isPlayerExpanded = false },
            onNavigateToEqualizer = {
                isPlayerExpanded = false
                navController.navigate(Screen.Equalizer.route)
            }
        )
    }
}
