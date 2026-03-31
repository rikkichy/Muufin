package cat.ri.muufin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import cat.ri.muufin.core.AuthManager
import cat.ri.muufin.core.DownloadManager
import cat.ri.muufin.core.JellyfinRepository
import cat.ri.muufin.core.JellyfinUrls
import cat.ri.muufin.core.SettingsManager
import cat.ri.muufin.model.dto.BaseItemDto
import cat.ri.muufin.model.primaryImageTag
import cat.ri.muufin.model.subtitle
import cat.ri.muufin.ui.components.ItemCard
import cat.ri.muufin.ui.components.PlayerUiState
import androidx.media3.session.MediaController
import androidx.compose.ui.input.nestedscroll.nestedScroll
import cat.ri.muufin.ui.util.rememberMuufinHaptics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    repo: JellyfinRepository,
    artistId: String,
    controller: MediaController?,
    playerUi: PlayerUiState,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val haptics = rememberMuufinHaptics()
    val scope = rememberCoroutineScope()
    val offlineMode by SettingsManager.offlineMode.collectAsState()
    var artist by remember { mutableStateOf<BaseItemDto?>(null) }
    var albums by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(artistId, offlineMode) {
        if (offlineMode) {
            // Offline: derive albums from downloaded tracks matching this artist name
            val catalog = DownloadManager.catalog.value
            val offlineAlbums = catalog.tracks
                .filter { it.artists.contains(artistId) && !it.albumId.isNullOrBlank() }
                .groupBy { it.albumId!! }
                .map { (albumId, tracks) ->
                    val rep = tracks.first()
                    BaseItemDto(
                        id = albumId,
                        name = rep.album ?: "Unknown Album",
                        type = "MusicAlbum",
                        artists = rep.artists,
                        imageTags = rep.imageTags,
                    )
                }
                .sortedBy { it.name.lowercase() }
            artist = BaseItemDto(id = artistId, name = artistId, type = "MusicArtist")
            albums = offlineAlbums
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        error = null
        val aRes = runCatching { repo.getItem(artistId) }
        val al = runCatching { repo.getArtistAlbums(artistId) }.getOrNull()
        artist = aRes.getOrNull()
        albums = al.orEmpty()
        isLoading = false
        if (artist == null) {
            error = aRes.exceptionOrNull()?.let { friendlyError(it) } ?: "Failed to load artist"
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(artist?.name ?: "Artist") },
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
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                error != null -> {
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
                                val aRes = runCatching { repo.getItem(artistId) }
                                val al = runCatching { repo.getArtistAlbums(artistId) }.getOrNull()
                                artist = aRes.getOrNull()
                                albums = al.orEmpty()
                                isLoading = false
                                if (artist == null) error = aRes.exceptionOrNull()?.let { friendlyError(it) } ?: "Failed to load artist"
                            }
                        }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Try again")
                        }
                    }
                }
                else -> {
                    ArtistHeader(artist)

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(160.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 180.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(albums, key = { it.id }) { item ->
                            ItemCard(
                                title = item.name,
                                subtitle = item.subtitle(),
                                artwork = item.albumArtworkModel(),
                                onClick = { onOpenAlbum(item.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistHeader(artist: BaseItemDto?) {
    val s = AuthManager.state.value
    val offlineMode by SettingsManager.offlineMode.collectAsState()
    val art: Any? = remember(artist?.id, artist?.primaryImageTag(), s.baseUrl, offlineMode) {
        if (offlineMode) {
            // Use artwork from any downloaded track by this artist
            val artDir = DownloadManager.getArtworkDir()
            val catalog = DownloadManager.catalog.value
            catalog.tracks.firstOrNull { it.artists.contains(artist?.name) }?.let { track ->
                val file = java.io.File(artDir, "${track.id}.jpg")
                if (file.exists()) android.net.Uri.fromFile(file) else null
            }
        } else {
            if (artist == null || s.baseUrl.isBlank()) null
            else JellyfinUrls.itemImage(state = s, itemId = artist.id, tag = artist.primaryImageTag(), maxWidth = 768)
        }
    }

    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = art,
                contentDescription = null,
                modifier = Modifier
                    .size(120.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(artist?.name.orEmpty(), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Artist",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun BaseItemDto.albumArtworkModel(maxWidth: Int = 320): Any? {
    if (SettingsManager.offlineMode.value) {
        val artDir = DownloadManager.getArtworkDir()
        val catalog = DownloadManager.catalog.value
        val track = catalog.tracks.firstOrNull { it.albumId == id }
        if (track != null) {
            val file = java.io.File(artDir, "${track.id}.jpg")
            if (file.exists()) return android.net.Uri.fromFile(file)
        }
        return null
    }
    val s = AuthManager.state.value
    if (s.baseUrl.isBlank()) return null
    val tag = primaryImageTag()
    return JellyfinUrls.itemImage(state = s, itemId = id, tag = tag, maxWidth = maxWidth)
}
