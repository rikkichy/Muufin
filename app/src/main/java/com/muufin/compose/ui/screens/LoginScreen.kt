package com.muufin.compose.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.muufin.compose.core.AuthManager
import com.muufin.compose.core.HttpClients
import com.muufin.compose.core.JellyfinAuthorization
import com.muufin.compose.data.JellyfinApi
import com.muufin.compose.model.AuthState
import com.muufin.compose.ui.util.rememberMuufinHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.HttpException
import retrofit2.Retrofit
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

private enum class LoginStep {
    Instance,
    ConnectionOptions,
    Credentials,
}

private fun normalizeInstanceUrl(raw: String): String {
    val t = raw.trim().removeSuffix("/")
    if (t.isBlank()) return ""
    return if (t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true)) {
        t
    } else {
        "https://$t"
    }
}

private fun describeInstanceError(e: Throwable): String = when {
    e is UnknownHostException ->
        "Server not found. Check the URL for typos."
    e is ConnectException ->
        "Connection refused. Is the server running?"
    e is SocketTimeoutException ->
        "Connection timed out. The server may be unreachable."
    e is SSLException || e.cause is SSLException ->
        "SSL/TLS error. Try enabling \"Disable TLS Verification\" or import the server certificate."
    e is HttpException && e.code() == 503 ->
        "Server is starting up. Try again in a moment."
    e is HttpException ->
        "Unexpected response (HTTP ${e.code()}). Is this a Jellyfin server?"
    e is kotlinx.serialization.SerializationException ||
    e is IllegalArgumentException ->
        "Not a Jellyfin server. Check the URL."
    else ->
        e.message ?: "Could not reach the server."
}

