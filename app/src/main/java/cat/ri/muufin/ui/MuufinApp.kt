package cat.ri.muufin.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cat.ri.muufin.core.AuthManager
import cat.ri.muufin.core.DownloadManager
import cat.ri.muufin.core.JellyfinRepository
import cat.ri.muufin.ui.components.NowPlayingBar
import cat.ri.muufin.ui.components.rememberMediaController
import cat.ri.muufin.ui.components.rememberPlayerUiState
import cat.ri.muufin.ui.screens.AlbumDetailScreen
import cat.ri.muufin.ui.screens.ArtistDetailScreen
import cat.ri.muufin.ui.screens.HomeScreen
import cat.ri.muufin.ui.screens.LoginScreen
import cat.ri.muufin.ui.screens.PlayerScreen
import cat.ri.muufin.ui.screens.DownloadsScreen
import cat.ri.muufin.ui.screens.PlaylistDetailScreen
import cat.ri.muufin.ui.screens.SettingsScreen
import cat.ri.muufin.ui.theme.MuufinTheme
import cat.ri.muufin.ui.util.rememberMuufinHaptics

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val ALBUM = "album"
    const val ARTIST = "artist"
    const val PLAYLIST = "playlist"
    const val SETTINGS = "settings"
    const val DOWNLOADS = "downloads"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuufinApp() {
    val haptics = rememberMuufinHaptics()
    val auth by AuthManager.state.collectAsState()
    val nav = rememberNavController()
    val repo = remember { JellyfinRepository() }

    
    
    
    
    
    val controller by rememberMediaController(enabled = auth.isSignedIn)
    val playerUi by rememberPlayerUiState(controller)


    var showPlayer by rememberSaveable { mutableStateOf(false) }

    
    LaunchedEffect(auth.isSignedIn) {
        if (!auth.isSignedIn) showPlayer = false
        if (auth.isSignedIn) DownloadManager.resumePendingDownloads()
    }

    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route?.substringBefore("/")

    val hideBottomChrome = currentRoute == Routes.LOGIN
    val showBottomChrome = auth.isSignedIn && !hideBottomChrome

    MuufinTheme {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                contentWindowInsets = WindowInsets(0),
                bottomBar = {
                    if (showBottomChrome) {
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentRoute == Routes.HOME,
                                onClick = {
                                    haptics.tap()
                                    if (currentRoute != Routes.HOME) {
                                        nav.navigate(Routes.HOME) {
                                            popUpTo(Routes.HOME) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                icon = { Icon(Icons.Rounded.MusicNote, contentDescription = null) },
                                label = { Text("Library") },
                            )
                            NavigationBarItem(
                                selected = currentRoute == Routes.SETTINGS,
                                onClick = {
                                    haptics.tap()
                                    if (currentRoute != Routes.SETTINGS) {
                                        nav.navigate(Routes.SETTINGS) {
                                            popUpTo(Routes.HOME)
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                                label = { Text("Settings") },
                            )
                        }
                    }
                },
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                NavHost(
                    navController = nav,
                    startDestination = if (auth.isSignedIn) Routes.HOME else Routes.LOGIN,
                    enterTransition = { fadeIn(tween(210, delayMillis = 90)) },
                    exitTransition = { fadeOut(tween(90)) },
                    popEnterTransition = { fadeIn(tween(210, delayMillis = 90)) },
                    popExitTransition = { fadeOut(tween(90)) },
                ) {
                    composable(Routes.LOGIN) {
                        LoginScreen(
                            onSignedIn = {
                                nav.navigate(Routes.HOME) {
                                    popUpTo(Routes.LOGIN) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(Routes.HOME) {
                        HomeScreen(
                            repo = repo,
                            onOpenAlbum = { nav.navigate("${Routes.ALBUM}/$it") },
                            onOpenArtist = { nav.navigate("${Routes.ARTIST}/$it") },
                            onOpenPlaylist = { nav.navigate("${Routes.PLAYLIST}/$it") },
                            onOpenPlayer = { showPlayer = true },
                            onOpenDownloads = { nav.navigate(Routes.DOWNLOADS) },
                        )
                    }

                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            repo = repo,
                            onBack = {
                                nav.navigate(Routes.HOME) {
                                    popUpTo(Routes.HOME) { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                            onSignedOut = {
                                nav.navigate(Routes.LOGIN) {
                                    popUpTo(Routes.HOME) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(
                        route = Routes.DOWNLOADS,
                        enterTransition = { slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300)) },
                        exitTransition = { fadeOut(tween(150)) },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { slideOutHorizontally(tween(300)) { it / 4 } + fadeOut(tween(300)) },
                    ) {
                        DownloadsScreen(
                            onBack = { nav.popBackStack() },
                            onOpenPlayer = { showPlayer = true },
                        )
                    }

                    composable(
                        route = "${Routes.ALBUM}/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.StringType }),
                        enterTransition = { slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300)) },
                        exitTransition = { fadeOut(tween(150)) },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { slideOutHorizontally(tween(300)) { it / 4 } + fadeOut(tween(300)) },
                    ) {
                        val id = it.arguments?.getString("id") ?: return@composable
                        AlbumDetailScreen(
                            repo = repo,
                            albumId = id,
                            controller = controller,
                            playerUi = playerUi,
                            onBack = { nav.popBackStack() },
                            onOpenPlayer = { showPlayer = true },
                        )
                    }

                    composable(
                        route = "${Routes.ARTIST}/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.StringType }),
                        enterTransition = { slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300)) },
                        exitTransition = { fadeOut(tween(150)) },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { slideOutHorizontally(tween(300)) { it / 4 } + fadeOut(tween(300)) },
                    ) {
                        val id = it.arguments?.getString("id") ?: return@composable
                        ArtistDetailScreen(
                            repo = repo,
                            artistId = id,
                            controller = controller,
                            playerUi = playerUi,
                            onBack = { nav.popBackStack() },
                            onOpenAlbum = { nav.navigate("${Routes.ALBUM}/$it") },
                            onOpenPlayer = { showPlayer = true },
                        )
                    }

                    composable(
                        route = "${Routes.PLAYLIST}/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.StringType }),
                        enterTransition = { slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300)) },
                        exitTransition = { fadeOut(tween(150)) },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { slideOutHorizontally(tween(300)) { it / 4 } + fadeOut(tween(300)) },
                    ) {
                        val id = it.arguments?.getString("id") ?: return@composable
                        PlaylistDetailScreen(
                            repo = repo,
                            playlistId = id,
                            controller = controller,
                            playerUi = playerUi,
                            onBack = { nav.popBackStack() },
                            onOpenPlayer = { showPlayer = true },
                        )
                    }
                }

                if (showBottomChrome && currentRoute != Routes.SETTINGS) {
                    NowPlayingBar(
                        controller = controller,
                        state = playerUi,
                        onOpenPlayer = { showPlayer = true },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
                }
            }


            
            if (auth.isSignedIn && showPlayer && controller != null) {
                val density = LocalDensity.current
                val sheetState = remember {
                    SheetState(
                        skipPartiallyExpanded = true,
                        initialValue = SheetValue.Expanded,
                        positionalThreshold = { with(density) { 56.dp.toPx() } },
                        velocityThreshold = { with(density) { 125.dp.toPx() } },
                    )
                }
                ModalBottomSheet(
                    onDismissRequest = { showPlayer = false },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    PlayerScreen(
                        controller = controller,
                        ui = playerUi,
                        onBack = { showPlayer = false },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
