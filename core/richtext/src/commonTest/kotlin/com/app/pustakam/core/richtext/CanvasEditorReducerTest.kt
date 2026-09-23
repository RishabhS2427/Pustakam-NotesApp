package com.app.pustakam.core.richtext

import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.richtext.master.model.CanvasDocument
import com.app.pustakam.core.richtext.master.model.CanvasNode
import com.app.pustakam.core.richtext.master.model.CanvasRect
import com.app.pustakam.core.richtext.master.model.CanvasRole
import com.app.pustakam.core.richtext.master.model.Viewport
import com.app.pustakam.core.richtext.master.presentation.CanvasCommands
import com.app.pustakam.core.richtext.master.presentation.CanvasEditorIntent
import com.app.pustakam.core.richtext.master.presentation.CanvasEditorReducer
import com.app.pustakam.core.richtext.master.presentation.CanvasEditorState
import com.app.pustakam.core.richtext.master.presentation.PageScrollRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 🔧 24-Sep-2026 — pins how the master editor's pages and widgets behave: a page is a viewport
 * over widgets that keep their own size and place, nothing ever overlaps anything, a tap fits a
 * page and asks for its end, and a carried widget lands on a free spot or goes back home.
 * A failure here is a canvas that loses, hides or stacks someone's content.
 */
class CanvasEditorReducerTest {

    private fun page(id: String, x: Float, order: Double) = CanvasNode(
        id = id,
        kind = ContentType.TEXT,
        rect = CanvasRect(x, 0f, 360f, 640f),
        role = CanvasRole.PAGE,
        pageOrder = order
    )

    private fun widget(
        id: String,
        pageId: String,
        x: Float,
        y: Float,
        order: Double,
        kind: ContentType = ContentType.IMAGE,
        height: Float = 100f
    ) = CanvasNode(
        id = id,
        kind = kind,
        rect = CanvasRect(x, y, 200f, height),
        contentId = "content-$id",
        parentId = pageId,
        slotOrder = order
    )

    private fun board(vararg nodes: CanvasNode) = CanvasEditorState(
        document = CanvasDocument(nodes.toList()),
        viewport = Viewport(widthPx = 1000f, heightPx = 2000f)
    )

    private fun CanvasEditorState.after(vararg intents: CanvasEditorIntent): CanvasEditorState =
        intents.fold(this) { state, intent -> CanvasEditorReducer.reduce(state, intent) }

    private fun CanvasEditorState.node(id: String): CanvasNode = document.nodeById(id)!!

    private fun CanvasDocument.assertNothingOverlaps() {
        pages.forEach { a ->
            pages.filter { it.id != a.id }.forEach { b ->
                assertFalse(a.rect.intersects(b.rect), "page ${a.id} overlaps page ${b.id}")
            }
            val onPage = widgetsOf(a.id)
            onPage.forEach { w ->
                onPage.filter { it.id != w.id }.forEach { o ->
                    assertFalse(w.rect.intersects(o.rect), "widget ${w.id} overlaps ${o.id}")
                }
            }
        }
    }

    // ---- tap on paper ----

    @Test
    fun tappingPaperFitsItToTheScreenAndAsksForItsEnd() {
        val state = board(page("p1", 0f, 0.0)).after(CanvasEditorIntent.FocusPage("p1"))
        assertEquals(CanvasRect(0f, 0f, 968f, 1968f), state.node("p1").rect)
        assertEquals(Viewport.DEFAULT_SCALE, state.viewport.scale)
        assertEquals(PageScrollRequest("p1", 1), state.scrollRequest)
        assertNull(state.editingNodeId, "a tap on paper never opens the keyboard")
    }

    @Test
    fun everyTapIsANewScrollRequest() {
        val state = board(page("p1", 0f, 0.0)).after(
            CanvasEditorIntent.FocusPage("p1"),
            CanvasEditorIntent.FocusPage("p1")
        )
        assertEquals(2, CanvasCommands.scrollTokenFor(state, "p1"))
        assertEquals(0, CanvasCommands.scrollTokenFor(state, "elsewhere"))
    }

    @Test
    fun aFittedPagePushesItsNeighbourAsideInsteadOfCoveringIt() {
        val state = board(page("p1", 0f, 0.0), page("p2", 408f, 1.0))
            .after(CanvasEditorIntent.FocusPage("p1"))
        assertEquals(0f, state.node("p1").rect.x, "the page being looked at never moves")
        assertTrue(state.node("p2").rect.x >= state.node("p1").rect.right)
        state.document.assertNothingOverlaps()
    }

