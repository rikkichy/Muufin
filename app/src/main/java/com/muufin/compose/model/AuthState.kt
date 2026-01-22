package com.muufin.compose.model


data class AuthState(
    val baseUrl: String = "",
    val userId: String = "",
    val accessToken: String = "",
    val deviceId: String = "",
    val clientName: String = "Muufin",
    val deviceName: String = android.os.Build.MODEL ?: "Android",
    val appVersion: String = "0.1.0",
    val disableTls: Boolean = false,
    
    val customCaBase64: String = "",
) {
    val isSignedIn: Boolean get() = baseUrl.isNotBlank() && userId.isNotBlank() && accessToken.isNotBlank()
}
