package com.muufin.compose.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.muufin.compose.core.JellyfinRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repo: JellyfinRepository,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {  },
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Muufin") },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LibraryScreen(
                repo = repo,
                onOpenAlbum = onOpenAlbum,
                onOpenArtist = onOpenArtist,
                onOpenPlaylist = onOpenPlaylist,
                onOpenPlayer = onOpenPlayer,
            )
        }
    }
}
