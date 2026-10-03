package com.app.pustakam.feature.notes.data.sync

import com.app.pustakam.core.model.models.response.notes.Note
import com.app.pustakam.core.model.models.response.notes.NoteContentModel

// 🔄 20-Aug-2026 sync: the ONE place that knows what belongs on the wire and what is device state.
//   copy() is used deliberately here: withX() helpers stamp updatedAt, and neither preparing a note
//   for the wire nor merging a pulled one is a user edit — stamping would re-dirty it forever.
object NoteWireMapper {

    /** 🖼️ localPath and thumbnailPath are absolute paths on THIS device. The server strips them
     *  anyway (backend.md S1-11 / E22); not sending them keeps device paths off the network. */
    fun toWire(note: Note): Note = note.copy(
        deletedAt = null,
        serverUpdatedAt = null,
        share = null,
        contents = note.contents.map { it.forWire() },
    )

    private fun NoteContentModel.forWire(): NoteContentModel =
        if (this is NoteContentModel.MediaContent) copy(localPath = null, thumbnailPath = null) else this

    /** 🖼️ a pulled note carries no local file paths. Without this merge, pulling a note the device
     *  already has would orphan every media file that is sitting on its disk right now. */
    fun mergeLocalMedia(incoming: Note, local: Note?): Note {
        val localMedia = local?.contents
            ?.filterIsInstance<NoteContentModel.MediaContent>()
            ?.associateBy { it.id }
            ?: return incoming
        if (localMedia.isEmpty()) return incoming

        return incoming.copy(
            contents = incoming.contents.map { content ->
                if (content !is NoteContentModel.MediaContent) return@map content
                val known = localMedia[content.id] ?: return@map content
                content.copy(
                    localPath = content.localPath ?: known.localPath,
                    thumbnailPath = content.thumbnailPath ?: known.thumbnailPath,
                )
            }
        )
    }

    /** 🔄 a local note with unpushed edits must never be overwritten by a pull — it has not had its
     *  turn at the server yet. It stays dirty and the next push resolves it properly. */
    fun canApplyOverLocal(local: Note?, syncedMarker: String): Boolean =
        local == null || local.syncStatus == syncedMarker
}
