package cat.ri.muufin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import cat.ri.muufin.core.AuthManager
import cat.ri.muufin.core.DownloadManager
import cat.ri.muufin.core.JellyfinRepository
import cat.ri.muufin.core.JellyfinUrls
import cat.ri.muufin.core.PlayerManager
import cat.ri.muufin.core.SettingsManager
import cat.ri.muufin.model.dto.BaseItemDto
import cat.ri.muufin.model.dto.DownloadTaskStatus
import cat.ri.muufin.model.durationLabel
import cat.ri.muufin.model.primaryImageTag
import cat.ri.muufin.ui.components.PlayerUiState
import cat.ri.muufin.ui.components.TrackDownloadState
import cat.ri.muufin.ui.components.TrackRow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.media3.session.MediaController
import androidx.compose.ui.input.nestedscroll.nestedScroll
import cat.ri.muufin.ui.util.rememberMuufinHaptics

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

    val cachedAlbum = remember { repo.getCachedItem(albumId) }
    val cachedTracks = remember { repo.getCachedAlbumTracks(albumId) }
    var album by remember { mutableStateOf(cachedAlbum) }
    var tracks by remember { mutableStateOf(cachedTracks ?: emptyList()) }
    var isLoading by remember { mutableStateOf(cachedTracks == null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(albumId) {
        val hasCached = cachedTracks != null
        if (!hasCached) isLoading = true
        error = null
        coroutineScope {
            val aDeferred = async { runCatching { repo.getItem(albumId) }.getOrNull() }
            val tDeferred = async { runCatching { repo.getAlbumTracks(albumId) }.getOrNull() }
            val a = aDeferred.await()
            val t = tDeferred.await()
            if (a != null) album = a
            if (!t.isNullOrEmpty()) tracks = t
            isLoading = false
            if (a == null && !hasCached) error = "Failed to load album"
        }
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
            val downloadedIds by DownloadManager.downloadedIds.collectAsState()
            val downloadQueue by DownloadManager.queue.collectAsState()
            val offlineMode by SettingsManager.offlineMode.collectAsState()

            val allDownloaded = tracks.isNotEmpty() && tracks.all { it.id in downloadedIds }
            val anyDownloading = tracks.any { t ->
                downloadQueue.any { it.trackId == t.id && (it.status == DownloadTaskStatus.PENDING || it.status == DownloadTaskStatus.DOWNLOADING) }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp),
            ) {
                item(contentType = "album_header") {
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
                        onDownload = {
                            if (!allDownloaded) DownloadManager.enqueueAll(tracks)
                        },
                        allDownloaded = allDownloaded,
                        anyDownloading = anyDownloading,
                    )
                }

                itemsIndexed(tracks, key = { _, it -> it.id }, contentType = { _, _ -> "track_row" }) { index, item ->
                    val dlState = when {
                        item.id in downloadedIds -> TrackDownloadState.DOWNLOADED
                        downloadQueue.any { it.trackId == item.id && it.status == DownloadTaskStatus.DOWNLOADING } -> TrackDownloadState.DOWNLOADING
                        downloadQueue.any { it.trackId == item.id } -> TrackDownloadState.PENDING
                        else -> TrackDownloadState.NONE
                    }
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
                        downloadState = dlState,
                        onDownloadClick = if (dlState == TrackDownloadState.NONE) {
                            { DownloadManager.enqueue(item) }
                        } else null,
                        enabled = !offlineMode || item.id in downloadedIds,
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
    onDownload: () -> Unit = {},
    allDownloaded: Boolean = false,
    anyDownloading: Boolean = false,
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
        HorizontalDivider()
    }
}

