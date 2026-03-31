package cat.ri.muufin.ui.components

import androidx.compose.runtime.*
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import cat.ri.muufin.core.PlaybackUris
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
    val isLocal: Boolean = false,
    val artworkBytes: ByteArray? = null,
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
                        isLocal = tag?.isLocal ?: false,
                        artworkBytes = null,
                    )
                }

                override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                    state.value = state.value.copy(
                        title = mediaMetadata.title?.toString().orEmpty(),
                        artist = mediaMetadata.artist?.toString().orEmpty(),
                        artworkUri = mediaMetadata.artworkUri ?: state.value.artworkUri,
                        artworkBytes = mediaMetadata.artworkData?.takeIf { it.isValidImageBytes() }
                            ?: state.value.artworkBytes,
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
                isLocal = initTag?.isLocal ?: false,
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

private fun ByteArray.isValidImageBytes(): Boolean {
    if (size < 100) return false
    // JPEG: FF D8 FF
    if (this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte() && this[2] == 0xFF.toByte()) return true
    // PNG: 89 50 4E 47
    if (size >= 4 && this[0] == 0x89.toByte() && this[1] == 0x50.toByte()
        && this[2] == 0x4E.toByte() && this[3] == 0x47.toByte()) return true
    // WebP: RIFF....WEBP
    if (size >= 12 && this[0] == 0x52.toByte() && this[1] == 0x49.toByte()
        && this[2] == 0x46.toByte() && this[3] == 0x46.toByte()
        && this[8] == 0x57.toByte() && this[9] == 0x45.toByte()
        && this[10] == 0x42.toByte() && this[11] == 0x50.toByte()) return true
    return false
}
