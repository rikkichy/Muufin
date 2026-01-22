package com.muufin.compose.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
fun PlaylistDetailScreen(
    repo: JellyfinRepository,
    playlistId: String,
    controller: MediaController?,
    playerUi: PlayerUiState,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val haptics = rememberMuufinHaptics()
    val scope = rememberCoroutineScope()

    var playlist by remember { mutableStateOf<BaseItemDto?>(null) }
    var tracks by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(playlistId) {
        isLoading = true
        error = null
        val p = runCatching { repo.getItem(playlistId) }.getOrNull()
        val t = runCatching { repo.getPlaylistTracks(playlistId) }.getOrNull()
        playlist = p
        tracks = t.orEmpty()
        isLoading = false
        if (p == null) error = "Failed to load playlist"
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val showTopTitle by remember {
        derivedStateOf { scrollBehavior.state.collapsedFraction > 0.6f }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    AnimatedContent(
                        targetState = showTopTitle,
                        transitionSpec = {
                            (fadeIn() + slideInVertically { it / 3 }) togetherWith
                                (fadeOut() + slideOutVertically { -it / 3 })
                        },
                        label = "playlistTopTitle",
                    ) { show ->
                        if (show) {
                            Text(playlist?.name ?: "Playlist")
                        } else {
                            Spacer(Modifier.height(0.dp))
                        }
                    }
                },
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

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    PlaylistHeader(
                        playlist = playlist,
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
                    val s = AuthManager.state.value
                    val primaryTag = item.primaryImageTag()
                    val coverItemId = remember(item.id, item.albumId, primaryTag) {
                        
                        
                        if (!primaryTag.isNullOrBlank()) item.id else item.albumId ?: item.id
                    }

                    val coverUrl = remember(coverItemId, primaryTag, s.baseUrl) {
                        if (s.baseUrl.isBlank()) null
                        else JellyfinUrls.itemImage(
                            state = s,
                            itemId = coverItemId,
                            
                            tag = if (coverItemId == item.id) primaryTag else null,
                            maxWidth = 256,
                        )
                    }
                    TrackRow(
                        index = index,
                        title = item.name,
                        subtitle = item.artists.joinToString().ifBlank { item.album.orEmpty() },
                        duration = item.durationLabel(),
                        leadingImageUrl = coverUrl,
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
private fun PlaylistHeader(
    playlist: BaseItemDto?,
    trackCount: Int,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    val haptics = rememberMuufinHaptics()
    val s = AuthManager.state.value
    val art = remember(playlist?.id, playlist?.primaryImageTag(), s.baseUrl) {
        if (playlist == null || s.baseUrl.isBlank()) null
        else JellyfinUrls.itemImage(state = s, itemId = playlist.id, tag = playlist.primaryImageTag(), maxWidth = 768)
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
                    if (!playlist?.name.isNullOrBlank()) {
                        Text(
                            text = playlist?.name.orEmpty(),
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 2,
                        )
                    }
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
