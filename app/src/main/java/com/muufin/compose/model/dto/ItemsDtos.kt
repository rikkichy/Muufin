package com.muufin.compose.model.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BaseItemQueryResultDto(
    @SerialName("Items") val items: List<BaseItemDto> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int? = null,
)

@Serializable
data class BaseItemDto(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("Type") val type: String? = null,
    @SerialName("Album") val album: String? = null,
    @SerialName("AlbumId") val albumId: String? = null,
    @SerialName("Artists") val artists: List<String> = emptyList(),
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
    @SerialName("MediaStreams") val mediaStreams: List<MediaStreamDto>? = null,
)

@Serializable
data class MediaStreamDto(
    @SerialName("Codec") val codec: String? = null,
    @SerialName("Profile") val profile: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("Channels") val channels: Int? = null,
    @SerialName("ChannelLayout") val channelLayout: String? = null,
    @SerialName("SampleRate") val sampleRate: Int? = null,
    @SerialName("BitRate") val bitRate: Int? = null,
    @SerialName("BitDepth") val bitDepth: Int? = null,
    @SerialName("AudioSpatialFormat") val audioSpatialFormat: String? = null,
    @SerialName("Language") val language: String? = null,
)
