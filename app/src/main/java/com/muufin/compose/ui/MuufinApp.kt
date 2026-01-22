package com.muufin.compose.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.muufin.compose.core.AuthManager
import com.muufin.compose.core.JellyfinRepository
import com.muufin.compose.ui.components.NowPlayingBar
import com.muufin.compose.ui.components.rememberMediaController
import com.muufin.compose.ui.components.rememberPlayerUiState
import com.muufin.compose.ui.screens.AlbumDetailScreen
import com.muufin.compose.ui.screens.ArtistDetailScreen
import com.muufin.compose.ui.screens.HomeScreen
import com.muufin.compose.ui.screens.LoginScreen
import com.muufin.compose.ui.screens.PlayerScreen
import com.muufin.compose.ui.screens.PlaylistDetailScreen
import com.muufin.compose.ui.screens.SettingsScreen
import com.muufin.compose.ui.theme.MuufinTheme
import com.muufin.compose.ui.util.rememberMuufinHaptics

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val ALBUM = "album"
    const val ARTIST = "artist"
    const val PLAYLIST = "playlist"
    const val SETTINGS = "settings"
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

    val homeTab = rememberSaveable { mutableIntStateOf(0) }

    var showPlayer by rememberSaveable { mutableStateOf(false) }

    
    LaunchedEffect(auth.isSignedIn) {
        if (!auth.isSignedIn) showPlayer = false
    }

    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route?.substringBefore("/")

    
    
    val hideBottomChrome = currentRoute == Routes.LOGIN || currentRoute == Routes.SETTINGS
    val showBottomChrome = auth.isSignedIn && !hideBottomChrome

    MuufinTheme {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                contentWindowInsets = WindowInsets(0),
                bottomBar = {
                    if (showBottomChrome) {
                        Column {
                            NowPlayingBar(
                                controller = controller,
                                state = playerUi,
                                onOpenPlayer = { showPlayer = true },
                            )

                            
                            
                            NavigationBar {
                                NavigationBarItem(
                                    selected = homeTab.intValue == 0,
                                    onClick = {
                                        haptics.tap()
                                        homeTab.intValue = 0
                                        if (currentRoute != Routes.HOME) {
                                            nav.navigate(Routes.HOME) {
                                                popUpTo(Routes.HOME) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    icon = { Icon(Icons.Rounded.MusicNote, contentDescription = null) },
                                    label = { Text("Library") },
                                )
                                NavigationBarItem(
                                    selected = homeTab.intValue == 1,
                                    onClick = {
                                        haptics.tap()
                                        homeTab.intValue = 1
                                        if (currentRoute != Routes.HOME) {
                                            nav.navigate(Routes.HOME) {
                                                popUpTo(Routes.HOME) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    icon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                    label = { Text("Search") },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                NavHost(
                    navController = nav,
                    startDestination = if (auth.isSignedIn) Routes.HOME else Routes.LOGIN,
                    modifier = Modifier.padding(padding),
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
                            tab = homeTab.intValue,
                            onOpenAlbum = { nav.navigate("${Routes.ALBUM}/$it") },
                            onOpenArtist = { nav.navigate("${Routes.ARTIST}/$it") },
                            onOpenPlaylist = { nav.navigate("${Routes.PLAYLIST}/$it") },
                            onOpenPlayer = { showPlayer = true },
                            onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                        )
                    }

                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            repo = repo,
                            onBack = { nav.popBackStack() },
                            onSignedOut = {
                                nav.navigate(Routes.LOGIN) {
                                    popUpTo(Routes.HOME) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(
                        route = "${Routes.ALBUM}/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.StringType })
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
                        arguments = listOf(navArgument("id") { type = NavType.StringType })
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
                        arguments = listOf(navArgument("id") { type = NavType.StringType })
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
            }

            
            
            if (auth.isSignedIn && showPlayer) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { showPlayer = false },
                    sheetState = sheetState,
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
