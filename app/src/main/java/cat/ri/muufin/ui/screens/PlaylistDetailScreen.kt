package cat.ri.muufin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest


import cat.ri.muufin.core.AuthManager
import cat.ri.muufin.core.DownloadManager
import cat.ri.muufin.core.JellyfinRepository
import cat.ri.muufin.core.JellyfinUrls
import cat.ri.muufin.core.PlayerManager
import cat.ri.muufin.core.SettingsManager
import cat.ri.muufin.model.dto.BaseItemDto
import cat.ri.muufin.model.dto.DownloadTaskStatus
import cat.ri.muufin.model.dto.toBaseItemDto
import cat.ri.muufin.model.durationLabel
import cat.ri.muufin.model.primaryImageTag
import cat.ri.muufin.ui.components.PlayerUiState
import cat.ri.muufin.ui.components.TrackDownloadState
import cat.ri.muufin.ui.components.TrackRow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.media3.session.MediaController

import cat.ri.muufin.ui.util.rememberMuufinHaptics

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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

    val offlineMode by SettingsManager.offlineMode.collectAsState()
    val cachedPlaylist = remember { repo.getCachedItem(playlistId) }
    val cachedTracks = remember { repo.getCachedPlaylistTracks(playlistId) }
    var playlist by remember { mutableStateOf(cachedPlaylist) }
    var tracks by remember { mutableStateOf(cachedTracks ?: emptyList()) }
    var isLoading by remember { mutableStateOf(cachedTracks == null) }
    var error by remember { mutableStateOf<String?>(null) }

    var query by remember { mutableStateOf("") }

    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext

    val listState = rememberLazyListState()
    val indexById = remember(tracks) { tracks.withIndex().associate { (i, t) -> t.id to i } }
    val displayTracks by remember {
        derivedStateOf {
            if (query.isBlank()) tracks else tracks.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.artists.any { a -> a.contains(query, ignoreCase = true) }
            }
        }
    }

    // Index of the playing track in displayTracks (+1 for header item)
    val mediaId = playerUi.mediaId
    val playingDisplayIndex by remember {
        derivedStateOf {
            displayTracks.indexOfFirst { it.id == mediaId }.takeIf { it >= 0 }?.let { it + 1 }
        }
    }
    val playingIndexState = rememberUpdatedState(playingDisplayIndex)
    val showScrollToPlaying by remember {
        derivedStateOf {
            val idx = playingIndexState.value ?: return@derivedStateOf false
            val visible = listState.layoutInfo.visibleItemsInfo
            visible.none { it.index == idx }
        }
    }

    LaunchedEffect(playlistId, offlineMode) {
        if (offlineMode) {
            // Offline: build tracks from cached playlist metadata + download catalog
            val cachedPl = DownloadManager.cachedPlaylists.value.find { it.id == playlistId }
            val catalog = DownloadManager.catalog.value
            val downloadedIds = DownloadManager.downloadedIds.value
            if (cachedPl != null) {
                val trackIdSet = cachedPl.trackIds.toSet()
                val offlineTracks = catalog.tracks
                    .filter { it.id in trackIdSet && it.id in downloadedIds }
                    .map { it.toBaseItemDto() }
                tracks = offlineTracks
                if (playlist == null) {
                    playlist = BaseItemDto(
                        id = cachedPl.id,
                        name = cachedPl.name,
                        type = "Playlist",
                        imageTags = cachedPl.imageTags,
                    )
                }
            }
            isLoading = false
            return@LaunchedEffect
        }
        val hasCached = cachedTracks != null
        if (!hasCached) isLoading = true
        error = null
        coroutineScope {
            val pResult = async { runCatching { repo.getItem(playlistId) } }
            val tResult = async { runCatching { repo.getPlaylistTracks(playlistId) } }
            val pRes = pResult.await()
            val tRes = tResult.await()
            val p = pRes.getOrNull()
            val t = tRes.getOrNull()
            if (p != null) playlist = p
            if (!t.isNullOrEmpty()) {
                tracks = t
                // Cache playlist metadata for offline mode
                if (p != null) DownloadManager.cachePlaylist(p, t.map { it.id })
                // Prefetch first visible track images into memory cache BEFORE showing UI
                val loader = SingletonImageLoader.get(appContext)
                val s = AuthManager.state.value
                if (s.baseUrl.isNotBlank()) {
                    t.take(15).map { item ->
                        val primaryTag = item.primaryImageTag()
                        val coverItemId = if (!primaryTag.isNullOrBlank()) item.id else item.albumId ?: item.id
                        val url = JellyfinUrls.itemImage(
                            state = s,
                            itemId = coverItemId,
                            tag = if (coverItemId == item.id) primaryTag else null,
                            maxWidth = 64,
                        )
                        async {
                            runCatching {
                                loader.execute(
                                    ImageRequest.Builder(appContext).data(url).size(128).build()
                                )
                            }
                        }
                    }.awaitAll()
                }
            }
            isLoading = false
            if (p == null && !hasCached) {
                error = pRes.exceptionOrNull()?.let { friendlyError(it) } ?: "Failed to load playlist"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Oh..", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(onClick = {
                        scope.launch {
                            isLoading = true
                            error = null
                            val pRes = runCatching { repo.getItem(playlistId) }
                            val tRes = runCatching { repo.getPlaylistTracks(playlistId) }
                            val p = pRes.getOrNull()
                            val t = tRes.getOrNull()
                            if (p != null) playlist = p
                            if (!t.isNullOrEmpty()) tracks = t
                            isLoading = false
                            if (p == null) error = pRes.exceptionOrNull()?.let { friendlyError(it) } ?: "Failed to load playlist"
                        }
                    }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Try again")
                    }
                }
                return@Box
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Search tracks...") },
                        shape = RoundedCornerShape(20.dp),
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = if (query.isNotEmpty()) {
                            { IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, null) } }
                        } else null,
                        singleLine = true,
                    )

                    val authState = AuthManager.state.value
                    val downloadedIds by DownloadManager.downloadedIds.collectAsState()
                    val downloadQueue by DownloadManager.queue.collectAsState()
                    val offlineMode by SettingsManager.offlineMode.collectAsState()

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 100.dp),
                ) {
                    item(contentType = "playlist_header") {
                        val plAllDownloaded = tracks.isNotEmpty() && tracks.all { it.id in downloadedIds }
                        val plAnyDownloading = tracks.any { t ->
                            downloadQueue.any { it.trackId == t.id && (it.status == DownloadTaskStatus.PENDING || it.status == DownloadTaskStatus.DOWNLOADING) }
                        }
                        PlaylistHeader(
                            playlist = playlist,
                            trackCount = displayTracks.size,
                            onPlay = {
                                scope.launch {
                                    if (tracks.isNotEmpty()) {
                                        PlayerManager.setQueueSource(playlistId)
                                        if (PlayerManager.playQueue(tracks)) onOpenPlayer()
                                    }
                                }
                            },
                            onShuffle = {
                                scope.launch {
                                    if (tracks.isNotEmpty()) {
                                        PlayerManager.setQueueSource(playlistId)
                                        if (PlayerManager.playQueue(tracks.shuffled())) onOpenPlayer()
                                    }
                                }
                            },
                            onDownload = if (!offlineMode) { { if (!plAllDownloaded) DownloadManager.enqueueAll(tracks) } } else null,
                            allDownloaded = plAllDownloaded,
                            anyDownloading = plAnyDownloading,
                        )
                    }

                    itemsIndexed(displayTracks, key = { _, it -> it.id }, contentType = { _, _ -> "track_row" }) { _, item ->
                        val originalIndex = indexById[item.id] ?: 0
                        val primaryTag = item.primaryImageTag()
                        val coverItemId = remember(item.id, item.albumId, primaryTag) {
                            if (!primaryTag.isNullOrBlank()) item.id else item.albumId ?: item.id
                        }

                        val coverUrl: Any? = remember(coverItemId, primaryTag, authState.baseUrl, offlineMode) {
                            if (offlineMode) {
                                val file = java.io.File(DownloadManager.getArtworkDir(), "${item.id}.jpg")
                                if (file.exists()) android.net.Uri.fromFile(file) else null
                            } else if (authState.baseUrl.isBlank()) null
                            else JellyfinUrls.itemImage(
                                state = authState,
                                itemId = coverItemId,
                                tag = if (coverItemId == item.id) primaryTag else null,
                                maxWidth = 64,
                            )
                        }
                        val dlState = when {
                            item.id in downloadedIds -> TrackDownloadState.DOWNLOADED
                            downloadQueue.any { it.trackId == item.id && it.status == DownloadTaskStatus.DOWNLOADING } -> TrackDownloadState.DOWNLOADING
                            downloadQueue.any { it.trackId == item.id } -> TrackDownloadState.PENDING
                            else -> TrackDownloadState.NONE
                        }
                        TrackRow(
                            index = originalIndex,
                            title = item.name,
                            subtitle = item.artists.joinToString().ifBlank { item.album.orEmpty() },
                            duration = item.durationLabel(),
                            isPlaying = item.id == mediaId,
                            leadingImageUrl = coverUrl,
                            onClick = {
                                scope.launch {
                                    PlayerManager.setQueueSource(playlistId)
                                    if (PlayerManager.playQueue(tracks, startIndex = originalIndex))
                                        onOpenPlayer()
                                }
                            },
                            downloadState = if (offlineMode) TrackDownloadState.NONE else dlState,
                            onDownloadClick = if (!offlineMode && dlState == TrackDownloadState.NONE) {
                                { DownloadManager.enqueue(item) }
                            } else null,
                            enabled = !offlineMode || item.id in downloadedIds,
                        )
                    }
                }
                }

                AnimatedVisibility(
                    visible = showScrollToPlaying,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 100.dp),
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            val idx = playingDisplayIndex ?: return@SmallFloatingActionButton
                            scope.launch { listState.animateScrollToItem(idx) }
                        },
                    ) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = "Scroll to playing track")
                    }
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
    onDownload: (() -> Unit)? = null,
    allDownloaded: Boolean = false,
    anyDownloading: Boolean = false,
) {
    val haptics = rememberMuufinHaptics()
    val s = AuthManager.state.value
    val offlineMode by SettingsManager.offlineMode.collectAsState()
    val art: Any? = remember(playlist?.id, playlist?.primaryImageTag(), s.baseUrl, offlineMode) {
        if (offlineMode) {
            // Use cached playlist cover from disk
            val artDir = DownloadManager.getArtworkDir()
            val file = java.io.File(artDir, "${playlist?.id}_playlist.jpg")
            if (file.exists()) android.net.Uri.fromFile(file) else null
        } else {
            if (playlist == null || s.baseUrl.isBlank()) null
            else JellyfinUrls.itemImage(state = s, itemId = playlist.id, tag = playlist.primaryImageTag(), maxWidth = 768)
        }
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
                            text = playlist.name,
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
        if (onDownload != null) {
            FilledTonalButton(
                onClick = {
                    haptics.tap()
                    onDownload()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = when {
                        allDownloaded -> Icons.Rounded.DownloadDone
                        anyDownloading -> Icons.Rounded.Downloading
                        else -> Icons.Rounded.Download
                    },
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        allDownloaded -> "Downloaded"
                        anyDownloading -> "Downloading..."
                        else -> "Download"
                    }
                )
            }
        }
        HorizontalDivider()
    }
}
