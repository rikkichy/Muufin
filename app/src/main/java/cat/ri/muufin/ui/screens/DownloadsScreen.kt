package cat.ri.muufin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cat.ri.muufin.core.AuthManager
import cat.ri.muufin.core.DownloadManager
import cat.ri.muufin.core.JellyfinUrls
import cat.ri.muufin.core.PlayerManager
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

    // Group downloaded tracks by album
    val albumGroups = remember(catalog) {
        catalog.tracks
            .groupBy { it.albumId ?: "unknown" }
            .toList()
            .sortedByDescending { (_, tracks) -> tracks.maxOf { it.downloadedAtEpochMs } }
    }

    val pendingCount = queue.count { it.status == DownloadTaskStatus.PENDING }
    val failedTasks = remember(queue) { queue.filter { it.status == DownloadTaskStatus.FAILED } }

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
            if (catalog.tracks.isEmpty() && queue.isEmpty() && failedTasks.isEmpty()) {
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
                // Active downloads banner
                if (activeDownload != null || pendingCount > 0) {
                    item(contentType = "active_banner") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    if (isPaused) Icons.Rounded.Pause else Icons.Rounded.Downloading,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    val active = activeDownload
                                    if (isPaused) {
                                        Text(
                                            "Paused" + if (active != null) " — ${active.name}" else "",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    } else if (active != null) {
                                        Text(
                                            "${active.name} — ${active.progressPercent}%",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                    if (pendingCount > 0) {
                                        Text(
                                            "$pendingCount more in queue",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                        )
                                    }
                                }
                                IconButton(onClick = {
                                    haptics.tap()
                                    if (isPaused) DownloadManager.resumeDownloads()
                                    else DownloadManager.pauseDownloads()
                                }) {
                                    Icon(
                                        if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                        contentDescription = if (isPaused) "Resume" else "Pause",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                                TextButton(onClick = {
                                    haptics.tap()
                                    DownloadManager.cancelAll()
                                }) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }

                // Failed downloads
                if (failedTasks.isNotEmpty()) {
                    item(contentType = "failed_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
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
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    Text(
                                        task.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                    )
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

                // Downloaded tracks grouped by album
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
                                    // Build simple BaseItemDto for playback from downloaded tracks
                                    val albumTracks = tracks.map { it.toBaseItemDto() }
                                    val startIndex = albumTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                                    PlayerManager.playQueue(albumTracks, startIndex = startIndex)
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(albumName, style = MaterialTheme.typography.titleSmall)
            Text(
                "$artistName · $trackCount tracks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Rounded.DownloadDone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
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
                Text(
                    track.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
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
