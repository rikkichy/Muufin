package com.muufin.compose.core


data class PlaybackUris(
    val directPlayUrl: String,
    val hlsUrl: String,
    val mode: Mode,
    val hasFallenBack: Boolean = false,
    
    val artworkItemId: String? = null,
    val artworkTag: String? = null,
) {
    enum class Mode {
        DIRECT,
        HLS,
    }
}
