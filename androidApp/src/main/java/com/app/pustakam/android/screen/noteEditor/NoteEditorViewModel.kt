package com.app.pustakam.android.screen.noteEditor

import com.app.pustakam.core.common.util.displayMessage
import com.app.pustakam.core.common.util.log_d
import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.viewModelScope
import com.app.pustakam.android.fileUtils.deleteFile
import com.app.pustakam.android.fileUtils.generateThumbnail
import com.app.pustakam.android.fileimport.FileImportManager
import com.app.pustakam.android.fileimport.ImportResult
import com.app.pustakam.android.noteContentProvider.addContent
import com.app.pustakam.android.permission.NeededPermission
import com.app.pustakam.android.screen.NOTES_CODES
import com.app.pustakam.android.screen.NoteContentUiState
import com.app.pustakam.android.screen.NoteUIState
import com.app.pustakam.android.screen.TaskCode
import com.app.pustakam.android.screen.base.BaseViewModel
import com.app.pustakam.android.screen.base.apiWithCollect
import com.app.pustakam.feature.notes.domain.editor.EditorCapabilityReducer
import com.app.pustakam.feature.notes.domain.editor.EditorCommands
import com.app.pustakam.feature.notes.domain.editor.EditorCapabilityState
import com.app.pustakam.feature.notes.domain.editor.EditorEffect
import com.app.pustakam.feature.notes.domain.editor.EditorIntent
import com.app.pustakam.feature.notes.domain.editor.EditorReducer
import com.app.pustakam.feature.notes.domain.editor.EditorState
import com.app.pustakam.feature.notes.domain.history.NoteEditKind
import com.app.pustakam.feature.notes.domain.history.NoteHistory
import com.app.pustakam.feature.notes.domain.usecase.CreateORUpdateNoteUseCase
import com.app.pustakam.feature.notes.domain.usecase.SyncNowUseCase
import com.app.pustakam.feature.notes.domain.usecase.DeleteNoteContentUseCase
import com.app.pustakam.feature.notes.domain.usecase.DeleteNoteUseCase
import com.app.pustakam.feature.notes.domain.usecase.ObserveNoteContentsUseCase
import com.app.pustakam.feature.notes.domain.usecase.ReadNoteUseCase
import com.app.pustakam.core.model.models.BaseResponse
import com.app.pustakam.core.model.models.response.notes.Note
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import com.app.pustakam.core.model.models.response.notes.NoteContentObjectHelper
import com.app.pustakam.core.model.models.response.notes.TextBlockSplitter
import com.app.pustakam.feature.notes.domain.usecase.ClearSelectedNoteContentUseCase
import com.app.pustakam.feature.notes.domain.usecase.SetSelectedNoteContentUseCase
import com.app.pustakam.feature.notes.domain.usecase.UpdateSelectedMediaContentUseCase
import com.app.pustakam.core.common.extensions.isNotnull
import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.common.util.ContentType.AUDIO
import com.app.pustakam.core.common.util.ContentType.IMAGE
import com.app.pustakam.core.common.util.ContentType.LOCATION
import com.app.pustakam.core.common.util.ContentType.TEXT
import com.app.pustakam.core.common.util.ContentType.VIDEO
import com.app.pustakam.core.common.util.Error
import com.app.pustakam.core.common.util.log_d
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.inject
import com.app.pustakam.core.common.util.Result
import com.app.pustakam.core.common.util.isGalleryEligible
import com.app.pustakam.core.common.util.isImage
import org.koin.core.component.get

class NoteEditorViewModel : BaseViewModel() {
    private val setSelectedNoteContentUseCase by inject<SetSelectedNoteContentUseCase>()
    private val updateSelectedMediaContentUseCase by inject<UpdateSelectedMediaContentUseCase>()
    private val clearSelectedNoteContentUseCase by inject<ClearSelectedNoteContentUseCase>()
    private val readNoteUseCase by inject<ReadNoteUseCase>()
    private val deleteNoteUseCase by inject<DeleteNoteUseCase>()
    private val deleteNoteContentUseCase by inject<DeleteNoteContentUseCase>()
    private val createUpdateNoteUseCase by inject<CreateORUpdateNoteUseCase>()
    private val syncNowUseCase by inject<SyncNowUseCase>()
    private val observeNoteContents by inject<ObserveNoteContentsUseCase>()
    private val _noteUiState = MutableStateFlow(NoteUIState(isLoading = false))
    val noteUIState: StateFlow<NoteUIState> = _noteUiState.asStateFlow()
    private val _noteContentUiState = MutableStateFlow(NoteContentUiState())
    val noteContentUiState: StateFlow<NoteContentUiState> = _noteContentUiState.asStateFlow()
    private val dirtyContentIds = mutableSetOf<String>()
    private var contentSyncJob: Job? = null
    // ✍️ 23-Sep-2026 — a deleted row leaves no dirty id behind, so remember the note changed anyway
    private var structureChanged = false
    private var autoSaveJob: Job? = null
    private val _history = MutableStateFlow(NoteHistory())
    val history: StateFlow<NoteHistory> = _history.asStateFlow()

