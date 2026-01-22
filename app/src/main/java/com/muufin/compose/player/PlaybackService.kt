package com.muufin.compose.player

import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.muufin.compose.core.PlaybackUris
import androidx.media3.common.MimeTypes
import android.net.Uri
import androidx.media3.session.DefaultMediaNotificationProvider
import com.muufin.compose.core.HttpClients
import com.muufin.compose.R
import com.muufin.compose.core.PlaybackReporter
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.session.CacheBitmapLoader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@UnstableApi
class PlaybackService : MediaSessionService() {
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "muufin_playback"
        private const val TAG = "PlaybackService"
    }

    private lateinit var mediaSession: MediaSession
    private lateinit var playbackReporter: PlaybackReporter

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        
        
        val okHttpFactory = OkHttpDataSource.Factory(HttpClients.playerOkHttp())
        val dataSourceFactory = DefaultDataSource.Factory(this, okHttpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        
        playbackReporter = PlaybackReporter(player, serviceScope)
        player.addListener(playbackReporter)

        
        player.addListener(
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "Player error: ${error.errorCodeName}", error)

                    
                    
                    fallbackCurrentItemToHls(player, reason = "error:${error.errorCodeName}")
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    Log.d(TAG, "Playback state=$playbackState isLoading=${player.isLoading}")

                    if (playbackState == Player.STATE_READY) {
                        
                        
                        if (!player.isCurrentMediaItemSeekable) {
                            fallbackCurrentItemToHls(player, reason = "unseekable")
                        }
                    }
                }
            }
        )

        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true
        )

        
        
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(NOTIFICATION_CHANNEL_ID)
            .setChannelName(R.string.notification_channel_playback_name)
            .build()
        setMediaNotificationProvider(notificationProvider)

        
        
        
        
        
        
        val bitmapLoader = CacheBitmapLoader(
            DataSourceBitmapLoader.Builder(this)
                .setDataSourceFactory(dataSourceFactory)
                .build()
        )

        mediaSession = MediaSession.Builder(this, player)
            .setId("muufin_session")
            .setBitmapLoader(bitmapLoader)
            .build()
    }

    private fun fallbackCurrentItemToHls(player: ExoPlayer, reason: String) {
        val item = player.currentMediaItem ?: return
        val cfg = item.localConfiguration ?: return

        val tag = cfg.tag as? PlaybackUris ?: return

        
        if (tag.mode != PlaybackUris.Mode.DIRECT || tag.hasFallenBack) return

        
        val isAlreadyHls = cfg.mimeType == MimeTypes.APPLICATION_M3U8 || cfg.uri.toString().contains(".m3u8")
        if (isAlreadyHls) return

        val hlsUrl = tag.hlsUrl
        if (hlsUrl.isBlank()) return

        val currentIndex = player.currentMediaItemIndex
        val currentPos = player.currentPosition
        val playWhenReady = player.playWhenReady

        Log.w(TAG, "Falling back to HLS for item=${item.mediaId} reason=$reason")

        val newItem = item.buildUpon()
            .setUri(Uri.parse(hlsUrl))
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .setTag(tag.copy(mode = PlaybackUris.Mode.HLS, hasFallenBack = true))
            .build()

        player.replaceMediaItem(currentIndex, newItem)
        player.seekTo(currentIndex, currentPos)
        player.prepare()
        player.playWhenReady = playWhenReady
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onDestroy() {
        runCatching { playbackReporter.release() }
        serviceScope.cancel()
        mediaSession.player.release()
        mediaSession.release()
        super.onDestroy()
    }
}