    @Test
    fun fittingAPageNeverResizesItsWidgets() {
        val image = widget("w1", "p1", 24f, 24f, 0.0)
        val state = board(page("p1", 0f, 0.0), image).after(CanvasEditorIntent.FocusPage("p1"))
        assertEquals(image.rect, state.node("w1").rect)
    }

    // ---- text editing ----

    @Test
    fun tappingATextWidgetEditsItAndFitsItsPage() {
        val text = widget("t1", "p1", 24f, 24f, 0.0, kind = ContentType.TEXT)
        val state = board(page("p1", 0f, 0.0), text).after(CanvasEditorIntent.SetEditing("t1"))
        assertEquals("t1", state.editingNodeId)
        assertEquals("p1", state.selectedNodeId)
        assertEquals(968f, state.node("p1").rect.width)
        assertEquals(text.rect, state.node("t1").rect, "the widget keeps its own size")
    }

    @Test
    fun aGrowingTextWidgetPushesTheWidgetBelowItDown() {
        val state = board(
            page("p1", 0f, 0.0),
            widget("t1", "p1", 24f, 24f, 0.0, kind = ContentType.TEXT, height = 96f),
            widget("w1", "p1", 24f, 132f, 1.0)
        ).after(CanvasEditorIntent.MeasureWidget("t1", 300f))
        assertEquals(24f + 300f + CanvasNode.WIDGET_GAP, state.node("w1").rect.y)
        state.document.assertNothingOverlaps()
    }

    @Test
    fun measuringStillWorksWhileTheKeyboardIsUp() {
        val state = board(
            page("p1", 0f, 0.0),
            widget("t1", "p1", 24f, 24f, 0.0, kind = ContentType.TEXT, height = 96f)
        ).after(
            CanvasEditorIntent.SetEditing("t1"),
            CanvasEditorIntent.MeasureWidget("t1", 240f)
        )
        assertEquals(240f, state.node("t1").rect.height)
    }

    @Test
    fun aWidgetBesideAGrowingTextWidgetStaysWhereItIs() {
        val state = board(
            page("p1", 0f, 0.0),
            widget("t1", "p1", 24f, 24f, 0.0, kind = ContentType.TEXT, height = 96f),
            widget("w1", "p1", 240f, 24f, 1.0)
        ).after(CanvasEditorIntent.MeasureWidget("t1", 500f))
        assertEquals(24f, state.node("w1").rect.y, "only what the text now covers moves")
    }

    // ---- carrying a widget ----

    @Test
    fun aWidgetDroppedOnAnotherWidgetSlidesDownToTheFirstFreeSpot() {
        val state = board(
            page("p1", 0f, 0.0),
            widget("a", "p1", 24f, 24f, 0.0),
            widget("b", "p1", 24f, 300f, 1.0)
        ).after(
            CanvasEditorIntent.BeginDrag("b"),
            CanvasEditorIntent.DropWidget("b", "p1", 30f, 50f)
        )
        assertEquals(CanvasRect(30f, 124f + CanvasNode.WIDGET_GAP, 200f, 100f), state.node("b").rect)
        assertNull(state.draggingNodeId)
        state.document.assertNothingOverlaps()
    }

    @Test
    fun aDropNeverLandsLeftOfAboveOrPastThePaper() {
        val base = board(page("p1", 0f, 0.0), widget("b", "p1", 24f, 300f, 0.0))
        val topLeft = base.after(CanvasEditorIntent.DropWidget("b", "p1", -50f, -80f))
        assertEquals(0f, topLeft.node("b").rect.x)
        assertEquals(0f, topLeft.node("b").rect.y)
        val farRight = base.after(CanvasEditorIntent.DropWidget("b", "p1", 1000f, 40f))
        assertEquals(360f - 200f, farRight.node("b").rect.x)
    }

    @Test
    fun aWidgetLetGoOverBareCanvasGoesBackHome() {
        val start = board(page("p1", 0f, 0.0), widget("b", "p1", 24f, 300f, 0.0))
        val state = start.after(
            CanvasEditorIntent.BeginDrag("b"),
            CanvasCommands.cancelLift("b")
        )
        assertEquals(start.node("b"), state.node("b"))
        assertNull(state.draggingNodeId)
    }

