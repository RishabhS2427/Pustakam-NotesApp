package com.app.pustakam.android.screen.masterEditor

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.pustakam.android.fileUtils.deleteFilesLater
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
import com.app.pustakam.core.filesys.canvas.CanvasPaginator
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
import com.app.pustakam.feature.notes.domain.editor.NoteFiles
import com.app.pustakam.feature.notes.domain.history.NoteEditKind
import com.app.pustakam.feature.notes.domain.history.NoteHistory
import com.app.pustakam.feature.notes.domain.history.NoteHistoryStep
import com.app.pustakam.feature.notes.domain.history.NoteSnapshots
import com.app.pustakam.feature.notes.domain.usecase.DeleteNoteContentUseCase
import com.app.pustakam.feature.notes.domain.usecase.MoveCanvasNodeUseCase
import com.app.pustakam.feature.notes.domain.usecase.ObserveNoteContentsUseCase
import com.app.pustakam.feature.notes.domain.usecase.ObserveRemoteCanvasUseCase
import com.app.pustakam.feature.notes.domain.usecase.PruneCanvasOrphansUseCase
import com.app.pustakam.feature.notes.domain.usecase.SaveCanvasEditUseCase
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import com.app.pustakam.android.widgets.drawing.DrawingHost
import com.app.pustakam.core.drawing.editor.DrawCommands
import com.app.pustakam.core.drawing.note.DrawNoteContents
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
    // 📄 true once the canvas AND its saved viewport are in: only then may the first page be fitted
    val canvasReady: Boolean = false,
    val capabilities: EditorCapabilityState = EditorCapabilityState(),
    val history: NoteHistory = NoteHistory(),
    val error: String? = null
) {
    fun textFor(nodeId: String): MasterTextState? = texts[nodeId]
}

class MasterEditorViewModel : BaseViewModel(), KoinComponent {

    private val readCanvas by inject<ReadCanvasUseCase>()
    private val readCanvasViewport by inject<ReadCanvasViewportUseCase>()
    private val saveCanvasNode by inject<SaveCanvasNodeUseCase>()
    private val saveCanvasNodes by inject<SaveCanvasNodesUseCase>()
    private val saveCanvasEdit by inject<SaveCanvasEditUseCase>()
    private val observeRemoteCanvas by inject<ObserveRemoteCanvasUseCase>()
    private val pruneCanvasOrphans by inject<PruneCanvasOrphansUseCase>()

    private val moveCanvasNode by inject<MoveCanvasNodeUseCase>()
    private val resizeCanvasNode by inject<ResizeCanvasNodeUseCase>()
    private val renameCanvasNode by inject<RenameCanvasNodeUseCase>()
    private val removeCanvasNode by inject<RemoveCanvasNodeUseCase>()
    private val saveCanvasViewport by inject<SaveCanvasViewportUseCase>()
    private val readNoteUseCase by inject<ReadNoteUseCase>()
    private val saveNoteUseCase by inject<CreateORUpdateNoteUseCase>()
    private val updateSelectedMediaContentUseCase by inject<UpdateSelectedMediaContentUseCase>()
    private val setSelectedNoteContentUseCase by inject<SetSelectedNoteContentUseCase>()
    private val clearSelectedNoteContentUseCase by inject<ClearSelectedNoteContentUseCase>()
    private val deleteNoteContentUseCase by inject<DeleteNoteContentUseCase>()
    private val observeNoteContents by inject<ObserveNoteContentsUseCase>()

    private var pendingCanvas: CanvasDocument? = null
    // 🔄 24-Sep-2026 — another device's canvas waits here while a finger is busy; what this device touches meanwhile stays its own
    private var pendingRemote: List<CanvasNode>? = null
    private val touchedWhilePending = mutableSetOf<String>()
    private var remoteCanvasJob: Job? = null
    // 🔄 24-Sep-2026 — the board as it was when a drag or resize began, so letting go without moving stamps nothing
    private var gestureStart: CanvasDocument? = null
    private var pendingMediaPaths: List<Pair<String, ContentType>> = emptyList()
    private var pendingOpen: (() -> Unit)? = null
    private var contentSyncJob: Job? = null
    private var dirtyContentIds: Set<String> = emptySet()
    private var lastSavedDirtyIds: Set<String> = emptySet()
    private var editDepth = 0
    private var editStart: Note? = null
    private val trashedFiles = mutableSetOf<String>()
    private val _state = MutableStateFlow(MasterEditorUiState())
    val state: StateFlow<MasterEditorUiState> = _state.asStateFlow()
    val drawing = DrawingHost(
        contents = { _state.value.note?.contents.orEmpty() },
        noteId = { _state.value.note?.id },
        documentId = { null },
        onWrite = { content -> edit(NoteEditKind.DRAWING) { onEditorIntent(EditorIntent.UpdateContent(content)) } }
    )

