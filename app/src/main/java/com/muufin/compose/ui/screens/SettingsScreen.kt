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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.OutlinedButton
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
import coil3.compose.AsyncImage
import com.muufin.compose.core.AuthManager
import com.muufin.compose.core.BuildInfo
import com.muufin.compose.core.JellyfinRepository
import com.muufin.compose.core.JellyfinUrls
import com.muufin.compose.core.SettingsManager
import com.muufin.compose.model.dto.PublicSystemInfoDto
import com.muufin.compose.ui.util.rememberMuufinHaptics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
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
    val privateMode by SettingsManager.enablePlaybackReporting.collectAsState()
    val defaultTab by SettingsManager.defaultLibraryTab.collectAsState()
    val libraryLayout by SettingsManager.libraryLayout.collectAsState()

    var userName by remember { mutableStateOf<String?>(null) }
    var serverInfo by remember { mutableStateOf<PublicSystemInfoDto?>(null) }
    LaunchedEffect(auth.userId, auth.accessToken, auth.baseUrl) {
        userName = runCatching { repo.getCurrentUser().name }.getOrNull()
        serverInfo = runCatching { repo.getPublicSystemInfo() }.getOrNull()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                    .verticalScroll(rememberScrollState())
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
                                Text("Private mode")
                                Text(
                                    "Hide what you're currently playing from your Jellyfin instance.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.size(12.dp))
                            Switch(
                                checked = !privateMode,
                                onCheckedChange = { enabled ->
                                    haptics.toggle()
                                    scope.launch { SettingsManager.setEnablePlaybackReporting(!enabled) }
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
                            Icon(Icons.Rounded.Brush, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Interface", style = MaterialTheme.typography.titleMedium)
                        }

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
                                    Triple("List", Icons.AutoMirrored.Rounded.ViewList, 1),
                                    Triple("Grid", Icons.Rounded.GridView, 0),
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
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Playback", style = MaterialTheme.typography.titleMedium)
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text("Player Mode")
                                Text(
                                    "How Muufin streams audio from your server.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ToggleButton(
                                    checked = !preferLossless,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            haptics.toggle()
                                            scope.launch { SettingsManager.setPreferLosslessDirectPlay(false) }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                                ) {
                                    Text("Compatibility", maxLines = 1)
                                }
                                ToggleButton(
                                    checked = preferLossless,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            haptics.toggle()
                                            scope.launch { SettingsManager.setPreferLosslessDirectPlay(true) }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                                ) {
                                    Text("Quality", maxLines = 1)
                                }
                            }

                            Text(
                                text = if (preferLossless)
                                    "Streams original files (FLAC, ALAC) without transcoding. Falls back to HLS if playback fails."
                                else
                                    "Transcodes audio to MP3 via HLS for maximum device compatibility.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                val context = LocalContext.current
                val appVersion = remember { BuildInfo.appVersion(context) }
                val serverLine = remember(serverInfo) {
                    listOfNotNull(serverInfo?.serverName, serverInfo?.version).joinToString(" · ")
                }

                val uriHandler = LocalUriHandler.current

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Muufin $appVersion",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (serverLine.isNotBlank()) {
                        Text(
                            text = serverLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Made with \uD83E\uDD0D by Rii",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = {
                                haptics.tap()
                                uriHandler.openUri("https://ko-fi.com/Rikkichy")
                            },
                        ) {
                            Text("Support project")
                        }
                        OutlinedButton(
                            onClick = {
                                haptics.tap()
                                uriHandler.openUri("https://github.com/rikkichy/Muufin")
                            },
                        ) {
                            Text("GitHub")
                        }
                    }
                }
            }
        }
    }
}
