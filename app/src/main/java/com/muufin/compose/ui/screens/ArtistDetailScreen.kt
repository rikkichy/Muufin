package com.muufin.compose.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import com.muufin.compose.model.dto.BaseItemDto
import com.muufin.compose.model.primaryImageTag
import com.muufin.compose.model.subtitle
import com.muufin.compose.ui.components.ItemCard
import com.muufin.compose.ui.components.PlayerUiState
import androidx.media3.session.MediaController
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.muufin.compose.ui.util.rememberMuufinHaptics

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
    var artist by remember { mutableStateOf<BaseItemDto?>(null) }
    var albums by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(artistId) {
        isLoading = true
        error = null
        val a = runCatching { repo.getItem(artistId) }.getOrNull()
        val al = runCatching { repo.getArtistAlbums(artistId) }.getOrNull()
        artist = a
        albums = al.orEmpty()
        isLoading = false
        if (a == null) error = "Failed to load artist"
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
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                else -> {
                    ArtistHeader(artist)

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(160.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 180.dp, bottom = 24.dp),
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
    val art = remember(artist?.id, artist?.primaryImageTag(), s.baseUrl) {
        if (artist == null || s.baseUrl.isBlank()) null
        else JellyfinUrls.itemImage(state = s, itemId = artist.id, tag = artist.primaryImageTag(), maxWidth = 768)
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

private fun BaseItemDto.albumArtworkModel(maxWidth: Int = 512): String? {
    val s = AuthManager.state.value
    if (s.baseUrl.isBlank()) return null
    val tag = primaryImageTag()
    return JellyfinUrls.itemImage(state = s, itemId = id, tag = tag, maxWidth = maxWidth)
}
