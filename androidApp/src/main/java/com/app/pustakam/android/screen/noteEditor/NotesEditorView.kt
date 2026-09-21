package com.app.pustakam.android.screen.noteEditor

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.IosShare   // 🔧 20-Jul-2026: export action
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu   // 🔧 20-Jul-2026: export format menu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.pustakam.R
import com.app.pustakam.android.MyApplicationTheme
import com.app.pustakam.android.fileimport.IncomingShare
import com.app.pustakam.android.fileUtils.saveMediaToGallery
import com.app.pustakam.android.fileUtils.writeMediaToUri
import com.app.pustakam.android.hardware.camera.ImageDataViewModel
import com.app.pustakam.android.screen.editor.EditorCapabilityCallbacks
import com.app.pustakam.android.screen.editor.EditorCapabilityHost
import com.app.pustakam.android.screen.editor.permissionsFor
import com.app.pustakam.android.screen.NoteContentUiState
import com.app.pustakam.android.screen.OnLifecycleEvent
import com.app.pustakam.android.screen.navigation.Route
import com.app.pustakam.android.theme.typography
import com.app.pustakam.android.widgets.LoadingUI
import com.app.pustakam.android.widgets.SnackBarUi
import com.app.pustakam.android.widgets.audio.AudioPlayerUIState
import com.app.pustakam.android.widgets.audio.AudioRecording
import com.app.pustakam.android.widgets.document.InlineBookFileWidget
import com.app.pustakam.android.widgets.fabWidget.OverLayEditorButtons
import com.app.pustakam.android.widgets.image.ImageCard
import com.app.pustakam.android.widgets.smartText.LocalSmartTextToolbar
import com.app.pustakam.android.widgets.smartText.SmartTextKeyboardToolbarHost
import com.app.pustakam.android.widgets.smartText.SmartTextToolbarReservedHeight
import com.app.pustakam.android.widgets.masterEditor.MasterTextContentWidget
import com.app.pustakam.android.widgets.smartText.rememberSmartTextToolbarController
import com.app.pustakam.core.richtext.codec.RichTextCodec
import com.app.pustakam.android.widgets.video.VideoCard
import com.app.pustakam.android.export.NoteExporter
import com.app.pustakam.android.export.shareExportedFile
import com.app.pustakam.android.export.shareMediaFile
import com.app.pustakam.core.model.models.CameraData
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import com.app.pustakam.core.model.models.response.notes.getMediaUrl
import com.app.pustakam.core.filesys.export.ExportFormat
import com.app.pustakam.core.common.extensions.isNotnull
import com.app.pustakam.core.common.extensions.toLocalFormat
import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.filesys.mime.MimeCatalog
import com.app.pustakam.core.media.naming.MediaFileNaming
import com.app.pustakam.core.filesys.naming.FileNameGenerator.suggestedFileNameFromMedia
import kotlinx.coroutines.flow.MutableStateFlow