    init {
        viewModelScope.launch {
            _state.map { it.note?.contents }
                .distinctUntilChanged { old, new -> old === new }
                .collect { drawing.sync(it.orEmpty()) }
        }
    }

    fun load(id: String?) = readFromDataBase(id)

    private fun snapshot(document: CanvasDocument = _state.value.canvas.document): Note? {
        val state = _state.value
        val note = state.note ?: return null
        if (!state.canvasReady) return null
        return NoteSnapshots.masterEditor(note, note.contents, document)
    }

    private fun <T> edit(kind: NoteEditKind, block: () -> T): T {
        if (editDepth == 0) editStart = snapshot()
        editDepth++
        try {
            return block()
        } finally {
            editDepth--
            if (editDepth == 0) {
                val start = editStart
                editStart = null
                _state.update { it.copy(history = it.history.track(start, snapshot(), kind)) }
            }
        }
    }

    fun undo() {
        val current = snapshot() ?: return
        _state.value.history.undoStep(current)?.let(::travel)
    }

    fun redo() {
        val current = snapshot() ?: return
        _state.value.history.redoStep(current)?.let(::travel)
    }

    private fun travel(step: NoteHistoryStep) {
        editDepth++
        try {
            _state.update { it.copy(history = step.history) }
            dirtyContentIds = dirtyContentIds + step.dirtyIds - step.removed.map { it.id }.toSet()
            step.removed.forEach(::discard)
            _state.update { it.copy(note = it.note?.withContents(step.note.contents)) }
            NoteSnapshots.canvasOf(step.note)
                ?.takeIf { it != _state.value.canvas.document }
                ?.let { dispatchCanvas(CanvasCommands.replaceDocument(it), housekeeping = false) }
            rebuildTexts(step.dirtyIds)
            onEditorIntent(EditorIntent.SaveRequested)
        } finally {
            editDepth--
        }
    }

    private fun discard(content: NoteContentModel) {
        trashedFiles += NoteFiles.pathsOf(content)
        makeAWish(NOTES_CODES.DELETE, showLoader = false) { deleteNoteContentUseCase.invoke(content.id) }
    }

    private fun rebuildTexts(changed: Set<String>) {
        val state = _state.value
        val textContents = state.note?.contents.orEmpty().filterIsInstance<NoteContentModel.TextContent>()
        val fresh = textStatesOf(state.canvas.document.nodes, textContents)
        val kept = fresh.mapValues { (nodeId, built) ->
            val contentId = state.canvas.document.nodeById(nodeId)?.contentId
            state.texts[nodeId]?.takeIf { contentId !in changed } ?: built
        }
        _state.update { it.copy(texts = kept) }
    }

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

    // 🔄 24-Sep-2026 — housekeeping, never a user edit: a content that arrived without a widget gets one
    private fun adoptOrphanContents() {
        val state = _state.value
        if (CanvasCommands.pageForSpawn(state.canvas) == null) return
        state.note?.contents.orEmpty()
            .filter { !it.isOverlayDrawing() && state.canvas.document.nodeForContent(it.id) == null }
            .forEach { spawnWidgetNode(it.type, it.id, housekeeping = true) }
    }

    private fun observeRemoteCanvasOf(noteId: String) {
        if (noteId.isEmpty()) return
        remoteCanvasJob?.cancel()
        remoteCanvasJob = viewModelScope.launch {
            observeRemoteCanvas(noteId).collect { nodes ->
                pendingRemote = nodes
                touchedWhilePending.clear()
                adoptPendingRemote()
            }
        }
    }

    private fun adoptPendingRemote() {
        val nodes = pendingRemote ?: return
        val state = _state.value
        if (!state.canvasReady || !CanvasCommands.canAdoptRemote(state.canvas)) return
        val keep = touchedWhilePending.toSet()
        pendingRemote = null
        touchedWhilePending.clear()
        dispatchCanvas(CanvasCommands.adoptRemote(nodes, keep), housekeeping = true)
        refreshMissingTexts()
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
            .filter { it.isTextWidget }
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
        observeRemoteCanvasOf(note.id)
        // 🔄 24-Sep-2026 — a widget whose content was deleted elsewhere is dropped before the board is read
        makeAWish(CANVAS_CODES.PRUNE_ORPHANS, showLoader = false) { pruneCanvasOrphans() }
    }

