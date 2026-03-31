package cat.ri.muufin.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import cat.ri.muufin.R
import cat.ri.muufin.core.AuthManager
import cat.ri.muufin.core.DownloadManager
import cat.ri.muufin.core.HttpClients
import cat.ri.muufin.core.JellyfinUrls
import cat.ri.muufin.data.JellyfinApi
import cat.ri.muufin.model.dto.DownloadTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import okhttp3.Request
import java.io.File
import java.io.IOException

class DownloadService : Service() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "muufin_downloads"
        private const val NOTIFICATION_ID = 9001
        private const val TAG = "DownloadService"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Preparing download...", 0))

        if (downloadJob?.isActive != true) {
            downloadJob = serviceScope.launch { processQueue() }
        }

        return START_STICKY
    }

    private suspend fun processQueue() {
        while (true) {
            // Wait while paused
            while (DownloadManager.paused.value && serviceScope.isActive) {
                updateNotification("Paused", 0)
                delay(500)
            }
            if (!serviceScope.isActive) break
            val task = DownloadManager.takeNextPending() ?: break
            downloadTrackWithRetry(task)
        }
        // Final check — a download may have been enqueued while we were finishing the last one
        val late = DownloadManager.takeNextPending()
        if (late != null) {
            downloadTrackWithRetry(late)
            return processQueue()
        }
        stopSelf()
    }

    private suspend fun downloadTrackWithRetry(task: DownloadTask, maxRetries: Int = 3) {
        repeat(maxRetries) { attempt ->
            try {
                downloadTrack(task)
                return
            } catch (e: IOException) {
                Log.w(TAG, "Download attempt ${attempt + 1}/$maxRetries failed for ${task.trackId}: ${e.message}")
                if (attempt < maxRetries - 1) {
                    updateNotification("Retrying ${task.name}...", 0)
                    delay(2000L * (attempt + 1))
                } else {
                    DownloadManager.onDownloadFailed(task.trackId, "Network error after $maxRetries attempts: ${e.message}")
                }
            }
        }
    }

    private suspend fun downloadTrack(task: DownloadTask) {
        try {
            updateNotification(task.name, 0)

            val auth = AuthManager.state.value
            if (auth.baseUrl.isBlank() || auth.accessToken.isBlank()) {
                DownloadManager.onDownloadFailed(task.trackId, "Not signed in")
                return
            }

            // Get playback info for file size and container
            var container = task.container
            var expectedSize = task.fileSizeBytes
            var codec: String? = null
            var bitRate: Int? = null

            if (container == null || expectedSize == null) {
                runCatching {
                    val api = JellyfinApi.create(auth)
                    val info = api.getPlaybackInfo(task.trackId, auth.userId)
                    val source = info.mediaSources.firstOrNull()
                    if (source != null) {
                        container = source.container
                        expectedSize = source.size
                        bitRate = source.bitrate
                        codec = source.mediaStreams
                            ?.firstOrNull { it.type == "Audio" }
                            ?.codec
                    }
                }.onFailure { Log.w(TAG, "PlaybackInfo failed, will use Content-Type", it) }
            }

            // Check disk space if we know expected size
            if (expectedSize != null && expectedSize > 0 && !DownloadManager.hasAvailableDiskSpace(expectedSize)) {
                DownloadManager.onDownloadFailed(task.trackId, "Insufficient disk space")
                return
            }

            val client = HttpClients.playerOkHttp()

            // Try dedicated download endpoint first, fall back to stream on 403
            val response = run {
                val downloadUrl = JellyfinUrls.itemDownload(auth, task.trackId)
                val reqBuilder = Request.Builder().url(downloadUrl)
                if (task.bytesDownloaded > 0) reqBuilder.header("Range", "bytes=${task.bytesDownloaded}-")
                val resp = client.newCall(reqBuilder.build()).execute()
                if (resp.code == 403) {
                    resp.close()
                    val fallbackUrl = JellyfinUrls.audioStreamStatic(auth, task.trackId)
                    val fallbackBuilder = Request.Builder().url(fallbackUrl)
                    if (task.bytesDownloaded > 0) fallbackBuilder.header("Range", "bytes=${task.bytesDownloaded}-")
                    client.newCall(fallbackBuilder.build()).execute()
                } else {
                    resp
                }
            }

            response.use { resp ->
                if (!resp.isSuccessful && resp.code != 206) {
                    DownloadManager.onDownloadFailed(task.trackId, "HTTP ${resp.code}")
                    return
                }
                downloadBody(task, resp, container, expectedSize, codec, bitRate)
            }

        } catch (e: IOException) {
            // Rethrow IO errors so retry logic can handle them
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for ${task.trackId}", e)
            DownloadManager.onDownloadFailed(task.trackId, e.message ?: "Unknown error")
        }
    }

    private suspend fun downloadBody(
        task: DownloadTask,
        response: okhttp3.Response,
        container: String?,
        expectedSize: Long?,
        codec: String?,
        bitRate: Int?,
    ) {
        val body = response.body

        val ext = container
            ?: contentTypeToExtension(response.header("Content-Type"))
            ?: "mp3"

        // Detect whether server honored Range request (206 = partial content)
        val resumed = task.bytesDownloaded > 0 && response.code == 206
        val contentLen = body.contentLength().takeIf { it > 0 }
        val totalBytes = if (resumed) {
            contentLen?.let { it + task.bytesDownloaded } ?: expectedSize ?: -1L
        } else {
            contentLen ?: expectedSize ?: -1L
        }

        val fileName = "${task.trackId}.$ext"
        val downloadsDir = DownloadManager.getDownloadsDir()
        val tmpFile = File(downloadsDir, "$fileName.tmp")
        val finalFile = File(downloadsDir, fileName)

        try {
            body.byteStream().use { input ->
                java.io.FileOutputStream(tmpFile, resumed).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Long = if (resumed) task.bytesDownloaded else 0
                    var lastPercent = -1

                    while (serviceScope.isActive) {
                        // Check cancel — abort immediately
                        if (DownloadManager.cancelled.value) {
                            tmpFile.delete()
                            return
                        }

                        // Check pause — close connection and abort; will be retried
                        if (DownloadManager.paused.value) {
                            output.flush()
                            updateNotification("Paused — ${task.name}", lastPercent.coerceAtLeast(0))
                            // Wait for resume
                            while (DownloadManager.paused.value && serviceScope.isActive) {
                                delay(500)
                            }
                            if (!serviceScope.isActive) break
                            // Connection likely stale after pause; abort and let processQueue retry
                            DownloadManager.onDownloadPaused(task.trackId, bytesRead)
                            return
                        }

                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesRead += read

                        if (totalBytes > 0) {
                            val percent = ((bytesRead * 100) / totalBytes).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                DownloadManager.onDownloadProgress(task.trackId, percent)
                                updateNotification(task.name, percent)
                            }
                        }
                    }
                }
            }

            // Verify file integrity (allow 10% tolerance for transcoded streams)
            val actualSize = tmpFile.length()
            if (totalBytes > 0 && actualSize > 0) {
                val ratio = actualSize.toDouble() / totalBytes
                if (ratio < 0.9 || ratio > 1.1) {
                    tmpFile.delete()
                    DownloadManager.onDownloadFailed(task.trackId, "Size mismatch: expected $totalBytes, got $actualSize")
                    return
                }
            }

            if (!tmpFile.renameTo(finalFile)) {
                tmpFile.delete()
                DownloadManager.onDownloadFailed(task.trackId, "Failed to finalize download")
                return
            }

            // Download cover art (non-blocking)
            serviceScope.launch { downloadArtwork(task) }

            DownloadManager.onDownloadCompleted(
                task = task,
                fileName = fileName,
                codec = codec,
                container = ext,
                bitRate = bitRate,
                fileSize = actualSize,
            )

        } catch (e: Exception) {
            tmpFile.delete()
            throw e
        }
    }

    private fun downloadArtwork(task: DownloadTask) {
        runCatching {
            val auth = AuthManager.state.value
            val primaryTag = task.imageTags["Primary"]
            val artworkItemId = if (!primaryTag.isNullOrBlank()) task.trackId else (task.albumId ?: task.trackId)

            val url = JellyfinUrls.itemImage(
                state = auth,
                itemId = artworkItemId,
                tag = primaryTag,
                maxWidth = 480,
                quality = 90,
            )

            val client = HttpClients.imageOkHttp()
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (response.isSuccessful) {
                    val artworkDir = DownloadManager.getArtworkDir()
                    val tmpFile = File(artworkDir, "${task.trackId}.jpg.tmp")
                    val finalFile = File(artworkDir, "${task.trackId}.jpg")
                    response.body.byteStream().use { input ->
                        tmpFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    // Validate: minimum size and JPEG magic bytes
                    if (tmpFile.length() < 100 || !isValidJpeg(tmpFile)) {
                        tmpFile.delete()
                        Log.w(TAG, "Artwork for ${task.trackId} failed validation, discarded")
                        return@runCatching
                    }
                    if (!tmpFile.renameTo(finalFile)) {
                        tmpFile.copyTo(finalFile, overwrite = true)
                        tmpFile.delete()
                    }
                }
            }
        }.onFailure { Log.w(TAG, "Artwork download failed for ${task.trackId}", it) }
    }

    private fun isValidJpeg(file: File): Boolean {
        return runCatching {
            file.inputStream().use { stream ->
                val header = ByteArray(3)
                if (stream.read(header) < 3) return@runCatching false
                // JPEG magic: FF D8 FF
                header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()
            }
        }.getOrDefault(false)
    }

    private fun contentTypeToExtension(contentType: String?): String? {
        return when {
            contentType == null -> null
            "flac" in contentType -> "flac"
            "mpeg" in contentType -> "mp3"
            "mp4" in contentType || "m4a" in contentType -> "m4a"
            "ogg" in contentType -> "ogg"
            "wav" in contentType -> "wav"
            "aac" in contentType -> "aac"
            "webm" in contentType -> "webm"
            else -> null
        }
    }

    private fun ensureNotificationChannel() {
        val mgr = getSystemService<NotificationManager>() ?: return
        if (mgr.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Downloads",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Music download progress"
        }
        mgr.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String, progress: Int): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Downloading music")
            .setContentText(text)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(text: String, progress: Int) {
        val mgr = getSystemService<NotificationManager>() ?: return
        mgr.notify(NOTIFICATION_ID, buildNotification(text, progress))
    }

    override fun onDestroy() {
        downloadJob?.cancel()
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
