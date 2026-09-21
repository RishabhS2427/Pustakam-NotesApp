@file:kotlin.OptIn(ExperimentalMaterial3Api::class)

package com.app.pustakam.android.widgets.audio

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import androidx.annotation.OptIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.app.pustakam.android.MyApplicationTheme
import com.app.pustakam.android.extension.startServiceWrapper
import com.app.pustakam.android.hardware.audio.player.MediaPlayingUIEvent
import com.app.pustakam.android.hardware.audio.player.PlayMediaViewModel
import com.app.pustakam.android.hardware.audio.player.PlayerUiState
import com.app.pustakam.android.services.mediaSessionService.PustakmMediaPlayerService
import com.app.pustakam.android.theme.typography
import com.app.pustakam.android.widgets.media.MediaDownloadOverlay
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import com.app.pustakam.core.common.util.ContentType


@OptIn(UnstableApi::class)
@Composable
fun  AudioPlayerUIState(
    noteContentModel: NoteContentModel.MediaContent, onDelete: (NoteContentModel) -> Unit = {}, onSave: () -> Unit = {}
) {
    val noteContent = remember { noteContentModel }
    val viewModel: PlayMediaViewModel = viewModel()
    val localContext = LocalContext.current as Activity
    val state = viewModel.state.collectAsStateWithLifecycle()
    val mediaState = state.value.mediaStates[noteContent.id] ?: PlayerUiState(noteContent = noteContent)

    if (mediaState.noteContent.position == noteContent.position) {
        AudioPlayView(state = mediaState, onDelete = {
            viewModel.onPlayingIntent(MediaPlayingUIEvent.PlayOrPauseUIEvent(mediaId = noteContent.id))
            onDelete(noteContentModel)
        }, onSeek = {
            viewModel.onPlayingIntent(MediaPlayingUIEvent.SeekToUIEvent(it, noteContent.id))
        }, onPlay = {
            if (!state.value.isServiceIsRunning) localContext.startServiceWrapper(Intent(localContext, PustakmMediaPlayerService::class.java))
            viewModel.onPlayingIntent(MediaPlayingUIEvent.SelectedMediaChange(noteContent.id))
        },
            onSave = onSave,
            // 📥 21-Sep-2026 — the LIVE block, not the remembered copy: that never learns the asset id arriving later
            media = noteContentModel,
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun AudioPlayView(
    state: PlayerUiState, onDelete: () -> Unit = {}, onPlay: () -> Unit = {}, onSeek: (Float) -> Unit = {},
    onSave : () -> Unit = {},
    media: NoteContentModel.MediaContent = state.noteContent,
) {

    val iconModifier = Modifier.size(28.dp)
    val interactionSource = remember { MutableInteractionSource() }
    Card(modifier = Modifier.padding(8.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 12.dp
        )
    ) {
        Column {
            Box(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp).fillMaxWidth(),
            ) {
                Text(
                    state.totalDuration, style = typography.labelSmall, modifier = Modifier.align(Alignment.TopStart)
                )
                Text(
                    state.timeElapsed, style = typography.labelMedium, modifier = Modifier.align(Alignment.TopCenter)
                )
                Text(
                    state.timeRemaining, style = typography.labelSmall, modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = 6.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)
            ) {
                Slider(
                    modifier = Modifier.weight(0.5f).padding(horizontal = 6.dp).padding(bottom = 4.dp),
                    value = state.progress,
                    valueRange = 0f..100f,
                    onValueChange = {newValue ->
                        onSeek(newValue)
                    },
                    interactionSource = interactionSource,
                    track = { sliderPositions ->
                        SliderDefaults.Track(
                            colors = SliderDefaults.colors(
                                thumbColor = colorScheme.secondary,
                                inactiveTrackColor = colorScheme.onSurface.copy(0.5f),
                                activeTrackColor = colorScheme.secondary
                            ),
                            sliderState = sliderPositions,
                            modifier = Modifier.height(8.dp),
                        )
                    },
                    thumb = {
                        SliderDefaults.Thumb(
                            colors = SliderDefaults.colors(
                               thumbColor =  colorScheme.secondary,
                            ),
                            modifier = Modifier.scale(scaleX = 0.5f, scaleY =1f),
                            interactionSource = interactionSource,
                        )
                    }
                )
                IconButton(onClick = onPlay) {
                    val drawable = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow
                    Icon(
                        imageVector = drawable, contentDescription = "", tint = colorScheme.secondary, modifier = iconModifier
                    )
                }
                IconButton(onClick = onSave) {
                    Icon(
                        Icons.Default.SaveAlt, contentDescription = "", tint = colorScheme.error, modifier = iconModifier
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete, contentDescription = "", tint = colorScheme.error, modifier = iconModifier
                    )
                }
            }
            // 📥 20-Sep-2026 — the generic transfer bar; draws nothing once the bytes are here
            MediaDownloadOverlay(media)
        }
    }
}

@Preview("default")
@Preview("dark theme", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview("large font", fontScale = 2f)
@Composable
private fun AudioPlayerPreview() {
    MyApplicationTheme {
        val state = PlayerUiState(
            totalDuration = "00 sec", progress = 10f, timeRemaining = "00:20",
            timeElapsed = "00:20",
            duration = 100, noteContent = NoteContentModel.MediaContent(
                noteId = "",
                duration = 100, position = 1.0, type = ContentType.AUDIO,
                updatedAt = "",
                createdAt = "",
                id = "1233",
                localPath = "",
                url = "",
                title ="",
                mimeType = "image",
                sizeBytes = 0,
                width = 10,
                height = 10,
                thumbnailPath = ""
            )
        )
        AudioPlayView(state = state)
    }
}
