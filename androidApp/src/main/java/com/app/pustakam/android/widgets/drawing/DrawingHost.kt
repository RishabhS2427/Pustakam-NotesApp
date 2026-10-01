package com.app.pustakam.android.widgets.drawing

import com.app.pustakam.core.drawing.editor.DrawCommands
import com.app.pustakam.core.drawing.note.DrawNoteContents
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DrawingHost(
    private val contents: () -> List<NoteContentModel>,
    private val noteId: () -> String?,
    private val documentId: () -> String?,
    onWrite: (NoteContentModel.Drawing) -> Unit
) {
    private val sessions = DrawingSessions(onWrite)

    private val _target = MutableStateFlow<String?>(null)

    val target: StateFlow<String?> = _target.asStateFlow()

    private val _overlay = MutableStateFlow<DrawingSession?>(null)

    val overlay: StateFlow<DrawingSession?> = _overlay.asStateFlow()

    private var overlayDraft: NoteContentModel.Drawing? = null

    fun sync(latest: List<NoteContentModel>) {
        sessions.sync(latest)
        _overlay.value = overlaySession(create = false)
        if (sessions.get(_target.value) == null) _target.value = null
    }

    fun session(content: NoteContentModel.Drawing): DrawingSession = sessions.sessionFor(content, readOnly = false)

    fun active(): DrawingSession? = sessions.get(_target.value)

    fun overlayId(): String? = (storedOverlay(contents()) ?: overlayDraft)?.id

    fun isOverlayActive(): Boolean = _target.value != null && _target.value == overlayId()

    fun overlaySession(create: Boolean): DrawingSession? {
        val current = contents()
        storedOverlay(current)?.let { return session(it) }
        val draft = overlayDraft ?: if (create) newOverlayDraft(current) else null
        return draft?.let { sessions.sessionFor(it, readOnly = false, stored = false) }
    }

    fun toggleOverlay() {
        if (isOverlayActive()) {
            stop()
            return
        }
        _overlay.value = overlaySession(create = true)
        overlayId()?.let(::start)
    }

    fun open(content: NoteContentModel.Drawing) {
        session(content)
        start(content.id)
    }

    fun start(contentId: String) {
        sessions.activate(contentId)
        _target.value = contentId
    }

    fun stop() {
        _target.value = null
    }

    private fun storedOverlay(current: List<NoteContentModel>): NoteContentModel.Drawing? {
        val document = documentId()
        return if (document == null) DrawNoteContents.overlayOf(current) else DrawNoteContents.annotationOf(current, document)
    }

    private fun newOverlayDraft(current: List<NoteContentModel>): NoteContentModel.Drawing? {
        val id = noteId() ?: return null
        val position = DrawNoteContents.nextPosition(current)
        val document = documentId()
        val draft = if (document == null) {
            DrawNoteContents.create(id, position, DrawCommands.overlaySurface(), 0f, 0f)
        } else {
            DrawNoteContents.createAnnotation(id, position, document)
        }
        overlayDraft = draft
        return draft
    }
}
