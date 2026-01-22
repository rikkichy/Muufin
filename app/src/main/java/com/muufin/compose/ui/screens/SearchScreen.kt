package com.muufin.compose.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.muufin.compose.core.AuthManager
import com.muufin.compose.core.JellyfinRepository
import com.muufin.compose.core.JellyfinUrls
import com.muufin.compose.core.PlayerManager
import com.muufin.compose.model.dto.BaseItemDto
import com.muufin.compose.model.primaryImageTag
import com.muufin.compose.model.subtitle
import com.muufin.compose.ui.components.RowItem
import com.muufin.compose.ui.components.SectionHeader
import com.muufin.compose.ui.util.rememberMuufinHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    repo: JellyfinRepository,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = rememberMuufinHaptics()

    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var results by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query.text) {
        val term = query.text.trim()
        if (term.isBlank()) {
            results = emptyList()
            error = null
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        error = null
        delay(350)
        val res = runCatching { repo.search(term) }
        res.onFailure { error = it.message ?: "Search failed" }
        results = res.getOrNull().orEmpty()
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = query.text,
                    onQueryChange = { query = TextFieldValue(it) },
                    onSearch = {  },
                    expanded = false,
                    onExpandedChange = {  },
                    placeholder = { Text("Search your Jellyfin library") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.text.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    haptics.tap()
                                    query = TextFieldValue("")
                                },
                            ) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                )
            },
            expanded = false,
            onExpandedChange = {  },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) { }

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (error != null) {
            Text(
                text = error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        val grouped = remember(results) {
            results.groupBy { it.type ?: "" }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val tracks = grouped["Audio"].orEmpty()
            val albums = grouped["MusicAlbum"].orEmpty()
            val artists = grouped["MusicArtist"].orEmpty()
            val playlists = grouped["Playlist"].orEmpty()

            if (tracks.isNotEmpty()) {
                item { SectionHeader("Tracks") }
                items(tracks, key = { it.id }) { item ->
                    RowItem(
                        title = item.name,
                        subtitle = item.subtitle(),
                        artwork = item.artworkModel(),
                        trailing = {
                            FilledTonalIconButton(
                                onClick = {
                                    haptics.tap()
                                    scope.launch {
                                        PlayerManager.playQueue(tracks, startId = item.id)
                                        onOpenPlayer()
                                    }
                                },
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play")
                            }
                        },
                        onClick = {
                            scope.launch {
                                PlayerManager.playQueue(tracks, startId = item.id)
                                onOpenPlayer()
                            }
                        },
                    )
                }
            }

            if (albums.isNotEmpty()) {
                item { SectionHeader("Albums") }
                items(albums, key = { it.id }) { item ->
                    RowItem(
                        title = item.name,
                        subtitle = item.subtitle(),
                        artwork = item.artworkModel(),
                        onClick = { onOpenAlbum(item.id) },
                    )
                }
            }

            if (artists.isNotEmpty()) {
                item { SectionHeader("Artists") }
                items(artists, key = { it.id }) { item ->
                    RowItem(
                        title = item.name,
                        subtitle = item.subtitle(),
                        artwork = item.artworkModel(maxWidth = 256),
                        onClick = { onOpenArtist(item.id) },
                    )
                }
            }

            if (playlists.isNotEmpty()) {
                item { SectionHeader("Playlists") }
                items(playlists, key = { it.id }) { item ->
                    RowItem(
                        title = item.name,
                        subtitle = item.subtitle(),
                        artwork = item.artworkModel(),
                        onClick = { onOpenPlaylist(item.id) },
                    )
                }
            }

            if (!isLoading && query.text.isNotBlank() && results.isEmpty() && error == null) {
                item {
                    Text(
                        text = "No results",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

private fun BaseItemDto.artworkModel(maxWidth: Int = 512): String? {
    val s = AuthManager.state.value
    if (s.baseUrl.isBlank()) return null

    
    val primaryTag = primaryImageTag()
    if (!primaryTag.isNullOrBlank()) {
        return JellyfinUrls.itemImage(state = s, itemId = id, tag = primaryTag, maxWidth = maxWidth)
    }

    
    val album = albumId
    if (!album.isNullOrBlank()) {
        return JellyfinUrls.itemImage(state = s, itemId = album, tag = null, maxWidth = maxWidth)
    }

    return null
}