    @Test
    fun aWidgetDroppedOnAnotherPageJoinsItAndItsReadingOrder() {
        val state = board(
            page("p1", 0f, 0.0),
            page("p2", 408f, 1.0),
            widget("a", "p1", 24f, 24f, 0.0),
            widget("c", "p2", 24f, 24f, 1.0)
        ).after(
            CanvasEditorIntent.BeginDrag("a"),
            CanvasEditorIntent.DropWidget("a", "p2", 24f, 200f)
        )
        assertEquals("p2", state.node("a").parentId)
        assertEquals(
            listOf("content-c", "content-a"),
            state.document.orderedWidgets.mapNotNull { it.contentId }
        )
        state.document.assertNothingOverlaps()
    }

    @Test
    fun liftingAWidgetWritesNothingUntilItIsDropped() {
        val start = board(page("p1", 0f, 0.0), widget("b", "p1", 24f, 300f, 0.0))
        val lifted = start.after(CanvasEditorIntent.BeginDrag("b"))
        assertTrue(CanvasCommands.nodesToSave(start, lifted).isEmpty())
        val dropped = lifted.after(CanvasEditorIntent.DropWidget("b", "p1", 24f, 24f))
        assertTrue(CanvasCommands.nodesToSave(lifted, dropped).any { it.id == "b" })
    }

    @Test
    fun aResizeWritesEveryPageItPushedAsideWhenTheFingerLifts() {
        val start = board(page("p1", 0f, 0.0), page("p2", 408f, 1.0))
        val resizing = start.after(
            CanvasEditorIntent.BeginResize("p1"),
            CanvasEditorIntent.ResizeNode("p1", 600f, 640f)
        )
        assertTrue(CanvasCommands.nodesToSave(start, resizing).isEmpty(), "nothing mid-gesture")
        val done = resizing.after(CanvasEditorIntent.EndResize)
        val saved = CanvasCommands.nodesToSave(resizing, done).map { it.id }.toSet()
        assertEquals(setOf("p1", "p2"), saved)
        done.document.assertNothingOverlaps()
    }

    @Test
    fun aTapOnBareCanvasLeavesEditMode() {
        val state = board(
            page("p1", 0f, 0.0),
            widget("t1", "p1", 24f, 24f, 0.0, kind = ContentType.TEXT)
        ).after(
            CanvasEditorIntent.SetEditing("t1"),
            CanvasEditorIntent.SelectAt(990f, 1990f)
        )
        assertNull(state.editingNodeId)
    }

    @Test
    fun aDropIsMeasuredFromTheScreenPastScrollAndNameBar() {
        val state = CanvasEditorState(
            document = CanvasDocument(
                listOf(page("p1", 100f, 0.0).copy(rect = CanvasRect(100f, 50f, 360f, 640f)))
            ),
            viewport = Viewport(offsetX = 10f, offsetY = 20f, scale = 2f, widthPx = 1000f, heightPx = 2000f)
        )
        val target = CanvasCommands.dropTargetAt(state, 310f, 220f)
        assertNotNull(target)
        val drop = CanvasCommands.dropWidget(
            state, "w", target, 310f, 220f, scrollX = 40f, scrollY = 60f, headerHeight = 32f
        ) as CanvasEditorIntent.DropWidget
        assertEquals(70f, drop.x)
        assertEquals(64f, drop.y)
    }

    // ---- carrying a page ----

    @Test
    fun aPageLetGoOnAnotherPageSlidesClearOfIt() {
        val state = board(page("p1", 0f, 0.0), page("p2", 408f, 1.0)).after(
            CanvasEditorIntent.BeginDrag("p2"),
            CanvasEditorIntent.DragBy(-400f, 0f),
            CanvasEditorIntent.EndDrag
        )
        state.document.assertNothingOverlaps()
    }

    @Test
    fun aDraggedPageIsWrittenOnceWhenItIsLetGo() {
        val start = board(page("p1", 0f, 0.0))
        val moving = start.after(CanvasEditorIntent.BeginDrag("p1"), CanvasEditorIntent.DragBy(30f, 0f))
        assertTrue(CanvasCommands.nodesToSave(start, moving).isEmpty())
        val done = moving.after(CanvasEditorIntent.EndDrag)
        assertEquals(listOf("p1"), CanvasCommands.nodesToSave(moving, done).map { it.id })
    }

    // ---- pages ----

