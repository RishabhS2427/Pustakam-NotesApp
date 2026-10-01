package com.app.pustakam.feature.notes.domain.history

import com.app.pustakam.core.model.models.response.notes.Note
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import com.app.pustakam.core.richtext.master.model.CanvasDocument
import com.app.pustakam.core.richtext.master.presentation.NoteCanvasConverter

object NoteSnapshots {

    fun noteEditor(note: Note, title: String?, contents: List<NoteContentModel>): Note =
        note.withTitleAndContents(title, contents).withCanvas(null)

    fun masterEditor(note: Note, contents: List<NoteContentModel>, document: CanvasDocument): Note =
        note.withContents(contents).withCanvas(NoteCanvasConverter.toNoteNodes(document))

    fun canvasOf(snapshot: Note): CanvasDocument? =
        snapshot.canvas?.takeIf { it.isNotEmpty() }?.let(NoteCanvasConverter::fromNoteNodes)
}