    private fun recordHistory(kind: NoteEditKind) {
        val note = _noteContentUiState.value.note ?: return
        _history.update { it.record(note, kind) }
    }

    private fun restore(note: Note) {
        note.contents.forEach { dirtyContentIds.add(it.id) }
        _noteContentUiState.update { state ->
            state.contents.clear()
            state.contents.addAll(note.contents)
            state.titleTextState.value = note.title.orEmpty()
            state.copy(note = note, contents = state.contents, isAllSetupDone = true)
        }
    }

    fun undo() {
        val current = _noteContentUiState.value.note ?: return
        val step = _history.value.undoStep(current) ?: return
        _history.value = step.history
        restore(step.note)
    }

    fun redo() {
        val current = _noteContentUiState.value.note ?: return
        val step = _history.value.redoStep(current) ?: return
        _history.value = step.history
        restore(step.note)
    }

    init {
        viewModelScope.launch {
            setSelectedNoteContentUseCase.selectedMediaContent.collect { media ->
                media.forEach { applyExternalContentUpdate(it) }
            }
        }
    }

    private fun applyExternalContentUpdate(updated: NoteContentModel) {
        _noteContentUiState.update { state ->
            val index = state.contents.indexOfFirst { it.id == updated.id }
            if (index == -1) return@update state
            if (state.contents[index].updatedAt == updated.updatedAt) return@update state
            state.contents[index] = updated
            val updatedNote = state.note?.let { n ->
                n.withContents(n.contents.map { if (it.id == updated.id) updated else it })
            }
            state.copy(note = updatedNote, contents = state.contents)
        }
    }

    private var lastSavedDirtyIds: Set<String> = emptySet()


    //default methods
    override fun onLoading(taskCode: TaskCode) {
        _noteUiState.update {
            it.copy(isLoading = true, successMessage = "")
        }
    }

    override fun onSuccess(taskCode: TaskCode, result: Result.Success<BaseResponse<*>>) {
        when (taskCode) {
            NOTES_CODES.INSERT -> {
                log_d("Loading", "Getting Update data")
                val note = result.data.data as Note
                _noteUiState.update {
                    val noteStatus = when (it.noteStatus) {
                        NoteStatus.onBackPress, NoteStatus.onSaveCompletedExit -> NoteStatus.onSaveCompletedExit
                        else -> NoteStatus.onSaveCompleted
                    }
                    it.copy(noteStatus = noteStatus, isLoading = false)
                }

                _noteContentUiState.update { it.copy(note = note,
                    isAllSetupDone = true) }

                dirtyContentIds.removeAll(lastSavedDirtyIds)
                lastSavedDirtyIds = emptySet()
                consumePendingMediaPaths()
            }

            NOTES_CODES.READ -> {
                val note = result.data.data as Note
                if (!noteContentUiState.value.isAllSetupDone) _noteContentUiState.update {
                    it.titleTextState.value = note.title ?: ""
                    it.copy(
                        titleTextState = it.titleTextState, note = note,
                        isAllSetupDone = true,

                        contents = mutableStateListOf(*note.contents.sortedBy { c -> c.position }.toTypedArray())
                    )
                }

                onEditorIntent(EditorIntent.ExternalContentsChanged(note.contents))
                observeExternalContents(note.id)
                startAutoSave()
                setSelectedNoteContentUseCase(_noteContentUiState.value.note ?: note)
                consumePendingMediaPaths()
            }

            NOTES_CODES.DELETE -> {
                _noteUiState.update {
                    it.copy(
                        isLoading = false, noteStatus = NoteStatus.exit
                    )
                }
            }
        }
    }

