package com.muufin.compose.ui.components

import androidx.compose.runtime.*
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlinx.coroutines.delay

data class PlayerUiState(
    val title: String = "",
    val artist: String = "",
    val artworkUri: android.net.Uri? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val hasQueue: Boolean = false,
)

@Composable
fun rememberPlayerUiState(controller: MediaController?): State<PlayerUiState> {
    val state = remember { mutableStateOf(PlayerUiState()) }

    DisposableEffect(controller) {
        val c = controller
        if (c == null) {
            state.value = PlayerUiState()
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    state.value = state.value.copy(isPlaying = isPlaying)
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    state.value = state.value.copy(shuffleEnabled = shuffleModeEnabled)
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    state.value = state.value.copy(repeatMode = repeatMode)
                }

                override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                    val md = mediaItem?.mediaMetadata
                    state.value = state.value.copy(
                        title = md?.title?.toString().orEmpty(),
                        artist = md?.artist?.toString().orEmpty(),
                        artworkUri = md?.artworkUri,
                        durationMs = c.duration.coerceAtLeast(0L),
                        shuffleEnabled = c.shuffleModeEnabled,
                        repeatMode = c.repeatMode,
                        hasQueue = c.mediaItemCount > 0,
                    )
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    state.value = state.value.copy(
                        durationMs = c.duration.coerceAtLeast(0L),
                        shuffleEnabled = c.shuffleModeEnabled,
                        repeatMode = c.repeatMode,
                        hasQueue = c.mediaItemCount > 0,
                    )
                }
            }

            c.addListener(listener)

            
            val item = c.currentMediaItem
            state.value = state.value.copy(
                title = item?.mediaMetadata?.title?.toString().orEmpty(),
                artist = item?.mediaMetadata?.artist?.toString().orEmpty(),
                artworkUri = item?.mediaMetadata?.artworkUri,
                isPlaying = c.isPlaying,
                durationMs = c.duration.coerceAtLeast(0L),
                shuffleEnabled = c.shuffleModeEnabled,
                repeatMode = c.repeatMode,
                hasQueue = c.mediaItemCount > 0,
            )

            onDispose { c.removeListener(listener) }
        }
    }

    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        while (true) {
            state.value = state.value.copy(
                positionMs = c.currentPosition,
                durationMs = c.duration.coerceAtLeast(0L),
                hasQueue = c.mediaItemCount > 0,
            )
            delay(500)
        }
    }

    return state
}
