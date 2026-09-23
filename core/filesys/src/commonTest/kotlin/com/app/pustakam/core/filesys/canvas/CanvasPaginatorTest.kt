package com.app.pustakam.core.filesys.canvas

import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import com.app.pustakam.core.richtext.master.model.CanvasNode
import com.app.pustakam.core.richtext.master.model.CanvasRect
import com.app.pustakam.core.richtext.master.model.CanvasRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 🔧 23-Sep-2026 — pins the contract a canvas page now has to keep: it CARRIES contents rather
 * than standing for one, consecutive similar contents share a page because the reading-mode
 * grouping says so, an empty note still shows paper, and an old canvas upgrades without losing
 * a single content. A failure here means a note opens with blocks missing or duplicated.
 */
class CanvasPaginatorTest {

    private fun text(id: String, position: Double, body: String = "hello") =
        NoteContentModel.TextContent(
            id = id, noteId = NOTE, text = body, position = position,
            updatedAt = null, createdAt = null
        )

    private fun image(id: String, position: Double) = NoteContentModel.MediaContent(
        id = id, noteId = NOTE, url = "", position = position, type = ContentType.IMAGE,
        localPath = null, thumbnailPath = null, updatedAt = null, createdAt = null
    )

    private fun widget(id: String, contentId: String, parentId: String?, x: Float, y: Float) =
        CanvasNode(
            id = id,
            kind = ContentType.TEXT,
            rect = CanvasRect(x, y, 200f, 100f),
            contentId = contentId,
            parentId = parentId
        )

    // ---- paper ----

    @Test
    fun anEmptyNoteStillShowsOnePage() {
        val document = CanvasPaginator.buildDefault(emptyList())
        assertEquals(1, document.pages.size)
        assertTrue(document.orderedWidgets.isEmpty())
        assertEquals(CanvasRole.PAGE, document.pages.first().role)
    }

    @Test
    fun aPageCarriesNoContentOfItsOwn() {
        val document = CanvasPaginator.buildDefault(listOf(text("t1", 0.0)))
        assertEquals(null, document.pages.first().contentId)
        assertEquals("t1", document.orderedWidgets.single().contentId)
    }

    @Test
    fun everyContentGetsExactlyOneWidget() {
        val contents = listOf(text("t1", 0.0), image("m1", 1.0), image("m2", 2.0), text("t2", 3.0))
        val document = CanvasPaginator.buildDefault(contents)
        assertEquals(
            listOf("t1", "m1", "m2", "t2"),
            document.orderedWidgets.mapNotNull { it.contentId }
        )
    }

    @Test
    fun aBlankTextBlockStillGetsAWidget() {
        // the reader drops blank paragraphs; an editor must keep somewhere to type
        val contents = listOf(text("t1", 0.0, body = ""), text("t2", 1.0))
        val document = CanvasPaginator.buildDefault(contents)
        assertEquals(2, document.orderedWidgets.size)
        assertNotNull(document.nodeForContent("t1"))
    }

    // ---- grouping comes from the reading-mode engine ----

    @Test
    fun consecutiveImagesShareOnePage() {
        val contents = listOf(image("m0", 0.0), image("m1", 1.0))
        val document = CanvasPaginator.buildDefault(contents)
        val pages = document.orderedWidgets.mapNotNull { it.parentId }.distinct()
        assertEquals(1, pages.size, "a run of images is one block, so it is one page")
    }

    @Test
    fun eachPageHoldsAContiguousRunOfTheNote() {
        val contents = listOf(
            text("t0", 0.0), image("m1", 1.0), image("m2", 2.0), text("t3", 3.0), image("m4", 4.0)
        )
        val document = CanvasPaginator.buildDefault(contents)
        val slotOf = document.orderedWidgets.withIndex().associate { (i, node) -> node.id to i }
        document.pages.forEach { page ->
            val slots = document.widgetsOf(page.id).mapNotNull { slotOf[it.id] }
            if (slots.size > 1) {
                assertEquals(
                    (slots.first()..slots.last()).toList(),
                    slots,
                    "page ${page.id} holds a gap in the note's order"
                )
            }
        }
    }

    @Test
    fun everyWidgetSitsOnARealPage() {
        val contents = listOf(text("t1", 0.0), image("m1", 1.0), text("t2", 2.0))
        val document = CanvasPaginator.buildDefault(contents)
        document.orderedWidgets.forEach { widget ->
            assertNotNull(document.pageOf(widget.id), "no page for ${widget.contentId}")
        }
    }

    @Test
    fun slotOrderFollowsContentPosition() {
        val contents = listOf(text("t3", 2.0), text("t1", 0.0), text("t2", 1.0))
        val document = CanvasPaginator.buildDefault(contents)
        assertEquals(
            listOf("t1", "t2", "t3"),
            document.orderedWidgets.mapNotNull { it.contentId }
        )
    }