@SuppressLint("StateFlowValueCalledInComposition", "SuspiciousIndentation")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    id: String? = null,
    noteEditorViewModel: NoteEditorViewModel = viewModel(),
    imageDataViewModel: ImageDataViewModel = viewModel(),
    onBack: () -> Unit = {},
    navigateTo: (Any) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    var showExportMenu by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    val exportScope = rememberCoroutineScope()
    val runExport: (ExportFormat) -> Unit = { format ->
        val note = noteEditorViewModel.noteContentUiState.value.note
        if (note == null) {
            Toast.makeText(context, "Nothing to export yet", Toast.LENGTH_SHORT).show()
        } else {
            isExporting = true
            exportScope.launch {
                val file = withContext(Dispatchers.IO) { NoteExporter.export(context, note, format) }
                isExporting = false
                if (file != null) shareExportedFile(context, file, format)
                else Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val capabilities = noteEditorViewModel.capabilities.collectAsStateWithLifecycle().value
    if (capabilities.isRecordingAudio) noteEditorViewModel.startStopAudioRecording()
    EditorCapabilityHost(
        state = capabilities,
        noteTitle = noteEditorViewModel.noteContentUiState.value.note?.title.orEmpty(),
        permissions = permissionsFor(capabilities.pendingCapture),
        callbacks = EditorCapabilityCallbacks(
            onState = noteEditorViewModel::onCapabilityState,
            onOpenCamera = {
                noteEditorViewModel.noteContentUiState.value.note?.id
                    ?.let { navigateTo(CameraData(it)) }
            },
            onAudioSaved = { noteEditorViewModel.updateContent(content = it) },
            onFilesPicked = { ctx, uris -> noteEditorViewModel.importDeviceFiles(ctx, uris) },
            onImportLink = { ctx, link -> noteEditorViewModel.importFromLink(ctx, link) },
            onDeleteContent = { noteEditorViewModel.removeContent(it) },
            onDeleteNote = { id?.let { noteId -> noteEditorViewModel.deleteNote(noteId) } }
        )
    )

    OnLifecycleEvent { _, event ->
        when (event) {
            Lifecycle.Event.ON_CREATE -> {
                noteEditorViewModel.changeNoteStatus(null)
                noteEditorViewModel.readFromDataBase(id)
            }
            Lifecycle.Event.ON_RESUME -> {
                noteEditorViewModel.refreshOnResume(id)
            }
            Lifecycle.Event.ON_PAUSE -> {
                noteEditorViewModel.saveNow()
            }

            else -> {}
        }
    }
    BackHandler {
        /**  block direct exit as my scope is getting distroyed  */
        noteEditorViewModel.changeNoteStatus(NoteStatus.onBackPress)
    }

    val state = noteEditorViewModel.noteContentUiState.collectAsStateWithLifecycle()
    val stateEditor = noteEditorViewModel.noteUIState.collectAsStateWithLifecycle().value.apply {
        when {
            isLoading -> LoadingUI()
            error.isNotnull() -> SnackBarUi(error = error!!) {
                noteEditorViewModel.clearError()
            }
        }
    }

    LaunchedEffect(stateEditor.noteStatus) {
        when (stateEditor.noteStatus) {
            NoteStatus.onBackPress -> noteEditorViewModel.createOrUpdateNote()
            NoteStatus.onSaveCompletedExit, NoteStatus.exit -> onBack()
            else -> {}
        }
    }
    val capturedPaths = imageDataViewModel.paths.collectAsStateWithLifecycle().value
    LaunchedEffect(capturedPaths) {
        if (capturedPaths.isNotEmpty()) {
            noteEditorViewModel.getMediaData(capturedPaths)
            imageDataViewModel.clearPaths()
        }
    }
    // 🔧 17-Aug-2026: Open With / Share — wait for the blank note to exist, then reuse the picker's
    //   import path. Clearing the buffer is what stops AppNavGraph re-navigating here.
    val sharedUris = IncomingShare.uris.collectAsStateWithLifecycle().value
    val isNoteReady = state.value.note.isNotnull()
    LaunchedEffect(sharedUris, isNoteReady) {
        if (sharedUris.isNotEmpty() && isNoteReady && id == null) {
            noteEditorViewModel.importSharedFiles(context, sharedUris)
            IncomingShare.clear()
        }
    }
    NotesEditor(
        isRefreshing = stateEditor.isRefreshing,
        onRefresh = { noteEditorViewModel.refresh(id) },
        state = state, topBar = {
        TopAppBar(title = {

        }, colors = TopAppBarDefaults.topAppBarColors(
            colorScheme.background,
        ) ,
            actions = {
            val noteHistory = noteEditorViewModel.history.collectAsStateWithLifecycle().value
                IconButton(
                onClick = { noteEditorViewModel.undo() },
                enabled = noteHistory.canUndo
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                )
            }
            IconButton(
                onClick = { noteEditorViewModel.redo() },
                enabled = noteHistory.canRedo
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                )
            }

                IconButton(
                    onClick = {
                        val route = if (id.isNotnull()) Route.MasterEditor +"/$id" else Route.MasterEditor
                        navigateTo(route)
                    }
                ) {
                    Icon(
                        painterResource(com.app.pustakam.android.R.drawable.workspace),
                        contentDescription = "MasterEditor",
                        Modifier.size(24.dp)
                    )
                }

            IconButton(onClick = {
                    state.value.note?.id?.let {
                        navigateTo(Route.NoteBookReader + "/${it}")
                    }
            }, enabled = noteEditorViewModel.isNoteValid()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = "Open as book",
                )
            }

            Box {
                IconButton(onClick = { showExportMenu = true }, enabled = !isExporting) {
                    Icon(imageVector = Icons.Filled.IosShare, contentDescription = "Export note")
                }
                DropdownMenu(expanded = showExportMenu, onDismissRequest = { showExportMenu = false }) {
                    DropdownMenuItem(text = { Text("Export as PDF") }, onClick = {
                        showExportMenu = false; runExport(ExportFormat.PDF)
                    })
                    DropdownMenuItem(text = { Text("Export as Image") }, onClick = {
                        showExportMenu = false; runExport(ExportFormat.IMAGE)
                    })
                    DropdownMenuItem(text = { Text("Export as Word (DOCX)") }, onClick = {
                        showExportMenu = false; runExport(ExportFormat.DOCX)
                    })
                }
            }
            IconButton(onClick = noteEditorViewModel::createOrUpdateNote) {
                Icon(
                    imageVector = Icons.Filled.Save,
                    contentDescription = "Save",
                )
            }
            IconButton(onClick = noteEditorViewModel::shareNote) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "Share Note",
                )
            }
            if (stateEditor.showDeleteButton) IconButton(onClick = {
                noteEditorViewModel.askDeleteNote()
            }) {
                Icon(
                    Icons.Default.Delete, tint = colorScheme.error,
                    contentDescription = "Delete a note",
                )
            }
        })
    }, onButtonOverLays = {
        Box(Modifier.fillMaxSize()) {
            OverLayEditorButtons(
                modifier = Modifier.align(alignment = Alignment.BottomEnd),
                onAddTextField = {
                    noteEditorViewModel.addNewText()
                },
                onArrowButton = { focusManager.clearFocus() },
                onRecordMic = { noteEditorViewModel.requestCapture(ContentType.AUDIO) },
                onLocation = { noteEditorViewModel.requestCapture(ContentType.LOCATION) },
                onCameraAction = { noteEditorViewModel.requestCapture(ContentType.IMAGE) },
                onImportFile = { noteEditorViewModel.openImportSheet() },
            )
        }
    }, contentList = { focusRequester ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = SmartTextToolbarReservedHeight + 24.dp)
            ) {
                state.value.contents.let {
                    itemsIndexed(it, key = { _, content -> content.id }) { index, contentValue ->
                        RenderWidget(
                            content = contentValue,
                            focusRequester = focusRequester,
                            onUpdate = { value ->
                                noteEditorViewModel.updateContent(index, value)
                            },
                            onDelete = { value -> noteEditorViewModel.askDeleteContent(value.id) },
                            onShare = {},
                            onOpenDocument = {
                                val cid = contentValue.id
                                noteEditorViewModel.saveThenOpen {
                                    state.value.note?.id?.let {
                                        navigateTo(Route.BookReader + "/${it}?contentId=${cid}")
                                    }
                                }
                            },
                            onMediaPreview = {
                                imageDataViewModel.onSetMediaToPreview(
                                    (contentValue as NoteContentModel.MediaContent).getMediaUrl(),
                                    contentValue.type,
                                    mediaId = contentValue.id
                                )
                                when {
                                    contentValue.type == ContentType.IMAGE  -> navigateTo(Route.ImagePreview)
                                    contentValue.type == ContentType.VIDEO -> navigateTo(Route.VideoPreview)
                                }
                            })
                    }
                }
            }
            if (stateEditor.showAudioRecorder) {
                val recordingContent = remember {
                    noteEditorViewModel.addNewContent(
                        context,
                        contentType = ContentType.AUDIO
                    ) as NoteContentModel.MediaContent
                }
                AudioRecording(
                    modifier = Modifier.align(Alignment.TopEnd),
                    noteContentModel = recordingContent,
                    onStop = {
                        noteEditorViewModel.updateContent(content = it)
                        noteEditorViewModel.startStopAudioRecording(false)
                    },
                )
            }
        }
    })


}

