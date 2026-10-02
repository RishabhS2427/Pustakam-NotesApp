package com.app.pustakam.core.drawing

import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.drawing.anchor.DrawAnchoring
import com.app.pustakam.core.drawing.editor.DrawCommands
import com.app.pustakam.core.drawing.model.DrawPoint
import com.app.pustakam.core.drawing.note.DrawBoardInk
import com.app.pustakam.core.drawing.note.DrawBoardPage
import com.app.pustakam.core.drawing.note.DrawNoteContents
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import com.app.pustakam.core.richtext.master.model.CanvasDocument
import com.app.pustakam.core.richtext.master.model.CanvasNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DrawBoardInkTest {

    private val header = 32f

    private val page = CanvasNode.page(order = 0.0, x = 0f, y = 0f, width = 360f, height = 640f)

    private fun widget(contentId: String, y: Float, height: Float, order: Double, x: Float = 8f, width: Float = 344f) =
        CanvasNode.of(ContentType.TEXT, contentId, x, y, width, height, parentId = page.id).withSlotOrder(order)

    private val board = CanvasDocument(listOf(page, widget("text-1", 8f, 100f, 0.0), widget("text-2", 116f, 80f, 1.0)))

    private fun frame(scale: Float = 1f, scrolled: Float = 0f) =
        DrawBoardPage(page.id, 0f, 0f, 360f * scale, 640f * scale, 0f, header - scrolled, scale)

    private fun overlay(vararg strokes: List<DrawPoint>, document: CanvasDocument = board, frames: List<DrawBoardPage> = listOf(frame())): NoteContentModel.Drawing {
        val anchors = DrawBoardInk.anchors(document, frames)
        val state = strokes.fold(DrawTestKit.overlay()) { acc, points -> DrawTestKit.stroke(acc, points, anchors = anchors) }
        return DrawNoteContents.written(DrawNoteContents.create("note-1", 9.0, DrawCommands.overlaySurface(), 0f, 0f), state.document)
    }

    private fun across(y: Float, fromX: Float = 60f, toX: Float = 200f) =
        DrawTestKit.line(DrawTestKit.point(fromX, y), DrawTestKit.point(toX, y), 20)

    @Test
    fun inkBesideAWidgetFollowsTheWidgetNotThePaper() {
        val anchors = DrawBoardInk.anchors(board, listOf(frame()))
        assertEquals("text-1", DrawAnchoring.pick(anchors, 100f, header + 50f)?.id)
        assertEquals("text-1", DrawAnchoring.pick(anchors, 356f, header + 50f)?.id)
        assertEquals("text-2", DrawAnchoring.pick(anchors, 100f, header + 150f)?.id)
        assertEquals(page.id, DrawAnchoring.pick(anchors, 100f, header + 400f)?.id)
    }

    @Test
    fun widgetInkIsMeasuredAgainstThePageColumnLikeANoteRow() {
        val anchors = DrawBoardInk.anchors(board, listOf(frame(scale = 2f)))
        val row = anchors.last { it.id == "text-2" }
        assertEquals(344f * 2f / DrawAnchoring.REFERENCE_WIDTH, row.scale, 0.0001f)
        assertEquals(CanvasNode.PAGE_PADDING * 2f, row.originX, 0.0001f)
        assertEquals(header + 116f * 2f, row.originY, 0.0001f)
    }

    @Test
    fun aWidgetScrolledOutOfThePaperCannotCatchInk() {
        val anchors = DrawBoardInk.anchors(board, listOf(frame(scrolled = 200f)))
        assertTrue(anchors.none { it.id == "text-1" })
        assertTrue(anchors.filter { it.id == "text-2" }.all { it.y >= 0f })
    }

    @Test
    fun inkOnTheBarePaperBecomesATransparentBlockWhereItWasDrawn() {
        val ink = overlay(across(header + 300f), across(header + 40f))
        val settled = DrawBoardInk.settle(ink, board, 10.0)
        val block = settled.blocks.single()
        val drawing = DrawNoteContents.documentOf(block.content)
        val element = drawing.elements.single()
        assertTrue(block.content.isWidget())
        assertTrue(drawing.paper.transparent)
        assertEquals(null, element.anchorId)
        assertEquals(page.id, block.node.parentId)
        assertEquals(ContentType.DRAWING, block.node.kind)
        assertEquals(block.content.id, block.node.contentId)
        val drawn = DrawNoteContents.documentOf(ink).elements.single { it.anchorId == page.id }.points.first()
        assertEquals(drawn.x, block.node.rect.x + element.points.first().x, 0.1f)
        assertEquals(drawn.y, block.node.rect.y + element.points.first().y, 0.1f)
        assertEquals(300f, drawn.y, 0.5f)
        assertTrue(block.node.slotOrder > 1.0)
        val left = DrawNoteContents.documentOf(settled.overlay).elements
        assertEquals(listOf("text-1"), left.map { it.anchorId })
    }

    @Test
    fun separateDoodlesStackAsSeparateBlocksInReadingOrder() {
        val ink = overlay(across(header + 260f), across(header + 500f))
        val blocks = DrawBoardInk.settle(ink, board, 10.0).blocks
        assertEquals(2, blocks.size)
        assertTrue(blocks[0].node.rect.bottom <= blocks[1].node.rect.y)
        assertTrue(blocks[0].node.slotOrder < blocks[1].node.slotOrder)
        assertEquals(listOf(10.0, 11.0), blocks.map { it.content.position })
    }

    @Test
    fun aBlockNeverCoversAWidget() {
        val ink = overlay(DrawTestKit.line(DrawTestKit.point(100f, header + 210f), DrawTestKit.point(120f, header + 150f), 20))
        val block = DrawBoardInk.settle(ink, board, 10.0).blocks.single()
        board.nodes.filter { it.isWidget }.forEach { assertFalse(it.rect.intersects(block.node.rect)) }
    }

    @Test
    fun inkOffEveryPageLandsAfterTheLastPagesWidgetsAndFitsItsColumn() {
        val wide = DrawTestKit.line(DrawTestKit.point(-400f, 900f), DrawTestKit.point(900f, 960f), 40)
        val ink = overlay(wide, frames = emptyList())
        val block = DrawBoardInk.settle(ink, board, 10.0).blocks.single()
        val element = DrawNoteContents.documentOf(block.content).elements.single()
        assertEquals(196f + CanvasNode.PAGE_PADDING, block.node.rect.y, 0.01f)
        assertEquals(CanvasNode.widgetWidthOn(page.rect.width), block.node.rect.width, 0.01f)
        assertTrue(element.bounds.x >= -0.01f && element.bounds.right <= block.node.rect.width + 0.01f)
    }

    @Test
    fun inkFarBelowThePaperIsPulledUpUnderTheWidgets() {
        val ink = overlay(across(header + 1500f))
        val block = DrawBoardInk.settle(ink, board, 10.0).blocks.single()
        assertEquals(196f + CanvasNode.PAGE_PADDING, block.node.rect.y, 0.01f)
    }

    @Test
    fun aBoardWithoutPaperAsksForAPageFirst() {
        val empty = CanvasDocument()
        val ink = overlay(across(300f), document = empty, frames = emptyList())
        assertTrue(DrawBoardInk.needsPage(ink, empty))
        assertTrue(DrawBoardInk.settle(ink, empty, 0.0).isEmpty)
        assertFalse(DrawBoardInk.needsPage(ink, board))
        assertTrue(DrawBoardInk.hasLooseInk(ink, board))
    }

    @Test
    fun widgetInkStaysOnTheOverlay() {
        val ink = overlay(across(header + 50f))
        assertFalse(DrawBoardInk.hasLooseInk(ink, board))
        val settled = DrawBoardInk.settle(ink, board, 10.0)
        assertTrue(settled.isEmpty)
        assertEquals(ink.drawing, settled.overlay.drawing)
    }

    @Test
    fun scaledInkKeepsItsShape() {
        val element = DrawTestKit.stroke(DrawTestKit.overlay(), across(100f)).document.elements.single()
        val moved = element.transformed(0.5f, 10f, 20f)
        assertEquals(element.points.first().x * 0.5f + 10f, moved.points.first().x, 0.0001f)
        assertEquals(element.points.first().y * 0.5f + 20f, moved.points.first().y, 0.0001f)
        assertEquals(element.brush.size * 0.5f, moved.brush.size, 0.0001f)
    }
}
