package com.muufin.compose.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import com.muufin.compose.core.AuthManager
import com.muufin.compose.core.JellyfinAuthorization
import com.muufin.compose.data.JellyfinApi
import com.muufin.compose.model.AuthState
import com.muufin.compose.ui.util.rememberMuufinHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class LoginStep {
    Instance,
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
        step = LoginStep.Credentials
    }

    fun goBackToInstance() {
        haptics.tap()
        error = null
        step = LoginStep.Instance
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                
                .statusBarsPadding()
                .navigationBarsPadding()
                .displayCutoutPadding()
                .imePadding()
                .verticalScroll(scrollState)
                
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (step) {
                LoginStep.Instance -> {
                    Text(
                        text = "Welcome to Muufin!",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        text = "Add your instance to get started",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (error != null) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                    }

                    OutlinedTextField(
                        value = serverInput,
                        onValueChange = { serverInput = it },
                        label = { Text("URL") },
                        placeholder = { Text("jf.example.com") },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Switch(
                            checked = disableTls,
                            onCheckedChange = {
                                haptics.toggle()
                                disableTls = it
                                AuthManager.updateDisableTls(it)
                            },
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Disable TLS Verification")
                            Text(
                                "Only enable for self-signed / local servers.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    OutlinedButton(
                        enabled = !isLoading,
                        onClick = {
                            haptics.tap()
                            importCertLauncher.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (certImported) "Certificate imported" else "Import certificate")
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        enabled = !isLoading && serverInput.trim().isNotBlank(),
                        onClick = { goToCredentials() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Next")
                    }
                }

                LoginStep.Credentials -> {
                    
                    IconButton(onClick = { goBackToInstance() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }

                    Text(
                        text = "Sign in",
                        style = MaterialTheme.typography.headlineLarge,
                    )

                    if (error != null) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                    }

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