    @Test
    fun removingAPageMovesItsWidgetsOntoItsNeighbour() {
        val state = board(
            page("p1", 0f, 0.0),
            page("p2", 408f, 1.0),
            widget("a", "p1", 24f, 24f, 0.0),
            widget("c", "p2", 24f, 24f, 1.0)
        ).after(CanvasEditorIntent.RemoveNode("p2"))
        assertNull(state.document.nodeById("p2"))
        assertEquals("p1", state.node("c").parentId)
        state.document.assertNothingOverlaps()
    }

    @Test
    fun theLastPageIsNeverRemoved() {
        val state = board(page("p1", 0f, 0.0)).after(CanvasEditorIntent.RemoveNode("p1"))
        assertNotNull(state.document.nodeById("p1"))
    }

    @Test
    fun aTapThatReachesTheBoardOverAPageChangesNothing() {
        val start = board(page("p1", 0f, 0.0))
        val state = start.after(CanvasEditorIntent.SelectAt(10f, 10f))
        assertEquals(start.document, state.document)
        assertNull(state.scrollRequest, "paper answers its own taps")
    }

    @Test
    fun aPagesContentReachesItsFurthestWidget() {
        val document = CanvasDocument(
            listOf(page("p1", 0f, 0.0), widget("w", "p1", 24f, 500f, 0.0, height = 300f))
        )
        assertEquals(
            CanvasRect(0f, 0f, 224f + CanvasNode.PAGE_PADDING, 800f + CanvasNode.PAGE_PADDING),
            document.contentExtentOf("p1")
        )
    }

    // ---- 24-Sep-2026: where a new widget goes ----

    private fun lookingAt(state: CanvasEditorState, x: Float): CanvasEditorState =
        state.copy(viewport = state.viewport.copy(offsetX = -x, offsetY = 0f, scale = 1f))

    @Test
    fun aNewWidgetGoesOnThePageInViewNotTheFirstPage() {
        val state = lookingAt(board(page("p1", 0f, 0.0), page("p2", 1100f, 1.0)), 1100f)
        assertEquals("p2", CanvasCommands.pageForSpawn(state)?.id)
    }

    @Test
    fun theTappedPageWinsWhileItIsStillOnScreen() {
        val state = board(page("p1", 0f, 0.0), page("p2", 408f, 1.0)).copy(selectedNodeId = "p1")
        assertEquals("p1", CanvasCommands.pageForSpawn(state)?.id)
    }

    @Test
    fun aTappedPageScrolledOffScreenLosesToThePageInView() {
        val state = lookingAt(
            board(page("p1", 0f, 0.0), page("p2", 1100f, 1.0)).copy(selectedNodeId = "p1"),
            1100f
        )
        assertEquals("p2", CanvasCommands.pageForSpawn(state)?.id)
    }

    @Test
    fun thePageBeingTypedOnWinsOverEverything() {
        val text = widget("t1", "p1", 8f, 8f, 0.0, kind = ContentType.TEXT)
        val state = lookingAt(
            board(page("p1", 0f, 0.0), page("p2", 1100f, 1.0), text).copy(editingNodeId = "t1"),
            1100f
        )
        assertEquals("p1", CanvasCommands.pageForSpawn(state)?.id)
    }

    @Test
    fun picturesVideosAndDocumentsAreCompactLandscapeCards() {
        val state = board(page("p1", 0f, 0.0))
        val paper = state.node("p1")
        listOf(ContentType.IMAGE, ContentType.GIF, ContentType.VIDEO, ContentType.PDF, ContentType.DOCX)
            .forEach { kind ->
                val node = CanvasCommands.widgetIn(state, paper, kind, "c")
                assertEquals(CanvasNode.CARD_WIDTH, node.rect.width, "$kind width")
                assertEquals(CanvasNode.CARD_HEIGHT, node.rect.height, "$kind height")
                assertTrue(node.rect.width > node.rect.height, "$kind is a landscape rectangle")
            }
        val audio = CanvasCommands.widgetIn(state, paper, ContentType.AUDIO, "a")
        assertEquals(360f - CanvasNode.PAGE_PADDING * 2f, audio.rect.width)
        assertTrue(audio.isMeasured, "an audio card is as tall as its player, never as tall as a zoom makes it")
    }

