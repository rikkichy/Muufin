package cat.ri.muufin.model.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DownloadedTrack(
    val id: String,
    val name: String,
    val album: String? = null,
    val albumId: String? = null,
    val artists: List<String> = emptyList(),
    val runTimeTicks: Long? = null,
    val imageTags: Map<String, String> = emptyMap(),
    val codec: String? = null,
    val container: String? = null,
    val bitRate: Int? = null,
    val fileSizeBytes: Long = 0L,
    val fileName: String,
    val downloadedAtEpochMs: Long,
    val serverBaseUrl: String,
)

@Serializable
data class DownloadCatalog(
    val version: Int = 1,
    val tracks: List<DownloadedTrack> = emptyList(),
)

@Serializable
enum class DownloadTaskStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

@Serializable
data class DownloadTask(
    val trackId: String,
    val name: String,
    val album: String? = null,
    val albumId: String? = null,
    val artists: List<String> = emptyList(),
    val runTimeTicks: Long? = null,
    val imageTags: Map<String, String> = emptyMap(),
    val container: String? = null,
    val fileSizeBytes: Long? = null,
    val status: DownloadTaskStatus = DownloadTaskStatus.PENDING,
    val progressPercent: Int = 0,
    val bytesDownloaded: Long = 0,
    val errorMessage: String? = null,
    val serverBaseUrl: String,
    val enqueuedAtEpochMs: Long,
)

fun BaseItemDto.toDownloadTask(serverBaseUrl: String): DownloadTask {
    return DownloadTask(
        trackId = id,
        name = name,
        album = album,
        albumId = albumId,
        artists = artists,
        runTimeTicks = runTimeTicks,
        imageTags = imageTags,
        serverBaseUrl = serverBaseUrl,
        enqueuedAtEpochMs = System.currentTimeMillis(),
    )
}

@Serializable
data class PlaybackInfoResponse(
    @SerialName("MediaSources") val mediaSources: List<MediaSourceInfo> = emptyList(),
)

@Serializable
data class MediaSourceInfo(
    @SerialName("Id") val id: String? = null,
    @SerialName("Container") val container: String? = null,
    @SerialName("Size") val size: Long? = null,
    @SerialName("Bitrate") val bitrate: Int? = null,
    @SerialName("SupportsDirectPlay") val supportsDirectPlay: Boolean? = null,
    @SerialName("SupportsDirectStream") val supportsDirectStream: Boolean? = null,
    @SerialName("SupportsTranscoding") val supportsTranscoding: Boolean? = null,
    @SerialName("MediaStreams") val mediaStreams: List<MediaStreamDto>? = null,
)
