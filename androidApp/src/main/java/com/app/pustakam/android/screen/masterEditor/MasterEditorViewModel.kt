package com.app.pustakam.android.screen.masterEditor

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.pustakam.android.fileUtils.deleteFile
import com.app.pustakam.android.fileUtils.generateThumbnail
import com.app.pustakam.android.noteContentProvider.addContent
import com.app.pustakam.android.fileimport.FileImportManager
import com.app.pustakam.android.fileimport.ImportResult
import com.app.pustakam.android.screen.CANVAS_CODES
import com.app.pustakam.android.screen.NOTES_CODES
import com.app.pustakam.android.screen.TaskCode
import com.app.pustakam.android.screen.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.app.pustakam.core.model.models.response.notes.Note
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import com.app.pustakam.core.model.models.response.notes.NoteContentObjectHelper
import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.common.util.Error
import com.app.pustakam.core.common.util.displayMessage
import com.app.pustakam.core.richtext.codec.RichTextCodec
import com.app.pustakam.core.richtext.master.model.CanvasDocument
import com.app.pustakam.core.richtext.master.model.CanvasNode
import com.app.pustakam.core.richtext.master.model.Viewport
import com.app.pustakam.core.richtext.master.presentation.CanvasCommands
import com.app.pustakam.core.richtext.master.presentation.CanvasEditorIntent
import com.app.pustakam.core.richtext.master.presentation.CanvasEditorReducer
import com.app.pustakam.core.richtext.master.presentation.CanvasEditorState
import com.app.pustakam.core.richtext.master.presentation.MasterTextIntent
import com.app.pustakam.core.richtext.master.presentation.MasterTextReducer
import com.app.pustakam.core.richtext.master.presentation.MasterTextState
import com.app.pustakam.core.richtext.master.presentation.NoteCanvasConverter
import com.app.pustakam.feature.notes.domain.editor.EditorCapabilityState
import com.app.pustakam.feature.notes.domain.editor.EditorEffect
import com.app.pustakam.feature.notes.domain.editor.EditorIntent
import com.app.pustakam.feature.notes.domain.editor.EditorReducer
import com.app.pustakam.feature.notes.domain.editor.EditorState
import com.app.pustakam.feature.notes.domain.usecase.ClearCanvasUseCase
import com.app.pustakam.feature.notes.domain.usecase.DeleteNoteContentUseCase
import com.app.pustakam.feature.notes.domain.usecase.MoveCanvasNodeUseCase
import com.app.pustakam.feature.notes.domain.usecase.ObserveNoteContentsUseCase
import com.app.pustakam.feature.notes.domain.usecase.ReadCanvasUseCase
import com.app.pustakam.feature.notes.domain.usecase.ReadCanvasViewportUseCase
import com.app.pustakam.feature.notes.domain.usecase.RemoveCanvasNodeUseCase
import com.app.pustakam.feature.notes.domain.usecase.RenameCanvasNodeUseCase
import com.app.pustakam.feature.notes.domain.usecase.ResizeCanvasNodeUseCase
import com.app.pustakam.feature.notes.domain.usecase.SaveCanvasNodeUseCase
import com.app.pustakam.feature.notes.domain.usecase.SaveCanvasNodesUseCase
import com.app.pustakam.feature.notes.domain.usecase.SaveCanvasViewportUseCase
import com.app.pustakam.feature.notes.domain.usecase.CreateORUpdateNoteUseCase
import com.app.pustakam.feature.notes.domain.usecase.ReadNoteUseCase
import com.app.pustakam.feature.notes.domain.usecase.UpdateSelectedMediaContentUseCase
import com.app.pustakam.feature.notes.domain.usecase.SetSelectedNoteContentUseCase
import com.app.pustakam.feature.notes.domain.usecase.ClearSelectedNoteContentUseCase
import com.app.pustakam.core.common.util.Result
import com.app.pustakam.core.model.models.BaseResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject

data class MasterEditorUiState(
    val note: Note? = null,
    val canvas: CanvasEditorState = CanvasEditorState(),
    val texts: Map<String, MasterTextState> = emptyMap(),
    val isLoading: Boolean = false,
    val capabilities: EditorCapabilityState = EditorCapabilityState(),
    val error: String? = null
) {
    fun textFor(nodeId: String): MasterTextState? = texts[nodeId]
}

class MasterEditorViewModel : BaseViewModel(), KoinComponent {

