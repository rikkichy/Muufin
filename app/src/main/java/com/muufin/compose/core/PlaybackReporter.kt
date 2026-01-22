package com.muufin.compose.core

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.muufin.compose.data.JellyfinApi
import com.muufin.compose.model.dto.PlaybackProgressInfo
import com.muufin.compose.model.dto.PlaybackStartInfo
import com.muufin.compose.model.dto.PlaybackStopInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import java.util.UUID


class PlaybackReporter(
    private val player: Player,
    private val scope: CoroutineScope,
) : Player.Listener {

    companion object {
        private const val TAG = "PlaybackReporter"
        private const val TICKS_PER_MILLISECOND = 10_000L

        
        private const val PROGRESS_INTERVAL_MS = 15_000L
    }

    private var api: JellyfinApi? = null
    private var apiBase: String = ""

    private var enabled: Boolean = SettingsManager.enablePlaybackReporting.value

    private var currentItemId: String? = null
    private var currentPlaySessionId: String? = null
    private var startedForItem: Boolean = false
    private var stoppedForItem: Boolean = false

    private var lastKnownPositionMs: Long = 0L
    private var progressJob: Job? = null

    init {
        
        scope.launch(Dispatchers.Main.immediate) {
            SettingsManager.enablePlaybackReporting.collect { isEnabled ->
                val prev = enabled
                enabled = isEnabled

                if (!isEnabled && prev) {
                    
                    reportStopForCurrent(nextMediaType = null)
                    stopProgressLoop()
                }

                if (isEnabled && !prev) {
                    
                    if (player.currentMediaItem != null) {
                        if (player.isPlaying) {
                            reportStartForCurrent()
                        } else {
                            
                            reportProgressForCurrent(isPaused = true)
                        }
                    }
                }
            }
        }
    }

    private fun apiOrNull(): JellyfinApi? {
        val state = AuthManager.state.value
        if (state.baseUrl.isBlank() || state.accessToken.isBlank()) return null

        val base = state.baseUrl.trim().removeSuffix("/") + "/"
        if (api == null || apiBase != base) {
            api = JellyfinApi.create(state)
            apiBase = base
        }
        return api
    }

    private fun currentPlayMethod(): String {
        val item = player.currentMediaItem ?: return "DirectPlay"
        val cfg = item.localConfiguration
        val tag = cfg?.tag as? PlaybackUris
        return when (tag?.mode) {
            PlaybackUris.Mode.DIRECT -> "DirectPlay"
            PlaybackUris.Mode.HLS -> "Transcode"
            else -> "DirectPlay"
        }
    }

    private fun positionTicks(positionMs: Long): Long {
        return (positionMs.coerceAtLeast(0L) * TICKS_PER_MILLISECOND)
    }

    private fun startProgressLoop() {
        if (progressJob?.isActive == true) return

        
        
        progressJob = scope.launch(Dispatchers.Main.immediate) {
            while (isActive) {
                delay(PROGRESS_INTERVAL_MS)
                if (!enabled) continue
                if (!player.isPlaying) continue
                reportProgressForCurrent(isPaused = false)
            }
        }
    }

    private fun stopProgressLoop() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun reportStartForCurrent() {
        if (!enabled) return

        val itemId = player.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: return

        
        if (startedForItem && currentItemId == itemId) return

        currentItemId = itemId
        startedForItem = true
        stoppedForItem = false
        lastKnownPositionMs = player.currentPosition

        if (currentPlaySessionId == null) {
            currentPlaySessionId = UUID.randomUUID().toString()
        }

        val body = PlaybackStartInfo(
            itemId = itemId,
            canSeek = player.isCurrentMediaItemSeekable,
            isPaused = false,
            positionTicks = positionTicks(lastKnownPositionMs),
            playMethod = currentPlayMethod(),
            playSessionId = currentPlaySessionId,
        )

        scope.launch(Dispatchers.IO) {
            runCatching { apiOrNull()?.reportPlaybackStart(body) }
                .onFailure { Log.w(TAG, "reportPlaybackStart failed", it) }
        }

        
        reportProgressForCurrent(isPaused = false)
        startProgressLoop()
    }

    private fun reportProgressForCurrent(isPaused: Boolean) {
        if (!enabled) return
        if (!startedForItem) return

        val itemId = player.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: return
        if (currentItemId != itemId) {
            
            currentItemId = itemId
        }

        lastKnownPositionMs = player.currentPosition

        val body = PlaybackProgressInfo(
            itemId = itemId,
            canSeek = player.isCurrentMediaItemSeekable,
            isPaused = isPaused,
            positionTicks = positionTicks(lastKnownPositionMs),
            playMethod = currentPlayMethod(),
            playSessionId = currentPlaySessionId,
        )

        scope.launch(Dispatchers.IO) {
            runCatching { apiOrNull()?.reportPlaybackProgress(body) }
                .onFailure { Log.w(TAG, "reportPlaybackProgress failed", it) }
        }
    }

    private fun reportStopForCurrent(nextMediaType: String?) {
        if (!enabled) return
        if (!startedForItem || stoppedForItem) return

        val itemId = currentItemId ?: player.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: return

        stoppedForItem = true

        
        
        val posMs = if (player.currentMediaItem?.mediaId == itemId) player.currentPosition else lastKnownPositionMs

        val body = PlaybackStopInfo(
            itemId = itemId,
            positionTicks = positionTicks(posMs),
            failed = false,
            playSessionId = currentPlaySessionId,
            nextMediaType = nextMediaType,
        )

        scope.launch(Dispatchers.IO) {
            runCatching { apiOrNull()?.reportPlaybackStopped(body) }
                .onFailure { Log.w(TAG, "reportPlaybackStopped failed", it) }
        }

        
        startedForItem = false
        currentPlaySessionId = null
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (!enabled) {
            
            currentItemId = mediaItem?.mediaId
            startedForItem = false
            stoppedForItem = false
            currentPlaySessionId = null
            return
        }

        
        val newId = mediaItem?.mediaId
        if (newId != null && newId == currentItemId) return

        reportStopForCurrent(nextMediaType = "Audio")

        currentItemId = newId
        startedForItem = false
        stoppedForItem = false
        currentPlaySessionId = null

        
        if (player.isPlaying) {
            reportStartForCurrent()
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!enabled) return

        if (isPlaying) {
            reportStartForCurrent()
            reportProgressForCurrent(isPaused = false)
            startProgressLoop()
        } else {
            
            reportProgressForCurrent(isPaused = true)
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (!enabled) return

        when (playbackState) {
            Player.STATE_ENDED -> {
                reportStopForCurrent(nextMediaType = null)
                stopProgressLoop()
            }
            Player.STATE_IDLE -> {
                
                if (player.mediaItemCount == 0) {
                    reportStopForCurrent(nextMediaType = null)
                    stopProgressLoop()
                }
            }
        }
    }

    
    fun release() {
        reportStopForCurrent(nextMediaType = null)
        stopProgressLoop()
    }
}
