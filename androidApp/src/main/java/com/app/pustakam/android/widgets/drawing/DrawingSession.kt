package com.app.pustakam.android.widgets.drawing

import com.app.pustakam.core.drawing.editor.DrawCommands
import com.app.pustakam.core.drawing.editor.DrawEditorState
import com.app.pustakam.core.drawing.editor.DrawIntent
import com.app.pustakam.core.drawing.editor.DrawReducer
import com.app.pustakam.core.drawing.model.DrawDocument
import com.app.pustakam.core.drawing.note.DrawAuthors
import com.app.pustakam.core.drawing.note.DrawNoteContents
import com.app.pustakam.core.drawing.render.DrawRenderCache
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DrawingSession(
    initial: DrawEditorState,
    private val onDocumentChanged: (DrawEditorState) -> Unit
) {
    private val _state = MutableStateFlow(initial)

    val state: StateFlow<DrawEditorState> = _state.asStateFlow()

    val cache: DrawRenderCache = DrawCommands.renderCache()

    val current: DrawEditorState get() = _state.value

    fun dispatch(intent: DrawIntent) {
        val before = _state.value
        val next = DrawReducer.reduce(before, intent)
        if (next === before) return
        _state.value = next
        if (DrawCommands.documentChanged(before, next)) {
            cache.retain(next.document.elements)
            onDocumentChanged(next)
        }
    }

    fun reload(document: DrawDocument): Boolean {
        if (DrawCommands.isDrawing(current)) return false
        cache.clear()
        _state.value = DrawReducer.reduce(current, DrawCommands.load(document))
        return true
    }
}

interface DrawingNavigator {
    fun navigate(panX: Float, panY: Float, zoom: Float, rotation: Float, focusX: Float, focusY: Float)

    fun end()
}

class DrawingSessionNavigator(private val session: DrawingSession) : DrawingNavigator {
    override fun navigate(panX: Float, panY: Float, zoom: Float, rotation: Float, focusX: Float, focusY: Float) =
        session.dispatch(DrawCommands.navigate(panX, panY, zoom, rotation, focusX, focusY))

    override fun end() = session.dispatch(DrawCommands.navigateEnd())
}

class DrawingSessions(private val onWrite: (NoteContentModel.Drawing) -> Unit) {

    private class Entry(val session: DrawingSession, var content: NoteContentModel.Drawing, var written: String, var stored: Boolean)

    private val entries = LinkedHashMap<String, Entry>()

    private var activeId: String? = null

    fun get(contentId: String?): DrawingSession? = contentId?.let { entries[it]?.session }

    fun sessionFor(content: NoteContentModel.Drawing, readOnly: Boolean, stored: Boolean = true): DrawingSession {
        entries[content.id]?.let { return it.session }
        val template = get(activeId)?.current
        val state = if (template != null) DrawNoteContents.stateCarrying(content, template, readOnly)
        else DrawNoteContents.stateFor(content, DrawAuthors.local(), readOnly)
        lateinit var entry: Entry
        val session = DrawingSession(state) { next -> write(entry, next) }
        entry = Entry(session, content, content.drawing, stored)
        entries[content.id] = entry
        return session
    }

    fun activate(contentId: String) {
        if (contentId == activeId) return
        val previous = get(activeId)
        activeId = contentId
        val next = get(contentId) ?: return
        if (previous != null) next.dispatch(DrawCommands.carryToolbox(previous.current))
    }

    fun sync(contents: List<NoteContentModel>) {
        val drawings = contents.filterIsInstance<NoteContentModel.Drawing>().associateBy { it.id }
        val gone = mutableListOf<String>()
        entries.forEach { (id, entry) ->
            val latest = drawings[id]
            when {
                latest == null -> if (entry.stored) gone.add(id)
                else -> {
                    entry.stored = true
                    entry.content = latest
                    if (latest.drawing != entry.written && entry.session.reload(DrawNoteContents.documentOf(latest))) {
                        entry.written = latest.drawing
                    }
                }
            }
        }
        gone.forEach { entries.remove(it) }
    }

    private fun write(entry: Entry, state: DrawEditorState) {
        val updated = DrawNoteContents.written(entry.content, state.document)
        entry.content = updated
        entry.written = updated.drawing
        entry.stored = true
        onWrite(updated)
    }
}
