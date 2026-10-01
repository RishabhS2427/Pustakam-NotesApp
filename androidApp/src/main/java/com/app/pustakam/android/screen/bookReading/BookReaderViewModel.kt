package com.app.pustakam.android.screen.bookReading

import androidx.lifecycle.viewModelScope
import com.app.pustakam.android.screen.BOOK
import com.app.pustakam.android.screen.TaskCode
import com.app.pustakam.android.screen.base.BaseViewModel
import com.app.pustakam.android.screen.base.apiWithCollect
import com.app.pustakam.android.widgets.drawing.DrawingHost
import com.app.pustakam.android.screen.notebookReader.BookPageFactory
import com.app.pustakam.android.screen.notebookReader.ReadingMode
import com.app.pustakam.core.common.util.Error
import com.app.pustakam.core.common.util.Result
import com.app.pustakam.core.common.util.log_d
import com.app.pustakam.core.database.localdb.preferences.IAppPreferences
import com.app.pustakam.core.model.models.BaseResponse
import com.app.pustakam.core.model.models.response.notes.Note
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import com.app.pustakam.feature.notes.domain.usecase.CreateORUpdateNoteUseCase
import com.app.pustakam.feature.notes.domain.usecase.ObserveNoteContentsUseCase
import com.app.pustakam.feature.notes.domain.usecase.ReadNoteUseCase
import com.app.pustakam.feature.notes.domain.usecase.ReadContentUseCase
import com.app.pustakam.feature.notes.domain.usecase.UpdateReadingProgressUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.milliseconds


class BookReaderViewModel : BaseViewModel() {
    private val readDocUseCase by inject<ReadContentUseCase>()
    private val updateReadingProgressUseCase by inject<UpdateReadingProgressUseCase>()
    private val userPrefs by inject<IAppPreferences>()
    private val readNoteUseCase by inject<ReadNoteUseCase>()
    private val saveNoteUseCase by inject<CreateORUpdateNoteUseCase>()
    private val observeNoteContents by inject<ObserveNoteContentsUseCase>()

    private val _bookUiState = MutableStateFlow(BookUIState(isLoading = false))
    val bookUiState: StateFlow<BookUIState> = _bookUiState.asStateFlow()

    private companion object {
        private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private const val ANNOTATION_SAVE_DELAY_MILLIS = 400L
    }

    private var annotationNote: Note? = null
    private var annotationJob: Job? = null
    private var annotationSaveJob: Job? = null
    private var pendingAnnotationId: String? = null

    val drawing = DrawingHost(
        contents = { annotationNote?.contents.orEmpty() },
        noteId = { annotationNote?.id },
        documentId = { _bookUiState.value.doc?.id },
        onWrite = ::saveAnnotation
    )

    private var lastKnownPage: Int = 0

    init {
        viewModelScope.launch(Dispatchers.IO) {
            userPrefs.readingModeFlow.collect { raw ->
                _bookUiState.update { it.copy(readingMode = ReadingMode.from(raw)) }
            }
        }
    }

    override fun onLoading(taskCode: TaskCode) {
        _bookUiState.update { if (it.pages.isEmpty()) it.copy(isLoading = true) else it }
    }

    fun onHandleIntent(intent: BookReaderIntent) {
        when (intent) {
            is BookReaderIntent.ToggleReadingMode -> {
                _bookUiState.update { it.copy(readingMode = intent.readingMode) }
                viewModelScope.launch(Dispatchers.IO) { userPrefs.setReadingMode(intent.readingMode.key) }
            }

            is BookReaderIntent.LoadBook -> load(intent.bookId)

            is BookReaderIntent.PageChanged -> {
                lastKnownPage = intent.page
                scheduleProgressSave(intent.book)
            }

            is BookReaderIntent.Bookmark -> Unit
        }
    }

    private fun load(bookId: String?) {
        if (bookId.isNullOrEmpty()) {
            _bookUiState.update { it.copy(isLoading = false, error = "Document not found") }
            return
        }
        makeAWish(BOOK.GET_BOOK) { readDocUseCase.invoke(bookId) }
    }

