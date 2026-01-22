package com.muufin.compose.core

import com.muufin.compose.model.AuthState

object JellyfinAuthorization {
    
    fun build(
        client: String,
        device: String,
        deviceId: String,
        version: String,
        token: String? = null,
    ): String {
        val parts = mutableListOf(
            "Client=\"$client\"",
            "Device=\"$device\"",
            "DeviceId=\"$deviceId\"",
            "Version=\"$version\"",
        )
        if (!token.isNullOrBlank()) {
            parts += "Token=\"$token\""
        }
        return "MediaBrowser " + parts.joinToString(", ")
    }

    fun buildFrom(state: AuthState, includeToken: Boolean = true): String {
        return build(
            client = state.clientName,
            device = state.deviceName,
            deviceId = state.deviceId,
            version = state.appVersion,
            token = if (includeToken) state.accessToken else null,
        )
    }
}
