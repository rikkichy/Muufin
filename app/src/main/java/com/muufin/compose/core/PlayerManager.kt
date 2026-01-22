package com.muufin.compose.core

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.muufin.compose.model.AuthState
import com.muufin.compose.model.dto.BaseItemDto
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import com.muufin.compose.player.PlaybackService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

object PlayerManager {
    private const val TAG = "PlayerManager"

    private lateinit var appContext: Context

    private val executor = Executors.newSingleThreadExecutor()

    @Volatile private var controllerFuture: ListenableFuture<MediaController>? = null

    
    fun releaseController() {
        controllerFuture?.let {
            runCatching { MediaController.releaseFuture(it) }
        }
        controllerFuture = null
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun notificationsAllowed(): Boolean {
        
        
        return if (Build.VERSION.SDK_INT < 33) {
            true
        } else {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun ensureFuture(): ListenableFuture<MediaController> {
        return controllerFuture ?: run {
            val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
            val fut = MediaController.Builder(appContext, token).buildAsync()
            controllerFuture = fut
            fut
        }
    }

    suspend fun controller(): MediaController {
        val future = ensureFuture()
        return awaitFuture(future)
    }

    suspend fun playQueue(items: List<BaseItemDto>, startId: String? = null, startIndex: Int? = null) {
        runCatching {
            if (!notificationsAllowed()) {
                Log.w(TAG, "Playback blocked: POST_NOTIFICATIONS permission not granted")
                return
            }

            val c = controller()
            val s = AuthManager.state.value
            val mediaItems = items.map { it.toMediaItem(s) }

            val idx = when {
                startIndex != null -> startIndex
                startId != null -> mediaItems.indexOfFirst { it.mediaId == startId }.takeIf { it >= 0 } ?: 0
                else -> 0
            }

            c.setMediaItems(mediaItems, idx, 0L)
            c.prepare()
            c.play()
        }.onFailure {
            
            Log.e(TAG, "playQueue failed", it)
        }
    }

    suspend fun stopPlayback() {
        runCatching {
            val c = controller()
            c.stop()
            c.clearMediaItems()
        }
    }

    private fun BaseItemDto.toMediaItem(auth: AuthState): MediaItem {
        val primaryTag = imageTags["Primary"]
        
        val artworkItemId = if (!primaryTag.isNullOrBlank()) id else (albumId ?: id)

        val artworkUri = runCatching {
            JellyfinUrls.itemImage(
                state = auth,
                itemId = artworkItemId,
                tag = primaryTag,
                maxWidth = 512,
            )
        }.getOrNull()
            
            
            
            ?.let { url ->
                val sep = if (url.contains("?")) "&" else "?"
                url + sep + "ApiKey=" + URLEncoder.encode(auth.accessToken, StandardCharsets.UTF_8.name())
            }

        val meta = MediaMetadata.Builder()
            .setTitle(name)
            .setArtist(artists.joinToString())
            .setAlbumTitle(album)
            .setArtworkUri(artworkUri?.let { android.net.Uri.parse(it) })
            .build()

        val directUrl = JellyfinUrls.audioStreamStatic(
            state = auth,
            itemId = id,
            enableRedirection = false,
        )

        val hlsUrl = JellyfinUrls.audioHlsPlaylist(
            state = auth,
            itemId = id,
            
            audioCodec = "mp3",
            segmentContainer = "mp3",
            
            allowAudioStreamCopy = true,
        )

        val preferLossless = SettingsManager.preferLosslessDirectPlay.value
        val initialMode = if (preferLossless) PlaybackUris.Mode.DIRECT else PlaybackUris.Mode.HLS
        val initialUrl = if (preferLossless) directUrl else hlsUrl

        val uri = android.net.Uri.parse(initialUrl)
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .setMediaMetadata(meta)
            .setTag(
                PlaybackUris(
                    directPlayUrl = directUrl,
                    hlsUrl = hlsUrl,
                    mode = initialMode,
                    hasFallenBack = false,
                    artworkItemId = artworkItemId,
                    artworkTag = primaryTag,
                )
            )
            .build()
    }

    private suspend fun <T> awaitFuture(future: ListenableFuture<T>): T {
        val deferred = CompletableDeferred<T>()
        future.addListener(
            {
                try {
                    deferred.complete(future.get())
                } catch (t: Throwable) {
                    deferred.completeExceptionally(t)
                }
            },
            executor
        )
        return withContext(Dispatchers.IO) { deferred.await() }
    }
}
