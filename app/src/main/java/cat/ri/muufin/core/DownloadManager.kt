package cat.ri.muufin.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.util.Log
import cat.ri.muufin.model.dto.BaseItemDto
import cat.ri.muufin.model.dto.DownloadCatalog
import cat.ri.muufin.model.dto.DownloadTask
import cat.ri.muufin.model.dto.DownloadTaskStatus
import cat.ri.muufin.model.dto.DownloadedTrack
import cat.ri.muufin.model.dto.toDownloadTask
import cat.ri.muufin.player.DownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object DownloadManager {
    private const val TAG = "DownloadManager"
    private const val CATALOG_FILE = "catalog.json"
    private const val CATALOG_BACKUP_FILE = "catalog.json.backup"
    private const val QUEUE_FILE = "queue.json"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val mutex = Mutex()

    private lateinit var appContext: Context
    private lateinit var metadataDir: File
    private lateinit var downloadsDir: File
    private lateinit var artworkDir: File

    private val index = ConcurrentHashMap<String, DownloadedTrack>()

    private val _catalog = MutableStateFlow(DownloadCatalog())
    val catalog: StateFlow<DownloadCatalog> = _catalog.asStateFlow()

    private val _queue = MutableStateFlow<List<DownloadTask>>(emptyList())
    val queue: StateFlow<List<DownloadTask>> = _queue.asStateFlow()

    private val _downloadedIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedIds: StateFlow<Set<String>> = _downloadedIds.asStateFlow()

    private val _activeDownload = MutableStateFlow<DownloadTask?>(null)
    val activeDownload: StateFlow<DownloadTask?> = _activeDownload.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private val _cancelled = MutableStateFlow(false)
    val cancelled: StateFlow<Boolean> = _cancelled.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        metadataDir = File(appContext.filesDir, "downloads").also { it.mkdirs() }
        downloadsDir = File(
            appContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: appContext.filesDir,
            "downloads"
        ).also { it.mkdirs() }
        artworkDir = File(downloadsDir, "artwork").also { it.mkdirs() }

        scope.launch {
            AuthManager.ready.await()
            mutex.withLock {
                val cat = loadCatalog()
                _catalog.value = cat
                cat.tracks.forEach { index[it.id] = it }
                _downloadedIds.value = index.keys.toSet()

                // Validate catalog entries against actual files
                val missing = cat.tracks.filter { !File(downloadsDir, it.fileName).exists() }
                if (missing.isNotEmpty()) {
                    missing.forEach { index.remove(it.id) }
                    _catalog.value = _catalog.value.copy(
                        tracks = _catalog.value.tracks.filter { File(downloadsDir, it.fileName).exists() }
                    )
                    _downloadedIds.value = index.keys.toSet()
                    persistCatalogSync()
                    Log.w(TAG, "Removed ${missing.size} orphaned catalog entries")
                }

                val q = loadQueue()
                _queue.value = q
                    .filter { it.status != DownloadTaskStatus.COMPLETED && it.status != DownloadTaskStatus.CANCELLED }
                    .map {
                        if (it.status == DownloadTaskStatus.DOWNLOADING) it.copy(status = DownloadTaskStatus.PENDING)
                        else it
                    }
                persistQueueSync()

                // Clean up orphan temp files
                downloadsDir.listFiles()?.filter { it.extension == "tmp" }?.forEach { it.delete() }
            }
        }
    }

    fun getDownloadsDir(): File = downloadsDir
    fun getArtworkDir(): File = artworkDir

    fun resumePendingDownloads() {
        if (_queue.value.any { it.status == DownloadTaskStatus.PENDING }) {
            startService()
        }
    }

    fun pauseDownloads() {
        _paused.value = true
    }

    fun resumeDownloads() {
        _paused.value = false
        resumePendingDownloads()
    }

    fun hasAvailableDiskSpace(requiredBytes: Long): Boolean {
        return runCatching {
            val stat = StatFs(downloadsDir.path)
            stat.availableBytes > requiredBytes + 10 * 1024 * 1024 // 10MB buffer
        }.getOrDefault(true)
    }

    // --- Enqueue ---

    fun enqueue(track: BaseItemDto) {
        scope.launch {
            mutex.withLock {
                if (index.containsKey(track.id) || _queue.value.any { it.trackId == track.id }) return@withLock
                val auth = AuthManager.state.value
                if (auth.baseUrl.isBlank()) return@withLock
                val task = track.toDownloadTask(auth.baseUrl)
                _queue.value = _queue.value + task
                persistQueueSync()
            }
            startService()
        }
    }

    fun enqueueAll(tracks: List<BaseItemDto>) {
        scope.launch {
            mutex.withLock {
                val auth = AuthManager.state.value
                if (auth.baseUrl.isBlank()) return@withLock
                val existing = _downloadedIds.value + _queue.value.map { it.trackId }.toSet()
                val newTasks = tracks
                    .filter { it.id !in existing }
                    .map { it.toDownloadTask(auth.baseUrl) }
                if (newTasks.isEmpty()) return@withLock
                _queue.value = _queue.value + newTasks
                persistQueueSync()
            }
            startService()
        }
    }

    // --- Cancel / Remove / Retry ---

    fun cancelDownload(trackId: String) {
        scope.launch {
            mutex.withLock {
                _queue.value = _queue.value.filter { it.trackId != trackId }
                persistQueueSync()
            }
        }
    }

    fun cancelAll() {
        _cancelled.value = true
        scope.launch {
            mutex.withLock {
                _queue.value = emptyList()
                _activeDownload.value = null
                persistQueueSync()
            }
            // Stop the service
            runCatching {
                appContext.stopService(Intent(appContext, DownloadService::class.java))
            }
        }
    }

    fun retryDownload(trackId: String) {
        scope.launch {
            mutex.withLock {
                _queue.value = _queue.value.map {
                    if (it.trackId == trackId && it.status == DownloadTaskStatus.FAILED) {
                        it.copy(status = DownloadTaskStatus.PENDING, errorMessage = null)
                    } else it
                }
                persistQueueSync()
            }
            startService()
        }
    }

    fun retryAllFailed() {
        scope.launch {
            mutex.withLock {
                _queue.value = _queue.value.map {
                    if (it.status == DownloadTaskStatus.FAILED) {
                        it.copy(status = DownloadTaskStatus.PENDING, errorMessage = null)
                    } else it
                }
                persistQueueSync()
            }
            startService()
        }
    }

    fun clearFailed() {
        scope.launch {
            mutex.withLock {
                _queue.value = _queue.value.filter { it.status != DownloadTaskStatus.FAILED }
                persistQueueSync()
            }
        }
    }

    fun removeDownload(trackId: String) {
        scope.launch {
            mutex.withLock {
                val track = index.remove(trackId) ?: return@withLock
                val file = File(downloadsDir, track.fileName)
                if (file.exists()) file.delete()
                File(artworkDir, "$trackId.jpg").delete()
                _catalog.value = _catalog.value.copy(tracks = _catalog.value.tracks.filter { it.id != trackId })
                _downloadedIds.value = index.keys.toSet()
                persistCatalogSync()
            }
        }
    }

    fun removeAll() {
        scope.launch {
            mutex.withLock {
                downloadsDir.listFiles()?.forEach { if (it.name != "artwork") it.delete() }
                artworkDir.listFiles()?.forEach { it.delete() }
                index.clear()
                _catalog.value = DownloadCatalog()
                _downloadedIds.value = emptySet()
                persistCatalogSync()
            }
        }
    }

    fun removeDownloadsBySync(trackIds: Set<String>) {
        scope.launch {
            mutex.withLock {
                var changed = false
                trackIds.forEach { trackId ->
                    val track = index.remove(trackId) ?: return@forEach
                    val file = File(downloadsDir, track.fileName)
                    if (file.exists()) file.delete()
                    File(artworkDir, "$trackId.jpg").delete()
                    changed = true
                }
                if (changed) {
                    _catalog.value = _catalog.value.copy(
                        tracks = _catalog.value.tracks.filter { it.id !in trackIds }
                    )
                    _downloadedIds.value = index.keys.toSet()
                    persistCatalogSync()
                }
            }
        }
    }

    fun getAllDownloadedTrackIds(): Set<String> = index.keys.toSet()

    // --- Lookups ---

    fun isDownloaded(trackId: String): Boolean = index.containsKey(trackId)

    fun getLocalFile(trackId: String): File? {
        val track = index[trackId] ?: return null
        val file = File(downloadsDir, track.fileName)
        return if (file.exists()) file else null
    }

    fun getLocalUri(trackId: String): Uri? {
        return getLocalFile(trackId)?.let { Uri.fromFile(it) }
    }

    // --- Callbacks from DownloadService ---

    internal suspend fun takeNextPending(): DownloadTask? {
        return mutex.withLock {
            val next = _queue.value.firstOrNull { it.status == DownloadTaskStatus.PENDING } ?: return@withLock null
            val updated = next.copy(status = DownloadTaskStatus.DOWNLOADING)
            _queue.value = _queue.value.map { if (it.trackId == next.trackId) updated else it }
            _activeDownload.value = updated
            persistQueueSync()
            updated
        }
    }

    internal fun onDownloadProgress(trackId: String, percent: Int) {
        // Only update activeDownload to avoid copying entire queue on every progress tick
        _activeDownload.value?.let {
            if (it.trackId == trackId) _activeDownload.value = it.copy(progressPercent = percent)
        }
    }

    internal fun onDownloadCompleted(
        task: DownloadTask,
        fileName: String,
        codec: String?,
        container: String?,
        bitRate: Int?,
        fileSize: Long,
    ) {
        val track = DownloadedTrack(
            id = task.trackId,
            name = task.name,
            album = task.album,
            albumId = task.albumId,
            artists = task.artists,
            runTimeTicks = task.runTimeTicks,
            imageTags = task.imageTags,
            codec = codec,
            container = container,
            bitRate = bitRate,
            fileSizeBytes = fileSize,
            fileName = fileName,
            downloadedAtEpochMs = System.currentTimeMillis(),
            serverBaseUrl = task.serverBaseUrl,
        )

        scope.launch {
            mutex.withLock {
                index[track.id] = track
                _catalog.value = _catalog.value.copy(tracks = _catalog.value.tracks + track)
                _downloadedIds.value = index.keys.toSet()
                _queue.value = _queue.value.filter { it.trackId != task.trackId }
                _activeDownload.value = null
                persistCatalogSync()
                persistQueueSync()
            }
        }
    }

    internal fun onDownloadPaused(trackId: String, bytesRead: Long) {
        scope.launch {
            mutex.withLock {
                _queue.value = _queue.value.map {
                    if (it.trackId == trackId) it.copy(
                        status = DownloadTaskStatus.PENDING,
                        bytesDownloaded = bytesRead,
                    ) else it
                }
                _activeDownload.value = null
                persistQueueSync()
            }
        }
    }

    internal fun onDownloadFailed(trackId: String, error: String) {
        scope.launch {
            mutex.withLock {
                _queue.value = _queue.value.map {
                    if (it.trackId == trackId) it.copy(
                        status = DownloadTaskStatus.FAILED,
                        errorMessage = error,
                    ) else it
                }
                _activeDownload.value = null
                persistQueueSync()
            }
        }
    }

    // --- Persistence (must be called under mutex) ---

    private fun loadCatalog(): DownloadCatalog {
        val file = File(metadataDir, CATALOG_FILE)
        val backup = File(metadataDir, CATALOG_BACKUP_FILE)
        if (!file.exists() && !backup.exists()) return DownloadCatalog()

        if (file.exists()) {
            runCatching {
                return json.decodeFromString<DownloadCatalog>(file.readText())
            }.onFailure { Log.e(TAG, "Catalog corrupted, trying backup", it) }
        }
        if (backup.exists()) {
            runCatching {
                return json.decodeFromString<DownloadCatalog>(backup.readText())
            }.onFailure { Log.e(TAG, "Backup catalog also corrupted", it) }
        }
        return DownloadCatalog()
    }

    private fun persistCatalogSync() {
        runCatching {
            val file = File(metadataDir, CATALOG_FILE)
            val backup = File(metadataDir, CATALOG_BACKUP_FILE)
            if (file.exists()) file.copyTo(backup, overwrite = true)
            val tmp = File(metadataDir, "$CATALOG_FILE.tmp")
            tmp.writeText(json.encodeToString(DownloadCatalog.serializer(), _catalog.value))
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }.onFailure { Log.e(TAG, "Failed to persist catalog", it) }
    }

    private fun loadQueue(): List<DownloadTask> {
        val file = File(metadataDir, QUEUE_FILE)
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<DownloadTask>>(file.readText())
        }.getOrElse {
            Log.e(TAG, "Failed to load queue", it)
            emptyList()
        }
    }

    private fun persistQueueSync() {
        runCatching {
            val file = File(metadataDir, QUEUE_FILE)
            val tmp = File(metadataDir, "$QUEUE_FILE.tmp")
            val serializer = kotlinx.serialization.builtins.ListSerializer(DownloadTask.serializer())
            tmp.writeText(json.encodeToString(serializer, _queue.value))
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }.onFailure { Log.e(TAG, "Failed to persist queue", it) }
    }

    private fun startService() {
        _cancelled.value = false
        runCatching {
            val intent = Intent(appContext, DownloadService::class.java)
            appContext.startForegroundService(intent)
        }.onFailure { Log.e(TAG, "Failed to start DownloadService", it) }
    }
}
