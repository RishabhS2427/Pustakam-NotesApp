package com.app.pustakam.android.hardware.camera

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.pustakam.android.extension.toBitmap
import com.app.pustakam.android.fileUtils.deleteFilesLater
import com.app.pustakam.android.fileUtils.saveBitmapToFile
import com.app.pustakam.android.widgets.drawing.DrawingHost
import com.app.pustakam.android.widgets.drawing.NoteAnnotations
import com.app.pustakam.android.widgets.drawing.renderInk
import com.app.pustakam.core.drawing.note.DrawNoteContents
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import com.app.pustakam.core.model.models.response.notes.getMediaUrl
import com.app.pustakam.feature.notes.domain.editor.NoteFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ImageAnnotationViewModel : ViewModel() {

    private var mediaId: String? = null

    private val _target = MutableStateFlow<NoteContentModel.MediaContent?>(null)
    val target: StateFlow<NoteContentModel.MediaContent?> = _target.asStateFlow()

    private val _base = MutableStateFlow<Bitmap?>(null)
    val base: StateFlow<Bitmap?> = _base.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val annotations = NoteAnnotations(viewModelScope, { _target.value?.id }, ::resolve)

    val drawing: DrawingHost get() = annotations.drawing

    fun open(noteId: String?, mediaId: String?) {
        if (noteId.isNullOrEmpty() || mediaId.isNullOrEmpty() || this.mediaId == mediaId) return
        this.mediaId = mediaId
        annotations.follow(noteId)
    }

    fun toggle() {
        if (_base.value != null) drawing.toggleOverlay()
    }

    fun finish(newFile: () -> File) {
        drawing.stop()
        annotations.flush()
        val original = _target.value ?: return
        val base = _base.value ?: return
        val contents = annotations.contents()
        val annotation = DrawNoteContents.annotationOf(contents, original.id)
        if (!DrawNoteContents.hasInk(annotation, original.id)) {
            DrawNoteContents.editedCopyOf(contents, original.id)?.let(::discard)
            return
        }
        val file = newFile()
        _saving.value = true
        exportScope.launch {
            val entries = DrawNoteContents.inkFrame(annotation, original.id, base.width.toFloat(), base.height.toFloat())
            val saved = saveBitmapToFile(renderInk(base, entries), file)
            withContext(Dispatchers.Main) {
                if (saved) replaceCopy(original, file.absolutePath, base.width, base.height)
                _saving.value = false
            }
        }
    }

    private fun replaceCopy(original: NoteContentModel.MediaContent, path: String, width: Int, height: Int) {
        val contents = annotations.contents()
        val previous = DrawNoteContents.editedCopyOf(contents, original.id)
        annotations.write(DrawNoteContents.editedCopy(contents, original, path, width, height))
        previous?.let(::discard)
    }

    private fun discard(copy: NoteContentModel.MediaContent) {
        annotations.remove(copy.id)
        deleteFilesLater(NoteFiles.pathsOf(copy))
    }

    private fun resolve(contents: List<NoteContentModel>) {
        val id = mediaId ?: return
        val target = DrawNoteContents.editTargetOf(contents, id) ?: return
        val changed = target.id != _target.value?.id
        _target.value = target
        if (!changed && _base.value != null) return
        val path = target.getMediaUrl()
        if (path.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = path.toBitmap(MAX_EDGE, MAX_EDGE)
            withContext(Dispatchers.Main) { _base.value = bitmap }
        }
    }

    override fun onCleared() {
        annotations.flush()
        super.onCleared()
    }

    private companion object {
        private const val MAX_EDGE = 2048
        private val exportScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
