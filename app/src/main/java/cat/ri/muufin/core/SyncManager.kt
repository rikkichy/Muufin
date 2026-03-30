package cat.ri.muufin.core

import android.content.Context
import android.util.Log
import cat.ri.muufin.data.JellyfinApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

object SyncManager {
    private const val TAG = "SyncManager"
    private const val SYNC_INTERVAL_MS = 15L * 60 * 1000 // 15 minutes
    private const val SYNC_STATE_FILE = "sync_state.json"
    private const val SYNC_COUNTS_FILE = "sync_counts.json"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val rateLimiter = RateLimiter(maxRequests = 10, windowMs = 60_000L)
    private val repo = JellyfinRepository()

    private lateinit var metadataDir: java.io.File

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private var periodicJob: Job? = null

    fun init(context: Context) {
        metadataDir = java.io.File(context.applicationContext.filesDir, "downloads").also { it.mkdirs() }
        scope.launch {
            _syncState.value = loadSyncState()
        }
    }

    fun startPeriodicSync() {
        if (periodicJob?.isActive == true) return
        periodicJob = scope.launch {
            while (isActive) {
                delay(SYNC_INTERVAL_MS)
                runCatching { syncLibrary() }
                    .onFailure { Log.w(TAG, "Periodic sync failed", it) }
            }
        }
    }

    fun stopPeriodicSync() {
        periodicJob?.cancel()
        periodicJob = null
    }

    suspend fun syncLibrary() {
        if (_syncState.value.syncInProgress) return
        _syncState.value = _syncState.value.copy(syncInProgress = true)

        try {
            rateLimiter.acquire()
            val albumCount = repo.getAlbumCount()
            rateLimiter.acquire()
            val playlistCount = repo.getPlaylistCount()
            rateLimiter.acquire()
            val artistCount = repo.getArtistCount()

            val prev = loadSyncCounts()
            val changed = albumCount != prev.albumCount ||
                    playlistCount != prev.playlistCount ||
                    artistCount != prev.artistCount

            if (changed) {
                repo.clearAllCaches()
                saveSyncCounts(SyncCounts(albumCount, playlistCount, artistCount))
                Log.i(TAG, "Library changed: albums=$albumCount playlists=$playlistCount artists=$artistCount")
            }

            _syncState.value = _syncState.value.copy(
                lastFullSyncEpochMs = System.currentTimeMillis(),
                syncInProgress = false,
            )
            saveSyncState(_syncState.value)
        } catch (e: Exception) {
            _syncState.value = _syncState.value.copy(syncInProgress = false)
            Log.w(TAG, "Library sync failed", e)
        }
    }

    suspend fun validateDownloads() {
        val downloadedIds = DownloadManager.getAllDownloadedTrackIds()
        if (downloadedIds.isEmpty()) return

        val removedIds = mutableSetOf<String>()

        downloadedIds.chunked(50).forEach { batch ->
            rateLimiter.acquire()
            try {
                val s = AuthManager.state.value
                val qp = buildMap {
                    put("userId", s.userId)
                    put("ids", batch.joinToString(","))
                    put("limit", batch.size.toString())
                    put("enableImages", "false")
                }
                val result = JellyfinApi.create(s).getItems(qp)
                val foundIds = result.items.map { it.id }.toSet()
                val missing = batch.filter { it !in foundIds }
                removedIds.addAll(missing)
            } catch (e: Exception) {
                Log.w(TAG, "Download validation batch failed", e)
                // On network error, skip — don't remove anything
            }
        }

        if (removedIds.isNotEmpty()) {
            Log.i(TAG, "Found ${removedIds.size} downloads for server-deleted tracks")
            DownloadManager.removeDownloadsBySync(removedIds)
            _syncState.value = _syncState.value.copy(
                removedItemIds = _syncState.value.removedItemIds + removedIds,
                lastDownloadValidationEpochMs = System.currentTimeMillis(),
            )
            saveSyncState(_syncState.value)
        }
    }

    // --- Persistence ---

    private fun loadSyncState(): SyncState {
        val file = java.io.File(metadataDir, SYNC_STATE_FILE)
        if (!file.exists()) return SyncState()
        return runCatching {
            json.decodeFromString<SyncState>(file.readText())
        }.getOrElse {
            Log.e(TAG, "Failed to load sync state", it)
            SyncState()
        }
    }

    private fun saveSyncState(state: SyncState) {
        runCatching {
            val file = java.io.File(metadataDir, SYNC_STATE_FILE)
            val tmp = java.io.File(metadataDir, "$SYNC_STATE_FILE.tmp")
            tmp.writeText(json.encodeToString(SyncState.serializer(), state))
            tmp.renameTo(file)
        }.onFailure { Log.e(TAG, "Failed to save sync state", it) }
    }

    private fun loadSyncCounts(): SyncCounts {
        val file = java.io.File(metadataDir, SYNC_COUNTS_FILE)
        if (!file.exists()) return SyncCounts()
        return runCatching {
            json.decodeFromString<SyncCounts>(file.readText())
        }.getOrElse {
            Log.e(TAG, "Failed to load sync counts", it)
            SyncCounts()
        }
    }

    private fun saveSyncCounts(counts: SyncCounts) {
        runCatching {
            val file = java.io.File(metadataDir, SYNC_COUNTS_FILE)
            val tmp = java.io.File(metadataDir, "$SYNC_COUNTS_FILE.tmp")
            tmp.writeText(json.encodeToString(SyncCounts.serializer(), counts))
            tmp.renameTo(file)
        }.onFailure { Log.e(TAG, "Failed to save sync counts", it) }
    }
}
