package cat.ri.muufin

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.util.DebugLogger
import cat.ri.muufin.core.AuthManager
import cat.ri.muufin.core.DownloadManager
import cat.ri.muufin.core.HttpClients
import cat.ri.muufin.core.PlayerManager
import cat.ri.muufin.core.SettingsManager
import cat.ri.muufin.core.SyncManager
import cat.ri.muufin.player.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MuufinApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        HttpClients.init(this)
        AuthManager.init(this)
        SettingsManager.init(this)
        PlayerManager.init(this)
        DownloadManager.init(this)
        SyncManager.init(this)
        createPlaybackNotificationChannel()
        cleanupLegacyCaches()
        warmupImageCache()
    }

    private fun warmupImageCache() {
        CoroutineScope(Dispatchers.IO).launch {
            // Pre-build the image OkHttpClient (SSL context, interceptors, connection pool)
            HttpClients.imageOkHttp()
            // Full Coil pipeline warmup: journal read → fetcher → decoder → BitmapFactory → memory cache
            val loader = SingletonImageLoader.get(this@MuufinApplication)
            loader.diskCache?.size
            loader.execute(
                ImageRequest.Builder(this@MuufinApplication)
                    .data(R.mipmap.ic_launcher)
                    .size(1)
                    .build()
            )
        }
    }

    private fun createPlaybackNotificationChannel() {
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

    private fun cleanupLegacyCaches() {
        filesDir.resolve("coil_cache").takeIf { it.exists() }?.deleteRecursively()
        filesDir.resolve("http_cache").takeIf { it.exists() }?.deleteRecursively()
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { HttpClients.imageOkHttp() }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20) // 20% of available memory
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("coil_cache"))
                    .maxSizeBytes(50L * 1024 * 1024) // 50 MB
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .apply { if (BuildConfig.DEBUG) logger(DebugLogger()) }
            .build()
    }
}
