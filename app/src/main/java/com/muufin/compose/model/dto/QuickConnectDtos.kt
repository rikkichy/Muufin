package com.muufin.compose.model.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QuickConnectInitiateResponseDto(
    @SerialName("Code") val code: String? = null,
    @SerialName("Secret") val secret: String? = null,
)

@Serializable
data class QuickConnectConnectResponseDto(
    @SerialName("Authenticated") val authenticated: Boolean? = null,
    @SerialName("AccessToken") val accessToken: String? = null,
    
    @SerialName("Id") val id: String? = null,
    @SerialName("UserId") val userId: String? = null,
) {
    val resolvedUserId: String? get() = id ?: userId
}
