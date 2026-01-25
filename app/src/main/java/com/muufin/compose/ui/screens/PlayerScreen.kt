package com.muufin.compose.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import com.muufin.compose.core.AuthManager
import com.muufin.compose.core.JellyfinRepository
import com.muufin.compose.core.JellyfinUrls
import com.muufin.compose.core.PlaybackUris
import com.muufin.compose.model.dto.LyricDto
import com.muufin.compose.model.dto.LyricLine
import com.muufin.compose.ui.components.PlayerUiState
import com.muufin.compose.ui.util.rememberMuufinHaptics
import kotlin.math.roundToInt


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    controller: MediaController?,
    ui: PlayerUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberMuufinHaptics()
    val c = controller
    if (c == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.CircularProgressIndicator()
        }
        return
    }

    
    
    val auth by AuthManager.state.collectAsState()
    val config = LocalConfiguration.current
    val density = LocalDensity.current

    val currentItem = c.currentMediaItem
    val playbackTag = currentItem?.localConfiguration?.tag as? PlaybackUris
    val coverItemId = playbackTag?.artworkItemId ?: currentItem?.mediaId
    val coverTag = playbackTag?.artworkTag

    val coverMaxWidth = remember(config, density) {
        
        val coverDp = (config.screenWidthDp - 48).coerceAtLeast(0).dp
        val px = with(density) { coverDp.toPx() }
        
        (px * 2f).roundToInt().coerceIn(512, 2000)
    }

    val hiResArtworkUri = remember(auth.baseUrl, coverItemId, coverTag, coverMaxWidth) {
        if (auth.baseUrl.isBlank() || coverItemId.isNullOrBlank()) {
            null
        } else {
            android.net.Uri.parse(
                JellyfinUrls.itemImage(
                    state = auth,
                    itemId = coverItemId,
                    tag = coverTag,
                    maxWidth = coverMaxWidth,
                    quality = 95,
                    format = "Webp",
                )
            )
        }
    }

    val artworkForUi = hiResArtworkUri ?: ui.artworkUri

    
    val artTransitionSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow,
    )

    val repo = remember { JellyfinRepository() }
    val trackId = currentItem?.mediaId

    var showLyrics by remember { mutableStateOf(false) }
    var lyricsLoading by remember { mutableStateOf(false) }
    var lyrics by remember { mutableStateOf<LyricDto?>(null) }

    LaunchedEffect(trackId, auth.baseUrl, auth.accessToken) {
        if (trackId.isNullOrBlank() || auth.baseUrl.isBlank()) {
            lyrics = null
            lyricsLoading = false
            return@LaunchedEffect
        }

        lyricsLoading = true
        lyrics = repo.getLyrics(trackId)
        lyricsLoading = false
    }

    var speedMenuExpanded by remember { mutableStateOf(false) }

    var sliderPos by remember { mutableStateOf<Float?>(null) }
    val duration = ui.durationMs.coerceAtLeast(0L)
    val position = ui.positionMs.coerceIn(0L, duration.takeIf { it > 0 } ?: Long.MAX_VALUE)

    val lyricLines = lyrics?.lyrics?.filter { it.text.isNotBlank() } ?: emptyList()
    val offsetMs = ticksToMs(lyrics?.metadata?.offset)
    val isSynced = lyrics?.metadata?.isSynced ?: lyricLines.any { it.start != null }
    val currentLineIndex = if (!isSynced) {
        -1
    } else {
        lyricLines.indexOfLast { ln ->
            val start = ln.start?.let { ticksToMs(it) + offsetMs } ?: Long.MAX_VALUE
            start <= position
        }
    }

    val lyricsListState = rememberLazyListState()

    LaunchedEffect(showLyrics, currentLineIndex, trackId, lyricLines.size) {
        if (!showLyrics) return@LaunchedEffect
        if (lyricLines.isEmpty()) return@LaunchedEffect

        if (currentLineIndex >= 0) {
            lyricsListState.animateScrollToItem(currentLineIndex)
        } else {
            lyricsListState.scrollToItem(0)
        }
    }

    
    LaunchedEffect(position, duration) {
        if (sliderPos == null && duration > 0) {
            sliderPos = position.toFloat()
            sliderPos = null
        }
    }

    val qualityText = remember(c.currentTracks) { audioQualityText(c) }

    
    val overlayColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .fillMaxHeight(),
    ) {
        
        AnimatedContent(
            targetState = artworkForUi,
            transitionSpec = {
                (fadeIn(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy)) +
                        scaleIn(initialScale = 1.05f, animationSpec = artTransitionSpec))
                    .togetherWith(
                        fadeOut(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy)) +
                            scaleOut(targetScale = 0.98f, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy))
                    )
            },
            label = "playerBgArt",
        ) { artwork ->
            AsyncImage(
                model = artwork,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(60.dp),
                contentScale = ContentScale.Crop,
                alpha = 0.28f,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(overlayColor),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            
            Spacer(Modifier.height(4.dp))

            
            AnimatedContent(
                targetState = showLyrics,
                transitionSpec = {
                    (fadeIn(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy)) + scaleIn(initialScale = 0.98f, animationSpec = artTransitionSpec))
                        .togetherWith(
                            fadeOut(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy)) + scaleOut(targetScale = 1.02f, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy))
                        )
                },
                label = "playerCoverOrLyrics",
            ) { show ->
                if (show) {
                    LyricsPanel(
                        loading = lyricsLoading,
                        lines = lyricLines,
                        currentIndex = currentLineIndex,
                        listState = lyricsListState,
                        onLineClick = { line ->
                            val startTicks = line.start
                            if (startTicks != null) {
                                val target = (ticksToMs(startTicks) + offsetMs).coerceAtLeast(0L)
                                c.seekTo(target)
                            }
                        },
                    )
                } else {
                    CoverPanel(
                        artworkForUi = artworkForUi,
                        artTransitionSpec = artTransitionSpec,
                    )
                }
            }

            
            AnimatedContent(
                targetState = ui.title to ui.artist,
                transitionSpec = {
                    (slideInVertically(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        initialOffsetY = { it / 2 },
                    ) + fadeIn())
                        .togetherWith(
                            slideOutVertically(
                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                                targetOffsetY = { -it / 2 },
                            ) + fadeOut()
                        )
                },
                label = "playerTitleArtist",
            ) { (title, artist) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = title.ifBlank { "Nothing playing" },
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(),
                    )
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(),
                    )
                }
            }

            
            val sliderValue = sliderPos ?: position.toFloat()
            Slider(
                value = sliderValue,
                onValueChange = { sliderPos = it },
                onValueChangeFinished = {
                    val newPos = (sliderPos ?: sliderValue).toLong().coerceIn(0L, duration)
                    haptics.confirm()
                    c.seekTo(newPos)
                    sliderPos = null
                },
                valueRange = 0f..(duration.coerceAtLeast(1L).toFloat()),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatTime(position),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.weight(1f))

                if (!qualityText.isNullOrBlank()) {
                    Text(
                        text = qualityText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                }

                Text(
                    formatTime(duration),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(4.dp))

            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = {
                        haptics.tap()
                        c.seekToPreviousMediaItem()
                    },
                    modifier = Modifier.size(72.dp),
                ) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous")
                }

                Spacer(Modifier.width(14.dp))

                FilledIconButton(
                    onClick = {
                        haptics.tap()
                        if (c.isPlaying) c.pause() else c.play()
                    },
                    modifier = Modifier.size(92.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    AnimatedContent(
                        targetState = ui.isPlaying,
                        transitionSpec = {
                            (scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn())
                                .togetherWith(scaleOut(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy)) + fadeOut())
                        },
                        label = "playerPlayPauseIcon",
                    ) { isPlaying ->
                        if (isPlaying) {
                            Icon(Icons.Rounded.Pause, contentDescription = "Pause", modifier = Modifier.size(44.dp))
                        } else {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", modifier = Modifier.size(44.dp))
                        }
                    }
                }

                Spacer(Modifier.width(14.dp))

                FilledTonalIconButton(
                    onClick = {
                        haptics.tap()
                        c.seekToNextMediaItem()
                    },
                    modifier = Modifier.size(72.dp),
                ) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next")
                }
            }

            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShuffleActionButton(controller = c, enabled = ui.shuffleEnabled)
                RepeatActionButton(controller = c, mode = ui.repeatMode)

                SpeedActionButton(
                    controller = c,
                    expanded = speedMenuExpanded,
                    onExpandedChange = { speedMenuExpanded = it },
                )

                FilledTonalIconButton(
                    onClick = {
                        haptics.tap()
                        showLyrics = !showLyrics
                    },
                    modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (showLyrics) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (showLyrics) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(Icons.Rounded.Lyrics, contentDescription = "Lyrics")
                }
            }
        }
    }
}

