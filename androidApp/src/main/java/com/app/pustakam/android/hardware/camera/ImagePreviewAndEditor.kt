package com.app.pustakam.android.hardware.camera

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.pustakam.android.fileUtils.createFileWithFolders
import com.app.pustakam.android.widgets.drawing.DrawingChrome
import com.app.pustakam.android.widgets.drawing.DrawingHistoryButtons
import com.app.pustakam.android.widgets.drawing.DrawingPageLayer
import com.app.pustakam.android.widgets.drawing.rememberCapturesTouches
import com.app.pustakam.android.widgets.zoom.zoomable   // 🔧 19-Jul-2026: pinch-zoom on preview
import com.app.pustakam.core.drawing.note.DrawNoteContents
import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.common.util.ScreenOrientation
import com.app.pustakam.core.common.util.getCurrentTimestamp

/**
 * Screen Handle Following features
 * F1 : Preview Captured Image
 * F2 : Crop Image
 * F3 : Save Image
 * F4 : Discard EditChanges
 * F5 : Draw on Image (pencil tool)
 * F6 : undo and redo changes on Image.
 * */
@Composable
fun ImageEditorScreen(imageDataViewModel: ImageDataViewModel= viewModel(viewModelStoreOwner =  requireNotNull(LocalView.current.findViewTreeViewModelStoreOwner())),
                      landingRoute: String? = null,
                      onDismiss: (route: String?) -> Unit, ) {
    val state = imageDataViewModel
        .mediaFileState.collectAsStateWithLifecycle().value
    val annotation: ImageAnnotationViewModel = viewModel()
    LaunchedEffect(state.noteId, state.mediaId) { annotation.open(state.noteId, state.mediaId) }
    ImagePreviewAndEditor(
        state = state,
        onEditImageAction = imageDataViewModel::onHandleMediaOperation,
        landingRoute = landingRoute,
        onDismiss = onDismiss,
        annotation = annotation
    )
}
@Composable
fun ImagePreviewAndEditor(
    state: MediaFileStateHandler,
    onEditImageAction: (MediaProcessingEvent) -> Unit,
    // where "Done" returns to — the screen that opened the capture, not a fixed route
    landingRoute: String? = null,
    onDismiss: (route: String?) -> Unit,
    modifier: Modifier = Modifier,
    annotation: ImageAnnotationViewModel? = null
) {
    val context = LocalContext.current as Activity
    var islandScapeMode by rememberSaveable {
        mutableStateOf(false)
    }
    val saveImage = {
        val timeStamp = getCurrentTimestamp()
        onEditImageAction(MediaProcessingEvent.OnSaveImage(
            createFileWithFolders(context,
                "${ContentType.IMAGE.name.lowercase()}/${timeStamp}"
                , "${timeStamp}${ContentType.IMAGE.getExt()}")
        ))
    }
    BackHandler(enabled = true) {
        onEditImageAction(MediaProcessingEvent.ScreenOrientationEvent(ScreenOrientation.UNSPECIFIED))
        onDismiss(null)
    }
    DisposableEffect(state.orientation) {
        context.requestedOrientation = when (state.orientation) {
            ScreenOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        onDispose {
            context.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }


    val target = annotation?.target?.collectAsStateWithLifecycle()?.value
    val base = annotation?.base?.collectAsStateWithLifecycle()?.value
    val saving = annotation?.saving?.collectAsStateWithLifecycle()?.value == true
    val inkTarget = annotation?.drawing?.target?.collectAsStateWithLifecycle()?.value
    val inkSession = annotation?.drawing?.overlay?.collectAsStateWithLifecycle()?.value
    val drawing = annotation != null && inkTarget != null && inkTarget == annotation.drawing.overlayId()
    val inkCaptures = rememberCapturesTouches(inkSession)
    val finishDrawing = {
        annotation?.finish {
            val timeStamp = getCurrentTimestamp()
            createFileWithFolders(
                context,
                "${ContentType.IMAGE.name.lowercase()}/${timeStamp}",
                "${timeStamp}${ContentType.IMAGE.getExt()}"
            )
        }
    }
    BackHandler(enabled = drawing) { finishDrawing() }

    Box(modifier = modifier.fillMaxSize()) {
        val shown = if (drawing && base != null) base else state.bitmap
        shown?.asImageBitmap()?.let { image ->
            // 🔧 19-Jul-2026: FIX — preview no longer fills/crops; whole image fits + zoomable
            Box(
                modifier = Modifier.matchParentSize().zoomable(enabled = !(drawing && inkCaptures)),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.aspectRatio(image.width.toFloat() / image.height.coerceAtLeast(1))) {
                    Image(
                        image, contentDescription = "Image Preview",
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.FillBounds
                    )
                    if (drawing && inkSession != null && target != null) {
                        DrawingPageLayer(
                            session = inkSession,
                            anchorId = DrawNoteContents.imageAnchorId(target.id),
                            active = true,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
            }
        }
        if (drawing && inkSession != null) {
            DrawingHistoryButtons(
                session = inkSession,
                tint = colorScheme.onBackground,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)
            )
            DrawingChrome(
                session = inkSession,
                onDone = { finishDrawing() },
                modifier = Modifier
                    .matchParentSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(top = 56.dp)
            )
        } else Row(
            modifier = Modifier.fillMaxWidth()
                .background(color = colorScheme.background.copy(alpha = .5f))
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            horizontalArrangement =Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
               saveImage()
                onDismiss(null)
            }, modifier = Modifier) {
                Icon(imageVector = Icons.Default.Save,
                    contentDescription = "Save Image")
            }
            //todo add image cropping feature

            IconButton(onClick = {
                onEditImageAction(if(state.dataStateEvent == DataStateEvent.Editing) MediaProcessingEvent.CropImage else MediaProcessingEvent.EditImage)
            }, modifier = Modifier) {
                Icon(imageVector =  if(state.dataStateEvent == DataStateEvent.Editing) Icons.Default.Crop else Icons.Filled.Edit,
                    contentDescription = "Crop Image")
            }
            IconButton(
                onClick = {
                       onEditImageAction(MediaProcessingEvent.ScreenOrientationEvent(
                           if(state.orientation == ScreenOrientation.UNSPECIFIED)
                               ScreenOrientation.LANDSCAPE else ScreenOrientation.UNSPECIFIED))
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Autorenew,
                    contentDescription = "Rotate screen"
                )
            }
            if (annotation != null && target != null && base != null) {
                IconButton(onClick = annotation::toggle) {
                    Icon(imageVector = Icons.Default.Brush, contentDescription = "Draw on image")
                }
            }
        }
        if (saving) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        if (!drawing) Button(
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp), onClick = {
                //TODO TBD for other android screen orientation
                onEditImageAction(MediaProcessingEvent.ScreenOrientationEvent(ScreenOrientation.UNSPECIFIED))
                if(state.dataStateEvent == DataStateEvent.Editing) saveImage()
                onDismiss(landingRoute)
            }){ Text("Done") }
    }
}