    override fun onFailure(taskCode: TaskCode, error: Error) {
        when (taskCode) {
            // 🔧 17-Aug-2026: a READ miss is NOT a reason to leave the editor — exiting on it is what
            //   popped the screen the instant a new note opened. Drop the loader and stay put.
            NOTES_CODES.READ -> {
                log_d("NoteEditor", "read failed (suppressed for UX): $error")
                _noteUiState.update { it.copy(isLoading = false) }
            }

            else -> _noteUiState.update {
                it.copy(isLoading = false, error = error.displayMessage(), noteStatus = null)
            }
        }
    }


    override fun clearError() {
        _noteUiState.update {
            it.copy(
                error = null, successMessage = null, isLoading = false
            )
        }
    }

    /** CRUD operations on Notes */

    // call make a wish api
    fun isNoteValid(): Boolean =
        if (_noteContentUiState.value.titleTextState.value.isEmpty()) {
            if (_noteContentUiState.value.note?.contents?.isEmpty() == true) {
                 false
            } else  true
        } else true

    fun createOrUpdateNote() {
        if(!isNoteValid()) {
                changeNoteStatus(NoteStatus.exit)
                return
        }
        updateNoteObject()
        // 🔧 15-Jul-2026 Phase 0.4: snapshot the dirty ids for THIS save; cleared on INSERT success.
        lastSavedDirtyIds = dirtyContentIds.toSet()
        makeAWish(NOTES_CODES.INSERT) {
            createUpdateNoteUseCase.invoke(_noteContentUiState.value.note!!, lastSavedDirtyIds)
        }
    }
    /** Flush without touching NoteStatus, so leaving or backgrounding never triggers navigation. */
    // ✍️ 23-Sep-2026 — pausing an UNTOUCHED note must not re-stamp it: that push overwrote the other device's newer copy
    fun saveNow() {
        if (hasPendingChanges()) saveThenOpen { }
    }