@Composable
private fun TopRoundButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(52.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@Composable
private fun SpeedActionButton(
    controller: Player,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val haptics = rememberMuufinHaptics()
    Box {
        FilledTonalIconButton(
            onClick = {
                haptics.tap()
                onExpandedChange(true)
            },
            modifier = Modifier.size(52.dp),
        ) {
            Icon(Icons.Rounded.Speed, contentDescription = "Playback speed")
        }

        PlaybackSpeedMenu(
            expanded = expanded,
            onDismiss = { onExpandedChange(false) },
            current = controller.playbackParameters.speed,
            onSelect = { speed ->
                haptics.tap()
                controller.setPlaybackParameters(PlaybackParameters(speed))
                onExpandedChange(false)
            },
        )
    }
}

@Composable
private fun PlaybackSpeedMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    current: Float,
    onSelect: (Float) -> Unit,
) {
    val haptics = rememberMuufinHaptics()
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        speeds.forEach { speed ->
            val label = if (speed == 1.0f) "Normal" else "${speed}x"
            DropdownMenuItem(
                text = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                trailingIcon = {
                    if (kotlin.math.abs(current - speed) < 0.01f) {
                        Text("✓", color = MaterialTheme.colorScheme.primary)
                    }
                },
                onClick = {
                    haptics.tap()
                    onSelect(speed)
                },
            )
        }
    }
}