    private val readCanvas by inject<ReadCanvasUseCase>()
    private val readCanvasViewport by inject<ReadCanvasViewportUseCase>()
    private val saveCanvasNode by inject<SaveCanvasNodeUseCase>()
    private val saveCanvasNodes by inject<SaveCanvasNodesUseCase>()

    private val moveCanvasNode by inject<MoveCanvasNodeUseCase>()
    private val resizeCanvasNode by inject<ResizeCanvasNodeUseCase>()
    private val renameCanvasNode by inject<RenameCanvasNodeUseCase>()
    private val removeCanvasNode by inject<RemoveCanvasNodeUseCase>()
    private val clearCanvas by inject<ClearCanvasUseCase>()
    private val saveCanvasViewport by inject<SaveCanvasViewportUseCase>()
    private val readNoteUseCase by inject<ReadNoteUseCase>()
    private val saveNoteUseCase by inject<CreateORUpdateNoteUseCase>()
    private val updateSelectedMediaContentUseCase by inject<UpdateSelectedMediaContentUseCase>()
    private val setSelectedNoteContentUseCase by inject<SetSelectedNoteContentUseCase>()
    private val clearSelectedNoteContentUseCase by inject<ClearSelectedNoteContentUseCase>()
    private val deleteNoteContentUseCase by inject<DeleteNoteContentUseCase>()
    private val observeNoteContents by inject<ObserveNoteContentsUseCase>()

    private var pendingLayoutNodes: List<CanvasNode> = emptyList()
    private var pendingMediaPaths: List<Pair<String, ContentType>> = emptyList()
    private var pendingOpen: (() -> Unit)? = null
    private var contentSyncJob: Job? = null
    private var dirtyContentIds: Set<String> = emptySet()
    private var lastSavedDirtyIds: Set<String> = emptySet()
    private val _state = MutableStateFlow(MasterEditorUiState())
    val state: StateFlow<MasterEditorUiState> = _state.asStateFlow()

    fun load(id: String?) = readFromDataBase(id)

    /**
     * Re-reads the note that is already open. Uses the loaded note's id, never the route id,
     * so coming back to the screen cannot mint a second note when the screen was opened
     * without one — mirrors NoteEditorViewModel.refreshOnResume().
     */
    fun refreshOnResume() {
        val id = _state.value.note?.id ?: return
        if (id.isEmpty()) return
        readFromDataBase(id)
    }

    /** A null id makes the repository hand back a fresh empty note, same as the note editor. */
    fun readFromDataBase(id: String?) {
        makeAWish(NOTES_CODES.READ) {
            readNoteUseCase.invoke(id)
        }
    }

    private fun observeExternalContents(noteId: String) {
        if (noteId.isEmpty()) return
        contentSyncJob?.cancel()
        contentSyncJob = viewModelScope.launch {
            observeNoteContents(noteId).collect { contents ->
                onEditorIntent(EditorIntent.ExternalContentsChanged(contents))
                adoptOrphanContents()
                refreshMissingTexts()
            }
        }
    }

    private fun adoptOrphanContents() {
        val state = _state.value
        if (CanvasCommands.pageForSpawn(state.canvas) == null) return
        state.note?.contents.orEmpty()
            .filter { state.canvas.document.nodeForContent(it.id) == null }
            .forEach { spawnWidgetNode(it.type, it.id) }
    }

    private fun refreshMissingTexts() {
        val state = _state.value
        val byContentId = state.note?.contents
            ?.filterIsInstance<NoteContentModel.TextContent>()
            ?.associateBy { it.id }
            .orEmpty()
        val updated = state.texts.toMutableMap()
        var changed = false

        state.canvas.document.nodes
            .filter { it.kind == ContentType.TEXT }
            .forEach { node ->
                val content = node.contentId?.let { byContentId[it] } ?: return@forEach
                if (content.id in dirtyContentIds) return@forEach
                val document = RichTextCodec.documentFrom(content)
                if (updated[node.id]?.document != document) {
                    updated[node.id] = MasterTextState.of(document)
                    changed = true
                }
            }

        val stale = updated.keys - state.canvas.document.nodes.map { it.id }.toSet()
        if (stale.isNotEmpty()) {
            stale.forEach { updated.remove(it) }
            changed = true
        }
        if (changed) _state.update { it.copy(texts = updated) }
    }

    private fun readCanvasOf(note: Note) {
        observeExternalContents(note.id)
        makeAWish(CANVAS_CODES.READ_CANVAS, showLoader = false) {
            readCanvas(note.id)
        }
    }

