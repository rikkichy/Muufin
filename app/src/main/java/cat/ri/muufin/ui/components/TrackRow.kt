package cat.ri.muufin.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import androidx.compose.ui.platform.LocalContext
import cat.ri.muufin.ui.util.rememberMuufinHaptics

@Composable
fun TrackRow(
    index: Int,
    title: String,
    subtitle: String,
    duration: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    leadingImageUrl: Any? = null,
    leadingImageContentDescription: String? = null,
    leadingImageSize: Dp = 40.dp,
    downloadState: TrackDownloadState = TrackDownloadState.NONE,
    onDownloadClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val haptics = rememberMuufinHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.995f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "trackRowScale",
    )

    val shape = if (isPlaying) RoundedCornerShape(16.dp) else RoundedCornerShape(0.dp)

    Surface(
        tonalElevation = 0.dp,
        color = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.surface,
        shape = shape,
        modifier = modifier.fillMaxWidth().padding(horizontal = if (isPlaying) 8.dp else 0.dp),
        onClick = {
            if (enabled) {
                haptics.tap()
                onClick()
            }
        },
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = if (enabled) 1f else 0.4f
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingImageUrl != null) {
                Box(
                    modifier = Modifier
                        .size(leadingImageSize)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(leadingImageUrl)
                            .size(Size(128, 128))
                            .build(),
                        contentDescription = leadingImageContentDescription,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.width(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (duration.isNotBlank()) {
                Text(
                    duration,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (downloadState) {
                TrackDownloadState.NONE -> {
                    if (onDownloadClick != null) {
                        IconButton(
                            onClick = {
                                haptics.tap()
                                onDownloadClick()
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Download,
                                contentDescription = "Download",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                TrackDownloadState.PENDING -> {
                    Icon(
                        Icons.Rounded.Schedule,
                        contentDescription = "Queued",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TrackDownloadState.DOWNLOADING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                }
                TrackDownloadState.DOWNLOADED -> {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = "Downloaded",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
