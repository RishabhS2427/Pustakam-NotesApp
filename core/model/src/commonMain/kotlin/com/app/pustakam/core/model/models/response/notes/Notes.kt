package com.app.pustakam.core.model.models.response.notes

import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.common.util.isDoc
import com.app.pustakam.core.common.util.isGalleryEligible
import com.app.pustakam.core.common.util.isImage
import kotlinx.serialization.Serializable

// 🔧 15-Jul-2026 Phase 0.1: shared page size for the notes list (Android list paging; a page is
//   "full" when it returns exactly this many notes, so fewer means no next page).
const val NOTES_PAGE_SIZE = 20

@Serializable
data class Notes(val notes: ArrayList<Note> = arrayListOf(), val count: Int = 0, val page: Int = 0)

// 🔧 15-Jul-2026 Summary query: what the LIST SCREEN renders — never the full contents.
//   snippet is null for notes without any text (media/doc-only); the per-type counts let the
//   card describe those ("3 photos · 1 audio"); thumbnailPath is the first visual media.
@Serializable
data class NoteSummary(
    val id: String,
    val title: String? = null,
    val categoryId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val snippet: String? = null,
    val contentCount: Int = 0,
    val imageCount: Int = 0,
    val videoCount: Int = 0,
    val audioCount: Int = 0,
    val docCount: Int = 0,
    val thumbnailPath: String? = null,
    val shared: Boolean = false,
)

// 🔧 15-Jul-2026 (iOS parity): a contents-less Note carrying just the identity/header fields.
//   iOS navigation (Router.Destination.NoteEditor) carries a Note?; the editor re-reads the full
//   note from the DB by id, so the stub only needs to get the user to the right note.
fun NoteSummary.toNoteStub(): Note = Note(
    id = id, title = title, updatedAt = updatedAt, createdAt = createdAt, categoryId = categoryId,
)

// 🔧 15-Jul-2026: builds the same summary shape from an in-memory Note — used to keep the
//   summaries flow in sync after a save/delete without re-querying the DB.
fun Note.toSummary(): NoteSummary {
    val sorted = contents.sortedBy { it.position }
    val snippet = sorted.filterIsInstance<NoteContentModel.TextContent>()
        .firstOrNull { it.text.isNotBlank() }?.text?.take(200)
    val thumb = sorted.filterIsInstance<NoteContentModel.MediaContent>()
        .firstOrNull { it.type.isGalleryEligible()}
        // 🔧 15-Jul-2026 iOS MEDIA-LOST FIX: thumbnailPath resolved too (stale container UUID after app update)
        ?.let { com.app.pustakam.core.common.util.resolveLocalFilePath(it.thumbnailPath) ?: it.getMediaUrl().takeIf { p -> p.isNotEmpty() } }
    return NoteSummary(
        id = id, title = title, categoryId = categoryId,
        createdAt = createdAt, updatedAt = updatedAt,
        snippet = snippet,
        contentCount = contents.size,
        imageCount = contents.count { it.type.isImage() },
        videoCount = contents.count { it.type == ContentType.VIDEO },
        audioCount = contents.count { it.type == ContentType.AUDIO },
        docCount = contents.count { it.type.isDoc() },
        thumbnailPath = thumb,
    )
}