@Composable
fun rememberFocusRequester() = remember { FocusRequester() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesEditor(
    state: State<NoteContentUiState>,
    topBar: @Composable () -> Unit,
    contentList: @Composable (FocusRequester) -> Unit,
    onButtonOverLays: @Composable () -> Unit,
    // 🔄 28-Aug-2026 — pull to refresh inside the editor. Defaulted so the preview call site
    //   further down this file keeps compiling untouched.
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    val isRuledEnabledState = remember { mutableStateOf(false) }
    val focusRequester = rememberFocusRequester()
    val focusManager = LocalFocusManager.current
    val paddingLeft = if (isRuledEnabledState.value) 100.dp else 12.dp
    val smartTextToolbar = rememberSmartTextToolbarController()
    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(topBar = topBar, floatingActionButton = onButtonOverLays) { padding ->
            // 🔄 28-Aug-2026 — pull down here to fetch this note's latest content from the server
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    // 🔧 07-Aug-2026 — without this the list keeps its full height and the
                    //   focused line ends up underneath the keyboard
                    .imePadding()
            ) {
                if (isRuledEnabledState.value) RuledPage()
                CompositionLocalProvider(LocalSmartTextToolbar provides smartTextToolbar) {
                    Column {
                        state.value.note?.updatedAt?.let {
                            Text(
                                it.toLocalFormat(), style = typography.bodySmall,
                                modifier = Modifier.padding(start = paddingLeft)
                            )
                        }
                        TextField(
                            value = state.value.titleTextState.value,
                            textStyle = typography.headlineLarge,
                            placeholder = {
                                Text(
                                    "Title : Keep your thoughts alive.",
                                    modifier = Modifier.padding(start = paddingLeft),
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                cursorColor = colorScheme.tertiary
                            ),
                            onValueChange = {
                                state.value.titleTextState.value = it
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = {
                                focusManager.moveFocus(FocusDirection.Down)
                            }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .padding(top = 2.dp)
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            contentList(focusRequester)
                        }
                    }
                }
            }
    }
        SmartTextKeyboardToolbarHost(
            controller = smartTextToolbar,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

}

@Composable
fun RenderWidget(
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = rememberFocusRequester(),
    content: NoteContentModel,
    onUpdate: (content: NoteContentModel) -> Unit,
    onDelete: (content: NoteContentModel) -> Unit,
    onShare: (content: NoteContentModel) -> Unit,
    onMediaPreview: () -> Unit,
    onOpenDocument: () -> Unit = {},   // 🔧 18-Jul-2026: open imported file in the book reader
) {
    var focusedMediaId by remember { mutableStateOf<String?>(null) }
    when (content.type) {
        ContentType.TEXT -> {
            val textContent = content as NoteContentModel.TextContent
            Box(
                modifier = Modifier
            ) {
                MasterTextContentWidget(
                    text = textContent.text,
                    metadata = textContent.metadata,
                    focusRequester = focusRequester,
                    onDocumentChange = { onUpdate(RichTextCodec.applyTo(textContent, it)) },
                    modifier = Modifier.padding( 16.dp)
                )
            }
        }

        ContentType.IMAGE -> {
            val contentImage = content as NoteContentModel.MediaContent
            val path = contentImage.localPath ?: contentImage.url
            ImageCard(
                imageUrl = path, modifier = Modifier, media = contentImage,
                onShowActions = { visible ->
                    focusedMediaId = when {
                        visible -> contentImage.id
                        focusedMediaId == contentImage.id -> null
                        else -> focusedMediaId
                    }
                },
                onClick = onMediaPreview){
                 MediaSaveOverlay(contentImage,
                     isFocused = focusedMediaId == content.id,
                     onDelete = {
                     onDelete(contentImage)
                 },
                     onShare =  {}
                 )
            }

        }

        ContentType.VIDEO -> {
            val contentVideo = content as NoteContentModel.MediaContent

            VideoCard(
                modifier = Modifier,
                contentVideo,
                onShowActions =  { visible ->
                    focusedMediaId = when {
                        visible -> contentVideo.id
                        focusedMediaId == contentVideo.id -> null
                        else -> focusedMediaId
                    }
                },
                onClick = onMediaPreview,

            ) {
                MediaSaveOverlay(
                    contentVideo, onDelete = {
                        onDelete(contentVideo)
                    },
                    isFocused = focusedMediaId == contentVideo.id,
                    onShare = {}
                )
            }
        }

        ContentType.AUDIO -> {
            val contentAudio = content as NoteContentModel.MediaContent
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val exportLauncher = rememberLauncherForActivityResult(
                // 🎧 21-Sep-2026 — MIME follows the file (.m4a now, .mp3 older) or Android saves it as x.m4a.mp3
                contract = CreateDocument(MediaFileNaming.declaredMimeFor(contentAudio.type, "", contentAudio.localPath.orEmpty()))
            ) { uri ->
                if (uri != null) {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { writeMediaToUri(context, contentAudio, uri) }
                        Toast.makeText(
                            context,
                            if (ok) "Saved to selected location" else "Save failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            AudioPlayerUIState(contentAudio, onDelete = onDelete, onSave = {

                    exportLauncher.launch(suggestedFileNameFromMedia(contentAudio))
                })
        }

        ContentType.LINK -> {
            val contentLink = content as NoteContentModel.Link
            Text(contentLink.url, modifier = Modifier.clickable {})
        }
        ContentType.DOCX, ContentType.PDF, ContentType.TXT,
        ContentType.MD, ContentType.EPUB, ContentType.OTHER -> {
            val contentDoc = content as NoteContentModel.MediaContent
            InlineBookFileWidget(
                media = contentDoc,
                onOpenFull = onOpenDocument,
                onPageChanged = {page ->
                    onUpdate(contentDoc.copy(progressPage = page ))
                },
                onShowActions = { visible ->
                    focusedMediaId = when {
                        visible -> contentDoc.id
                        focusedMediaId == contentDoc.id -> null
                        else -> focusedMediaId
                    }
                },
            ) {
                val docShareContext =LocalContext.current
                MediaSaveOverlay(
                    contentDoc,
                    isFocused = focusedMediaId == contentDoc.id,
                    onDelete = { onDelete(contentDoc) },
                    onShare = { shareMediaFile(docShareContext, contentDoc) }
                )
            }
        }

        ContentType.LOCATION -> {
            content as NoteContentModel.Location

        }

        // 🔧 18-Jul-2026: GIF now renders like an image card (was an empty branch)
        ContentType.GIF -> {
            val contentGif = content as NoteContentModel.MediaContent
            ImageCard(
                imageUrl = contentGif.localPath ?: contentGif.url, modifier = Modifier, media = contentGif,
                onShowActions = { visible ->
                    focusedMediaId = when {
                        visible -> contentGif.id
                        focusedMediaId == contentGif.id -> null
                        else -> focusedMediaId
                    }
                },
                onClick = onMediaPreview
            ) {
                MediaSaveOverlay(
                    contentGif,
                    isFocused = focusedMediaId == contentGif.id,
                    onDelete = { onDelete(contentGif) },
                    onShare = {}
                )
            }
        }

        // canvas-only kinds, nothing to draw in the linear editor
        ContentType.DRAWING, ContentType.FORMULA, ContentType.TABLE -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.MediaSaveOverlay(
    media: NoteContentModel.MediaContent,
    isFocused : Boolean = false,
    onDelete: () -> Unit = {},
    onShare : ()-> Unit ={},
) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isGalleryType = media.type == ContentType.IMAGE || media.type == ContentType.VIDEO
    val exportLauncher = rememberLauncherForActivityResult(
        contract = CreateDocument(MimeCatalog.mimeFor(media.type))
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { writeMediaToUri(context, media, uri) }
                Toast.makeText(
                    context,
                    if (ok) "Saved to selected location" else "Save failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    var showSaveSheet by remember { mutableStateOf(false) }

    val saveToGallery: () -> Unit = {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { saveMediaToGallery(context, media) }
            Toast.makeText(
                context,
                if (ok) "Saved to Gallery" else "Save failed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    if(isFocused)
        Row(modifier = Modifier.fillMaxWidth()
            .background(
            Brush.verticalGradient(
                listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = .05f),
                    Color.Black.copy(alpha = .30f),
                    Color.Black.copy(alpha = .55f)
                )
            )
        ).align(Alignment.BottomEnd), horizontalArrangement = Arrangement.End) {
            IconButton(
                onClick = {
                    if (isGalleryType) {
                        // 🔧 14-Jul-2026: CHANGED — was a silent gallery save; now opens the two-option
                        //   bottom sheet so the user picks Gallery vs. a file location.
                        showSaveSheet = true
                    } else {
                        // Opens the picker with the suggested file name; default folder = Downloads.
                        exportLauncher.launch(suggestedFileNameFromMedia(media))
                    }
                },
                modifier = Modifier.padding(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SaveAlt,
                    contentDescription = "Save media to device",
                    tint = colorScheme.primary,
                )
            }
            IconButton(
                onClick = onShare,
                modifier = Modifier
                    .padding(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "Save media to device",
                    tint = colorScheme.primary,
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .padding(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Save media to device",
                    tint = colorScheme.error,
                )
            }
        }

    if (showSaveSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showSaveSheet = false },
            sheetState = sheetState,
        ) {
            ListItem(
                headlineContent = { Text("Save to Gallery") },
                leadingContent = {
                    Icon(Icons.Filled.SaveAlt, contentDescription = null, tint = colorScheme.primary)
                },
                modifier = Modifier.clickable {
                    showSaveSheet = false
                    saveToGallery()
                }
            )
            ListItem(
                headlineContent = { Text("Save to location as file") },
                leadingContent = {
                    Icon(Icons.Filled.SaveAs, contentDescription = null, tint = colorScheme.primary)
                },
                modifier = Modifier.clickable {
                    showSaveSheet = false
                    exportLauncher.launch(suggestedFileNameFromMedia(media))
                }
            )
        }
    }
}

@Composable
fun RuledPage() {
    val lineColor = Color.LightGray
    val marginColor = Color.Red
    Canvas(modifier = Modifier.fillMaxSize()) {
        val lineSpacing = 35.dp.toPx()
        val startX = 80.dp.toPx()
        var y = lineSpacing + 80
        while (y < size.height) {
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
            y += lineSpacing
        }
        drawLine(
            color = marginColor,
            start = Offset(startX, 0f),
            end = Offset(startX, size.height),
            strokeWidth = 2.dp.toPx()
        )
        drawLine(
            color = marginColor,
            start = Offset(startX + 20f, 0f),
            end = Offset(startX + 20f, size.height),
            strokeWidth = 2.dp.toPx()
        )
    }
}


@Preview("default")
@Preview("dark theme", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview("large font", fontScale = 2f)
@Composable
private fun NoteEditorPreview() {
    MyApplicationTheme {
        val state = MutableStateFlow(NoteContentUiState()).collectAsStateWithLifecycle()
        NotesEditor(state = state, topBar = {}, onButtonOverLays = {
            OverLayEditorButtons()
        }, contentList = {})
    }
}