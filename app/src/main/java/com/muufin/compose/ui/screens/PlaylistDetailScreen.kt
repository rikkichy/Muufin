package com.muufin.compose.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.muufin.compose.core.AuthManager
import com.muufin.compose.core.JellyfinRepository
import com.muufin.compose.core.JellyfinUrls
import com.muufin.compose.core.PlayerManager
import com.muufin.compose.model.dto.BaseItemDto
import com.muufin.compose.model.durationLabel
import com.muufin.compose.model.primaryImageTag
import com.muufin.compose.ui.components.PlayerUiState
import com.muufin.compose.ui.components.TrackRow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import androidx.media3.session.MediaController
import coil3.imageLoader
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.muufin.compose.ui.util.rememberMuufinHaptics

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
    val context = androidx.compose.ui.platform.LocalContext.current

    var playlist by remember { mutableStateOf<BaseItemDto?>(null) }
    var tracks by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var query by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var searchKey by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val showSearchState = rememberUpdatedState(showSearch)
    val triggerSearch = rememberUpdatedState {
        haptics.tap()
        searchKey++
        showSearch = true
    }
    val pullToSearch = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput && !showSearchState.value) {
                    triggerSearch.value()
                }
                return Offset.Zero
            }
        }
    }

    val indexById = remember(tracks) { tracks.withIndex().associate { (i, t) -> t.id to i } }
    val displayTracks = if (query.isBlank()) tracks else tracks.filter {
        it.name?.contains(query, ignoreCase = true) == true ||
            it.artists.any { a -> a.contains(query, ignoreCase = true) }
    }

    LaunchedEffect(playlistId) {
        isLoading = true
        error = null
        val p = runCatching { repo.getItem(playlistId) }.getOrNull()
        val t = runCatching { repo.getPlaylistTracks(playlistId) }.getOrNull()
        playlist = p
        tracks = t.orEmpty()

        // Pre-warm Coil memory cache for visible tracks (parallel decode)
        val auth = AuthManager.state.value
        if (auth.baseUrl.isNotBlank()) {
            val loader = context.imageLoader
            t?.take(15)?.map { item ->
                async {
                    val tag = item.primaryImageTag()
                    val itemId = if (!tag.isNullOrBlank()) item.id else item.albumId ?: item.id
                    val url = JellyfinUrls.itemImage(
                        state = auth,
                        itemId = itemId,
                        tag = if (itemId == item.id) tag else null,
                        maxWidth = 64,
                    )
                    runCatching {
                        loader.execute(
                            ImageRequest.Builder(context).data(url).size(128).build()
                        )
                    }
                }
            }?.awaitAll()
        }

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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(pullToSearch),
            ) {
                Box(
                    modifier = Modifier.animateContentSize(
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    ),
                ) {
                    if (showSearch) {
                        LaunchedEffect(searchKey) { focusRequester.requestFocus() }
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .focusRequester(focusRequester),
                            placeholder = { Text("Search tracks...") },
                            shape = RoundedCornerShape(20.dp),
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            trailingIcon = {
                                IconButton(onClick = {
                                    query = ""
                                    showSearch = false
                                    keyboardController?.hide()
                                }) {
                                    Icon(Icons.Rounded.Close, null)
                                }
                            },
                            singleLine = true,
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
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

                    val s = AuthManager.state.value
                    itemsIndexed(displayTracks, key = { _, it -> it.id }) { _, item ->
                        val originalIndex = indexById[item.id] ?: 0
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
                                maxWidth = 64,
                            )
                        }
                        TrackRow(
                            index = originalIndex,
                            title = item.name,
                            subtitle = item.artists.joinToString().ifBlank { item.album.orEmpty() },
                            duration = item.durationLabel(),
                            leadingImageUrl = coverUrl,
                            onClick = {
                                scope.launch {
                                    PlayerManager.playQueue(tracks, startIndex = originalIndex)
                                    onOpenPlayer()
                                }
                            },
                        )
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