    private fun applyCanvas(document: CanvasDocument) {
        val note = _state.value.note ?: return
        val existing = note.contents.filterIsInstance<NoteContentModel.TextContent>()
        val needsSeed = document.nodes.isEmpty()

        // the first page needs a content row of its own, otherwise its node points at nothing
        // and the page renders as an empty placeholder
        val seedContent =
            if (needsSeed && existing.isEmpty()) {
                NoteContentObjectHelper.createText(noteId = note.id, positionedAt = 0.0)
            } else {
                null
            }
        val textContents = existing + listOfNotNull(seedContent)
        val seededNote = seedContent?.let { note.withContents(note.contents + it) } ?: note

        val nodes = document.nodes.ifEmpty {
            CanvasCommands.stackedTextNodes(textContents.map { it.id }).also { seeded ->
                makeAWish(CANVAS_CODES.SAVE_NODES, showLoader = false) {
                    saveCanvasNodes(note.id, seeded)
                }
            }
        }
        _state.update {
            it.copy(
                note = seededNote,
                canvas = CanvasCommands.loaded(it.canvas, nodes, null),
                texts = textStatesOf(nodes, textContents)
            )
        }
        // a note from the "create empty note" path lives only in memory until now; write it so
        // the canvas has a real parent and a later read by id can find it
        if (needsSeed) {
            makeAWish(NOTES_CODES.UPDATE, showLoader = false) { saveNoteUseCase(seededNote) }
        }
        makeAWish(CANVAS_CODES.READ_VIEWPORT, showLoader = false) {
            readCanvasViewport(note.id)
        }
    }

    private fun applyViewport(viewport: Viewport?) {
        _state.update {
            it.copy(
                isLoading = false,
                canvas = CanvasCommands.loaded(it.canvas, it.canvas.document.nodes, viewport)
            )
        }
    }

    private fun textStatesOf(
        nodes: List<CanvasNode>,
        textContents: List<NoteContentModel.TextContent>
    ): Map<String, MasterTextState> = nodes
        .filter { it.kind == ContentType.TEXT }
        .mapNotNull { node ->
            val content = textContents.firstOrNull { it.id == node.contentId }
                ?: return@mapNotNull null
            node.id to MasterTextState.of(RichTextCodec.documentFrom(content))
        }
        .toMap()

    private fun editorState(): EditorState = _state.value.let {
        EditorState(
            note = it.note,
            contents = it.note?.contents.orEmpty(),
            dirtyContentIds = dirtyContentIds,
            isLoading = it.isLoading,
            error = it.error,
            capabilities = it.capabilities
        )
    }

    fun onEditorIntent(intent: EditorIntent) {
        val before = editorState()
        val next = EditorReducer.reduce(before, intent)
        dirtyContentIds = next.dirtyContentIds
        _state.update {
            it.copy(
                note = next.noteWithContents() ?: next.note,
                isLoading = next.isLoading,
                error = next.error,
                capabilities = next.capabilities
            )
        }
        EditorReducer.effects(before, next, intent).forEach { runEffect(it) }
    }

    private fun runEffect(effect: EditorEffect) {
        when (effect) {
            is EditorEffect.ReadNote -> readFromDataBase(effect.noteId)

            is EditorEffect.SaveNote -> {
                lastSavedDirtyIds = effect.dirtyContentIds
                makeAWish(NOTES_CODES.UPDATE, showLoader = false) {
                    saveNoteUseCase(effect.note, effect.dirtyContentIds)
                }
            }

            is EditorEffect.DeleteContentRow ->
                makeAWish(NOTES_CODES.DELETE, showLoader = false) {
                    deleteNoteContentUseCase.invoke(effect.contentId)
                }

            is EditorEffect.DeleteFiles -> viewModelScope.launch(Dispatchers.IO) {
                effect.paths.forEach { runCatching { deleteFile(filePath = it) } }
            }

            is EditorEffect.PublishMedia -> updateSelectedMediaContentUseCase(effect.content)

            is EditorEffect.MakeThumbnail -> viewModelScope.launch(Dispatchers.IO) {
                val path = generateThumbnail(get(), effect.content.localPath!!, effect.content.type)
                    ?: return@launch
                withContext(Dispatchers.Main) {
                    onEditorIntent(EditorIntent.ThumbnailReady(effect.content.id, path))
                }
            }

            else -> Unit
        }
    }