    private fun readStoredCanvas() {
        val noteId = _state.value.note?.id ?: return
        makeAWish(CANVAS_CODES.READ_CANVAS, showLoader = false) {
            readCanvas(noteId)
        }
    }

    /** The paper size new pages are cut to — an existing page's, else the fitted screen's. */
    private fun pageWidth(): Float {
        val canvas = _state.value.canvas
        return canvas.document.pages.firstOrNull()?.rect?.width
            ?: CanvasCommands.fittedPageWidth(canvas)
    }

    private fun pageHeight(): Float {
        val canvas = _state.value.canvas
        return canvas.document.pages.firstOrNull()?.rect?.height
            ?: CanvasCommands.fittedPageHeight(canvas)
    }

    /**
     * 📄 23-Sep-2026 — a page now CARRIES contents instead of standing for one, so what arrives
     * from storage takes one of three paths: an old canvas is upgraded in place (pages keep their
     * id, name and rect), an empty one is laid out by [CanvasPaginator] through the reading-mode
     * grouping, and a current one only has to be guaranteed at least one page.
     */
    private fun applyCanvas(document: CanvasDocument) {
        val note = _state.value.note ?: return
        val existing = note.contents.filterIsInstance<NoteContentModel.TextContent>()
        val isNew = document.nodes.isEmpty()

        // an empty note still needs one text row, otherwise its first page has nothing to type into
        val seedContent =
            if (isNew && note.contents.isEmpty()) {
                NoteContentObjectHelper.createText(noteId = note.id, positionedAt = 0.0)
            } else {
                null
            }
        val textContents = existing + listOfNotNull(seedContent)
        val seededNote = seedContent?.let { note.withContents(note.contents + it) } ?: note

        val width = pageWidth()
        val height = pageHeight()
        val rebuilt = when {
            isNew -> CanvasPaginator.build(seededNote.contents, width, height)
            CanvasPaginator.needsUpgrade(document.nodes) ->
                CanvasPaginator.upgrade(document.nodes, width, height)

            else -> CanvasPaginator.ensurePage(document, width, height)
        }
        val nodes = rebuilt.nodes
        if (isNew || nodes != document.nodes) {
            makeAWish(CANVAS_CODES.SAVE_NODES, showLoader = false) {
                saveCanvasNodes(note.id, nodes)
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
        // 🔄 24-Sep-2026 — only then: opening a note that already exists must never re-stamp it
        if (seedContent != null) {
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
                canvasReady = true,
                canvas = CanvasCommands.loaded(it.canvas, it.canvas.document.nodes, viewport)
            )
        }
        // 🔄 24-Sep-2026 — a canvas that arrived meanwhile lands first; contents still without a widget get one after it
        adoptPendingRemote()
        adoptOrphanContents()
    }

    private fun textStatesOf(
        nodes: List<CanvasNode>,
        textContents: List<NoteContentModel.TextContent>
    ): Map<String, MasterTextState> = nodes
        .filter { it.isTextWidget }
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
        if (EditorReducer.isPassive(intent)) edit(NoteEditKind.SYNC) { reduceEditor(intent) } else reduceEditor(intent)
    }

