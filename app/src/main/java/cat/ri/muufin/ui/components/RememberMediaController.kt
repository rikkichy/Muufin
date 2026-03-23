package cat.ri.muufin.ui.components

import androidx.compose.runtime.*
import androidx.media3.session.MediaController
import cat.ri.muufin.core.PlayerManager


@Composable
fun rememberMediaController(
    enabled: Boolean = true,
): State<MediaController?> {
    val state = remember { mutableStateOf<MediaController?>(null) }

    LaunchedEffect(enabled) {
        if (!enabled) {
            
            PlayerManager.releaseController()
            state.value = null
            return@LaunchedEffect
        }

        runCatching { PlayerManager.controller() }
            .onSuccess { state.value = it }
            .onFailure { state.value = null }
    }

    return state
}