    fun onCanvasIntent(intent: CanvasEditorIntent) {
        val before = _state.value.canvas
        val next = CanvasEditorReducer.reduce(before, intent)
        _state.update { it.copy(canvas = next) }
        persistCanvasChange(before, next, intent)
    }

    private fun persistCanvasChange(
        before: CanvasEditorState,
        next: CanvasEditorState,
        intent: CanvasEditorIntent
    ) {
        val id = _state.value.note?.id ?: return
        when (intent) {
            // full upsert, not move(): a drop can also have changed the parent page
            is CanvasEditorIntent.EndDrag ->
                before.draggingNodeId
                    ?.let { next.document.nodeById(it) }
                    ?.let { node -> saveNode(id, node) }

            is CanvasEditorIntent.ReparentNode ->
                next.document.nodeById(intent.nodeId)?.let { node -> saveNode(id, node) }

            is CanvasEditorIntent.ResizeNode ->
                next.document.nodeById(intent.nodeId)?.let { node -> resizeNode(node) }

            is CanvasEditorIntent.AddNode -> saveNode(id, intent.node)

            is CanvasEditorIntent.RemoveNode ->
                makeAWish(CANVAS_CODES.REMOVE_NODE, showLoader = false) {
                    removeCanvasNode(intent.nodeId)
                }

            is CanvasEditorIntent.SelectAt,
            is CanvasEditorIntent.SelectNode -> {
                CanvasCommands.fittedPageId(next, intent)
                    ?.let { next.document.nodeById(it) }
                    ?.takeIf { it.rect != before.document.nodeById(it.id)?.rect }
                    ?.let { node -> resizeNode(node) }
                if (before.viewport != next.viewport) saveViewport(id, next.viewport)
            }

            is CanvasEditorIntent.Pan,
            is CanvasEditorIntent.Zoom,
            is CanvasEditorIntent.ZoomTo,
            CanvasEditorIntent.ZoomIn,
            CanvasEditorIntent.ZoomOut,
            CanvasEditorIntent.ZoomToFit,
            is CanvasEditorIntent.FocusNode -> saveViewport(id, next.viewport)

            else -> Unit
        }
    }

    private fun saveNode(noteId: String, node: CanvasNode) {
        makeAWish(CANVAS_CODES.SAVE_NODE, showLoader = false) { saveCanvasNode(noteId, node) }
    }

    private fun resizeNode(node: CanvasNode) {
        makeAWish(CANVAS_CODES.RESIZE_NODE, showLoader = false) {
            resizeCanvasNode(node.id, node.rect.width, node.rect.height)
        }
    }

    private fun saveViewport(noteId: String, viewport: Viewport) {
        makeAWish(CANVAS_CODES.SAVE_VIEWPORT, showLoader = false) {
            saveCanvasViewport(noteId, viewport)
        }
    }

    fun onTextIntent(nodeId: String, intent: MasterTextIntent) {
        val current = _state.value.texts[nodeId] ?: return
        val next = MasterTextReducer.reduce(current, intent)
        _state.update { it.copy(texts = it.texts + (nodeId to next)) }
        if (next.document != current.document) persistText(nodeId, next)
    }

    private fun persistText(nodeId: String, textState: MasterTextState) {
        val note = _state.value.note ?: return
        val contentId = _state.value.canvas.document.nodeById(nodeId)?.contentId ?: return
        val content = note.contents
            .filterIsInstance<NoteContentModel.TextContent>()
            .firstOrNull { it.id == contentId } ?: return

        onEditorIntent(EditorIntent.UpdateContent(RichTextCodec.applyTo(content, textState.document)))
    }

    fun addPage() {
        val note = _state.value.note ?: return
        val content = NoteContentObjectHelper.createText(
            noteId = note.id,
            positionedAt = note.contents.size.toDouble()
        )
        val page = CanvasCommands.pageNode(_state.value.canvas, content.id)
        _state.update {
            it.copy(
                texts = it.texts + (page.id to MasterTextState.of(RichTextCodec.documentFrom(content)))
            )
        }
        onCanvasIntent(CanvasEditorIntent.AddNode(page))
        onCanvasIntent(CanvasCommands.selectNode(page.id))
        onEditorIntent(EditorIntent.AddContent(content))
    }