    // ---- geometry ----

    @Test
    fun aWidgetRectIsRelativeToItsPaper() {
        val contents = listOf(text("t1", 0.0))
        val document = CanvasPaginator.buildDefault(contents)
        val page = document.pages.first()
        val widget = document.orderedWidgets.single()
        val moved = document.replacing(page.movedTo(500f, 300f))
        assertEquals(widget.rect.x, moved.nodeById(widget.id)!!.rect.x, "the widget did not move")
        assertEquals(500f + widget.rect.x, moved.absoluteRectOf(moved.nodeById(widget.id)!!).x)
    }

    @Test
    fun thePaperKeepsItsSizeAndItsContentReachesPastIt() {
        val document = CanvasPaginator.buildDefault(listOf(text("t1", 0.0)))
        val page = document.pages.first()
        val widget = document.orderedWidgets.single()
        val tall = document.replacing(widget.resizedTo(widget.rect.width, 4000f))
        assertEquals(page.rect, tall.pages.first().rect, "the paper never grows with its text")
        assertTrue(tall.contentExtentOf(page.id).height >= 4000f, "the page scrolls to it")
    }

    @Test
    fun aDropBetweenTwoWidgetsTakesTheMidpoint() {
        val contents = (0 until 3).map { text("t$it", it.toDouble()) }
        val document = CanvasPaginator.buildDefault(contents)
        val page = document.pages.first()
        val widgets = document.widgetsOf(page.id)
        val order = document.slotOrderAt(
            page.id, widgets[1].rect.x, widgets[1].rect.y - 1f, widgets[2].id
        )
        assertTrue(order > widgets[0].slotOrder && order < widgets[1].slotOrder, "got $order")
    }

    @Test
    fun noTwoWidgetsOverlapAfterAnUpgrade() {
        val old = listOf(
            widget("page-1", "t1", null, 0f, 0f),
            widget("kid-1", "m1", "page-1", 10f, 10f),
            widget("kid-2", "m2", "page-1", 30f, 40f)
        )
        val upgraded = CanvasPaginator.upgrade(old, 360f, 640f)
        val onPage = upgraded.widgetsOf("page-1")
        onPage.forEach { a ->
            onPage.filter { it.id != a.id }.forEach { b ->
                assertFalse(a.rect.intersects(b.rect), "${a.contentId} overlaps ${b.contentId}")
            }
        }
    }

    // ---- old canvases ----

    @Test
    fun anOldCanvasIsDetected() {
        val old = listOf(widget("n1", "t1", null, 0f, 0f))
        assertTrue(CanvasPaginator.needsUpgrade(old))
        assertFalse(CanvasPaginator.needsUpgrade(CanvasPaginator.buildDefault(emptyList()).nodes))
    }

    @Test
    fun upgradeKeepsThePageIdAndLosesNoContent() {
        val old = listOf(
            widget("page-1", "t1", null, 0f, 0f).copy(name = "Chapter one"),
            widget("kid-1", "m1", "page-1", 20f, 200f),
            widget("kid-2", "m2", "page-1", 20f, 400f)
        )
        val upgraded = CanvasPaginator.upgrade(old, 360f, 640f)

        val page = upgraded.pages.single()
        assertEquals("page-1", page.id, "the page kept its identity")
        assertEquals("Chapter one", page.name, "the page kept its name")
        assertEquals(null, page.contentId, "the page stopped standing for a content")
        assertEquals(
            listOf("t1", "m1", "m2"),
            upgraded.orderedWidgets.mapNotNull { it.contentId },
            "every content that had a node still has one, in the order it was drawn"
        )
    }

    @Test
    fun upgradeRebasesChildRectsOntoTheirPaper() {
        val old = listOf(
            widget("page-1", "t1", null, 100f, 50f),
            widget("kid-1", "m1", "page-1", 120f, 250f)
        )
        val upgraded = CanvasPaginator.upgrade(old, 360f, 640f)
        val moved = upgraded.orderedWidgets.first { it.contentId == "m1" }
        assertEquals(20f, moved.rect.x, "120 absolute on a page at x=100 is 20 relative")
        assertEquals(200f, moved.rect.y, "250 absolute on a page at y=50 is 200 relative")
        assertEquals(120f, upgraded.absoluteRectOf(moved).x, "and it draws where it always did")
    }

    @Test
    fun upgradeOfACurrentCanvasChangesNothingButGuaranteesPaper() {
        val current = CanvasPaginator.buildDefault(listOf(text("t1", 0.0))).nodes
        val upgraded = CanvasPaginator.upgrade(current, 360f, 640f)
        assertEquals(current, upgraded.nodes)
    }

    private companion object {
        const val NOTE = "note-1"
    }
}
