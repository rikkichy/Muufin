package com.muufin.compose.model.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LyricDto(
    @SerialName("Metadata") val metadata: LyricMetadata? = null,
    @SerialName("Lyrics") val lyrics: List<LyricLine> = emptyList(),
)

@Serializable
data class LyricMetadata(
    @SerialName("Artist") val artist: String? = null,
    @SerialName("Album") val album: String? = null,
    @SerialName("Title") val title: String? = null,
    @SerialName("Author") val author: String? = null,
    @SerialName("Length") val length: Long? = null,
    @SerialName("By") val by: String? = null,
    @SerialName("Offset") val offset: Long? = null,
    @SerialName("Creator") val creator: String? = null,
    @SerialName("Version") val version: String? = null,
    @SerialName("IsSynced") val isSynced: Boolean? = null,
)

@Serializable
data class LyricLine(
    @SerialName("Text") val text: String = "",
    @SerialName("Start") val start: Long? = null,
    @SerialName("End") val end: Long? = null,
    @SerialName("Cues") val cues: List<LyricLineCue> = emptyList(),
)

@Serializable
data class LyricLineCue(
    @SerialName("Text") val text: String = "",
    @SerialName("Start") val start: Long? = null,
    @SerialName("End") val end: Long? = null,
)