    fun addWidget(kind: ContentType, content: NoteContentModel? = null) {
        if (kind == ContentType.TEXT) {
            addPage()
            return
        }
        if (_state.value.note == null) return
        if (content != null) onEditorIntent(EditorIntent.AddContent(content))
        spawnWidgetNode(kind, content?.id)
    }

    private fun spawnWidgetNode(kind: ContentType, contentId: String?) {
        val page = CanvasCommands.pageForSpawn(_state.value.canvas)
        if (page == null) {
            addPage()
            spawnWidgetNode(kind, contentId)
            return
        }
        val node = CanvasCommands.widgetIn(_state.value.canvas, page, kind, contentId)
        onCanvasIntent(CanvasEditorIntent.AddNode(node))
    }

    fun deleteContent(contentId: String) {
        val nodeId = _state.value.canvas.document.nodeForContent(contentId)?.id
        if (nodeId != null) deleteNode(nodeId) else removeContentOnly(contentId)
    }

    private fun removeContentOnly(contentId: String) {
        onEditorIntent(EditorIntent.RemoveContent(contentId))
    }

    fun importDeviceFiles(context: Context, uris: List<Uri>) {
        val note = _state.value.note ?: return
        if (uris.isEmpty()) return
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val items = FileImportManager.importUris(
                context.applicationContext, note.id, note.contents.count().toDouble(), uris
            )
            withContext(Dispatchers.Main) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = if (items.isEmpty()) "Couldn't import the selected files." else null
                    )
                }
                landCapturedMedia(items)
            }
        }
    }

    fun importFromLink(context: Context, url: String) {
        val note = _state.value.note ?: return
        if (url.isBlank()) return
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = FileImportManager.importFromUrl(
                context.applicationContext, note.id, note.contents.count().toDouble(), url
            )
            withContext(Dispatchers.Main) {
                _state.update { it.copy(isLoading = false) }
                when (result) {
                    is ImportResult.Success -> landCapturedMedia(result.contents)
                    is ImportResult.Failed ->
                        _state.update { it.copy(error = result.message) }

                    ImportResult.NoFileFound ->
                        _state.update { it.copy(error = "Nothing to import from that link.") }
                }
            }
        }
    }



    /** The reader opens on the stored file, so the note is flushed before we navigate. */
    fun saveThenOpen(onSaved: () -> Unit) {
        val note = _state.value.note ?: run { onSaved(); return }
        pendingOpen = onSaved
        makeAWish(NOTES_CODES.UPDATE, showLoader = false) { saveNoteUseCase(note) }
    }

    private fun runPendingOpen() {
        val open = pendingOpen ?: return
        pendingOpen = null
        viewModelScope.launch(Dispatchers.Main) { open() }
    }

    fun linkedNodes(nodeId: String) = CanvasCommands.linkedNodes(_state.value.canvas, nodeId)

    fun deleteNode(nodeId: String) {
        val note = _state.value.note ?: return
        val contentId = _state.value.canvas.document.nodeById(nodeId)?.contentId
        onCanvasIntent(CanvasCommands.removeNode(nodeId))
        _state.update { it.copy(texts = it.texts - nodeId) }
        if (contentId != null) onEditorIntent(EditorIntent.RemoveContent(contentId))
    }

    fun renameNode(nodeId: String, name: String) {
        onCanvasIntent(CanvasCommands.renameNode(nodeId, name))
        makeAWish(CANVAS_CODES.RENAME_NODE, showLoader = false) {
            renameCanvasNode(nodeId, name)
        }
    }

    fun rebuildLayoutFromNote() {
        val note = _state.value.note ?: return
        val document = NoteCanvasConverter.toCanvas(note.contents)
        onCanvasIntent(CanvasCommands.replaceDocument(document))
        val textContents = note.contents.filterIsInstance<NoteContentModel.TextContent>()
        _state.update { it.copy(texts = textStatesOf(document.nodes, textContents)) }
        // the rewrite has to land after the wipe, so it is chained off CLEAR_CANVAS
        pendingLayoutNodes = document.nodes
        makeAWish(CANVAS_CODES.CLEAR_CANVAS, showLoader = false) { clearCanvas(note.id) }
    }

    fun applyCanvasOrderToNote() {
        val note = _state.value.note ?: return
        val reordered = NoteCanvasConverter.reorderContents(
            note.contents,
            _state.value.canvas.document
        )
        onEditorIntent(EditorIntent.AddContents(reordered))
    }

    fun onCapabilityState(next: EditorCapabilityState) {
        _state.update { it.copy(capabilities = next) }
    }

    fun requestCapture(type: ContentType) {
        onEditorIntent(EditorIntent.CaptureRequested(type))
    }

    /**
     * Builds the content the recorder writes into. Goes through addContent() so the file is
     * created on disk first — createMedia() alone leaves localPath empty and the recorder
     * then opens "". Same call the note editor makes.
     */
    fun addNewContent(context: Context, contentType: ContentType): NoteContentModel? {
        val note = _state.value.note ?: return null
        return addContent(context = context, note = note, contentType = contentType)
    }

    fun onCaptured(content: NoteContentModel?) {
        onEditorIntent(EditorIntent.CaptureFinished)
        val media = content as? NoteContentModel.MediaContent ?: return
        landCapturedMedia(listOf(media))
    }

    fun getMediaData(list: List<Pair<String, ContentType>>) {
        if (list.isEmpty()) return
        val note = _state.value.note ?: run {
            pendingMediaPaths = pendingMediaPaths + list
            return
        }
        var position: Double = note.contents.count().toDouble()
        val captured = list.map { path ->
            val content = NoteContentObjectHelper.createMedia(
                positionedAt = position,
                noteId = note.id,
                localPath = path.first,
                contentType = path.second
            ).copy(title = "${'$'}{path.second}-${'$'}position")
            position += 1.0
            content
        }
        landCapturedMedia(captured)
    }

    private fun consumePendingMediaPaths() {
        if (pendingMediaPaths.isEmpty()) return
        val pending = pendingMediaPaths
        pendingMediaPaths = emptyList()
        getMediaData(pending)
    }

    /** Captured media becomes note content AND a canvas widget, so it shows up on the board. */
    private fun landCapturedMedia(items: List<NoteContentModel.MediaContent>) {
        if (items.isEmpty()) return
        val known = _state.value.note?.contents?.map { it.id }?.toSet().orEmpty()
        val fresh = items.filterNot { it.id in known }
        onEditorIntent(EditorIntent.AddContents(items))
        fresh.forEach { spawnWidgetNode(it.type, it.id) }
    }


    fun askDeleteContent(contentId: String) {
        onEditorIntent(EditorIntent.AskDeleteContent(contentId))
    }

    fun setAttachSheet(visible: Boolean) {
        onEditorIntent(EditorIntent.ShowAttachSheet(visible))
    }

    override fun onSuccess(
        taskCode: TaskCode,
        result: Result.Success<BaseResponse<*>>
    ) {
        when (taskCode) {
            NOTES_CODES.READ -> {
                val note = result.data.data as? Note ?: return
                _state.update { it.copy(note = note, error = null) }
                // 🎧 the canvas plays through the SAME shared player as the note editor — it needs this note's media list
                setSelectedNoteContentUseCase(note)
                readCanvasOf(note)
                consumePendingMediaPaths()
            }

            NOTES_CODES.UPDATE -> {
                val note = result.data.data as? Note ?: return
                dirtyContentIds = dirtyContentIds - lastSavedDirtyIds
                lastSavedDirtyIds = emptySet()
                _state.update { it.copy(note = note, isLoading = false, error = null) }
                runPendingOpen()
            }

            CANVAS_CODES.READ_CANVAS ->
                applyCanvas(result.data.data as? CanvasDocument ?: CanvasDocument())

            CANVAS_CODES.READ_VIEWPORT -> applyViewport(result.data.data as? Viewport)

            CANVAS_CODES.CLEAR_CANVAS -> {
                val note = _state.value.note ?: return
                val nodes = pendingLayoutNodes
                pendingLayoutNodes = emptyList()
                if (nodes.isEmpty()) return
                makeAWish(CANVAS_CODES.SAVE_NODES, showLoader = false) {
                    saveCanvasNodes(note.id, nodes)
                }
            }

            else -> _state.update { it.copy(isLoading = false) }
        }
    }

    override fun onLoading(taskCode: TaskCode) {
        _state.update { it.copy(isLoading = true) }
    }

    override fun clearError() {
        _state.update { it.copy(error = null) }
    }

    override fun onFailure(taskCode: TaskCode, error: Error) {
        super.onFailure(taskCode, error)
        _state.update { it.copy(isLoading = false, error = error.displayMessage()) }
        runPendingOpen()
    }

    // 🎧 the player's list belongs to the screen that is open, exactly as in the note editor
    override fun onCleared() {
        super.onCleared()
        clearSelectedNoteContentUseCase()
    }
}
