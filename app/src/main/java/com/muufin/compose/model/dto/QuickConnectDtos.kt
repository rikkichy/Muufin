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
)

@Serializable
data class QuickConnectAuthRequestDto(
    @SerialName("Secret") val secret: String,
)