private suspend fun validateInstance(
    baseUrl: String,
    disableTls: Boolean,
    customCaBase64: String,
) {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    val client = HttpClients.buildValidationClient(disableTls, customCaBase64)
    val retrofit = Retrofit.Builder()
        .baseUrl("$baseUrl/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
    val api = retrofit.create(JellyfinApi::class.java)
    withContext(Dispatchers.IO) {
        api.getPublicSystemInfo()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
) {
    val auth by AuthManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    val haptics = rememberMuufinHaptics()

    var step by rememberSaveable { mutableStateOf(LoginStep.Instance) }

    
    var serverInput by rememberSaveable { mutableStateOf("") }
    var disableTls by rememberSaveable { mutableStateOf(auth.disableTls) }

    
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var quickConnectOpen by remember { mutableStateOf(false) }
    val shakeOffset = remember { Animatable(0f) }

    val certImported = auth.customCaBase64.isNotBlank()

    val importCertLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                runCatching { AuthManager.importCertificate(uri) }
                    .onFailure { error = it.message ?: "Failed to import certificate" }
            }
        }
    )

    fun goToCredentials() {
        haptics.tap()
        error = null
        isLoading = true

        val baseUrl = normalizeInstanceUrl(serverInput)

        scope.launch {
            runCatching {
                validateInstance(
                    baseUrl = baseUrl,
                    disableTls = disableTls,
                    customCaBase64 = auth.customCaBase64,
                )
            }.onSuccess {
                step = LoginStep.Credentials
            }.onFailure { e ->
                error = describeInstanceError(e)
                haptics.reject()
                shakeOffset.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = 400
                        0f at 0
                        (-18f) at 50
                        18f at 100
                        (-14f) at 150
                        14f at 200
                        (-8f) at 250
                        8f at 300
                        (-4f) at 350
                        0f at 400
                    },
                )
            }
            isLoading = false
        }
    }

    fun goBackToInstance() {
        haptics.tap()
        error = null
        step = LoginStep.Instance
    }

    Scaffold(
        topBar = {
            when (step) {
                LoginStep.Instance -> {
                    TopAppBar(title = { Text("Muufin") })
                }
                LoginStep.ConnectionOptions -> {
                    TopAppBar(
                        title = { Text("Configure connection") },
                        navigationIcon = {
                            IconButton(onClick = {
                                haptics.tap()
                                step = LoginStep.Instance
                            }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                            }
                        },
                    )
                }
                LoginStep.Credentials -> {
                    TopAppBar(
                        title = { Text("Sign in") },
                        navigationIcon = {
                            IconButton(onClick = { goBackToInstance() }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (step) {
                LoginStep.Instance -> {
                    Text(
                        text = "Add your instance to get started",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedTextField(
                        value = serverInput,
                        onValueChange = {
                            serverInput = it
                            if (error != null) error = null
                        },
                        label = { Text("URL") },
                        placeholder = { Text("jf.example.com") },
                        singleLine = true,
                        isError = error != null,
                        supportingText = if (error != null) {
                            { Text(error!!) }
                        } else null,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(shakeOffset.value.toInt(), 0) },
                    )

                    Button(
                        enabled = !isLoading && serverInput.trim().isNotBlank(),
                        onClick = { goToCredentials() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (isLoading) "Checking…" else "Next")
                    }

                    OutlinedButton(
                        enabled = !isLoading,
                        onClick = {
                            haptics.tap()
                            step = LoginStep.ConnectionOptions
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Configure connection")
                    }
                }

                LoginStep.ConnectionOptions -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("TLS / SSL", style = MaterialTheme.typography.titleMedium)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Disable TLS Verification")
                                    Text(
                                        "Only enable for self-signed or local servers.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.size(12.dp))
                                Switch(
                                    checked = disableTls,
                                    onCheckedChange = {
                                        haptics.toggle()
                                        disableTls = it
                                        AuthManager.updateDisableTls(it)
                                    },
                                )
                            }

                            HorizontalDivider()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Custom certificate")
                                    Text(
                                        if (certImported) "Certificate imported."
                                        else "Import a .crt file for servers with a custom CA.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.size(12.dp))
                                OutlinedButton(
                                    onClick = {
                                        haptics.tap()
                                        importCertLauncher.launch(arrayOf("*/*"))
                                    },
                                ) {
                                    Icon(Icons.Rounded.Lock, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (certImported) "Replace" else "Import")
                                }
                            }
                        }
                    }
                }

                LoginStep.Credentials -> {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        isError = error != null,
                        supportingText = if (error != null) {
                            { Text(error!!) }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        visualTransformation = PasswordVisualTransformation(),
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !isLoading && username.isNotBlank() && password.isNotBlank(),
                            onClick = {
                                haptics.tap()
                                isLoading = true
                                error = null
                                val baseUrl = normalizeInstanceUrl(serverInput)

                                scope.launch {
                                    runCatching {
                                        AuthManager.signIn(
                                            baseUrl = baseUrl,
                                            username = username,
                                            password = password,
                                            disableTls = disableTls,
                                        )
                                    }.onFailure {
                                        error = it.message ?: "Sign-in failed"
                                    }.onSuccess {
                                        onSignedIn()
                                    }
                                    isLoading = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Sign in")
                        }

                        OutlinedButton(
                            enabled = !isLoading,
                            onClick = {
                                haptics.tap()
                                quickConnectOpen = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.QrCode, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Quick Connect")
                        }
                    }
                }
            }
        }

        if (quickConnectOpen) {
            QuickConnectDialog(
                server = normalizeInstanceUrl(serverInput),
                disableTls = disableTls,
                authState = auth,
                onDismiss = { quickConnectOpen = false },
                onAuthenticated = { baseUrl, userId, token ->
                    scope.launch {
                        isLoading = true
                        runCatching {
                            AuthManager.signInWithQuickConnect(
                                baseUrl = baseUrl,
                                userId = userId,
                                accessToken = token,
                                disableTls = disableTls,
                            )
                        }.onFailure {
                            error = it.message ?: "Quick Connect failed"
                        }.onSuccess {
                            onSignedIn()
                        }
                        isLoading = false
                    }
                },
            )
        }
    }
}

@Composable
private fun QuickConnectDialog(
    server: String,
    disableTls: Boolean,
    authState: AuthState,
    onDismiss: () -> Unit,
    onAuthenticated: (baseUrl: String, userId: String, accessToken: String) -> Unit,
) {
    val haptics = rememberMuufinHaptics()

    var code by remember { mutableStateOf<String?>(null) }
    var secret by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(true) }

    LaunchedEffect(server, disableTls, authState.customCaBase64) {
        isBusy = true
        error = null
        code = null
        secret = null

        val base = server.trim().removeSuffix("/")
        if (base.isBlank()) {
            error = "Enter your server URL first."
            isBusy = false
            return@LaunchedEffect
        }

        
        AuthManager.updateDisableTls(disableTls)

        val tmpState = authState.copy(
            baseUrl = base,
            userId = "",
            accessToken = "",
            disableTls = disableTls,
        )

        val api = JellyfinApi.create(tmpState)
        val authHeader = JellyfinAuthorization.buildFrom(tmpState, includeToken = false)

        runCatching {
            api.initiateQuickConnect(authorizationOverride = authHeader)
        }.onFailure {
            error = it.message ?: "Failed to start Quick Connect"
            isBusy = false
        }.onSuccess { res ->
            code = res.code
            secret = res.secret
            if (code.isNullOrBlank() || secret.isNullOrBlank()) {
                error = "Quick Connect is not available on this server."
            }
            isBusy = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick Connect") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                } else {
                    Text("On your Jellyfin server, approve this device:")
                    if (!code.isNullOrBlank()) {
                        Text(
                            text = code!!,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                    Text(
                        "Waiting for approval…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (isBusy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    haptics.tap()
                    onDismiss()
                }
            ) { Text("Cancel") }
        },
    )

    LaunchedEffect(secret) {
        val sec = secret ?: return@LaunchedEffect
        val base = server.trim().removeSuffix("/")

        val tmpState = authState.copy(
            baseUrl = base,
            userId = "",
            accessToken = "",
            disableTls = disableTls,
        )

        val api = JellyfinApi.create(tmpState)

        while (true) {
            delay(1000)

            val res = runCatching { api.checkQuickConnect(secret = sec) }.getOrNull() ?: continue

            if (
                res.authenticated == true &&
                !res.accessToken.isNullOrBlank() &&
                !res.resolvedUserId.isNullOrBlank()
            ) {
                onAuthenticated(base, res.resolvedUserId!!, res.accessToken!!)
                break
            }
        }
    }
}
