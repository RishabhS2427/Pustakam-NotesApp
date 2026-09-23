package com.app.pustakam.android.screen.masterEditor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.pustakam.android.hardware.camera.ImageDataViewModel
import androidx.lifecycle.Lifecycle
import com.app.pustakam.android.screen.OnLifecycleEvent
import com.app.pustakam.android.screen.editor.EditorCapabilityCallbacks
import com.app.pustakam.android.screen.editor.EditorCapabilityHost
import com.app.pustakam.android.screen.editor.permissionsFor
import com.app.pustakam.android.widgets.audio.AudioRecording
import com.app.pustakam.android.widgets.masterEditor.MasterCanvas
import com.app.pustakam.android.widgets.smartText.SmartTextKeyboardToolbar
import com.app.pustakam.android.widgets.smartText.SmartTextSheet
import com.app.pustakam.android.widgets.smartText.SmartTextSheetHost
import com.app.pustakam.core.richtext.master.presentation.MasterTextCommands
import com.app.pustakam.core.richtext.presentation.SmartTextCommands
import com.app.pustakam.android.widgets.smartText.SmartTextTokens
import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.android.screen.navigation.Route
import com.app.pustakam.core.common.util.isDoc
import com.app.pustakam.core.common.util.isImage
import com.app.pustakam.core.model.models.CameraData
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import com.app.pustakam.core.model.models.response.notes.getMediaUrl