    @Test
    fun widgetsSit8ApartAnd8FromThePaper() {
        assertEquals(8f, CanvasNode.PAGE_PADDING)
        assertEquals(8f, CanvasNode.WIDGET_GAP)
        val state = board(page("p1", 0f, 0.0), widget("a", "p1", 8f, 8f, 0.0))
        val next = CanvasCommands.widgetIn(state, state.node("p1"), ContentType.IMAGE, "c")
        assertEquals(8f, next.rect.x)
        assertEquals(8f + 100f + CanvasNode.PAGE_PADDING, next.rect.y)
    }

    // ---- 24-Sep-2026: carrying while typing ----

    @Test
    fun aLongPressWhileTypingCarriesTheWidgetAndPutsTheKeyboardAway() {
        val start = board(
            page("p1", 0f, 0.0),
            widget("t1", "p1", 8f, 8f, 0.0, kind = ContentType.TEXT),
            widget("w1", "p1", 8f, 300f, 1.0)
        ).after(CanvasEditorIntent.SetEditing("t1"))
        assertTrue(CanvasCommands.canLift(start))
        val lifted = start.after(CanvasEditorIntent.BeginDrag("w1"))
        assertNull(lifted.editingNodeId)
        assertEquals("w1", lifted.draggingNodeId)
    }

    @Test
    fun theWidgetBeingTypedInIsNeverPickedUp() {
        val start = board(
            page("p1", 0f, 0.0),
            widget("t1", "p1", 8f, 8f, 0.0, kind = ContentType.TEXT)
        ).after(CanvasEditorIntent.SetEditing("t1"))
        val state = start.after(CanvasEditorIntent.BeginDrag("t1"))
        assertEquals("t1", state.editingNodeId)
        assertNull(state.draggingNodeId)
    }

    // ---- 24-Sep-2026: another device's canvas ----

    @Test
    fun anotherDevicesCanvasMovesTheWidgetsButKeepsThisDevicesMeasuredHeights() {
        val local = board(
            page("p1", 0f, 0.0),
            widget("t1", "p1", 8f, 8f, 0.0, kind = ContentType.TEXT, height = 200f),
            widget("w1", "p1", 8f, 216f, 1.0)
        )
        val remote = listOf(
            page("p1", 0f, 0.0),
            widget("t1", "p1", 8f, 40f, 0.0, kind = ContentType.TEXT, height = 120f),
            widget("w1", "p1", 8f, 168f, 1.0)
        )
        val state = local.after(CanvasCommands.adoptRemote(remote, emptySet()))
        assertEquals(40f, state.node("t1").rect.y, "the position comes from the other device")
        assertEquals(200f, state.node("t1").rect.height, "the height stays this device's own")
        assertTrue(state.node("w1").rect.y >= 240f + CanvasNode.WIDGET_GAP, "and nothing overlaps because of it")
        state.document.assertNothingOverlaps()
    }

    @Test
    fun whatThisDeviceTouchedWhileTheRemoteCanvasWaitedStaysItsOwn() {
        val local = board(
            page("p1", 0f, 0.0),
            widget("kept", "p1", 8f, 500f, 0.0),
            widget("fresh", "p1", 8f, 700f, 1.0)
        )
        val remote = listOf(
            page("p1", 0f, 0.0),
            widget("kept", "p1", 8f, 8f, 0.0),
            widget("gone", "p1", 8f, 200f, 2.0),
            widget("theirs", "p1", 8f, 400f, 3.0)
        )
        val state = local.after(CanvasCommands.adoptRemote(remote, setOf("kept", "fresh", "gone")))
        assertEquals(500f, state.node("kept").rect.y)
        assertNotNull(state.document.nodeById("fresh"), "added here meanwhile")
        assertNull(state.document.nodeById("gone"), "removed here meanwhile")
        assertNotNull(state.document.nodeById("theirs"), "added there")
    }

    @Test
    fun aRemoteCanvasWithoutPaperIsNeverTaken() {
        val local = board(page("p1", 0f, 0.0), widget("w1", "p1", 8f, 8f, 0.0))
        val state = local.after(CanvasCommands.adoptRemote(listOf(widget("w1", "p1", 8f, 99f, 0.0)), emptySet()))
        assertEquals(local.document, state.document)
    }

    @Test
    fun aRemoteCanvasNeverReStampsTheNotesContentOrder() {
        assertTrue(CanvasCommands.isRemoteAdoption(CanvasCommands.adoptRemote(emptyList(), emptySet())))
        assertFalse(CanvasCommands.isRemoteAdoption(CanvasEditorIntent.FocusPage("p1")))
    }

    // ---- 24-Sep-2026: what travels to the other devices ----

