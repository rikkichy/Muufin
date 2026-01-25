package com.muufin.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.muufin.compose.core.AuthManager
import com.muufin.compose.core.JellyfinRepository
import com.muufin.compose.core.JellyfinUrls
import com.muufin.compose.core.SettingsManager
import com.muufin.compose.ui.util.rememberMuufinHaptics
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    repo: JellyfinRepository,
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val auth by AuthManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    val haptics = rememberMuufinHaptics()

    val preferLossless by SettingsManager.preferLosslessDirectPlay.collectAsState()
    val playbackReporting by SettingsManager.enablePlaybackReporting.collectAsState()
    val defaultTab by SettingsManager.defaultLibraryTab.collectAsState()
    val libraryLayout by SettingsManager.libraryLayout.collectAsState()

    var userName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(auth.userId, auth.accessToken, auth.baseUrl) {
        userName = runCatching { repo.getCurrentUser().name }.getOrNull()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            haptics.tap()
                            onBack()
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Interface", style = MaterialTheme.typography.titleMedium)

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text("Default tab in Library")
                                Text(
                                    "Which tab will be shown on start up by default?",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            val sections = remember {
                                listOf(
                                    Triple("Playlists", Icons.AutoMirrored.Rounded.PlaylistPlay, 0),
                                    Triple("Albums", Icons.Rounded.Album, 1),
                                    Triple("Artists", Icons.Rounded.Groups, 2),
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                sections.forEachIndexed { index, (label, icon, value) ->
                                    ToggleButton(
                                        checked = defaultTab == value,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                haptics.toggle()
                                                scope.launch { SettingsManager.setDefaultLibraryTab(value) }
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shapes = when (index) {
                                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                            sections.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                        },
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                        ) {
                                            Icon(icon, contentDescription = null)
                                            Spacer(Modifier.size(8.dp))
                                            Text(label, maxLines = 1)
                                        }
                                    }
                                }
                            }

                            HorizontalDivider()

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text("Library layout")
                                Text(
                                    "Default layout for Playlists and Albums.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            val layouts = remember {
                                listOf(
                                    Triple("Grid", Icons.Rounded.GridView, 0),
                                    Triple("List", Icons.AutoMirrored.Rounded.ViewList, 1),
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                layouts.forEachIndexed { index, (label, icon, value) ->
                                    ToggleButton(
                                        checked = libraryLayout == value,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                haptics.toggle()
                                                scope.launch { SettingsManager.setLibraryLayout(value) }
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shapes = when (index) {
                                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                            layouts.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                        },
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                        ) {
                                            Icon(icon, contentDescription = null)
                                            Spacer(Modifier.size(8.dp))
                                            Text(label, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Tune, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Playback", style = MaterialTheme.typography.titleMedium)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Lossless DirectPlay")
                                Text(
                                    "Disables compatibility layer so you can play FLAC/ALAC without transcoding and compression. If something will fail, Muufin will try use HLS transcoding.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.size(12.dp))
                            Switch(
                                checked = preferLossless,
                                onCheckedChange = { enabled ->
                                    haptics.toggle()
                                    scope.launch { SettingsManager.setPreferLosslessDirectPlay(enabled) }
                                }
                            )
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Account", style = MaterialTheme.typography.titleMedium)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            val avatarUrl = remember(auth.baseUrl, auth.userId) {
                                if (auth.baseUrl.isNotBlank() && auth.userId.isNotBlank()) {
                                    JellyfinUrls.userImage(auth, auth.userId, maxWidth = 128)
                                } else null
                            }

                            if (avatarUrl != null) {
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape),
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(userName ?: "Signed in", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    auth.baseUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Playback reporting")
                                Text(
                                    "Muufin will tell your Jellyfin instance what you're playing at the moment. Useful for such plugins as LastFM.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.size(12.dp))
                            Switch(
                                checked = playbackReporting,
                                onCheckedChange = { enabled ->
                                    haptics.toggle()
                                    scope.launch { SettingsManager.setEnablePlaybackReporting(enabled) }
                                }
                            )
                        }

                        HorizontalDivider()

                        Button(
                            onClick = {
                                haptics.tap()
                                scope.launch {
                                    AuthManager.signOut()
                                    onSignedOut()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Sign out")
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