    // ✍️ 23-Sep-2026 — an edit reaches the disk (and the sync queue) within 5s, not only when the screen pauses
    private fun startAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            while (isActive) {
                delay(EditorCommands.autoSaveMillis())
                if (hasPendingChanges()) saveThenOpen { }
            }
        }
    }

    // ✍️ what the disk has not seen yet: an edited row, a deleted one, or a retitled note
    private fun hasPendingChanges(): Boolean {
        val state = _noteContentUiState.value
        val note = state.note ?: return false
        return structureChanged || dirtyContentIds.isNotEmpty() ||
            state.titleTextState.value != note.title.orEmpty()
    }

    fun saveThenOpen(onSaved: () -> Unit) {
        if(!isNoteValid()) return
        updateNoteObject()
        val note = _noteContentUiState.value.note ?: run { onSaved(); return }
        val dirty = dirtyContentIds.toSet()
        createUpdateNoteUseCase(note = note, dirtyContentIds = dirty).apiWithCollect(
            scope = viewModelScope,
            onLoading = {},
            onFailure = {
                dirtyContentIds.removeAll(dirty)
                withContext(Dispatchers.Main) { onSaved() }
            },
            onSuccess = {
                dirtyContentIds.removeAll(dirty)
                structureChanged = false
                withContext(Dispatchers.Main) { onSaved() }
            }
        )
    }

    /** 🔄 28-Aug-2026 — PULL TO REFRESH in the editor: sync, then re-read THIS note from the DB
     *  so anything that arrived for it is on screen immediately rather than on the next open. */
    fun refresh(noteId: String?) {
        viewModelScope.launch {
            _noteUiState.update { it.copy(isRefreshing = true, error = null) }
            syncNowUseCase().collect { result ->
                when (result) {
                    is Result.Error -> _noteUiState.update {
                        it.copy(isRefreshing = false, error = result.error.displayMessage())
                    }
                    is Result.Success -> {
                        _noteUiState.update { it.copy(isRefreshing = false) }
                        readFromDataBase(noteId)
                    }
                    is Result.Loading -> {}
                }
            }
        }
    }

    fun readFromDataBase(id: String?) {
        if (!id.isNullOrEmpty()) _noteUiState.update {
            it.copy(showDeleteButton = true)
        }
        makeAWish(NOTES_CODES.READ) {
            readNoteUseCase.invoke(id)
        }
    }
    fun refreshOnResume(id: String?) {
        if (dirtyContentIds.isNotEmpty()) return   // don't overwrite unsaved edits
        // 🔧 17-Aug-2026: a Quick note lives only in memory until it is saved. Re-reading the id it
        //   was handed at creation misses in the DB, and that miss used to close the editor on open.
        val persisted = id?.takeIf { it.isNotEmpty() } ?: return
        readFromDataBase(persisted)
    }

    fun deleteNote(noteId: String) {
        makeAWish(NOTES_CODES.DELETE) {
            deleteNoteUseCase.invoke(noteId)
        }
    }

    private fun updateNoteObject() {
        TextBlockSplitter.splitOversized(_noteContentUiState.value.contents.toList())?.let { split ->
            dirtyContentIds.addAll(split.changedIds)
            val live = _noteContentUiState.value.contents
            live.clear()
            live.addAll(split.contents)
        }
        _noteContentUiState.update {
            val updatedNote = it.note?.withTitleAndContents(
                newTitle = it.titleTextState.value,
                newContents = it.contents.toList()
            )
            it.copy(note = updatedNote)
        }
    }


    //action status methods
    fun changeNoteStatus(status: NoteStatus?) {
        _noteUiState.update {
            it.copy(
                noteStatus = status, isLoading = false
            )
        }
    }

    private fun setContentType(contentType: ContentType) {
        _noteUiState.update {
            it.copy(contentType = contentType, isLoading = true)
        }
    }

    //dialog trigger methods
    fun showDeleteAlertBox(value: Boolean, deleteNoteContentId: String? = null) {
        // some time it doesn't update a single value
        _noteUiState.update { it.copy(showDeleteAlert = value, deleteNoteContentId = deleteNoteContentId, isLoading = false) }
    }

    fun showPermissionAlert(value: Boolean?) {
        _noteUiState.update {
            it.copy(showPermissionAlert = value)
        }
    }

    //hardware permission logic
    @SuppressLint("NewApi")
    private fun getPermissions(contentType: ContentType?) = when (contentType) {
        VIDEO -> listOf(NeededPermission.CAMERA, NeededPermission.RECORD_AUDIO)
        AUDIO -> listOf(NeededPermission.RECORD_AUDIO)
        IMAGE -> listOf(NeededPermission.CAMERA)
        LOCATION -> listOf(NeededPermission.COARSE_LOCATION, NeededPermission.FINE_LOCATION,
            NeededPermission.BACKGROUND_LOCATION)
        else -> listOf(NeededPermission.POST_NOTIFICATIONS)
    }

   /**permission dialog setup*/
    private val _capabilities = MutableStateFlow(EditorCapabilityState())
    val capabilities: StateFlow<EditorCapabilityState> = _capabilities.asStateFlow()

    fun onCapabilityState(next: EditorCapabilityState) {
        _capabilities.value = next
    }

    private fun observeExternalContents(noteId: String) {
        if (noteId.isEmpty()) return
        contentSyncJob?.cancel()
        contentSyncJob = viewModelScope.launch {
            observeNoteContents(noteId).collect {
                onEditorIntent(EditorIntent.ExternalContentsChanged(it))
            }
        }
    }

    private fun editorState(): EditorState = EditorState(
        note = _noteContentUiState.value.note,
        contents = _noteContentUiState.value.contents.toList(),
        dirtyContentIds = dirtyContentIds.toSet(),
        isLoading = _noteUiState.value.isLoading,
        error = _noteUiState.value.error,
        capabilities = _capabilities.value
    )

    fun onEditorIntent(intent: EditorIntent) {
        val before = editorState()
        val next = EditorReducer.reduce(before, intent)
        _capabilities.value = next.capabilities
        dirtyContentIds.addAll(next.dirtyContentIds - before.dirtyContentIds)
        syncContents(before.contents, next.contents, next.note)
        if (next.error != before.error) {
            _noteUiState.update { it.copy(error = next.error) }
        }
        EditorReducer.effects(before, next, intent).forEach { runEffect(it) }
    }

    private fun syncContents(
        before: List<NoteContentModel>,
        next: List<NoteContentModel>,
        note: Note?
    ) {
        if (before == next) return
        val live = _noteContentUiState.value.contents
        val keep = next.map { it.id }.toSet()
        live.removeAll { it.id !in keep }
        next.sortedBy { it.position }.forEach { content ->
            val index = live.indexOfFirst { it.id == content.id }
            if (index == -1) live.add(content) else if (live[index] != content) live[index] = content
        }
        _noteContentUiState.update {
            it.copy(
                note = note?.withContents(live.toList()),
                contents = it.contents,
                isAllSetupDone = true
            )
        }
    }

    private fun runEffect(effect: EditorEffect) {
        when (effect) {
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

    fun requestCapture(type: ContentType) {
        onEditorIntent(EditorIntent.CaptureRequested(type))
    }

    fun openImportSheet() {
        onEditorIntent(EditorIntent.ShowImportSheet(true))
    }

    fun captureFinished() {
        onEditorIntent(EditorIntent.CaptureFinished)
    }

    fun stopRecordingAudio(){
        _capabilities.value = EditorCapabilityReducer.stopAudio(_capabilities.value)
    }
    fun askDeleteContent(contentId: String) {
        onEditorIntent(EditorIntent.AskDeleteContent(contentId))
    }

    fun askDeleteNote() {
        onEditorIntent(EditorIntent.AskDeleteNote)
    }
      fun addNewText() {
          recordHistory(NoteEditKind.ADD_TEXT)
          val note = _noteContentUiState.value.note!!
          if (note.isNotnull()) {
              val textContent =
                  NoteContentObjectHelper.createText(
                      noteId = note.id,
                      positionedAt = note.contents.count().toDouble()   // 🔧 C4
                  )
              setContentType(TEXT)
              updateContent(content = textContent)
          }
      }

    fun locationState(value: Boolean ) {
        _noteUiState.update { it.copy(LocationState = value) }
    }
    fun updateContent(index: Int = -1, content: NoteContentModel) {
        recordHistory(if (content is NoteContentModel.TextContent) NoteEditKind.TEXT else NoteEditKind.ADD_MEDIA)
        dirtyContentIds.add(content.id)   // 🔧 15-Jul-2026 Phase 0.4: touched → will be saved
        if(index== -1) {
            addContentData(content)
        }else{
            _noteContentUiState.update {
                it.contents[index] = content
                // immutable Note: upsert by id via copy (was: in-place add — also fixed
                // the old bug of ADDING a duplicate on update instead of replacing)
                val updatedNote = it.note?.let { n ->
                    val newContents = n.contents.toMutableList()
                    val i = newContents.indexOfFirst { c -> c.id == content.id }
                    if (i != -1) newContents[i] = content else newContents.add(content)
                    n.withContents(newContents)
                }
                it.copy(note = updatedNote, contents = it.contents, isAllSetupDone = true)
            }
        }
        if (content.isPlayableMedia())
            updateSelectedMediaContentUseCase(content as NoteContentModel.MediaContent)
    }
    /**content logic
     * Add new content to the note content list
     * by selecting it type on the bases of user selection
     * */
    fun addNewContent(context: Context, contentType: ContentType): NoteContentModel {
        setContentType(contentType)
        val content = addContent( context = context, note = _noteContentUiState.value.note!!, contentType = contentType)
        return content
    }
    fun addContentData(content: NoteContentModel){
        dirtyContentIds.add(content.id)

        val liveContents = _noteContentUiState.value.contents
        val existingIndex = liveContents.indexOfFirst { it.id == content.id }
        if (existingIndex != -1) liveContents[existingIndex] = content else liveContents.add(content)
        _noteContentUiState.update {
            val updatedNote = it.note?.let { n ->
                val newContents = n.contents.toMutableList()
                val i = newContents.indexOfFirst { c -> c.id == content.id }
                if (i != -1) newContents[i] = content else newContents.add(content)
                n.withContents(newContents)
            }
            it.copy(note = updatedNote, contents = it.contents, isAllSetupDone = true)
        }
    }

    fun startStopAudioRecording(value: Boolean = true) {
        _noteUiState.update { it.copy(isLoading = false, showAudioRecorder = value) }
        if(!value) stopRecordingAudio()
    }
/**
 * Remove a note content for note
 * */
    fun removeContent(value: String) {
        recordHistory(NoteEditKind.DELETE_CONTENT)
        structureChanged = true   // ✍️ the row is gone; nothing dirty is left to prove the note changed
        dirtyContentIds.remove(value)   // 🔧 15-Jul-2026 Phase 0.4: deleted → nothing to save
        val find = _noteContentUiState.value.note?.contents?.find { value == it.id }
        viewModelScope.launch(Dispatchers.IO) {
            var rowDeleted = false
            deleteNoteContentUseCase.invoke(value).collect { result ->
                when (result) {
                    is Result.Success -> rowDeleted = true
                    is Result.Error -> log_d("NoteEditor", "content delete failed: ${result.error}")
                    else -> Unit
                }
            }

            if (rowDeleted && find?.isMediaFile() == true) {
                find as NoteContentModel.MediaContent
                find.localPath?.let { deleteFile(filePath = it) }
                // 🔧 15-Jul-2026 (iOS-parity cleanup): the media's thumbnail file goes with it
                find.thumbnailPath?.let { deleteFile(filePath = it) }
            }
        }
        _noteContentUiState.update {
            val indexContent = it.contents.indexOf(find)
            if (indexContent != -1) it.contents.removeAt(indexContent)
            val updatedNote = it.note?.let { n ->
                n.withContents(n.contents.filterNot { c -> c.id == value })
            }
            it.copy(note = updatedNote, contents = it.contents)
        }
        showDeleteAlertBox(false, null)
    }


 /**
  * Share note with others
  */
    fun shareNote(){}

    override fun onCleared() {
        super.onCleared()
        clearSelectedNoteContentUseCase()
    }

    private var pendingMediaPaths: List<Pair<String, ContentType>> = emptyList()

    private fun consumePendingMediaPaths() {
        if (pendingMediaPaths.isEmpty()) return
        val pending = pendingMediaPaths
        pendingMediaPaths = emptyList()
        getMediaData(pending)
    }

    fun getMediaData(list: List<Pair<String, ContentType>>) {
        if (list.isEmpty()) return
        val currentState = _noteContentUiState.value
        val note = currentState.note ?: run {
            pendingMediaPaths = pendingMediaPaths + list   // 🔧 stash instead of dropping
            return
        }
        var position: Double = note.contents.count().toDouble()   // 🔧 C4
        val newItems = list.map { path ->
            val content = NoteContentObjectHelper.createMedia(
                positionedAt = position,
                noteId = note.id,
                localPath = path.first,
                contentType = path.second
            ).copy(title = "${path.second}-$position")
            position += 1.0
            content
        }
        // Single mutation of the observed list (safe: called off the composition pass).
        currentState.contents.addAll(newItems)
        _noteContentUiState.update {
            it.copy(
                note = note.withContents(note.contents + newItems),
                contents = it.contents,
                isAllSetupDone = true
            )
        }
        onEditorIntent(EditorIntent.AddContents(newItems))
    }

    // 🔧 17-Aug-2026: Open With / Share — same import as the picker, plus the file names the note
    //   while the title is still blank, so a shared file is findable in the list.
    fun importSharedFiles(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val titleState = _noteContentUiState.value.titleTextState
        if (titleState.value.isBlank()) {
            FileImportManager
                .displayNameOf(context, uris.first())
                ?.substringBeforeLast('.')
                ?.takeIf { it.isNotBlank() }
                ?.let { titleState.value = it }
        }
        importDeviceFiles(context, uris)
    }

    fun importDeviceFiles(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val note = _noteContentUiState.value.note ?: run {
            _noteUiState.update { it.copy(error = "Note is still loading. Try again.") }; return
        }
        _noteUiState.update { it.copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val items = FileImportManager.importUris(
                context.applicationContext, note.id, note.contents.count().toDouble(), uris
            )
           withContext(Dispatchers.Main) {
                _noteUiState.update { it.copy(isLoading = false) }
                if (items.isEmpty()) _noteUiState.update { it.copy(error = "Couldn't import the selected files.") }
                else addImportedContents(items)
            }
        }
    }


    fun importFromLink(context: Context, url: String) {
        if (url.isBlank()) return
        val note = _noteContentUiState.value.note ?: run {
            _noteUiState.update { it.copy(error = "Note is still loading. Try again.") }; return
        }
        _noteUiState.update { it.copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = FileImportManager.importFromUrl(
                context.applicationContext, note.id, note.contents.count().toDouble(), url
            )
            withContext(Dispatchers.Main) {
                _noteUiState.update { it.copy(isLoading = false) }
                when (result) {
                    is ImportResult.Success -> addImportedContents(result.contents)
                    is ImportResult.NoFileFound ->
                        _noteUiState.update { it.copy(error = "No file found at this link.") }
                    is ImportResult.Failed ->
                        _noteUiState.update { it.copy(error = result.message) }
                }
            }
        }
    }

    // 🔧 18-Jul-2026: same safe mutation pattern as getMediaData — list touched ONCE outside update{}
    private fun addImportedContents(items: List<NoteContentModel.MediaContent>) {
        if (items.isEmpty()) return
        val currentState = _noteContentUiState.value
        val note = currentState.note ?: return
        currentState.contents.addAll(items)
        _noteContentUiState.update {
            it.copy(note = note.withContents(note.contents + items), contents = it.contents, isAllSetupDone = true)
        }
        onEditorIntent(EditorIntent.AddContents(items))
    }
}