import com.app.pustakam.core.richtext.master.presentation.CanvasCommands
import com.app.pustakam.core.richtext.master.presentation.CanvasTool

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterEditorScreen(
    noteId: String? = null,
    viewModel: MasterEditorViewModel = viewModel(),
    imageDataViewModel: ImageDataViewModel = viewModel(requireNotNull(LocalView.current.findViewTreeViewModelStoreOwner())),
    onBack: () -> Unit = {},
    navigateTo: (Any) -> Unit
) {
    val colors = SmartTextTokens.colors
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val canvas = uiState.canvas
    var showAttach by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(SmartTextSheet.NONE) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val imeHeightPx = WindowInsets.ime.getBottom(density).toFloat()

    OnLifecycleEvent { _, event ->
        when (event) {
            Lifecycle.Event.ON_CREATE -> viewModel.load(noteId)
            Lifecycle.Event.ON_RESUME -> viewModel.refreshOnResume()
            else -> {}
        }
    }

    // 📄 a note opens on its LAST page, fitted to the screen and scrolled to its end — the same
    //   as tapping that page. The keyboard stays down until a text widget is tapped.
    var autoFocused by remember(noteId) { mutableStateOf(false) }
    val autoFocusId = CanvasCommands.lastPageId(canvas)

    LaunchedEffect(autoFocusId, canvas.viewport.widthPx, uiState.canvasReady) {
        if (!autoFocused &&
            uiState.canvasReady &&
            autoFocusId != null &&
            canvas.viewport.widthPx > 0f &&
            canvas.editingNodeId == null
        ) {
            autoFocused = true
            viewModel.onCanvasIntent(CanvasCommands.focusPage(autoFocusId))
        }
    }

    // 📄 leaving edit mode — a tap on paper, on bare canvas, or the toolbar's dismiss — puts the
    //   keyboard away; editingNodeId going null is the only signal, so it is watched here
    val focusManager = LocalFocusManager.current
    val editingId = canvas.editingNodeId
    LaunchedEffect(editingId) {
        if (editingId == null) focusManager.clearFocus()
    }

    val capturedPaths = imageDataViewModel.paths.collectAsStateWithLifecycle().value
    LaunchedEffect(capturedPaths) {
        if (capturedPaths.isNotEmpty()) {
            viewModel.getMediaData(capturedPaths)
            imageDataViewModel.clearPaths()
        }
    }

    EditorCapabilityHost(
        state = uiState.capabilities,
        noteTitle = uiState.note?.title.orEmpty(),
        permissions = permissionsFor(uiState.capabilities.pendingCapture),
        callbacks = EditorCapabilityCallbacks(
            onState = viewModel::onCapabilityState,
            onOpenCamera = { uiState.note?.id?.let { it1 -> navigateTo(CameraData (it1)) } },
            onAudioSaved = viewModel::onCaptured,
            onFilesPicked = { context, uris -> viewModel.importDeviceFiles(context, uris) },
            onImportLink = { context, link -> viewModel.importFromLink(context, link) },
            onDeleteContent = viewModel::deleteContent,
            onDeleteNote = { onBack() }
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.page)
    ) {
        MasterCanvas(
            state = canvas,
            onIntent = viewModel::onCanvasIntent,
            onRename = viewModel::renameNode,
            onMeasured = viewModel::onWidgetMeasured,
            keyboardInsetPx = imeHeightPx
        ) { node, isEditing ->
            val nodeContent = uiState.note?.contents?.firstOrNull { it.id == node.contentId }
            MasterNodeContent(
                node = node,
                isEditing = isEditing,
                scale = canvas.viewport.scale,
                textState = uiState.textFor(node.id),
                content = nodeContent,
                onTextIntent = { viewModel.onTextIntent(node.id, it) },
                onFocused = { viewModel.onCanvasIntent(CanvasCommands.setEditing(node.id)) },
                onOpenMedia = {
                    val media = nodeContent as? NoteContentModel.MediaContent
                    when {
                        media == null -> Unit
                        // documents open in the reader, which reads the saved file off disk
                        media.type.isDoc() -> viewModel.saveThenOpen {
                            uiState.note?.id?.let { id ->
                                navigateTo(Route.BookReader + "/$id?contentId=${media.id}")
                            }
                        }

                        else -> {
                            imageDataViewModel.onSetMediaToPreview(
                                media.getMediaUrl(),
                                media.type,
                                mediaId = media.id
                            )
                            when {
                                media.type.isImage() -> navigateTo(Route.ImagePreview)
                                media.type == ContentType.VIDEO -> navigateTo(Route.VideoPreview)
                                else -> Unit
                            }
                        }
                    }
                },
                onDelete = { viewModel.deleteNode(node.id) },
                keyboardInsetPx = imeHeightPx
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 16.dp)
                .background(colors.toolbar, RoundedCornerShape(12.dp))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(onClick = { viewModel.onCanvasIntent(CanvasCommands.zoomOut()) }) {
                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out", tint = colors.onSurface)
            }
            Text(
                text = "${canvas.zoomPercent}%",
                style = TextStyle(color = colors.onSurfaceMuted, fontSize = 13.sp)
            )
            IconButton(onClick = { viewModel.onCanvasIntent(CanvasCommands.zoomIn()) }) {
                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in", tint = colors.onSurface)
            }
            IconButton(
                onClick = { showAttach = true },

            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom in", tint = colors.onSurface)
            }
            IconButton(onClick = { viewModel.onCanvasIntent(CanvasCommands.zoomToFit()) }) {
                Icon(
                    Icons.Default.CenterFocusStrong,
                    contentDescription = "Fit to screen",
                    tint = colors.onSurface
                )
            }
            CanvasCommands.tools().forEach { tool ->
                val active = canvas.tool == tool
                IconButton(onClick = { viewModel.onCanvasIntent(CanvasCommands.setTool(tool)) }) {
                    Icon(
                        imageVector = when (tool) {
                            CanvasTool.SELECT -> Icons.Default.TouchApp
                            CanvasTool.HAND -> Icons.Default.PanTool
                            CanvasTool.ZOOM -> Icons.Default.ZoomIn
                            CanvasTool.LOCK -> Icons.Default.Lock
                        },
                        contentDescription = CanvasCommands.toolLabel(tool),
                        tint = if (active) colors.accent else colors.onSurface
                    )
                }
            }
        }
        if (uiState.capabilities.isRecordingAudio) {
            val recordingContent = remember {
                viewModel.addNewContent(context, ContentType.AUDIO)
                    as? NoteContentModel.MediaContent
            }
            if (recordingContent != null) {
                AudioRecording(
                    modifier = Modifier.align(Alignment.TopEnd),
                    noteContentModel = recordingContent,
                    onStop = { viewModel.onCaptured(it) },
                    onDelete = { viewModel.onCaptured(null) }
                )
            }
        }

        val focusedText = canvas.editingNodeId?.let { uiState.textFor(it) }
        if (focusedText != null) {
            SmartTextKeyboardToolbar(
                toolbar = focusedText.toolbar,
                expanded = focusedText.isToolbarExpanded,
                canUndo = focusedText.canUndo,
                canRedo = focusedText.canRedo,
                onAction = { action ->
                    val nodeId = canvas.editingNodeId ?: return@SmartTextKeyboardToolbar
                    if (SmartTextCommands.isMore(action)) {
                        viewModel.onTextIntent(
                            nodeId,
                            MasterTextCommands.setToolbarExpanded(!focusedText.isToolbarExpanded)
                        )
                    } else if (SmartTextCommands.isDismiss(action)) {
                        viewModel.onCanvasIntent(CanvasCommands.setEditing(null))
                    } else {
                        val intent = MasterTextCommands.forToolbar(action)
                        if (intent != null) {
                            viewModel.onTextIntent(nodeId, intent)
                        } else {
                            sheet = SmartTextSheet
                                .fromIndex(MasterTextCommands.sheetIndex(action))
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
            )
        }

        val sheetNodeId = canvas.editingNodeId
        if (sheet != SmartTextSheet.NONE && focusedText != null && sheetNodeId != null) {
            SmartTextSheetHost(
                sheet = sheet,
                currentStyle = focusedText.toolbar.paragraphStyle,
                currentAlign = focusedText.toolbar.align,
                currentFontSize = focusedText.toolbar.fontSize,
                currentLink = focusedText.toolbar.link,
                searchQuery = "",
                replacement = "",
                matchCount = 0,
                currentMatch = 0,
                tableRowCount = 0,
                tableColumnCount = 0,
                onDismiss = { sheet = SmartTextSheet.NONE },
                onStyle = {
                    viewModel.onTextIntent(sheetNodeId, MasterTextCommands.setParagraphStyle(it))
                    sheet = SmartTextSheet.NONE
                },
                onAlign = {
                    viewModel.onTextIntent(sheetNodeId, MasterTextCommands.setAlignment(it))
                    sheet = SmartTextSheet.NONE
                },
                onColor = {
                    val intent = if (sheet == SmartTextSheet.BACKGROUND_COLOR) {
                        MasterTextCommands.setBackgroundColor(it)
                    } else {
                        MasterTextCommands.setTextColor(it)
                    }
                    viewModel.onTextIntent(sheetNodeId, intent)
                    sheet = SmartTextSheet.NONE
                },
                onFontSize = {
                    viewModel.onTextIntent(sheetNodeId, MasterTextCommands.setFontSize(it))
                    sheet = SmartTextSheet.NONE
                },
                onLink = {
                    viewModel.onTextIntent(sheetNodeId, MasterTextCommands.setLink(it))
                    sheet = SmartTextSheet.NONE
                },
                onTable = { sheet = SmartTextSheet.NONE },
                onInsertTable = { _, _ -> sheet = SmartTextSheet.NONE },
                onSearchQuery = {},
                onReplacement = {},
                onFindNext = {},
                onFindPrevious = {},
                onReplaceCurrent = {},
                onReplaceAll = {}
            )
        }

        if (showAttach) {
            ModalBottomSheet(onDismissRequest = { showAttach = false }) {
                Column(modifier = Modifier.padding(bottom = 24.dp)) {
                    Text(
                        text = "Add beside the focused widget",
                        style = TextStyle(color = colors.onSurfaceMuted, fontSize = 13.sp),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                    Text(
                        text = "Rebuild layout from note order",
                        style = TextStyle(color = colors.accent, fontSize = 16.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAttach = false
                                viewModel.rebuildLayoutFromNote()
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    )
                    Text(
                        text = "Apply canvas order back to the note",
                        style = TextStyle(color = colors.accent, fontSize = 16.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAttach = false
                                viewModel.applyCanvasOrderToNote()
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    )
                    Text(
                        text = "New page",
                        style = TextStyle(color = colors.onSurface, fontSize = 16.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAttach = false
                                viewModel.addPage()
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    )
                    listOf(
                        "Text block" to ContentType.TEXT,
                        "Table" to ContentType.TABLE,
                        "Drawing" to ContentType.DRAWING
                    ).forEach { (label, kind) ->
                        Text(
                            text = label,
                            style = TextStyle(color = colors.onSurface, fontSize = 16.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAttach = false
                                    viewModel.addWidget(kind)
                                }
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        )
                    }

                    listOf(
                        "Photo or video" to ContentType.IMAGE,
                        "Record audio" to ContentType.AUDIO,
                        "Location" to ContentType.LOCATION
                    ).forEach { (label, kind) ->
                        Text(
                            text = label,
                            style = TextStyle(color = colors.onSurface, fontSize = 16.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAttach = false
                                    viewModel.requestCapture(kind)
                                }
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        )
                    }
                }
            }
        }
    }
}
