package com.app.pustakam.android.widgets.video


import android.app.Activity
import android.content.res.Configuration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
// 🔧 14-Jul-2026: long-press reveal for the save overlay (hover/focus reverted)
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView


import com.app.pustakam.android.MyApplicationTheme
import com.app.pustakam.android.extension.actionIconButtonBackground
import com.app.pustakam.android.hardware.audio.player.MediaPlayingUIEvent
// 🔧 15-Jul-2026 Phase 2.3: thumbnail placeholder for non-current video cards
import com.app.pustakam.android.widgets.LoadImage
import com.app.pustakam.android.widgets.media.MediaDownloadOverlay
import com.app.pustakam.android.hardware.audio.player.PlayMediaViewModel
import com.app.pustakam.android.hardware.audio.player.PlayerUiState
import com.app.pustakam.android.theme.actionIconTintColor
import com.app.pustakam.android.theme.typography
import com.app.pustakam.core.common.extensions.isNotnull
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import com.app.pustakam.core.common.util.ContentType
// 🔧 14-Jul-2026: auto-hide timer for the long-press overlay reveal
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCard(
    modifier: Modifier = Modifier,
    contentVideo: NoteContentModel.MediaContent,
    onClick: () -> Unit,
    onShowActions: (Boolean) -> Unit = {},
    widthFraction: Float = .7f,
    fixedHeight: Dp? = null,
    // 🧱 24-Sep-2026 — the master canvas spaces its cards itself
    outerPadding: Dp = 8.dp,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val viewModel: PlayMediaViewModel = viewModel()
    val noteContent = remember { contentVideo }
    val exoPlayer = remember { viewModel.getExoPlayer() }
    val state = viewModel.state.collectAsStateWithLifecycle()
    val mediaState = state.value.mediaStates[noteContent.id] ?: PlayerUiState(noteContent = noteContent)

    val isCurrentMedia = state.value.currentPlayingId == noteContent.id
    val scope = rememberCoroutineScope()
    val hideJob = remember { mutableStateOf<Job?>(null) }
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val cardHeight = (screenHeightDp * 0.42f).dp.coerceIn(260.dp, 400.dp)

        Card(
            modifier = Modifier.fillMaxWidth(widthFraction)
                .then(if (fixedHeight != null) Modifier.height(fixedHeight) else Modifier.requiredHeight(cardHeight))
                .padding(outerPadding)
                .clickable{ onClick() },
            elevation =CardDefaults.cardElevation(defaultElevation = 6.dp),
            shape = RoundedCornerShape(14.dp),
                ) {
            //Preview Video
            if (mediaState.noteContent.position == noteContent.position)
                Box(Modifier.fillMaxSize()) {
                    if (isCurrentMedia) {
                        VideoPlayer(
                            exoPlayer,
                            Modifier,
                            attachPlayer = true
                        )
                    } else {
                        val thumbnail = mediaState.noteContent.thumbnailPath ?: noteContent.thumbnailPath
                        if (!thumbnail.isNullOrEmpty()) {
                            LoadImage(url = thumbnail, modifier = Modifier.fillMaxSize())
                        } else {
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)))
                        }
                    }
                    VideoControllerUi(
                        state = mediaState,
                        onAction = viewModel::onPlayingIntent
                    )
                    Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "More options",
                        tint = actionIconTintColor,
                        modifier = Modifier
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                            .actionIconButtonBackground()
                            .align(
                                Alignment.TopEnd)
                            .clickable{
                                onShowActions(true)
                                hideJob.value?.cancel()
                                hideJob.value = scope.launch {
                                    delay(2500)
                                    onShowActions(false)
                                }
                            })
                    // 📥 20-Sep-2026 — the generic transfer bar; draws nothing once the bytes are here
                    // 📥 21-Sep-2026 — contentVideo, not the remembered copy: that never learns the asset id arriving later
                    MediaDownloadOverlay(contentVideo, Modifier.align(Alignment.BottomCenter))
                    overlay()
                }
        }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    exoPlayer: ExoPlayer,
    modifier: Modifier = Modifier,
    attachPlayer: Boolean = true
) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            PlayerView(context).also {
                it.player = if (attachPlayer) exoPlayer else null
                it.useController = false
                it.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                it.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT  ,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { view ->
            view.player = if (attachPlayer) exoPlayer else null
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoControllerUi(
    state: PlayerUiState,
    isFullControllerEnabled: Boolean = false,
    onAction: (MediaPlayingUIEvent) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val content = state.noteContent
    val iconModifier = Modifier.size(28.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isFullScreen by remember{ mutableStateOf(   isFullControllerEnabled) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.DarkGray.copy(alpha = 0.1f))
    ) {
        /***
         * Top Action buttons
         */

        /***
         * Middle Action buttons
         */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
        ) {
            //skip previous
            if (isFullScreen) IconButton(onClick = {
                onAction(MediaPlayingUIEvent.SeekToPrevious(content.id))
            }) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious, contentDescription = "Play/Pause Media",
                    tint = colorScheme.primary,
                    modifier = iconModifier
                )
            }

            if (isFullScreen) IconButton(onClick = {
                onAction(MediaPlayingUIEvent.Backward(  mediaId = content.id))
            }) {
                Icon(
                    imageVector = Icons.Filled.Replay10, contentDescription = "forward Media",
                    modifier = iconModifier,
                            tint = colorScheme.primary,
                )
            }
            //play/pause
            IconButton(onClick = {
                onAction(MediaPlayingUIEvent.PlayOrPauseUIEvent(content.id))
            }) {
                val drawable = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow
                Icon(
                    imageVector = drawable, contentDescription = "Play/Pause Media",tint=
                    colorScheme.primary, modifier = Modifier.size(34.dp)
                )
            }
            //forward some seconds
            if (isFullScreen) IconButton(onClick = {
                onAction(MediaPlayingUIEvent.Forward(mediaId = content.id))
            }) {
                Icon(
                    imageVector = Icons.Filled.Forward10, contentDescription = "forward Media", modifier = iconModifier,
                    tint = colorScheme.primary,
                )
            }
            //skip next
            if (isFullScreen) IconButton(onClick = {
                onAction(MediaPlayingUIEvent.SeekNextUIEvent(content.id))
            }) {
                Icon(
                    imageVector = Icons.Filled.SkipNext, contentDescription = "Play/Pause Media",
                    tint = colorScheme.primary, modifier = iconModifier
                )
            }
        }
        /**
         * Back Action buttons
         */
        if(isFullScreen)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Slider(modifier = Modifier
                .padding(horizontal = 4.dp)
                .padding(bottom = 4.dp),
                value = state.progress, valueRange = 0f..100f,
                onValueChange = { newValue ->
                    onAction(MediaPlayingUIEvent.SeekToUIEvent(newValue, content.id))
            }, interactionSource = interactionSource, track = { sliderPositions ->
                SliderDefaults.Track(
                    colors = SliderDefaults.colors(
                        inactiveTrackColor = colorScheme.onSurface.copy(0.5f),
                        activeTrackColor = colorScheme.primary
                    ),
                    thumbTrackGapSize = 0.dp,
                    sliderState = sliderPositions,
                    modifier = Modifier.height(8.dp),
                )
            }, thumb = {
                SliderDefaults.Thumb(
                    thumbSize = DpSize(width = 20.dp, height = 20.dp),
                    modifier = Modifier,
                    interactionSource = interactionSource,
                )
            })
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
                .padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    state.timeElapsed, style = typography.labelMedium,
                    color = colorScheme.primary,modifier = Modifier
                )
                Text(
                    state.timeRemaining, style = typography.labelMedium,
                    color = colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }

        }
    }
}

@Preview("default")
@Preview("dark theme", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview("light theme", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview("large font", fontScale = 2f)

@Composable
private fun PreviewVideoControllerUi() {
    val state = PlayerUiState(
        progress = 30f,
        noteContent = NoteContentModel.MediaContent(
            position = 0.0,
            noteId = "123434",
            type = ContentType.VIDEO,
            updatedAt = "2023-10-27T10:00:00Z",
            createdAt = "2023-10-27T10:00:00Z",
            id = "media_123",
            duration = 120000L,
            localPath = null,
            url = "https://example.com/video.mp4",
            title = "Sample Video",
            mimeType = "video/mp4",
            sizeBytes = 1024L * 1024L,
            width = 1920,
            height = 1080,
            thumbnailPath = null,
        )
    )
    MyApplicationTheme {
        VideoControllerUi(state, isFullControllerEnabled = true)
    }

}