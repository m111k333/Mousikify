package com.mousikify.navigation

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.mousikify.feature.library.generator.PlaylistGeneratorRoute
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mousikify.core.model.DeepLinkPayload
import com.mousikify.core.model.PlaybackContextType
import com.mousikify.feature.player.FullScreenPlayerOverlay
import com.mousikify.feature.player.MiniPlayerBar
import com.mousikify.core.model.Song
import com.mousikify.feature.player.radio.RadioRoute
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
    data object Radio : Screen("radio", "Radio", Icons.Filled.Radio, Icons.Outlined.Radio)
    data object PlaylistGenerator : Screen("playlist_generator", "Playlist Generator", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Search,
    Screen.Library
)

@Composable
fun MainAppContainer(
    navController: NavHostController = rememberNavController(),
    deepLinkPayload: DeepLinkPayload? = null,
    onDeepLinkHandled: () -> Unit = {}
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in bottomNavItems.map { it.route } ||
            currentRoute == Screen.Radio.route ||
            currentRoute == Screen.PlaylistGenerator.route
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var homeResetTrigger by remember { mutableStateOf(0L) }
    var searchResetTrigger by remember { mutableStateOf(0L) }
    var libraryResetTrigger by remember { mutableStateOf(0L) }
    var targetHomeMixId by remember { mutableStateOf<String?>(null) }
    var targetSearchArtist by remember { mutableStateOf<String?>(null) }
    var targetSearchAlbum by remember { mutableStateOf<Pair<String, String>?>(null) }
    var targetLibraryPlaylistId by remember { mutableStateOf<Long?>(null) }
    var targetLibraryAlbumId by remember { mutableStateOf<Long?>(null) }
    var targetLibraryAlbumTitle by remember { mutableStateOf<String?>(null) }
    var targetLibraryAlbumArtist by remember { mutableStateOf<String?>(null) }
    var targetSharedPlaylist by remember { mutableStateOf<DeepLinkPayload.Playlist?>(null) }
    var targetDeepLinkTrack by remember { mutableStateOf<Song?>(null) }
    var targetRadioSong by remember { mutableStateOf<Song?>(null) }

    LaunchedEffect(deepLinkPayload) {
        when (val payload = deepLinkPayload) {
            is DeepLinkPayload.Track -> {
                targetDeepLinkTrack = payload.song
                isPlayerExpanded = true
                onDeepLinkHandled()
            }
            is DeepLinkPayload.Playlist -> {
                isPlayerExpanded = false
                targetSharedPlaylist = payload
                navController.navigate(Screen.Library.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
                onDeepLinkHandled()
            }
            null -> {}
        }
    }

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
                                    if (currentRoute == Screen.Radio.route || currentRoute == Screen.PlaylistGenerator.route) {
                                        if (screen == Screen.Home) {
                                            homeResetTrigger = System.currentTimeMillis()
                                            navController.popBackStack(Screen.Home.route, inclusive = false)
                                        } else {
                                            navController.popBackStack(Screen.Home.route, inclusive = false)
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    } else if (currentRoute == screen.route) {
                                        when (screen) {
                                            Screen.Home -> homeResetTrigger = System.currentTimeMillis()
                                            Screen.Search -> searchResetTrigger = System.currentTimeMillis()
                                            Screen.Library -> libraryResetTrigger = System.currentTimeMillis()
                                            else -> {}
                                        }
                                    } else {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
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
                    initialMixId = targetHomeMixId,
                    onInitialMixHandled = { targetHomeMixId = null },
                    onNavigateToEqualizer = { navController.navigate(Screen.Equalizer.route) },
                    onNavigateToDiagnostics = { navController.navigate(Screen.Diagnostics.route) },
                    onNavigateToPlaylistGenerator = { navController.navigate(Screen.PlaylistGenerator.route) },
                    homeResetTrigger = homeResetTrigger,
                    onOpenRadio = { song ->
                        targetRadioSong = song
                        navController.navigate(Screen.Radio.route)
                    }
                )
            }
            composable(Screen.Search.route) {
                SearchRoute(
                    initialArtistName = targetSearchArtist,
                    onInitialArtistHandled = { targetSearchArtist = null },
                    initialAlbum = targetSearchAlbum,
                    onInitialAlbumHandled = { targetSearchAlbum = null },
                    searchResetTrigger = searchResetTrigger
                )
            }
            composable(Screen.Library.route) {
                LibraryRoute(
                    initialPlaylistId = targetLibraryPlaylistId,
                    onInitialPlaylistHandled = { targetLibraryPlaylistId = null },
                    initialAlbumId = targetLibraryAlbumId,
                    initialAlbumTitle = targetLibraryAlbumTitle,
                    initialAlbumArtist = targetLibraryAlbumArtist,
                    onInitialAlbumHandled = {
                        targetLibraryAlbumId = null
                        targetLibraryAlbumTitle = null
                        targetLibraryAlbumArtist = null
                    },
                    initialSharedPlaylist = targetSharedPlaylist,
                    onInitialSharedPlaylistHandled = { targetSharedPlaylist = null },
                    onNavigateToPlaylistGenerator = { navController.navigate(Screen.PlaylistGenerator.route) },
                    libraryResetTrigger = libraryResetTrigger
                )
            }
            composable(
                route = Screen.Equalizer.route,
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(200))
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(240, easing = FastOutLinearInEasing)
                    ) + fadeOut(animationSpec = tween(160))
                },
                popEnterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { -it / 3 },
                        animationSpec = tween(240, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(200))
                },
                popExitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(240, easing = FastOutLinearInEasing)
                    ) + fadeOut(animationSpec = tween(180))
                }
            ) {
                EqualizerRoute(
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.Diagnostics.route,
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(200))
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(240, easing = FastOutLinearInEasing)
                    ) + fadeOut(animationSpec = tween(160))
                },
                popEnterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { -it / 3 },
                        animationSpec = tween(240, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(200))
                },
                popExitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(240, easing = FastOutLinearInEasing)
                    ) + fadeOut(animationSpec = tween(180))
                }
            ) {
                DiagnosticsRoute(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.Radio.route,
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(200))
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(240, easing = FastOutLinearInEasing)
                    ) + fadeOut(animationSpec = tween(160))
                },
                popEnterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { -it / 3 },
                        animationSpec = tween(240, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(200))
                },
                popExitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(240, easing = FastOutLinearInEasing)
                    ) + fadeOut(animationSpec = tween(180))
                }
            ) {
                val song = targetRadioSong
                if (song != null) {
                    RadioRoute(
                        seedSong = song,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(
                route = Screen.PlaylistGenerator.route,
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(200))
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(240, easing = FastOutLinearInEasing)
                    ) + fadeOut(animationSpec = tween(160))
                },
                popEnterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { -it / 3 },
                        animationSpec = tween(240, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(200))
                },
                popExitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(240, easing = FastOutLinearInEasing)
                    ) + fadeOut(animationSpec = tween(180))
                }
            ) {
                PlaylistGeneratorRoute(
                    onBackClick = { navController.popBackStack() }
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
            },
            deepLinkTrack = targetDeepLinkTrack,
            onDeepLinkTrackHandled = { targetDeepLinkTrack = null },
            onNavigateToArtist = { artistName ->
                isPlayerExpanded = false
                targetSearchArtist = com.mousikify.core.model.cleanArtistName(artistName)
                navController.navigate(Screen.Search.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            onOpenRadio = { song ->
                targetRadioSong = song
                navController.navigate(Screen.Radio.route)
            },
            onNavigateToContext = { context ->
                isPlayerExpanded = false
                when (context.type) {
                    PlaybackContextType.MIX -> {
                        targetHomeMixId = context.id
                        navController.navigate(Screen.Home.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = false
                            }
                            launchSingleTop = true
                            restoreState = false
                        }
                    }
                    PlaybackContextType.ALBUM -> {
                        if (context.isLocalOrSaved) {
                            targetLibraryAlbumId = context.albumId
                            targetLibraryAlbumTitle = context.title
                            targetLibraryAlbumArtist = context.artistName
                            navController.navigate(Screen.Library.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        } else {
                            targetSearchAlbum = Pair(context.title, context.artistName.orEmpty())
                            navController.navigate(Screen.Search.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                    PlaybackContextType.PLAYLIST -> {
                        val isRadio = context.id?.startsWith("radio_") == true || (!context.isLocalOrSaved && context.title.endsWith("Radio"))
                        if (isRadio) {
                            val seedTitle = context.title.removeSuffix(" Radio").trim()
                            val seedKey = context.id?.removePrefix("radio_").orEmpty()
                            targetRadioSong = com.mousikify.core.model.Song(
                                id = -seedTitle.hashCode().toLong(),
                                contentUri = seedKey,
                                title = seedTitle,
                                artistId = 0L,
                                artistName = context.artistName.orEmpty(),
                                albumId = 0L,
                                albumName = "",
                                durationMs = 0L,
                                trackNumber = 0,
                                year = 0,
                                folderPath = "",
                                dateAdded = 0L,
                                dateModified = 0L,
                                artworkUri = context.artworkUri
                            )
                            navController.navigate(Screen.Radio.route) {
                                launchSingleTop = true
                            }
                        } else {
                            targetLibraryPlaylistId = context.playlistId ?: context.id?.toLongOrNull()
                            navController.navigate(Screen.Library.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                    PlaybackContextType.ARTIST -> {
                        targetSearchArtist = context.artistName ?: context.title
                        navController.navigate(Screen.Search.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                    else -> {
                        navController.navigate(Screen.Library.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            }
        )
    }
}
