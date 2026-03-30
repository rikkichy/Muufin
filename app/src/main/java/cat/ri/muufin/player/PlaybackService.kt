package cat.ri.muufin.player

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import cat.ri.muufin.core.PlaybackUris
import androidx.media3.common.MimeTypes
import android.net.Uri
import androidx.media3.session.DefaultMediaNotificationProvider
import cat.ri.muufin.core.HttpClients
import cat.ri.muufin.R
import cat.ri.muufin.core.PlaybackReporter
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

    private val turnShuffleOnButton by lazy {
        CommandButton.Builder(CommandButton.ICON_SHUFFLE_OFF)
            .setDisplayName("Shuffle")
            .setPlayerCommand(Player.COMMAND_SET_SHUFFLE_MODE, true)
            .build()
    }
    private val turnShuffleOffButton by lazy {
        CommandButton.Builder(CommandButton.ICON_SHUFFLE_ON)
            .setDisplayName("Shuffle")
            .setPlayerCommand(Player.COMMAND_SET_SHUFFLE_MODE, false)
            .build()
    }
    private val repeatOffButton by lazy {
        CommandButton.Builder(CommandButton.ICON_REPEAT_OFF)
            .setDisplayName("Repeat")
            .setPlayerCommand(Player.COMMAND_SET_REPEAT_MODE, Player.REPEAT_MODE_ALL)
            .build()
    }
    private val repeatAllButton by lazy {
        CommandButton.Builder(CommandButton.ICON_REPEAT_ALL)
            .setDisplayName("Repeat")
            .setPlayerCommand(Player.COMMAND_SET_REPEAT_MODE, Player.REPEAT_MODE_ONE)
            .build()
    }
    private val repeatOneButton by lazy {
        CommandButton.Builder(CommandButton.ICON_REPEAT_ONE)
            .setDisplayName("Repeat")
            .setPlayerCommand(Player.COMMAND_SET_REPEAT_MODE, Player.REPEAT_MODE_OFF)
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        
        
        val okHttpFactory = OkHttpDataSource.Factory(HttpClients.playerOkHttp())
        val dataSourceFactory = DefaultDataSource.Factory(this, okHttpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs */ 30_000,
                /* maxBufferMs */ 120_000,
                /* bufferForPlaybackMs */ 500,
                /* bufferForPlaybackAfterRebufferMs */ 1_000,
            )
            .build()

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setAudioOffloadPreferences(
                TrackSelectionParameters.AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
                    .build()
            )
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
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true
        )

        
        
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(NOTIFICATION_CHANNEL_ID)
            .setChannelName(R.string.notification_channel_playback_name)
            .build()
        notificationProvider.setSmallIcon(R.drawable.ic_launcher_foreground)
        setMediaNotificationProvider(notificationProvider)

        
        
        
        
        
        
        val bitmapLoader = CacheBitmapLoader(
            DataSourceBitmapLoader.Builder(this)
                .setDataSourceFactory(dataSourceFactory)
                .build()
        )

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        mediaSession = MediaSession.Builder(this, player)
            .setId("muufin_session")
            .setSessionActivity(sessionActivity)
            .setBitmapLoader(bitmapLoader)
            .build()

        player.addListener(object : Player.Listener {
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updateMediaButtonPreferences()
            }
            override fun onRepeatModeChanged(repeatMode: Int) {
                updateMediaButtonPreferences()
            }
        })
        updateMediaButtonPreferences()
    }

    private fun updateMediaButtonPreferences() {
        val player = mediaSession.player
        mediaSession.setMediaButtonPreferences(
            listOf(
                if (player.shuffleModeEnabled) turnShuffleOffButton else turnShuffleOnButton,
                when (player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> repeatOffButton
                    Player.REPEAT_MODE_ALL -> repeatAllButton
                    Player.REPEAT_MODE_ONE -> repeatOneButton
                    else -> repeatOffButton
                },
            )
        )
    }

    private fun fallbackCurrentItemToHls(player: ExoPlayer, reason: String) {
        val item = player.currentMediaItem ?: return
        val cfg = item.localConfiguration ?: return

        val tag = cfg.tag as? PlaybackUris ?: return

        // Don't fallback local files — there's no HLS equivalent
        if (tag.isLocal) return

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
        mediaSession.player.stop()
        mediaSession.player.release()
        mediaSession.release()
        super.onDestroy()
    }
}