    private fun edits(start: CanvasEditorState, vararg intents: CanvasEditorIntent): List<Boolean> {
        var state = start
        var gestureStart: CanvasDocument? = null
        return intents.map { intent ->
            val next = CanvasEditorReducer.reduce(state, intent)
            if (CanvasCommands.gestureStarted(state, next)) gestureStart = next.document
            val edited = CanvasCommands.editsLayout(state, next, intent, gestureStart)
            if (CanvasCommands.gestureEnded(state, next)) gestureStart = null
            state = next
            edited
        }
    }

    @Test
    fun aDropThatMovesAWidgetTravels() {
        val start = board(page("p1", 0f, 0.0), widget("a", "p1", 8f, 8f, 0.0))
        val stamped = edits(
            start,
            CanvasEditorIntent.BeginDrag("a"),
            CanvasEditorIntent.DropWidget("a", "p1", 8f, 300f)
        )
        assertEquals(listOf(false, true), stamped)
    }

    @Test
    fun aLiftLetGoWithoutMovingStampsNothing() {
        val start = board(page("p1", 0f, 0.0), widget("a", "p1", 8f, 8f, 0.0))
        assertEquals(
            listOf(false, false),
            edits(start, CanvasEditorIntent.BeginDrag("a"), CanvasCommands.cancelLift("a"))
        )
        assertEquals(
            listOf(false, false),
            edits(start, CanvasEditorIntent.BeginDrag("p1"), CanvasEditorIntent.EndDrag)
        )
    }

    @Test
    fun aWidgetLetGoWhereItWasPickedUpChangesAndSendsNothing() {
        val start = board(page("p1", 0f, 0.0), widget("a", "p1", 8f, 8f, 0.0), widget("b", "p1", 8f, 200f, 1.0))
        val stamped = edits(
            start,
            CanvasEditorIntent.BeginDrag("a"),
            CanvasEditorIntent.DropWidget("a", "p1", 8.6f, 7.4f)
        )
        assertEquals(listOf(false, false), stamped)
        val state = start.after(CanvasEditorIntent.BeginDrag("a"), CanvasEditorIntent.DropWidget("a", "p1", 8.6f, 7.4f))
        assertEquals(start.document, state.document)
    }

    @Test
    fun aContentTheOtherDevicePlacedNeverGetsASecondWidgetFromHere() {
        val local = board(page("p1", 0f, 0.0), widget("mine", "p1", 8f, 8f, 0.0))
        val remote = listOf(page("p1", 0f, 0.0), widget("theirs", "p1", 8f, 8f, 0.0).copy(contentId = "content-mine"))
        val state = local.after(CanvasCommands.adoptRemote(remote, setOf("mine")))
        assertNull(state.document.nodeById("mine"))
        assertEquals(1, state.document.orderedWidgets.count { it.contentId == "content-mine" })
    }

    @Test
    fun aMovedOrResizedPageTravels() {
        val start = board(page("p1", 0f, 0.0), page("p2", 408f, 1.0))
        assertEquals(
            listOf(false, false, true),
            edits(start, CanvasEditorIntent.BeginDrag("p2"), CanvasEditorIntent.DragBy(0f, 90f), CanvasEditorIntent.EndDrag)
        )
        assertEquals(
            listOf(false, false, true),
            edits(start, CanvasEditorIntent.BeginResize("p1"), CanvasEditorIntent.ResizeNode("p1", 500f, 700f), CanvasEditorIntent.EndResize)
        )
    }

    @Test
    fun fittingMeasuringAndZoomingNeverTravel() {
        val start = board(
            page("p1", 0f, 0.0),
            widget("t1", "p1", 8f, 8f, 0.0, kind = ContentType.TEXT, height = 96f)
        )
        assertEquals(
            listOf(false, false, false, false),
            edits(
                start,
                CanvasEditorIntent.FocusPage("p1"),
                CanvasEditorIntent.MeasureWidget("t1", 300f),
                CanvasEditorIntent.Zoom(2f, 10f, 10f),
                CanvasCommands.revealEnd("p1")
            )
        )
    }

    @Test
    fun addingRemovingAndRenamingTravel() {
        val start = board(page("p1", 0f, 0.0), widget("a", "p1", 8f, 8f, 0.0))
        val fresh = CanvasCommands.pageNode(start)
        assertEquals(
            listOf(true, true, true),
            edits(
                start,
                CanvasEditorIntent.AddNode(fresh),
                CanvasEditorIntent.RenameNode("p1", "Chapter one"),
                CanvasEditorIntent.RemoveNode("a")
            )
        )
    }