@Composable
private fun ShuffleActionButton(controller: Player, enabled: Boolean) {
    val haptics = rememberMuufinHaptics()

    
    val container = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val content = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    FilledTonalIconButton(
        onClick = {
            haptics.toggle()
            controller.shuffleModeEnabled = !enabled
        },
        modifier = Modifier.size(52.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = container,
            contentColor = content,
        ),
    ) {
        Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle")
    }
}

@Composable
private fun RepeatActionButton(controller: Player, mode: Int) {
    val haptics = rememberMuufinHaptics()
    val desc = when (mode) {
        Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne to "Repeat one"
        Player.REPEAT_MODE_ALL -> Icons.Rounded.Repeat to "Repeat all"
        else -> Icons.Rounded.Repeat to "Repeat"
    }.second

    val enabled = mode != Player.REPEAT_MODE_OFF

    
    val container = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val content = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    FilledTonalIconButton(
        onClick = {
            haptics.toggle()
            controller.repeatMode = when (mode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        },
        modifier = Modifier.size(52.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = container,
            contentColor = content,
        ),
    ) {
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                (scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn())
                    .togetherWith(scaleOut(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy)) + fadeOut())
            },
            label = "repeatIcon",
        ) { m ->
            val i = when (m) {
                Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                else -> Icons.Rounded.Repeat
            }
            Icon(i, contentDescription = desc)
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoverPanel(
    artworkForUi: android.net.Uri?,
    artTransitionSpec: androidx.compose.animation.core.FiniteAnimationSpec<Float>,
) {
    AnimatedContent(
        targetState = artworkForUi,
        transitionSpec = {
            (scaleIn(initialScale = 0.96f, animationSpec = artTransitionSpec) + fadeIn())
                .togetherWith(scaleOut(targetScale = 1.04f, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy)) + fadeOut())
        },
        label = "playerCoverArt",
    ) { artwork ->
        AsyncImage(
            model = artwork,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun LyricsPanel(
    loading: Boolean,
    lines: List<LyricLine>,
    currentIndex: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onLineClick: (LyricLine) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            androidx.compose.material3.CircularProgressIndicator()
        } else if (lines.isEmpty()) {
            Text(
                text = "No lyrics",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(18.dp),
            )
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(lines, key = { index, item -> "$index-${item.start ?: 0L}-${item.text}" }) { index, item ->
                    val active = index == currentIndex
                    val style = if (active) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall
                    val color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    Text(
                        text = item.text,
                        style = style,
                        textAlign = TextAlign.Center,
                        color = color,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = item.start != null) {
                                onLineClick(item)
                            },
                    )
                }
            }
        }
    }
}

private fun ticksToMs(ticks: Long?): Long {
    return if (ticks == null) 0L else ticks / 10_000L
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).toInt().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun audioQualityText(player: Player): String? {
    val groups = player.currentTracks.groups
    val audioGroup = groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
        ?: groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO }
        ?: return null

    val selectedIndex = (0 until audioGroup.length).firstOrNull { audioGroup.isTrackSelected(it) } ?: 0
    val format = audioGroup.getTrackFormat(selectedIndex)

    val sampleRate = format.sampleRate.takeIf { it > 0 }
    val channels = format.channelCount.takeIf { it > 0 }
    val bitDepth = when (format.pcmEncoding) {
        C.ENCODING_PCM_16BIT -> 16
        C.ENCODING_PCM_24BIT -> 24
        C.ENCODING_PCM_32BIT -> 32
        else -> null
    }

    
    if (sampleRate == null && channels == null && bitDepth == null) return null

    val parts = buildList {
        if (bitDepth != null) add("${bitDepth}-bit")
        if (sampleRate != null) add("${"%.1f".format(sampleRate / 1000f)} kHz")
        if (channels != null) add("${channels} ch")
    }

    return parts.joinToString(" • ")
}
