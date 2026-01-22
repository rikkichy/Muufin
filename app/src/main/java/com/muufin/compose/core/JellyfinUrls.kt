package com.muufin.compose.core

import com.muufin.compose.model.AuthState
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object JellyfinUrls {
    
    private const val DEFAULT_ACCEPTED_CONTAINERS = "mp3,aac,m4a,flac,ogg,wav,webm,webma"

    
    private const val TICKS_PER_MILLISECOND = 10_000L

    fun itemImage(
        state: AuthState,
        itemId: String,
        imageType: String = "Primary",
        tag: String? = null,
        maxWidth: Int? = null,
        quality: Int? = null,
        format: String? = null,
    ): String {
        val base = state.baseUrl.trim().removeSuffix("/")
        val sb = StringBuilder()
        sb.append(base).append("/Items/").append(itemId).append("/Images/").append(imageType)
        val qp = mutableListOf<String>()
        if (!tag.isNullOrBlank()) qp += "tag=${url(tag)}"
        if (maxWidth != null) qp += "maxWidth=${maxWidth}"
        if (quality != null) qp += "quality=${quality}"
        if (!format.isNullOrBlank()) qp += "format=${url(format)}"
        if (qp.isNotEmpty()) sb.append("?").append(qp.joinToString("&"))
        return sb.toString()
    }

    fun userImage(
        state: AuthState,
        userId: String,
        maxWidth: Int? = null,
    ): String {
        val base = state.baseUrl.trim().removeSuffix("/")
        val sb = StringBuilder()
        sb.append(base).append("/Users/").append(userId).append("/Images/Primary")
        val qp = mutableListOf<String>()
        if (maxWidth != null) qp += "maxWidth=${maxWidth}"
        if (qp.isNotEmpty()) sb.append("?").append(qp.joinToString("&"))
        return sb.toString()
    }

    
    fun audioStreamStatic(
        state: AuthState,
        itemId: String,
        
        enableRedirection: Boolean = false,
    ): String {
        val base = state.baseUrl.trim().removeSuffix("/")
        val qp = linkedMapOf(
            "static" to "true",
            "deviceId" to state.deviceId,
            "enableRedirection" to enableRedirection.toString(),
            
            "api_key" to state.accessToken,
        )
        return "$base/Audio/$itemId/stream?" + qp.entries.joinToString("&") { "${it.key}=${url(it.value)}" }
    }

    
    fun audioHlsPlaylist(
        state: AuthState,
        itemId: String,
        
        audioCodec: String = "mp3",
        segmentContainer: String = "mp3",
        
        maxStreamingBitrate: Int? = null,
        audioBitRate: Int? = null,
        maxAudioBitDepth: Int? = null,
        
        startPositionMs: Long? = null,
        enableAudioVbrEncoding: Boolean? = null,
        allowAudioStreamCopy: Boolean? = null,
    ): String {
        val base = state.baseUrl.trim().removeSuffix("/")
        val qp = linkedMapOf(
            "deviceId" to state.deviceId,
            "audioCodec" to audioCodec,
            "segmentContainer" to segmentContainer,
            
            
            "api_key" to state.accessToken,
        )

        if (maxStreamingBitrate != null) qp["maxStreamingBitrate"] = maxStreamingBitrate.toString()
        if (audioBitRate != null) qp["audioBitRate"] = audioBitRate.toString()
        if (maxAudioBitDepth != null) qp["maxAudioBitDepth"] = maxAudioBitDepth.toString()
        if (enableAudioVbrEncoding != null) qp["enableAudioVbrEncoding"] = enableAudioVbrEncoding.toString()
        if (allowAudioStreamCopy != null) qp["allowAudioStreamCopy"] = allowAudioStreamCopy.toString()

        if (startPositionMs != null && startPositionMs > 0L) {
            val ticks = startPositionMs * TICKS_PER_MILLISECOND
            qp["startTimeTicks"] = ticks.toString()
        }

        return "$base/Audio/$itemId/main.m3u8?" + qp.entries.joinToString("&") { "${it.key}=${url(it.value)}" }
    }

    
    fun audioUniversal(
        state: AuthState,
        itemId: String,
        acceptedContainers: String = DEFAULT_ACCEPTED_CONTAINERS,
        
        audioCodec: String? = null,
        transcodingContainer: String? = null,
        transcodingProtocol: String = "http",
        
        enableAudioVbrEncoding: Boolean? = null,
    ): String {
        val base = state.baseUrl.trim().removeSuffix("/")

        val qp = linkedMapOf(
            "userId" to state.userId,
            "deviceId" to state.deviceId,
            
            "container" to acceptedContainers,
            
            "enableRedirection" to "false",
            
            
            "api_key" to state.accessToken,
            "transcodingProtocol" to transcodingProtocol,
        )

        if (!audioCodec.isNullOrBlank()) qp["audioCodec"] = audioCodec
        if (!transcodingContainer.isNullOrBlank()) qp["transcodingContainer"] = transcodingContainer
        if (enableAudioVbrEncoding != null) qp["enableAudioVbrEncoding"] = enableAudioVbrEncoding.toString()

        return "$base/Audio/$itemId/universal?" + qp.entries.joinToString("&") { "${it.key}=${url(it.value)}" }
    }

    private fun url(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8.name())
}
