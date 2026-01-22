package com.muufin.compose.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import com.muufin.compose.ui.util.rememberMuufinHaptics

@Composable
fun NowPlayingBar(
    controller: MediaController?,
    state: PlayerUiState,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberMuufinHaptics()
    val visible = controller != null && state.hasQueue

    
    val enterSpec = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow,
    )

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { fullHeight -> fullHeight / 2 },
            animationSpec = enterSpec,
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { fullHeight -> fullHeight / 2 },
            animationSpec = enterSpec,
        ) + fadeOut(),
        modifier = modifier,
    ) {
        val c = controller ?: return@AnimatedVisibility

        val shape = RoundedCornerShape(24.dp)

        val rawProgress = run {
            val d = state.durationMs
            if (d <= 0L) 0f
            else (state.positionMs.toFloat() / d.toFloat()).coerceIn(0f, 1f)
        }
        val progress by animateFloatAsState(
            targetValue = rawProgress,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
            label = "nowPlayingProgress",
        )

        Surface(
            shape = shape,
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clickable {
                    haptics.tap()
                    onOpenPlayer()
                },
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AsyncImage(
                    model = state.artworkUri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.title.ifBlank { "Nothing playing" },
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = state.artist,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    )
                }

                FilledTonalIconButton(
                    onClick = {
                        haptics.tap()
                        if (c.isPlaying) c.pause() else c.play()
                    },
                ) {
                    AnimatedContent(
                        targetState = state.isPlaying,
                        transitionSpec = {
                            (scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn())
                                .togetherWith(scaleOut(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy)) + fadeOut())
                        },
                        label = "playPauseIcon",
                    ) { isPlaying ->
                        if (isPlaying) {
                            Icon(Icons.Rounded.Pause, contentDescription = "Pause")
                        } else {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play")
                        }
                    }
                }
            }
        }
    }
}
