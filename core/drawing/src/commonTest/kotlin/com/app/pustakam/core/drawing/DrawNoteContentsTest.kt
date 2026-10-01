package com.app.pustakam.core.drawing

import com.app.pustakam.core.drawing.editor.DrawCommands
import com.app.pustakam.core.drawing.model.DrawPaperSize
import com.app.pustakam.core.drawing.model.DrawSurface
import com.app.pustakam.core.drawing.note.DrawAuthors
import com.app.pustakam.core.drawing.note.DrawNoteContents
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DrawNoteContentsTest {

    @Test
    fun aNewPageContentOpensAsABlankPageOfItsSize() {
        val content = DrawNoteContents.create("note-1", 3.0, DrawSurface.PAGE, 390f, 780f)
        assertEquals("note-1", content.noteId)
        assertTrue(content.isPage())
        assertEquals(2f, DrawNoteContents.aspectOf(content), 0.001f)
        val state = DrawNoteContents.stateFor(content, DrawAuthors.local(), readOnly = false)
        assertEquals(DrawSurface.PAGE, state.surface)
        assertEquals(390f, state.document.paper.width, 0.001f)
        assertTrue(state.document.isEmpty)
    }

    @Test
    fun strokesSurviveTheTripThroughTheContentRow() {
        val content = DrawNoteContents.create("note-1", 0.0, DrawSurface.WIDGET, 300f, 200f)
        val sized = DrawTestKit.reduce(DrawNoteContents.stateFor(content, "me", false), DrawCommands.resize(300f, 200f))
        val drawn = DrawTestKit.horizontal(sized, 100f, 40f, 200f)
        val saved = DrawNoteContents.written(content, drawn.document)
        assertEquals(content.id, saved.id)
        val reopened = DrawNoteContents.documentOf(saved)
        assertEquals(drawn.document.elements.map { it.id }, reopened.elements.map { it.id })
        assertEquals(DrawSurface.WIDGET, reopened.surface)
    }

    @Test
    fun overlaysAreFoundAndNeverListedAsBlocks() {
        val overlay = DrawNoteContents.create("n", 5.0, DrawSurface.OVERLAY, 0f, 0f)
        val page = DrawNoteContents.create("n", 1.0, DrawSurface.PAGE, 100f, 100f)
        val contents = listOf<NoteContentModel>(page, overlay)
        assertEquals(overlay, DrawNoteContents.overlayOf(contents))
        assertEquals(listOf<NoteContentModel>(page), DrawNoteContents.withoutOverlays(contents))
        assertNull(DrawNoteContents.overlayOf(listOf(page)))
        assertEquals(6.0, DrawNoteContents.nextPosition(contents), 0.0001)
    }

    @Test
    fun aDamagedPayloadStillOpensTheRightSurface() {
        val content = DrawNoteContents.create("n", 0.0, DrawSurface.PAGE, 120f, 240f).withDrawing("{broken")
        val document = DrawNoteContents.documentOf(content)
        assertEquals(DrawSurface.PAGE, document.surface)
        assertTrue(document.isEmpty)
    }

    @Test
    fun aWidgetKeepsItsFrameAndFallsBackWhenUnsized() {
        val widget = DrawNoteContents.create("n", 0.0, DrawSurface.WIDGET, 320.4f, 180.6f)
        assertEquals(320f, DrawNoteContents.frameWidth(widget), 0.001f)
        assertEquals(181f, DrawNoteContents.frameHeight(widget), 0.001f)
        assertTrue(DrawNoteContents.isFramed(widget, 320f, 181f))
        val grown = DrawNoteContents.framed(widget, 400f, 250f)
        assertEquals(400, grown.width)
        assertEquals(250, grown.height)
        assertEquals(widget.drawing, grown.drawing)
        val unsized = DrawNoteContents.create("n", 0.0, DrawSurface.WIDGET, 0f, 0f)
        assertEquals(DrawNoteContents.DEFAULT_FRAME_WIDTH, DrawNoteContents.frameWidth(unsized), 0.001f)
        assertEquals(DrawNoteContents.DEFAULT_FRAME_WIDTH * DrawNoteContents.DEFAULT_ASPECT, DrawNoteContents.frameHeight(unsized), 0.01f)
    }

    @Test
    fun documentAnnotationsStayApartFromTheNoteOverlay() {
        val overlay = DrawNoteContents.create("n", 2.0, DrawSurface.OVERLAY, 0f, 0f)
        val annotation = DrawNoteContents.createAnnotation("n", 3.0, "doc-7")
        val contents = listOf<NoteContentModel>(annotation, overlay)
        assertEquals(overlay, DrawNoteContents.overlayOf(contents))
        assertEquals(annotation, DrawNoteContents.annotationOf(contents, "doc-7"))
        assertNull(DrawNoteContents.annotationOf(contents, "doc-8"))
        assertTrue(annotation.isOverlay())
        assertTrue(annotation.isOverlayDrawing())
        assertEquals(DrawSurface.OVERLAY, DrawNoteContents.documentOf(annotation).surface)
        assertEquals("doc-7:4", DrawNoteContents.pageAnchorId("doc-7", 4))
        assertEquals(emptyList<NoteContentModel>(), DrawNoteContents.withoutOverlays(contents))
    }

    @Test
    fun aPageRowFollowsItsPaperSoReadersKnowItsShape() {
        val content = DrawNoteContents.create("n", 0.0, DrawSurface.PAGE, 400f, 800f)
        val state = DrawTestKit.reduce(DrawNoteContents.stateFor(content, "me", false), DrawCommands.resize(400f, 800f))
        val a4 = DrawTestKit.reduce(state, DrawCommands.setPaperSize(state, DrawPaperSize.A4))
        val saved = DrawNoteContents.written(content, a4.document)
        assertEquals(595, saved.width)
        assertEquals(842, saved.height)
        assertEquals(842f / 595f, DrawNoteContents.aspectOf(saved), 0.0001f)
        val widget = DrawNoteContents.create("n", 0.0, DrawSurface.WIDGET, 300f, 200f)
        val widgetState = DrawTestKit.reduce(DrawNoteContents.stateFor(widget, "me", false), DrawCommands.resize(640f, 480f))
        assertEquals(300, DrawNoteContents.written(widget, widgetState.document).width)
    }

    @Test
    fun framesFitTheirBoxWithoutDistortion() {
        assertEquals(0.5f, DrawNoteContents.fitScale(400f, 200f, 200f, 300f), 0.0001f)
        assertEquals(0.25f, DrawNoteContents.fitScale(400f, 800f, 400f, 200f), 0.0001f)
        assertEquals(2f, DrawNoteContents.fitScale(100f, 100f, 200f, 0f), 0.0001f)
        assertEquals(1f, DrawNoteContents.fitScale(0f, 100f, 200f, 300f), 0.0001f)
    }
}
