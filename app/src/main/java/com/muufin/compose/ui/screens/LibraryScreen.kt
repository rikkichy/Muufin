package com.muufin.compose.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.muufin.compose.core.AuthManager
import com.muufin.compose.core.JellyfinRepository
import com.muufin.compose.core.JellyfinUrls
import com.muufin.compose.core.SettingsManager
import com.muufin.compose.model.dto.BaseItemDto
import com.muufin.compose.model.primaryImageTag
import com.muufin.compose.model.subtitle
import com.muufin.compose.ui.components.ItemCard
import com.muufin.compose.ui.components.RowItem
import com.muufin.compose.ui.util.rememberMuufinHaptics
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun LibraryScreen(
    repo: JellyfinRepository,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val defaultTab by SettingsManager.defaultLibraryTab.collectAsState()
    val libraryLayout by SettingsManager.libraryLayout.collectAsState()
    var tab by remember(defaultTab) { mutableIntStateOf(defaultTab) }

    val sections = remember {
        listOf(
            Triple("Playlists", Icons.AutoMirrored.Rounded.PlaylistPlay, 0),
            Triple("Albums", Icons.Rounded.Album, 1),
            Triple("Artists", Icons.Rounded.Groups, 2),
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            sections.forEachIndexed { index, (label, icon, value) ->
                val isSelected = tab == value
                ToggleButton(
                    checked = isSelected,
                    onCheckedChange = { checked ->
                        
                        if (checked) tab = value
                    },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { role = Role.RadioButton },
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
                        Spacer(Modifier.width(8.dp))
                        Text(label, maxLines = 1)
                    }
                }
            }
        }

        when (tab) {
            0 -> PlaylistsTab(repo = repo, onOpenPlaylist = onOpenPlaylist, layout = libraryLayout)
            1 -> AlbumsTab(repo = repo, onOpenAlbum = onOpenAlbum, layout = libraryLayout)
            2 -> ArtistsTab(repo = repo, onOpenArtist = onOpenArtist)
        }
    }
}

@Composable
private fun AlbumsTab(
    repo: JellyfinRepository,
    onOpenAlbum: (String) -> Unit,
    layout: Int,
) {
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    val albums = remember { mutableStateListOf<BaseItemDto>() }
    var isLoading by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }

    suspend fun loadMore() {
        if (isLoading || endReached) return
        isLoading = true
        val next = runCatching { repo.getAlbums(startIndex = albums.size, limit = 40) }.getOrNull().orEmpty()
        if (next.isEmpty()) endReached = true else albums.addAll(next)
        isLoading = false
    }

    LaunchedEffect(Unit) {
        if (albums.isEmpty()) loadMore()
    }

	val shouldLoadMore by remember(layout) {
		derivedStateOf {
			if (layout == 0) shouldLoadMoreGrid(gridState) else shouldLoadMoreList(listState)
		}
	}
	LaunchedEffect(shouldLoadMore) {
		if (shouldLoadMore) {
			loadMore()
		}
	}

    Box(modifier = Modifier.fillMaxSize()) {
        if (layout == 0) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                state = gridState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(albums, key = { it.id }) { item ->
                    ItemCard(
                        title = item.name,
                        subtitle = item.subtitle(),
                        artwork = item.artworkModel(),
                        onClick = { onOpenAlbum(item.id) },
                    )
                }

                if (isLoading) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(albums, key = { it.id }) { item ->
                    RowItem(
                        title = item.name,
                        subtitle = item.subtitle(),
                        artwork = item.artworkModel(maxWidth = 256),
                        onClick = { onOpenAlbum(item.id) },
                    )
                }

                if (isLoading) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }

        if (!isLoading && albums.isEmpty()) {
            EmptyState(
                title = "No albums",
                subtitle = "Nothing to show yet.",
                onRefresh = { scope.launch { endReached = false; albums.clear(); loadMore() } },
            )
        }
    }
}

