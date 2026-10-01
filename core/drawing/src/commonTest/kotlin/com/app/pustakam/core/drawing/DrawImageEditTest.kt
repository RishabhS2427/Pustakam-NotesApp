package com.app.pustakam.core.drawing

import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.drawing.editor.DrawCommands
import com.app.pustakam.core.drawing.note.DrawNoteContents
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DrawImageEditTest {

    private fun image(id: String, position: Double = 0.0, editedFrom: String? = null) = NoteContentModel.MediaContent(
        position = position, noteId = "note-1", type = ContentType.IMAGE, updatedAt = null, createdAt = null,
        id = id, localPath = "/files/$id.png", editedFrom = editedFrom
    )

    private fun inked(imageId: String, width: Float = 400f, height: Float = 300f): NoteContentModel.Drawing {
        val anchors = listOf(DrawCommands.anchor(DrawNoteContents.imageAnchorId(imageId), 0f, 0f, width, height))
        val state = DrawTestKit.stroke(
            DrawTestKit.overlay(),
            DrawTestKit.line(DrawTestKit.point(40f, 40f), DrawTestKit.point(200f, 120f), 12),
            anchors = anchors
        )
        return DrawNoteContents.written(DrawNoteContents.createAnnotation("note-1", 5.0, imageId), state.document)
    }

    @Test
    fun anEditedCopyIsEditedThroughItsOriginal() {
        val contents = listOf(image("photo", 1.0), image("photo-edit", 1.5, editedFrom = "photo"))
        assertEquals("photo", DrawNoteContents.editTargetOf(contents, "photo-edit")?.id)
        assertEquals("photo", DrawNoteContents.editTargetOf(contents, "photo")?.id)
        assertEquals("photo-edit", DrawNoteContents.editedCopyOf(contents, "photo")?.id)
        assertTrue(DrawNoteContents.showsWithOriginal(contents, contents[1]))
        assertFalse(DrawNoteContents.showsWithOriginal(contents, contents[0]))
        assertNull(DrawNoteContents.editTargetOf(contents, "missing"))
    }

    @Test
    fun aCopyWhoseOriginalIsGoneStandsAlone() {
        val contents = listOf(image("photo-edit", 1.5, editedFrom = "photo"))
        assertEquals("photo-edit", DrawNoteContents.editTargetOf(contents, "photo-edit")?.id)
        assertFalse(DrawNoteContents.showsWithOriginal(contents, contents.single()))
    }

    @Test
    fun removingAnImageOrItsCopyTakesTheInkWithIt() {
        val annotation = inked("photo")
        val unrelated = inked("other")
        val contents = listOf(image("photo", 1.0), image("photo-edit", 1.5, editedFrom = "photo"), annotation, unrelated)
        assertEquals(listOf(annotation.id), DrawNoteContents.dependentsOf(contents, "photo").map { it.id })
        assertEquals(listOf(annotation.id), DrawNoteContents.dependentsOf(contents, "photo-edit").map { it.id })
        assertTrue(DrawNoteContents.dependentsOf(contents, annotation.id).isEmpty())
    }

    @Test
    fun inkIsExportedAtTheImagesOwnSize() {
        val annotation = inked("photo")
        assertTrue(DrawNoteContents.hasInk(annotation, "photo"))
        assertFalse(DrawNoteContents.hasInk(annotation, "other"))
        assertFalse(DrawNoteContents.hasInk(null, "photo"))
        val small = DrawNoteContents.inkFrame(annotation, "photo", 400f, 300f)
        val large = DrawNoteContents.inkFrame(annotation, "photo", 800f, 600f)
        assertTrue(small.isNotEmpty())
        assertEquals(small.first().matrix.a * 2f, large.first().matrix.a, 0.0001f)
        assertTrue(DrawNoteContents.inkFrame(annotation, "other", 400f, 300f).isEmpty())
    }

    @Test
    fun anEditedCopyTakesThePlaceOfThePreviousOne() {
        val first = DrawNoteContents.editedCopy(listOf(image("photo", 1.0), image("next", 2.0)), image("photo", 1.0), "/files/one.png", 800, 600)
        assertEquals("photo", first.editedFrom)
        assertEquals(1.5, first.position, 0.0001)
        assertEquals(ContentType.IMAGE, first.type)
        assertEquals(800, first.width)
        val second = DrawNoteContents.editedCopy(listOf(image("photo", 1.0), first, image("next", 2.0)), image("photo", 1.0), "/files/two.png", 800, 600)
        assertEquals(first.position, second.position, 0.0001)
        assertTrue(first.id != second.id)
        assertEquals("/files/two.png", second.localPath)
    }

    @Test
    fun theCopySitsRightAfterItsOriginal() {
        val contents = listOf(image("a", 1.0), image("b", 2.0), image("c", 4.0))
        assertEquals(1.5, DrawNoteContents.positionAfter(contents, "a"), 0.0001)
        assertEquals(5.0, DrawNoteContents.positionAfter(contents, "c"), 0.0001)
        assertEquals(5.0, DrawNoteContents.positionAfter(contents, "missing"), 0.0001)
    }
}