    override fun onSuccess(taskCode: TaskCode, result: Result.Success<BaseResponse<*>>) {
        val doc = result.data.data as? NoteContentModel.MediaContent ?: run {
            _bookUiState.update { it.copy(isLoading = false, error = "Not a readable document") }
            return
        }
        when (taskCode){
             BOOK.GET_BOOK -> {
                 viewModelScope.launch(Dispatchers.IO) {
                     val pages = BookPageFactory.buildForContent(doc)
                     val startPage = doc.takeIf { it.hasReadingProgress() }
                         ?.progressPage
                         ?.coerceIn(0, (pages.size - 1).coerceAtLeast(0)) ?: 0
                     lastKnownPage = startPage
                     _bookUiState.update {
                         it.copy(
                             isLoading = false, error = null, doc = doc, pages = pages,
                             pageProgress = startPage,
                         )
                     }
                     withContext(Dispatchers.Main) { followNote(doc.noteId) }
                 }
             }
        }
    }

    override fun onFailure(taskCode: TaskCode, error: Error) {
        // 📖 the read flow can emit a local-miss BEFORE data arrives; never flash it over pages
        log_d("BookReader", "document read failed: $error")
        _bookUiState.update { if (it.pages.isEmpty()) it.copy(isLoading = false) else it }
    }

    private var saveJob: Job? = null
    private fun scheduleProgressSave(doc: NoteContentModel.MediaContent) {
        val total = _bookUiState.value.pages.size
        if (total <= 0) return
        val page = lastKnownPage.coerceIn(0, total - 1)
        _bookUiState.update { it.copy(pageProgress = page) }
        saveJob?.cancel()
        // 📖 debounced so scrolling doesn't spam the DB; saveScope outlives this ViewModel
        saveJob = saveScope.launch {
            delay(350.milliseconds)
            updateReadingProgressUseCase(doc.id, page, total).collect { }
        }
    }

    private fun followNote(noteId: String) {
        if (noteId.isEmpty() || annotationJob != null) return
        readNoteUseCase(noteId).apiWithCollect(
            showLoader = false,
            scope = viewModelScope,
            onFailure = { log_d("BookReader", "annotation note read failed: $it") },
            onSuccess = { result ->
                val note = result.data.data as? Note ?: return@apiWithCollect
                annotationNote = note
                drawing.sync(note.contents)
            }
        )
        annotationJob = viewModelScope.launch {
            observeNoteContents(noteId).collect { contents ->
                annotationNote = annotationNote?.withContents(contents)
                drawing.sync(contents)
            }
        }
    }

    private fun saveAnnotation(content: NoteContentModel.Drawing) {
        val note = annotationNote ?: return
        annotationNote = note.withContents(note.contents.filterNot { it.id == content.id } + content)
        pendingAnnotationId = content.id
        annotationSaveJob?.cancel()
        annotationSaveJob = viewModelScope.launch {
            delay(ANNOTATION_SAVE_DELAY_MILLIS)
            flushAnnotation()
        }
    }

    private fun flushAnnotation() {
        val note = annotationNote ?: return
        val id = pendingAnnotationId ?: return
        pendingAnnotationId = null
        saveScope.launch { saveNoteUseCase(note, setOf(id)).collect { } }
    }

    override fun onCleared() {
        annotationSaveJob?.cancel()
        flushAnnotation()
        saveJob?.cancel()
        val state = _bookUiState.value
        val doc = state.doc
        if (doc != null && state.pages.isNotEmpty()) {
            val page = lastKnownPage.coerceIn(0, state.pages.size - 1)
            saveScope.launch { updateReadingProgressUseCase(doc.id, page, state.pages.size).collect { } }
        }
        super.onCleared()
    }


    override fun clearError() {
        _bookUiState.update { it.copy(error = null) }
    }
}
