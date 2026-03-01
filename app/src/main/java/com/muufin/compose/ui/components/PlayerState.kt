package com.muufin.compose.ui.components

import androidx.compose.runtime.*
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.muufin.compose.core.PlaybackUris
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
    val mediaId: String = "",
    val coverItemId: String = "",
    val coverTag: String? = null,
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
                    val tag = mediaItem?.localConfiguration?.tag as? PlaybackUris
                    state.value = state.value.copy(
                        title = md?.title?.toString().orEmpty(),
                        artist = md?.artist?.toString().orEmpty(),
                        artworkUri = md?.artworkUri,
                        durationMs = c.duration.coerceAtLeast(0L),
                        shuffleEnabled = c.shuffleModeEnabled,
                        repeatMode = c.repeatMode,
                        hasQueue = c.mediaItemCount > 0,
                        mediaId = mediaItem?.mediaId.orEmpty(),
                        coverItemId = tag?.artworkItemId ?: mediaItem?.mediaId.orEmpty(),
                        coverTag = tag?.artworkTag,
                    )
                }

                override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                    state.value = state.value.copy(
                        title = mediaMetadata.title?.toString().orEmpty(),
                        artist = mediaMetadata.artist?.toString().orEmpty(),
                        artworkUri = mediaMetadata.artworkUri,
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
            val initTag = item?.localConfiguration?.tag as? PlaybackUris
            state.value = state.value.copy(
                title = item?.mediaMetadata?.title?.toString().orEmpty(),
                artist = item?.mediaMetadata?.artist?.toString().orEmpty(),
                artworkUri = item?.mediaMetadata?.artworkUri,
                isPlaying = c.isPlaying,
                durationMs = c.duration.coerceAtLeast(0L),
                shuffleEnabled = c.shuffleModeEnabled,
                repeatMode = c.repeatMode,
                hasQueue = c.mediaItemCount > 0,
                mediaId = item?.mediaId.orEmpty(),
                coverItemId = initTag?.artworkItemId ?: item?.mediaId.orEmpty(),
                coverTag = initTag?.artworkTag,
            )

            onDispose { c.removeListener(listener) }
        }
    }

    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        while (true) {
            val pos = c.currentPosition
            if (pos != state.value.positionMs) {
                state.value = state.value.copy(positionMs = pos)
            }
            delay(500)
        }
    }

    return state
}