    private fun reduceEditor(intent: EditorIntent) {
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

            is EditorEffect.DeleteFiles -> trashedFiles += effect.paths

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

    fun onCanvasIntent(intent: CanvasEditorIntent) = dispatchCanvas(intent, housekeeping = false)

    // 🔄 24-Sep-2026 — housekeeping (a fit, a measure, a placed orphan, another device's canvas) is written but never stamps the note
    private fun dispatchCanvas(intent: CanvasEditorIntent, housekeeping: Boolean) {
        val before = _state.value.canvas
        val next = CanvasEditorReducer.reduce(before, intent)
        _state.update { it.copy(canvas = next) }
        if (CanvasCommands.gestureStarted(before, next)) gestureStart = next.document
        val edited = !housekeeping && CanvasCommands.editsLayout(before, next, intent, gestureStart)
        if (edited && editDepth == 0) recordLayout(gestureStart ?: before.document)
        val ended = CanvasCommands.gestureEnded(before, next)
        if (ended) gestureStart = null
        persistCanvasChange(before, next, intent, edited)
        val waiting = pendingCanvas
        if (waiting != null && next.viewport.widthPx > 0f) {
            pendingCanvas = null
            applyCanvas(waiting)
        }
        adoptPendingRemote()
        followDrawingEditing(before, next)
        if (ended) syncDrawingFrames(next)
    }

    private fun recordLayout(start: CanvasDocument) {
        val previous = snapshot(start) ?: return
        _state.update { it.copy(history = it.history.record(previous, NoteEditKind.LAYOUT)) }
    }

    /**
     * 📄 23-Sep-2026 — one rule for every intent: whatever the reduce changed is written, what it
     * removed is deleted, and the note's content order follows the canvas when that changed.
     * CanvasCommands decides what "changed" means, so iOS writes exactly the same rows.
     */
    private fun persistCanvasChange(
        before: CanvasEditorState,
        next: CanvasEditorState,
        intent: CanvasEditorIntent,
        edited: Boolean
    ) {
        val id = _state.value.note?.id ?: return
        val changed = CanvasCommands.nodesToSave(before, next)
        val removed = CanvasCommands.removedNodeIds(before, next)
        if (pendingRemote != null) touchedWhilePending += changed.map { it.id } + removed
        if (edited) {
            // 🔄 the rows land first; only then is the note stamped, so the push that follows carries them
            makeAWish(CANVAS_CODES.SAVE_EDIT, showLoader = false) { saveCanvasEdit(id, changed, removed) }
        } else {
            if (changed.isNotEmpty()) {
                makeAWish(CANVAS_CODES.SAVE_NODES, showLoader = false) { saveCanvasNodes(id, changed) }
            }
            removed.forEach { removedId ->
                makeAWish(CANVAS_CODES.REMOVE_NODE, showLoader = false) { removeCanvasNode(removedId) }
            }
        }
        // 🔄 only a user's edit re-orders the note; housekeeping and another device's canvas never re-stamp it
        if (edited && CanvasCommands.orderChanged(before, next)) {
            reorderNoteByCanvas()
        }
        if (before.viewport != next.viewport && CanvasCommands.affectsViewport(intent)) {
            saveViewport(id, next.viewport)
        }
    }

    private fun saveViewport(noteId: String, viewport: Viewport) {
        makeAWish(CANVAS_CODES.SAVE_VIEWPORT, showLoader = false) {
            saveCanvasViewport(noteId, viewport)
        }
    }

    fun onTextIntent(nodeId: String, intent: MasterTextIntent) = edit(NoteEditKind.TEXT) {
        val current = _state.value.texts[nodeId] ?: return@edit
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

    // 📄 24-Sep-2026 — a new page is totally blank: fresh paper beside the last page, fitted to the screen, nothing on it
    fun addPage() = edit(NoteEditKind.LAYOUT) {
        if (_state.value.note == null) return@edit
        val page = addBarePage(housekeeping = false)
        onCanvasIntent(CanvasCommands.selectNode(page.id))
    }

    private fun addBarePage(housekeeping: Boolean): CanvasNode {
        val page = CanvasCommands.pageNode(_state.value.canvas)
        dispatchCanvas(CanvasEditorIntent.AddNode(page), housekeeping)
        return page
    }

    /** A text block is a widget on the current page now — it no longer costs a whole page. */
    fun addTextBlock() = edit(NoteEditKind.ADD_TEXT) {
        val note = _state.value.note ?: return@edit
        val page = CanvasCommands.pageForSpawn(_state.value.canvas) ?: return@edit
        val content = NoteContentObjectHelper.createText(
            noteId = note.id,
            positionedAt = note.contents.size.toDouble()
        )
        val node = CanvasCommands.widgetIn(_state.value.canvas, page, ContentType.TEXT, content.id)
        _state.update {
            it.copy(
                texts = it.texts + (node.id to MasterTextState.of(RichTextCodec.documentFrom(content)))
            )
        }
        onEditorIntent(EditorIntent.AddContent(content))
        onCanvasIntent(CanvasEditorIntent.AddNode(node))
        onCanvasIntent(CanvasCommands.setEditing(node.id))
    }

    fun addWidget(kind: ContentType, content: NoteContentModel? = null) {
        if (kind == ContentType.TEXT && content == null) {
            addTextBlock()
            return
        }
        if (kind == ContentType.DRAWING && content == null) {
            addDrawingWidget()
            return
        }
        if (_state.value.note == null) return
        edit(if (content == null) NoteEditKind.LAYOUT else NoteEditKind.ADD_MEDIA) {
            if (content != null) onEditorIntent(EditorIntent.AddContent(content))
            spawnWidgetNode(kind, content?.id)
            if (kind == ContentType.TEXT) refreshMissingTexts()
        }
    }

    fun addDrawingWidget() = edit(NoteEditKind.DRAWING) {
        val note = _state.value.note ?: return@edit
        val page = CanvasCommands.pageForSpawn(_state.value.canvas) ?: addBarePage(housekeeping = false)
        val draft = DrawNoteContents.create(note.id, note.contents.size.toDouble(), DrawCommands.widgetSurface(), 0f, 0f)
        val node = CanvasCommands.widgetIn(_state.value.canvas, page, ContentType.DRAWING, draft.id)
        val content = DrawNoteContents.framed(draft, node.rect.width, node.rect.height)
        onEditorIntent(EditorIntent.AddContent(content))
        onCanvasIntent(CanvasEditorIntent.AddNode(node))
        dispatchCanvas(CanvasCommands.revealEnd(page.id), housekeeping = true)
        onCanvasIntent(CanvasCommands.setEditing(node.id))
    }

    fun toggleDrawingOverlay() {
        if (!drawing.isOverlayActive()) onCanvasIntent(CanvasCommands.useSelectTool())
        drawing.toggleOverlay()
    }

    fun finishDrawing() {
        if (CanvasCommands.editingDrawingId(_state.value.canvas) != null) {
            onCanvasIntent(CanvasCommands.exitEditing())
        } else {
            drawing.stop()
        }
    }

    private fun followDrawingEditing(before: CanvasEditorState, next: CanvasEditorState) {
        val editing = CanvasCommands.editingDrawingId(next)
        if (editing == CanvasCommands.editingDrawingId(before)) return
        val contentId = editing?.let { next.document.nodeById(it)?.contentId }
        val content = contentId?.let { id -> _state.value.note?.contents?.firstOrNull { it.id == id } }
        when {
            content is NoteContentModel.Drawing -> drawing.open(content)
            !drawing.isOverlayActive() -> drawing.stop()
        }
    }

    private fun syncDrawingFrames(canvas: CanvasEditorState) {
        _state.value.note?.contents.orEmpty()
            .filterIsInstance<NoteContentModel.Drawing>()
            .forEach { content ->
                val node = canvas.document.nodeForContent(content.id)?.takeIf { it.isDrawingWidget } ?: return@forEach
                if (!DrawNoteContents.isFramed(content, node.rect.width, node.rect.height)) {
                    onEditorIntent(EditorIntent.UpdateContent(DrawNoteContents.framed(content, node.rect.width, node.rect.height)))
                }
            }
    }

    /**
     * 📄 the platform reports what a measured widget's content actually is; a text widget that
     * grew pushes whatever is under it down, and the page scrolls further — never spills.
     */
    fun onWidgetMeasured(nodeId: String, height: Float) {
        val node = _state.value.canvas.document.nodeById(nodeId) ?: return
        if (!node.isWidget || height <= 0f) return
        if (kotlin.math.abs(node.rect.height - height) < MEASURE_EPSILON) return
        dispatchCanvas(CanvasCommands.widgetMeasured(nodeId, height), housekeeping = true)
    }

    // 📄 24-Sep-2026 — a new widget lands on the page the user is on, and that page scrolls to show it
    private fun spawnWidgetNode(kind: ContentType, contentId: String?, housekeeping: Boolean = false) {
        val page = CanvasCommands.pageForSpawn(_state.value.canvas) ?: addBarePage(housekeeping)
        val node = CanvasCommands.widgetIn(_state.value.canvas, page, kind, contentId)
        dispatchCanvas(CanvasEditorIntent.AddNode(node), housekeeping)
        if (!housekeeping) dispatchCanvas(CanvasCommands.revealEnd(page.id), housekeeping = true)
    }

    fun deleteContent(contentId: String) = edit(NoteEditKind.DELETE_CONTENT) {
        val nodeId = _state.value.canvas.document.nodeForContent(contentId)?.id
        if (nodeId != null) deleteNode(nodeId) else removeContentOnly(contentId)
    }

    private fun removeContentOnly(contentId: String) {
        val dependents = DrawNoteContents.dependentsOf(_state.value.note?.contents.orEmpty(), contentId)
        onEditorIntent(EditorIntent.RemoveContent(contentId))
        dependents.forEach { onEditorIntent(EditorIntent.RemoveContent(it.id)) }
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

    // 🔄 24-Sep-2026 — the content goes first, so the re-order the removal triggers can never save it back
    fun deleteNode(nodeId: String) = edit(NoteEditKind.DELETE_CONTENT) {
        if (_state.value.note == null) return@edit
        val contentId = _state.value.canvas.document.nodeById(nodeId)?.contentId
        if (contentId != null) removeContentOnly(contentId)
        onCanvasIntent(CanvasCommands.removeNode(nodeId))
        _state.update { it.copy(texts = it.texts - nodeId) }
    }

    fun renameNode(nodeId: String, name: String) = edit(NoteEditKind.LAYOUT) {
        onCanvasIntent(CanvasCommands.renameNode(nodeId, name))
        makeAWish(CANVAS_CODES.RENAME_NODE, showLoader = false) {
            renameCanvasNode(nodeId, name)
        }
    }

    // 🔄 24-Sep-2026 — the rebuilt board replaces the old one in a single edit: new rows written, old ones deleted, then the note travels
    fun rebuildLayoutFromNote() = edit(NoteEditKind.LAYOUT) {
        val note = _state.value.note ?: return@edit
        val document = CanvasPaginator.build(note.contents, pageWidth(), pageHeight())
        onCanvasIntent(CanvasCommands.replaceDocument(document))
        val textContents = note.contents.filterIsInstance<NoteContentModel.TextContent>()
        _state.update { it.copy(texts = textStatesOf(document.nodes, textContents)) }
    }

    fun applyCanvasOrderToNote() = edit(NoteEditKind.REORDER) { reorderNoteByCanvas() }

    private fun reorderNoteByCanvas() {
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
    private fun landCapturedMedia(items: List<NoteContentModel.MediaContent>) = edit(NoteEditKind.ADD_MEDIA) {
        if (items.isEmpty()) return@edit
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

    // 🔄 24-Sep-2026 — every result is handled on the main thread, where the canvas and the dirty ids are changed
    override fun onSuccess(
        taskCode: TaskCode,
        result: Result.Success<BaseResponse<*>>
    ) {
        viewModelScope.launch(Dispatchers.Main) { handleSuccess(taskCode, result) }
    }

    private fun handleSuccess(taskCode: TaskCode, result: Result.Success<BaseResponse<*>>) {
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
                _state.update { state ->
                    val live = state.note?.contents ?: note.contents
                    state.copy(note = note.copy(contents = live), isLoading = false, error = null)
                }
                runPendingOpen()
            }

            // 📄 pages are cut to the fitted screen, so a canvas waits for the board to be measured
            CANVAS_CODES.READ_CANVAS -> {
                val document = result.data.data as? CanvasDocument ?: CanvasDocument()
                if (_state.value.canvas.viewport.widthPx > 0f) applyCanvas(document)
                else pendingCanvas = document
            }

            CANVAS_CODES.READ_VIEWPORT -> applyViewport(result.data.data as? Viewport)

            CANVAS_CODES.PRUNE_ORPHANS -> readStoredCanvas()

            // 🔄 24-Sep-2026 — a user's layout edit is on disk: stamp the note so it travels, exactly like a content edit
            CANVAS_CODES.SAVE_EDIT -> onEditorIntent(EditorIntent.SaveRequested)

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
        viewModelScope.launch(Dispatchers.Main) { handleFailure(taskCode, error) }
    }

    private fun handleFailure(taskCode: TaskCode, error: Error) {
        super.onFailure(taskCode, error)
        _state.update { it.copy(isLoading = false, error = error.displayMessage()) }
        // no saved viewport is not a reason to leave the canvas unready
        if (taskCode == CANVAS_CODES.READ_VIEWPORT) applyViewport(null)
        if (taskCode == CANVAS_CODES.PRUNE_ORPHANS) readStoredCanvas()
        runPendingOpen()
    }

    companion object {
        /** Sub-pixel measurement noise must not start a resize/measure ping-pong. */
        private const val MEASURE_EPSILON = 1.5f
    }

    // 🎧 the player's list belongs to the screen that is open, exactly as in the note editor
    override fun onCleared() {
        super.onCleared()
        clearSelectedNoteContentUseCase()
        deleteFilesLater(NoteFiles.discardable(trashedFiles, _state.value.note?.contents.orEmpty()))
    }
}
