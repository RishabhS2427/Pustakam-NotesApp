package com.app.pustakam.feature.notes.domain.editor

import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoteFilesTest {

    private fun media(id: String, thumbnail: String? = null) = NoteContentModel.MediaContent(
        id = id, noteId = "note-1", type = ContentType.IMAGE, position = 0.0,
        localPath = "/files/$id.png", thumbnailPath = thumbnail, updatedAt = null, createdAt = null
    )

    private fun text(id: String) = NoteContentModel.TextContent(
        id = id, noteId = "note-1", text = "", position = 0.0, updatedAt = null, createdAt = null
    )

    @Test
    fun mediaOwnsItsFileAndThumbnailAndTextOwnsNothing() {
        assertEquals(listOf("/files/m1.png", "/thumbs/m1.png"), NoteFiles.pathsOf(media("m1", "/thumbs/m1.png")))
        assertTrue(NoteFiles.pathsOf(text("t1")).isEmpty())
        assertTrue(NoteFiles.pathsOf(null).isEmpty())
    }

    @Test
    fun onlyFilesNoLiveContentUsesAreDiscarded() {
        val trashed = NoteFiles.pathsOfAll(listOf(media("m1", "/thumbs/m1.png"), media("m2"))).toSet()
        val discarded = NoteFiles.discardable(trashed, listOf(media("m2"), text("t1")))
        assertEquals(setOf("/files/m1.png", "/thumbs/m1.png"), discarded.toSet())
    }
}
