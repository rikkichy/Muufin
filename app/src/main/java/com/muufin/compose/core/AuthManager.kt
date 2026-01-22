package com.muufin.compose.core

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.muufin.compose.data.JellyfinApi
import com.muufin.compose.model.AuthState
import com.muufin.compose.model.dto.AuthByNameRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

object AuthManager {
    private const val PREFS_NAME = "muufin_auth"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var appContext: Context
    private lateinit var prefs: android.content.SharedPreferences

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext

        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

        val deviceId = prefs.getString("deviceId", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("deviceId", it).apply()
        }

        val loaded = AuthState(
            baseUrl = prefs.getString("baseUrl", "") ?: "",
            userId = prefs.getString("userId", "") ?: "",
            accessToken = prefs.getString("accessToken", "") ?: "",
            deviceId = deviceId,
            clientName = "Muufin",
            deviceName = android.os.Build.MODEL ?: "Android",
            appVersion = BuildInfo.appVersion(context),
            disableTls = prefs.getBoolean("disableTls", false),
            customCaBase64 = prefs.getString("customCaBase64", "") ?: "",
        )

        _state.value = loaded

        
        HttpClients.rebuild()
    }

    fun updateDisableTls(disableTls: Boolean) {
        prefs.edit().putBoolean("disableTls", disableTls).apply()
        _state.update { it.copy(disableTls = disableTls) }
        HttpClients.rebuild()
    }

    
    suspend fun importCertificate(uri: android.net.Uri) {
        val bytes = kotlinx.coroutines.withContext(Dispatchers.IO) {
            appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: throw IllegalArgumentException("Unable to read certificate")

        
        runCatching {
            val cf = java.security.cert.CertificateFactory.getInstance("X.509")
            cf.generateCertificate(java.io.ByteArrayInputStream(bytes))
        }.getOrElse { throw IllegalArgumentException("Invalid .crt certificate") }

        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        prefs.edit().putString("customCaBase64", b64).apply()
        _state.update { it.copy(customCaBase64 = b64) }
        HttpClients.rebuild()
    }

    fun clearImportedCertificate() {
        prefs.edit().remove("customCaBase64").apply()
        _state.update { it.copy(customCaBase64 = "") }
        HttpClients.rebuild()
    }

    suspend fun signIn(baseUrl: String, username: String, password: String, disableTls: Boolean) {
        updateDisableTls(disableTls)

        
        val state = _state.value.copy(baseUrl = baseUrl.trim().removeSuffix("/"))
        _state.value = state
        HttpClients.rebuild()

        val api = JellyfinApi.create(state)
        val auth = api.authenticateByName(
            body = AuthByNameRequest(username = username, password = password),
            authorizationOverride = JellyfinAuthorization.buildFrom(state, includeToken = false),
        )

        val next = state.copy(
            userId = auth.user?.id.orEmpty(),
            accessToken = auth.accessToken.orEmpty(),
        )

        persist(next)
        _state.value = next
        HttpClients.rebuild()
    }

    suspend fun signInWithQuickConnect(baseUrl: String, userId: String, accessToken: String, disableTls: Boolean) {
        updateDisableTls(disableTls)

        val state = _state.value.copy(
            baseUrl = baseUrl.trim().removeSuffix("/"),
            userId = userId,
            accessToken = accessToken,
        )

        persist(state)
        _state.value = state
        HttpClients.rebuild()
    }

    fun signOut() {
        val keepDeviceId = _state.value.deviceId
        val keepTls = _state.value.disableTls
        val keepCa = _state.value.customCaBase64

        prefs.edit()
            .remove("baseUrl")
            .remove("userId")
            .remove("accessToken")
            .putBoolean("disableTls", keepTls)
            .putString("customCaBase64", keepCa)
            .apply()

        _state.value = AuthState(
            baseUrl = "",
            userId = "",
            accessToken = "",
            deviceId = keepDeviceId,
            clientName = "Muufin",
            deviceName = android.os.Build.MODEL ?: "Android",
            appVersion = BuildInfo.appVersion(appContext),
            disableTls = keepTls,
            customCaBase64 = keepCa,
        )

        HttpClients.rebuild()
        scope.launch { PlayerManager.stopPlayback() }
    }

    private fun persist(state: AuthState) {
        prefs.edit()
            .putString("baseUrl", state.baseUrl)
            .putString("userId", state.userId)
            .putString("accessToken", state.accessToken)
            .putString("deviceId", state.deviceId)
            .putBoolean("disableTls", state.disableTls)
            .putString("customCaBase64", state.customCaBase64)
            .apply()
    }
}
