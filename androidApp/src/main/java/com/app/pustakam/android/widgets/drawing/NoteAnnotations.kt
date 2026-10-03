package com.app.pustakam.android.widgets.drawing

import com.app.pustakam.android.screen.base.apiWithCollect
import com.app.pustakam.core.common.util.log_d
import com.app.pustakam.core.model.models.response.notes.Note
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import com.app.pustakam.feature.notes.domain.usecase.CreateORUpdateNoteUseCase
import com.app.pustakam.feature.notes.domain.usecase.DeleteNoteContentUseCase
import com.app.pustakam.feature.notes.domain.usecase.ObserveNoteContentsUseCase
import com.app.pustakam.feature.notes.domain.usecase.ReadNoteUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class NoteAnnotations(
    private val scope: CoroutineScope,
    targetId: () -> String?,
    private val onContents: (List<NoteContentModel>) -> Unit = {}
) : KoinComponent {
    private val readNote by inject<ReadNoteUseCase>()
    private val observeContents by inject<ObserveNoteContentsUseCase>()
    private val saveNote by inject<CreateORUpdateNoteUseCase>()
    private val deleteContent by inject<DeleteNoteContentUseCase>()

    private var note: Note? = null
    private var followJob: Job? = null
    private var saveJob: Job? = null
    private var pendingId: String? = null

    val drawing = DrawingHost(
        contents = { note?.contents.orEmpty() },
        noteId = { note?.id },
        documentId = targetId,
        onWrite = ::schedule
    )

    fun contents(): List<NoteContentModel> = note?.contents.orEmpty()

    private val _writable = MutableStateFlow(true)
    val writable: StateFlow<Boolean> = _writable.asStateFlow()

    fun follow(noteId: String) {
        if (noteId.isEmpty() || followJob != null) return
        readNote(noteId).apiWithCollect(
            showLoader = false,
            scope = scope,
            onFailure = { log_d("NoteAnnotations", "note read failed: $it") },
            onSuccess = { result ->
                val loaded = result.data.data as? Note ?: return@apiWithCollect
                _writable.value = loaded.share?.canWrite() != false
                note = loaded
                refresh(loaded.contents)
            }
        )
        followJob = scope.launch {
            observeContents(noteId).collect { contents ->
                note = note?.withContents(contents)
                refresh(contents)
            }
        }
    }

    fun write(content: NoteContentModel) {
        val current = note?.takeIf { _writable.value } ?: return
        val next = current.withContents(current.contents.filterNot { it.id == content.id } + content)
        note = next
        refresh(next.contents)
        persist(content.id)
    }

    fun remove(contentId: String) {
        val current = note?.takeIf { _writable.value } ?: return
        val next = current.withContents(current.contents.filterNot { it.id == contentId })
        note = next
        refresh(next.contents)
        saveScope.launch { deleteContent(contentId).collect { } }
    }

    fun flush() {
        saveJob?.cancel()
        flushPending()
    }

    private fun refresh(contents: List<NoteContentModel>) {
        drawing.sync(contents)
        onContents(contents)
    }

    private fun schedule(content: NoteContentModel.Drawing) {
        val current = note?.takeIf { _writable.value } ?: return
        note = current.withContents(current.contents.filterNot { it.id == content.id } + content)
        pendingId = content.id
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DELAY_MILLIS)
            flushPending()
        }
    }

    private fun flushPending() {
        val id = pendingId ?: return
        pendingId = null
        persist(id)
    }

    private fun persist(contentId: String) {
        val current = note ?: return
        saveScope.launch { saveNote(current, setOf(contentId)).collect { } }
    }

    private companion object {
        private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private const val SAVE_DELAY_MILLIS = 400L
    }
}