    @Test
    fun aNewPageIsBlankPaperBesideTheLast() {
        val start = board(page("p1", 0f, 0.0), widget("a", "p1", 8f, 8f, 0.0))
        val fresh = CanvasCommands.pageNode(start)
        val state = start.after(CanvasEditorIntent.AddNode(fresh))
        assertTrue(state.document.widgetsOf(fresh.id).isEmpty())
        assertTrue(state.node(fresh.id).rect.x >= state.node("p1").rect.right)
    }

    @Test
    fun revealingAPagesEndIsAScrollRequestAndNothingElse() {
        val start = board(page("p1", 0f, 0.0))
        val state = start.after(CanvasCommands.revealEnd("p1"))
        assertEquals(PageScrollRequest("p1", 1), state.scrollRequest)
        assertEquals(start.document, state.document)
        assertEquals(start.viewport, state.viewport)
    }

    // ---- 24-Sep-2026: dragging near an edge scrolls the page ----

    @Test
    fun aFingerNearAnEdgeScrollsThatWayAndTheMiddleStaysStill() {
        assertEquals(0f, CanvasCommands.autoScrollStep(500f, 0f, 1000f))
        assertTrue(CanvasCommands.autoScrollStep(990f, 0f, 1000f) > 0f, "near the bottom it scrolls down")
        assertTrue(CanvasCommands.autoScrollStep(10f, 0f, 1000f) < 0f, "near the top it scrolls up")
        assertTrue(
            CanvasCommands.autoScrollStep(999f, 0f, 1000f) > CanvasCommands.autoScrollStep(960f, 0f, 1000f),
            "deeper into the edge is faster"
        )
        assertEquals(0f, CanvasCommands.autoScrollStep(1200f, 0f, 1000f), "off the page nothing scrolls")
    }

    // ---- 24-Sep-2026: the one-time layout upgrade ----

    @Test
    fun anOldPixelCanvasBecomesDpAndIsRestackedWithCompactCards() {
        val density = 2.75f
        val old = listOf(
            page("p1", 0f, 0.0).copy(rect = CanvasRect(0f, 0f, 990f, 1760f)),
            widget("pic", "p1", 66f, 66f, 0.0).copy(rect = CanvasRect(66f, 66f, 858f, 643.5f)),
            widget("doc", "p1", 66f, 742f, 1.0, kind = ContentType.PDF).copy(rect = CanvasRect(66f, 742f, 858f, 880f))
        )
        val upgraded = CanvasDocument(CanvasCommands.upgradedLayout(old, 1f / density, 360f))
        assertEquals(360f, upgraded.nodeById("p1")!!.rect.width, 0.01f)
        val pic = upgraded.nodeById("pic")!!.rect
        val doc = upgraded.nodeById("doc")!!.rect
        assertEquals(CanvasRect(8f, 8f, CanvasNode.CARD_WIDTH, CanvasNode.CARD_HEIGHT), pic)
        assertEquals(8f + CanvasNode.CARD_HEIGHT + CanvasNode.WIDGET_GAP, doc.y)
        assertEquals(CanvasNode.CARD_WIDTH, doc.width)
        upgraded.assertNothingOverlaps()
    }

    @Test
    fun theUpgradeKeepsTheReadingOrder() {
        val old = listOf(
            page("p1", 0f, 0.0),
            widget("b", "p1", 24f, 400f, 1.0),
            widget("a", "p1", 24f, 24f, 0.0)
        )
        val upgraded = CanvasDocument(CanvasCommands.upgradedLayout(old, 1f, 0f))
        assertEquals(listOf("a", "b"), upgraded.widgetsInReadingOrder("p1").map { it.id })
        assertEquals(listOf("content-a", "content-b"), upgraded.orderedWidgets.mapNotNull { it.contentId })
    }

    @Test
    fun thePageMostInViewIsTheOneCoveringMostOfIt() {
        val document = CanvasDocument(listOf(page("p1", 0f, 0.0), page("p2", 408f, 1.0)))
        assertEquals("p2", document.mostVisiblePage(CanvasRect(300f, 0f, 400f, 600f))?.id)
        assertNull(document.mostVisiblePage(CanvasRect(5000f, 0f, 100f, 100f)))
    }
}
