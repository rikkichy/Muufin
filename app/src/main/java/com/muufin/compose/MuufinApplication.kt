package com.muufin.compose

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.content.getSystemService
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.util.DebugLogger
import com.muufin.compose.core.AuthManager
import com.muufin.compose.core.HttpClients
import com.muufin.compose.core.PlayerManager
import com.muufin.compose.core.SettingsManager
import com.muufin.compose.player.PlaybackService

class MuufinApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        AuthManager.init(this)
        SettingsManager.init(this)
        PlayerManager.init(this)
        createPlaybackNotificationChannel()
    }

    private fun createPlaybackNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val mgr = getSystemService<NotificationManager>() ?: return
        val channel = NotificationChannel(
            PlaybackService.NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_playback_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_playback_description)
        }
        mgr.createNotificationChannel(channel)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient { HttpClients.apiOkHttp() }
            .logger(DebugLogger())
            .build()
    }
}
