package cat.ri.muufin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import cat.ri.muufin.core.AuthManager
import cat.ri.muufin.core.DownloadManager
import cat.ri.muufin.core.JellyfinUrls
import cat.ri.muufin.core.PlayerManager
import cat.ri.muufin.model.dto.DownloadTask
import cat.ri.muufin.model.dto.DownloadTaskStatus
import cat.ri.muufin.model.dto.DownloadedTrack
import cat.ri.muufin.ui.util.rememberMuufinHaptics
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val haptics = rememberMuufinHaptics()
    val scope = rememberCoroutineScope()
    val catalog by DownloadManager.catalog.collectAsState()
    val queue by DownloadManager.queue.collectAsState()
    val activeDownload by DownloadManager.activeDownload.collectAsState()
    val isPaused by DownloadManager.paused.collectAsState()

    var showClearDialog by remember { mutableStateOf(false) }

    val albumGroups = remember(catalog) {
        catalog.tracks
            .groupBy { it.albumId ?: "unknown" }
            .toList()
            .sortedByDescending { (_, tracks) -> tracks.maxOf { it.downloadedAtEpochMs } }
    }

    val activeTasks = remember(queue) {
        queue.filter { it.status == DownloadTaskStatus.PENDING || it.status == DownloadTaskStatus.DOWNLOADING }
    }
    val failedTasks = remember(queue) {
        queue.filter { it.status == DownloadTaskStatus.FAILED }
    }
    val hasQueue = activeTasks.isNotEmpty() || failedTasks.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = {
                        haptics.tap()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (catalog.tracks.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove all")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (catalog.tracks.isEmpty() && !hasQueue) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("No downloads yet", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Download tracks from albums or playlists to listen offline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Box
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp),
            ) {
                // --- Active queue section ---
                if (activeTasks.isNotEmpty()) {
                    item(key = "queue_header", contentType = "section_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    if (isPaused) Icons.Rounded.Pause else Icons.Rounded.Downloading,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    if (isPaused) "Paused · ${activeTasks.size} tracks"
                                    else "Downloading · ${activeTasks.size} tracks",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = {
                                        haptics.tap()
                                        if (isPaused) DownloadManager.resumeDownloads()
                                        else DownloadManager.pauseDownloads()
                                    },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                        contentDescription = if (isPaused) "Resume" else "Pause",
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        haptics.tap()
                                        DownloadManager.cancelAll()
                                    },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = "Cancel all",
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }

                    items(
                        items = activeTasks,
                        key = { "q_${it.trackId}" },
                        contentType = { "queue_track" },
                    ) { task ->
                        QueueTrackRow(
                            task = task,
                            activeDownload = activeDownload,
                            isPaused = isPaused,
                            onCancel = { DownloadManager.cancelDownload(task.trackId) },
                        )
                    }
                }

                // --- Failed section ---
                if (failedTasks.isNotEmpty()) {
                    item(key = "failed_header", contentType = "section_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    "${failedTasks.size} failed",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = {
                                    haptics.tap()
                                    DownloadManager.retryAllFailed()
                                }) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Retry all")
                                }
                                TextButton(onClick = {
                                    haptics.tap()
                                    DownloadManager.clearFailed()
                                }) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }

                    items(
                        items = failedTasks,
                        key = { "failed_${it.trackId}" },
                        contentType = { "failed_track" },
                    ) { task ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                haptics.tap()
                                DownloadManager.retryDownload(task.trackId)
                            },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(task.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                    Text(
                                        task.errorMessage ?: "Download failed",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 1,
                                    )
                                }
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = "Retry",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }

                // --- Divider between queue and completed ---
                if (hasQueue && albumGroups.isNotEmpty()) {
                    item(key = "divider", contentType = "divider") {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }

                // --- Completed downloads grouped by album ---
                if (albumGroups.isNotEmpty()) {
                    item(key = "completed_header", contentType = "section_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Rounded.DownloadDone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                "${catalog.tracks.size} tracks downloaded",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                }

                albumGroups.forEach { (albumId, tracks) ->
                    val albumName = tracks.firstOrNull()?.album ?: "Unknown Album"
                    val artistName = tracks.firstOrNull()?.artists?.joinToString().orEmpty()

                    item(key = "header_$albumId", contentType = "album_group_header") {
                        AlbumGroupHeader(
                            albumName = albumName,
                            artistName = artistName,
                            trackCount = tracks.size,
                            albumId = albumId,
                        )
                    }

                    items(
                        items = tracks.sortedBy { it.name },
                        key = { "dl_${it.id}" },
                        contentType = { "downloaded_track" },
                    ) { track ->
                        DownloadedTrackRow(
                            track = track,
                            onPlay = {
                                scope.launch {
                                    val albumTracks = tracks.map { it.toBaseItemDto() }
                                    val startIndex = albumTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                                    if (PlayerManager.playQueue(albumTracks, startIndex = startIndex))
                                        onOpenPlayer()
                                }
                            },
                            onRemove = { DownloadManager.removeDownload(track.id) },
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Remove all downloads?") },
            text = { Text("This will delete all downloaded tracks from your device.") },
            confirmButton = {
                TextButton(onClick = {
                    DownloadManager.removeAll()
                    showClearDialog = false
                }) {
                    Text("Remove all")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun QueueTrackRow(
    task: DownloadTask,
    activeDownload: DownloadTask?,
    isPaused: Boolean,
    onCancel: () -> Unit,
) {
    val haptics = rememberMuufinHaptics()
    val isActive = activeDownload?.trackId == task.trackId
    val progress = if (isActive) activeDownload?.progressPercent ?: 0 else 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Status icon
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (isActive && !isPaused) {
                CircularProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else if (isPaused && isActive) {
                Icon(
                    Icons.Rounded.Pause,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Icon(
                    Icons.Rounded.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(task.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(
                when {
                    isActive && isPaused -> "Paused"
                    isActive -> "$progress%"
                    else -> task.artists.joinToString().ifBlank { task.album.orEmpty() }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        IconButton(
            onClick = {
                haptics.tap()
                onCancel()
            },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Cancel",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AlbumGroupHeader(
    albumName: String,
    artistName: String,
    trackCount: Int,
    albumId: String,
) {
    val auth = AuthManager.state.value
    val artworkUrl = remember(albumId, auth.baseUrl) {
        if (auth.baseUrl.isBlank()) null
        else JellyfinUrls.itemImage(state = auth, itemId = albumId, maxWidth = 128)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(albumName, style = MaterialTheme.typography.labelLarge)
            Text(
                "$artistName · $trackCount tracks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DownloadedTrackRow(
    track: DownloadedTrack,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
) {
    val haptics = rememberMuufinHaptics()
    val durationLabel = remember(track.runTimeTicks) {
        track.runTimeTicks?.let { ticks ->
            val totalSeconds = ticks / 10_000_000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            "%d:%02d".format(minutes, seconds)
        } ?: ""
    }

    Surface(
        onClick = {
            haptics.tap()
            onPlay()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(track.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(
                    track.artists.joinToString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (durationLabel.isNotBlank()) {
                Text(
                    durationLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = {
                    haptics.tap()
                    onRemove()
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Remove download",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun DownloadedTrack.toBaseItemDto(): cat.ri.muufin.model.dto.BaseItemDto {
    return cat.ri.muufin.model.dto.BaseItemDto(
        id = id,
        name = name,
        album = album,
        albumId = albumId,
        artists = artists,
        runTimeTicks = runTimeTicks,
        imageTags = imageTags,
    )
}