@Composable
private fun ArtistsTab(
    repo: JellyfinRepository,
    onOpenArtist: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val artists = remember { mutableStateListOf<BaseItemDto>() }
    var isLoading by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }

    suspend fun loadMore() {
        if (isLoading || endReached) return
        isLoading = true
        val next = runCatching { repo.getArtists(startIndex = artists.size, limit = 40) }.getOrNull().orEmpty()
        if (next.isEmpty()) endReached = true else artists.addAll(next)
        isLoading = false
    }

    LaunchedEffect(Unit) {
        if (artists.isEmpty()) loadMore()
    }

	val shouldLoadMore by remember { derivedStateOf { shouldLoadMoreList(listState) } }
	LaunchedEffect(shouldLoadMore) {
		if (shouldLoadMore) loadMore()
	}

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(artists, key = { it.id }) { item ->
                RowItem(
                    title = item.name,
                    subtitle = item.subtitle(),
                    artwork = item.artworkModel(maxWidth = 256),
                    onClick = { onOpenArtist(item.id) },
                )
            }

            if (isLoading) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }

        if (!isLoading && artists.isEmpty()) {
            EmptyState(
                title = "No artists",
                subtitle = "Nothing to show yet.",
                onRefresh = { scope.launch { endReached = false; artists.clear(); loadMore() } },
            )
        }
    }
}

@Composable
private fun PlaylistsTab(
    repo: JellyfinRepository,
    onOpenPlaylist: (String) -> Unit,
    layout: Int,
) {
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    val playlists = remember { mutableStateListOf<BaseItemDto>() }
    var isLoading by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }

    suspend fun loadMore() {
        if (isLoading || endReached) return
        isLoading = true
        val next = runCatching { repo.getPlaylists(startIndex = playlists.size, limit = 40) }.getOrNull().orEmpty()
        if (next.isEmpty()) endReached = true else playlists.addAll(next)
        isLoading = false
    }

    LaunchedEffect(Unit) {
        if (playlists.isEmpty()) loadMore()
    }

	val shouldLoadMore by remember(layout) {
		derivedStateOf {
			if (layout == 0) shouldLoadMoreGrid(gridState) else shouldLoadMoreList(listState)
		}
	}
	LaunchedEffect(shouldLoadMore) {
		if (shouldLoadMore) loadMore()
	}

    Box(modifier = Modifier.fillMaxSize()) {
        if (layout == 0) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                state = gridState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(playlists, key = { it.id }) { item ->
                    ItemCard(
                        title = item.name,
                        subtitle = item.subtitle(),
                        artwork = item.artworkModel(),
                        onClick = { onOpenPlaylist(item.id) },
                    )
                }

                if (isLoading) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(playlists, key = { it.id }) { item ->
                    RowItem(
                        title = item.name,
                        subtitle = item.subtitle(),
                        artwork = item.artworkModel(maxWidth = 256),
                        onClick = { onOpenPlaylist(item.id) },
                    )
                }

                if (isLoading) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }

        if (!isLoading && playlists.isEmpty()) {
            EmptyState(
                title = "No playlists",
                subtitle = "Nothing to show yet.",
                onRefresh = { scope.launch { endReached = false; playlists.clear(); loadMore() } },
            )
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    subtitle: String,
    onRefresh: () -> Unit,
) {
    val haptics = rememberMuufinHaptics()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(
            onClick = {
                haptics.tap()
                onRefresh()
            },
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Refresh")
        }
    }
}

private fun BaseItemDto.artworkModel(maxWidth: Int = 512): String? {
    val s = AuthManager.state.value
    if (s.baseUrl.isBlank()) return null
    val tag = primaryImageTag()
    return JellyfinUrls.itemImage(state = s, itemId = id, tag = tag, maxWidth = maxWidth)
}

private fun shouldLoadMoreGrid(state: LazyGridState): Boolean {
    val layoutInfo = state.layoutInfo
    val total = layoutInfo.totalItemsCount
    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
    return total > 0 && lastVisible >= total - 6
}

private fun shouldLoadMoreList(state: androidx.compose.foundation.lazy.LazyListState): Boolean {
    val layoutInfo = state.layoutInfo
    val total = layoutInfo.totalItemsCount
    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
    return total > 0 && lastVisible >= total - 6
}
