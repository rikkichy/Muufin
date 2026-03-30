package cat.ri.muufin.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cat.ri.muufin.core.AuthManager
import cat.ri.muufin.core.JellyfinRepository
import cat.ri.muufin.core.JellyfinUrls
import cat.ri.muufin.core.PlayerManager
import cat.ri.muufin.core.SettingsManager
import cat.ri.muufin.model.dto.BaseItemDto
import cat.ri.muufin.model.primaryImageTag
import cat.ri.muufin.model.subtitle
import cat.ri.muufin.ui.components.ItemCard
import cat.ri.muufin.ui.components.RowItem
import cat.ri.muufin.ui.util.rememberMuufinHaptics
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

        val activePlaylistId by PlayerManager.queueSourceId.collectAsState()

        when (tab) {
            0 -> PlaylistsTab(repo = repo, onOpenPlaylist = onOpenPlaylist, layout = libraryLayout, activePlaylistId = activePlaylistId)
            1 -> AlbumsTab(repo = repo, onOpenAlbum = onOpenAlbum, layout = libraryLayout)
            2 -> ArtistsTab(repo = repo, onOpenArtist = onOpenArtist)
        }
    }
}

@Composable
private fun rememberPullToSearchConnection(
    showSearch: Boolean,
    onTrigger: () -> Unit,
): NestedScrollConnection {
    val showState = rememberUpdatedState(showSearch)
    val triggerState = rememberUpdatedState(onTrigger)
    return remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput && !showState.value) {
                    triggerState.value()
                }
                return Offset.Zero
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AlbumsTab(
    repo: JellyfinRepository,
    onOpenAlbum: (String) -> Unit,
    layout: Int,
) {
    val scope = rememberCoroutineScope()
    val haptics = rememberMuufinHaptics()
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    val albums = remember { mutableStateListOf<BaseItemDto>() }
    var isLoading by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    suspend fun loadMore() {
        if (isLoading || endReached) return
        isLoading = true
        val next = runCatching { repo.getAlbums(startIndex = albums.size, limit = 40) }.getOrNull().orEmpty()
        if (next.isEmpty()) endReached = true else albums.addAll(next)
        isLoading = false
    }

    suspend fun refresh() {
        isRefreshing = true
        endReached = false
        albums.clear()
        loadMore()
        isRefreshing = false
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

    var query by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var searchKey by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val pullToSearch = rememberPullToSearchConnection(showSearch) {
        haptics.tap()
        searchKey++
        showSearch = true
    }

    val displayAlbums by remember {
        derivedStateOf {
            if (query.isBlank()) albums.toList() else albums.filter {
                it.name.contains(query, ignoreCase = true)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { scope.launch { refresh() } },
        modifier = Modifier.fillMaxSize(),
    ) {
    Box(modifier = Modifier.fillMaxSize().nestedScroll(pullToSearch)) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                        placeholder = { Text("Search albums...") },
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

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (layout == 0) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(160.dp),
                        state = gridState,
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(displayAlbums, key = { it.id }, contentType = { "album_card" }) { item ->
                            ItemCard(
                                title = item.name,
                                subtitle = item.subtitle(),
                                artwork = item.artworkModel(),
                                onClick = { onOpenAlbum(item.id) },
                            )
                        }

                        if (isLoading && albums.isNotEmpty()) {
                            item(contentType = "loading") {
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
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(displayAlbums, key = { it.id }, contentType = { "album_row" }) { item ->
                            RowItem(
                                title = item.name,
                                subtitle = item.subtitle(),
                                artwork = item.artworkModel(maxWidth = 168),
                                onClick = { onOpenAlbum(item.id) },
                            )
                        }

                        if (isLoading && albums.isNotEmpty()) {
                            item(contentType = "loading") {
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

                if (isLoading && albums.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                if (!isLoading && albums.isEmpty()) {
                    EmptyState(
                        title = "No albums",
                        subtitle = "Nothing to show yet.",
                        onRefresh = { scope.launch { refresh() } },
                    )
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ArtistsTab(
    repo: JellyfinRepository,
    onOpenArtist: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = rememberMuufinHaptics()
    val listState = rememberLazyListState()

    val artists = remember { mutableStateListOf<BaseItemDto>() }
    var isLoading by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    suspend fun loadMore() {
        if (isLoading || endReached) return
        isLoading = true
        val next = runCatching { repo.getArtists(startIndex = artists.size, limit = 40) }.getOrNull().orEmpty()
        if (next.isEmpty()) endReached = true else artists.addAll(next)
        isLoading = false
    }

    suspend fun refresh() {
        isRefreshing = true
        endReached = false
        artists.clear()
        loadMore()
        isRefreshing = false
    }

    LaunchedEffect(Unit) {
        if (artists.isEmpty()) loadMore()
    }

	val shouldLoadMore by remember { derivedStateOf { shouldLoadMoreList(listState) } }
	LaunchedEffect(shouldLoadMore) {
		if (shouldLoadMore) loadMore()
	}

    var query by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var searchKey by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val pullToSearch = rememberPullToSearchConnection(showSearch) {
        haptics.tap()
        searchKey++
        showSearch = true
    }

    val displayArtists by remember {
        derivedStateOf {
            if (query.isBlank()) artists.toList() else artists.filter {
                it.name.contains(query, ignoreCase = true)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { scope.launch { refresh() } },
        modifier = Modifier.fillMaxSize(),
    ) {
    Box(modifier = Modifier.fillMaxSize().nestedScroll(pullToSearch)) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                        placeholder = { Text("Search artists...") },
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

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(displayArtists, key = { it.id }, contentType = { "artist_row" }) { item ->
                        RowItem(
                            title = item.name,
                            subtitle = item.subtitle(),
                            artwork = item.artworkModel(maxWidth = 168),
                            onClick = { onOpenArtist(item.id) },
                        )
                    }

                    if (isLoading && artists.isNotEmpty()) {
                        item(contentType = "loading") {
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

                if (isLoading && artists.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                if (!isLoading && artists.isEmpty()) {
                    EmptyState(
                        title = "No artists",
                        subtitle = "Nothing to show yet.",
                        onRefresh = { scope.launch { refresh() } },
                    )
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlaylistsTab(
    repo: JellyfinRepository,
    onOpenPlaylist: (String) -> Unit,
    layout: Int,
    activePlaylistId: String = "",
) {
    val scope = rememberCoroutineScope()
    val haptics = rememberMuufinHaptics()
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    val playlists = remember { mutableStateListOf<BaseItemDto>() }
    var isLoading by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    suspend fun loadMore() {
        if (isLoading || endReached) return
        isLoading = true
        val next = runCatching { repo.getPlaylists(startIndex = playlists.size, limit = 40) }.getOrNull().orEmpty()
        if (next.isEmpty()) endReached = true else playlists.addAll(next)
        isLoading = false
    }

    suspend fun refresh() {
        isRefreshing = true
        endReached = false
        playlists.clear()
        loadMore()
        isRefreshing = false
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

    var query by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var searchKey by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val pullToSearch = rememberPullToSearchConnection(showSearch) {
        haptics.tap()
        searchKey++
        showSearch = true
    }

    val displayPlaylists by remember {
        derivedStateOf {
            if (query.isBlank()) playlists.toList() else playlists.filter {
                it.name.contains(query, ignoreCase = true)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { scope.launch { refresh() } },
        modifier = Modifier.fillMaxSize(),
    ) {
    Box(modifier = Modifier.fillMaxSize().nestedScroll(pullToSearch)) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                        placeholder = { Text("Search playlists...") },
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

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (layout == 0) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(160.dp),
                        state = gridState,
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(displayPlaylists, key = { it.id }, contentType = { "playlist_card" }) { item ->
                            ItemCard(
                                title = item.name,
                                subtitle = item.subtitle(),
                                artwork = item.artworkModel(),
                                onClick = { onOpenPlaylist(item.id) },
                                isActive = item.id == activePlaylistId,
                            )
                        }

                        if (isLoading && playlists.isNotEmpty()) {
                            item(contentType = "loading") {
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
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(displayPlaylists, key = { it.id }, contentType = { "playlist_row" }) { item ->
                            RowItem(
                                title = item.name,
                                subtitle = item.subtitle(),
                                artwork = item.artworkModel(maxWidth = 168),
                                onClick = { onOpenPlaylist(item.id) },
                                isActive = item.id == activePlaylistId,
                            )
                        }

                        if (isLoading && playlists.isNotEmpty()) {
                            item(contentType = "loading") {
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

                if (isLoading && playlists.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                if (!isLoading && playlists.isEmpty()) {
                    EmptyState(
                        title = "No playlists",
                        subtitle = "Nothing to show yet.",
                        onRefresh = { scope.launch { refresh() } },
                    )
                }
            }
        }
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

private fun BaseItemDto.artworkModel(maxWidth: Int = 320): String? {
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
