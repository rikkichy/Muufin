package com.muufin.compose.model

import com.muufin.compose.model.dto.BaseItemDto
import kotlin.math.roundToInt

fun BaseItemDto.primaryImageTag(): String? = imageTags["Primary"]

fun BaseItemDto.subtitle(): String {
    return when (type) {
        "Audio" -> artists.joinToString().ifBlank { album ?: "" }.ifBlank { "Track" }
        "MusicAlbum" -> artists.joinToString().ifBlank { "Album" }
        "MusicArtist" -> "Artist"
        "Playlist" -> "Playlist"
        else -> artists.joinToString().ifBlank { type.orEmpty() }
    }
}

fun BaseItemDto.durationLabel(): String {
    val ticks = runTimeTicks ?: return ""
    
    val totalSeconds = (ticks / 10_000_000L).toInt().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
