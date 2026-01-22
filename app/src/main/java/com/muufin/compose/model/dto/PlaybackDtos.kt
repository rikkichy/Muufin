package com.muufin.compose.model.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable



@Serializable
data class PlaybackStartInfo(
    @SerialName("ItemId") val itemId: String,
    @SerialName("CanSeek") val canSeek: Boolean,
    @SerialName("IsPaused") val isPaused: Boolean,
    @SerialName("PositionTicks") val positionTicks: Long? = null,
    @SerialName("PlayMethod") val playMethod: String,
    @SerialName("PlaySessionId") val playSessionId: String? = null,
)

@Serializable
data class PlaybackProgressInfo(
    @SerialName("ItemId") val itemId: String,
    @SerialName("CanSeek") val canSeek: Boolean,
    @SerialName("IsPaused") val isPaused: Boolean,
    @SerialName("PositionTicks") val positionTicks: Long? = null,
    @SerialName("PlayMethod") val playMethod: String,
    @SerialName("PlaySessionId") val playSessionId: String? = null,
)

@Serializable
data class PlaybackStopInfo(
    @SerialName("ItemId") val itemId: String,
    @SerialName("PositionTicks") val positionTicks: Long? = null,
    @SerialName("Failed") val failed: Boolean = false,
    @SerialName("PlaySessionId") val playSessionId: String? = null,
    @SerialName("NextMediaType") val nextMediaType: String? = null,
)
