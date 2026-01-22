package com.muufin.compose.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.muufin.compose.core.AuthManager
import com.muufin.compose.core.JellyfinRepository
import com.muufin.compose.core.JellyfinUrls
import com.muufin.compose.core.PlayerManager
import com.muufin.compose.model.dto.BaseItemDto
import com.muufin.compose.model.durationLabel
import com.muufin.compose.model.primaryImageTag
import com.muufin.compose.ui.components.PlayerUiState
import com.muufin.compose.ui.components.TrackRow
import kotlinx.coroutines.launch
import androidx.media3.session.MediaController
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.muufin.compose.ui.util.rememberMuufinHaptics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    repo: JellyfinRepository,
    albumId: String,
    controller: MediaController?,
    playerUi: PlayerUiState,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val haptics = rememberMuufinHaptics()
    val scope = rememberCoroutineScope()

    var album by remember { mutableStateOf<BaseItemDto?>(null) }
    var tracks by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(albumId) {
        isLoading = true
        error = null
        val a = runCatching { repo.getItem(albumId) }.getOrNull()
        val t = runCatching { repo.getAlbumTracks(albumId) }.getOrNull()
        album = a
        tracks = t.orEmpty()
        isLoading = false
        if (a == null) error = "Failed to load album"
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(album?.name ?: "Album") },
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
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
                return@Box
            }

            if (error != null) {
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
                return@Box
            }

            val headerAlbum = album
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    AlbumHeader(
                        album = headerAlbum,
                        trackCount = tracks.size,
                        onPlay = {
                            scope.launch {
                                if (tracks.isNotEmpty()) {
                                    PlayerManager.playQueue(tracks)
                                    onOpenPlayer()
                                }
                            }
                        },
                        onShuffle = {
                            scope.launch {
                                if (tracks.isNotEmpty()) {
                                    PlayerManager.playQueue(tracks.shuffled())
                                    onOpenPlayer()
                                }
                            }
                        },
                    )
                }

                itemsIndexed(tracks, key = { _, it -> it.id }) { index, item ->
                    TrackRow(
                        index = index,
                        title = item.name,
                        subtitle = item.artists.joinToString().ifBlank { item.album.orEmpty() },
                        duration = item.durationLabel(),
                        onClick = {
                            scope.launch {
                                PlayerManager.playQueue(tracks, startIndex = index)
                                onOpenPlayer()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumHeader(
    album: BaseItemDto?,
    trackCount: Int,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    val haptics = rememberMuufinHaptics()
    val s = AuthManager.state.value
    val art = remember(album?.id, album?.primaryImageTag(), s.baseUrl) {
        if (album == null || s.baseUrl.isBlank()) null
        else JellyfinUrls.itemImage(state = s, itemId = album.id, tag = album.primaryImageTag(), maxWidth = 768)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AsyncImage(
                model = art,
                contentDescription = null,
                modifier = Modifier
                    .size(120.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )

            Column(
                modifier = Modifier
                    .heightIn(min = 120.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(album?.name.orEmpty(), style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = album?.artists?.joinToString().orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                    Text(
                        text = "$trackCount tracks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            haptics.tap()
                            onPlay()
                        },
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Play")
                    }
                    FilledTonalButton(
                        onClick = {
                            haptics.toggle()
                            onShuffle()
                        },
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Shuffle")
                    }
                }
            }
        }
        HorizontalDivider()
    }
}


