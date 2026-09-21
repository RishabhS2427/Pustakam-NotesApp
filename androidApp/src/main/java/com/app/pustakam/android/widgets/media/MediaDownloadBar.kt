package com.app.pustakam.android.widgets.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.pustakam.android.theme.typography
import com.app.pustakam.core.media.download.MediaDownloadCoordinator
import com.app.pustakam.core.media.model.MediaButtonAction
import com.app.pustakam.core.media.model.MediaTransferUi
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

// 📥 one entry point, so no screen has to thread a view model down into a card's widget
object MediaDownloads : KoinComponent {
    val coordinator: MediaDownloadCoordinator by inject()
}

// 📥⬆️ THE generic media transfer widget — bar, percent/ETA, done/left, one button, failures in red
@Composable
fun MediaDownloadBar(
    ui: MediaTransferUi,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
) {
    // ❗ an error is drawn in the error colour so it reads as a problem, not a status
    val accent = if (ui.isError) colorScheme.error else colorScheme.primary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ⏯️ pause / resume / download / retry — absent while an upload is simply running
            if (ui.buttonAction != MediaButtonAction.NONE) {
                Icon(
                    imageVector = iconFor(ui.buttonAction),
                    contentDescription = ui.buttonAction.label(),
                    tint = colorScheme.onPrimary,
                    modifier = Modifier
                        .size(BUTTON_SIZE)
                        .background(accent, CircleShape)
                        .clickable { onToggle() }
                        .padding(5.dp),
                )
            }

            // 📥 centre: percentage and ETA, or what went wrong
            Text(
                text = ui.centerLabel,
                style = typography.labelMedium,
                color = if (ui.isError) colorScheme.error else colorScheme.onPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            // 📥 end: what landed and what is left
            if (ui.edgeLabel.isNotEmpty()) {
                Text(
                    text = ui.edgeLabel,
                    style = typography.labelSmall,
                    color = colorScheme.onPrimary,
                    maxLines = 1,
                )
            }

            // 🗑️ only worth offering once there are bytes to throw away
            if (onCancel != null && ui.canCancel) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Cancel download",
                    tint = colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp).clickable { onCancel() },
                )
            }
        }

        // 📥 the bar itself, across the whole width of the card
        if (ui.showsBar) {
            if (ui.isIndeterminate) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(BAR_HEIGHT),
                    color = accent,
                    trackColor = colorScheme.onPrimary.copy(alpha = TRACK_ALPHA),
                )
            } else {
                LinearProgressIndicator(
                    progress = { ui.fraction },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(BAR_HEIGHT),
                    color = accent,
                    trackColor = colorScheme.onPrimary.copy(alpha = TRACK_ALPHA),
                )
            }
        }
    }
}

// 📥 the drop-in for a card: draws nothing once the bytes are here and nothing is wrong
@Composable
fun MediaDownloadOverlay(
    media: NoteContentModel.MediaContent,
    modifier: Modifier = Modifier,
) {
    // 🎨 @Preview never starts Koin — guard with if/else, never an early return (that skips an endGroup)
    if (LocalInspectionMode.current) {
        Unit
    } else {
        val coordinator = MediaDownloads.coordinator
        // 📥 keyed on the whole block: a landed download or a finished upload changes it, and must re-subscribe
        val flow = remember(media) { coordinator.transferUi(media) }
        val initial = remember(media) { coordinator.currentTransferUi(media) }
        val ui by flow.collectAsStateWithLifecycle(initialValue = initial)
        ui?.let { current ->
            MediaDownloadBar(
                ui = current,
                onToggle = { coordinator.toggle(media) },
                onCancel = { coordinator.cancel(media) },
                modifier = modifier,
            )
        }
    }
}

// ⏯️ the action comes from shared Kotlin; this only picks the glyph for it
private fun iconFor(action: MediaButtonAction): ImageVector = when (action) {
    MediaButtonAction.PAUSE -> Icons.Filled.Pause
    MediaButtonAction.RESUME -> Icons.Filled.PlayArrow
    MediaButtonAction.RETRY -> Icons.Filled.Refresh
    else -> Icons.Filled.Download
}

private val BUTTON_SIZE = 26.dp
private val BAR_HEIGHT = 4.dp
private const val SCRIM_ALPHA = 0.55f
private const val TRACK_ALPHA = 0.3f
