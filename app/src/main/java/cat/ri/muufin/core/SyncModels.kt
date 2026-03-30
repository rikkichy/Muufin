package cat.ri.muufin.core

import kotlinx.serialization.Serializable

@Serializable
data class SyncState(
    val lastFullSyncEpochMs: Long = 0L,
    val lastDownloadValidationEpochMs: Long = 0L,
    val removedItemIds: Set<String> = emptySet(),
    val syncInProgress: Boolean = false,
)

@Serializable
data class SyncCounts(
    val albumCount: Int = -1,
    val playlistCount: Int = -1,
    val artistCount: Int = -1,
)
