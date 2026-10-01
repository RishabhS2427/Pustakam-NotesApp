package com.app.pustakam.core.drawing

import com.app.pustakam.core.drawing.anchor.DrawAnchoring
import com.app.pustakam.core.drawing.editor.DrawCommands
import com.app.pustakam.core.drawing.editor.DrawReducer
import com.app.pustakam.core.drawing.model.DrawBrushKind
import com.app.pustakam.core.drawing.model.DrawColor
import com.app.pustakam.core.drawing.model.DrawElementKind
import com.app.pustakam.core.drawing.model.DrawPattern
import com.app.pustakam.core.drawing.model.DrawPointerType
import com.app.pustakam.core.drawing.model.DrawShapeKind
import com.app.pustakam.core.drawing.ops.DrawOp
import com.app.pustakam.core.drawing.tool.DrawEraserKind
import com.app.pustakam.core.drawing.tool.DrawToolId
import com.app.pustakam.core.drawing.tool.DrawToolMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DrawReducerTest {

    @Test
    fun aPenStrokeLandsAsOneUndoableElement() {
        val before = DrawTestKit.page()
        val after = DrawTestKit.horizontal(before, 200f)
        val element = after.document.elements.single()
        assertEquals(DrawElementKind.STROKE, element.kind)
        assertEquals(DrawTestKit.AUTHOR, element.author)
        assertTrue(after.canUndo)
        assertNull(after.gesture)
        assertTrue(after.preview.isEmpty)
        assertTrue(DrawCommands.documentChanged(before, after))
        assertTrue(DrawCommands.producedOp(before, after) is DrawOp.Add)
        assertEquals(after.colors.current, after.colors.recent.first())
    }

    @Test
    fun strokePointsAreStoredInDocumentUnits() {
        val state = DrawTestKit.page()
        val drawn = DrawTestKit.horizontal(state, 200f)
        val first = drawn.document.elements.single().points.first()
        val expected = state.viewport.toDocument(50f, 200f)
        assertEquals(expected.x, first.x, 0.01f)
        assertEquals(expected.y, first.y, 0.01f)
        assertEquals(state.brush.size / state.viewport.scale, drawn.document.elements.single().brush.size, 0.001f)
    }

    @Test
    fun previewFollowsTheFingerBeforeCommit() {
        val state = DrawTestKit.page()
        val down = DrawReducer.reduce(state, DrawCommands.pointerDown(DrawTestKit.point(10f, 10f), DrawPointerType.STYLUS, emptyList()))
        val moved = DrawReducer.reduce(down, DrawCommands.pointerMove(listOf(DrawTestKit.point(20f, 20f, 16L), DrawTestKit.point(30f, 30f, 32L))))
        assertEquals(1, moved.preview.elements.size)
        assertTrue(moved.document.isEmpty)
        assertTrue(DrawCommands.isDrawing(moved))
        val cancelled = DrawReducer.reduce(moved, DrawCommands.pointerCancel())
        assertTrue(cancelled.document.isEmpty)
        assertTrue(cancelled.preview.isEmpty)
    }

    @Test
    fun undoRemovesAndRedoRestores() {
        val drawn = DrawTestKit.horizontal(DrawTestKit.horizontal(DrawTestKit.page(), 100f), 200f)
        val undone = DrawReducer.reduce(drawn, DrawCommands.undo())
        assertEquals(1, undone.document.elements.size)
        assertTrue(undone.canRedo)
        val redone = DrawReducer.reduce(undone, DrawCommands.redo())
        assertEquals(drawn.document.elements.map { it.id }.toSet(), redone.document.elements.map { it.id }.toSet())
        assertFalse(redone.canRedo)
        assertTrue(DrawCommands.producedOp(undone, redone) != null)
    }

    @Test
    fun remoteOpsNeverEnterMyHistory() {
        val mine = DrawTestKit.horizontal(DrawTestKit.page(), 100f)
        val theirs = DrawTestKit.horizontal(DrawTestKit.page(), 300f).lastOp!!.restamped("author-b", 50L)
        val merged = DrawReducer.reduce(mine, DrawCommands.applyRemote(theirs))
        assertEquals(2, merged.document.elements.size)
        assertNull(DrawCommands.producedOp(mine, merged))
        assertTrue(DrawCommands.documentChanged(mine, merged))
        val undone = DrawReducer.reduce(merged, DrawCommands.undo())
        assertEquals(1, undone.document.elements.size)
        assertEquals((theirs as DrawOp.Add).elements.single().id, undone.document.elements.single().id)
        assertTrue(undone.document.clock > 50L)
    }

    @Test
    fun strokeEraserRemovesWholeStrokesItTouches() {
        var state = DrawTestKit.horizontal(DrawTestKit.horizontal(DrawTestKit.page(), 100f), 300f)
        state = DrawTestKit.reduce(state, DrawCommands.setEraserKind(DrawEraserKind.STROKE))
        val erased = DrawTestKit.stroke(state, DrawTestKit.line(DrawTestKit.point(150f, 60f), DrawTestKit.point(150f, 140f), 10))
        assertEquals(1, erased.document.elements.size)
        assertTrue(erased.lastOp is DrawOp.Remove)
        val undone = DrawReducer.reduce(erased, DrawCommands.undo())
        assertEquals(2, undone.document.elements.size)
    }

    @Test
    fun strokeEraserHidesTouchedStrokesWhileErasing() {
        var state = DrawTestKit.horizontal(DrawTestKit.page(), 100f)
        state = DrawTestKit.reduce(state, DrawCommands.setEraserKind(DrawEraserKind.STROKE))
        val down = DrawReducer.reduce(state, DrawCommands.pointerDown(DrawTestKit.point(150f, 100f), DrawPointerType.FINGER, emptyList()))
        assertEquals(setOf(state.document.elements.single().id), down.preview.hiddenIds)
    }

    @Test
    fun pixelEraserAddsAClearMarkAndObjectEraserTakesShapes() {
        var state = DrawTestKit.reduce(DrawTestKit.page(), DrawCommands.setShapeKind(DrawShapeKind.RECTANGLE))
        state = DrawTestKit.stroke(state, listOf(DrawTestKit.point(50f, 50f), DrawTestKit.point(200f, 200f)))
        state = DrawTestKit.reduce(state, DrawCommands.setEraserKind(DrawEraserKind.PIXEL))
        state = DrawTestKit.stroke(state, DrawTestKit.line(DrawTestKit.point(60f, 300f), DrawTestKit.point(120f, 300f), 5))
        assertEquals(listOf(DrawElementKind.SHAPE, DrawElementKind.ERASE), state.document.ordered.map { it.kind })
        state = DrawTestKit.reduce(state, DrawCommands.setEraserKind(DrawEraserKind.STROKE))
        state = DrawTestKit.stroke(state, DrawTestKit.line(DrawTestKit.point(50f, 120f), DrawTestKit.point(50f, 130f), 3))
        assertEquals(2, state.document.elements.size)
        state = DrawTestKit.reduce(state, DrawCommands.setEraserKind(DrawEraserKind.OBJECT))
        state = DrawTestKit.stroke(state, DrawTestKit.line(DrawTestKit.point(50f, 120f), DrawTestKit.point(50f, 130f), 3))
        assertEquals(listOf(DrawElementKind.ERASE), state.document.elements.map { it.kind })
    }

    @Test
    fun shapesNeedARealDrag() {
        var state = DrawTestKit.reduce(DrawTestKit.page(), DrawCommands.setShapeKind(DrawShapeKind.ELLIPSE))
        assertEquals(DrawToolId.SHAPE, state.tool)
        val tap = DrawTestKit.stroke(state, listOf(DrawTestKit.point(50f, 50f), DrawTestKit.point(51f, 50f)))
        assertTrue(tap.document.isEmpty)
        state = DrawTestKit.stroke(state, DrawTestKit.line(DrawTestKit.point(50f, 50f), DrawTestKit.point(150f, 120f), 20))
        val shape = state.document.elements.single()
        assertEquals(DrawShapeKind.ELLIPSE, shape.shape.kind)
        assertEquals(2, shape.points.size)
    }

    @Test
    fun readOnlyIgnoresEveryEdit() {
        val drawn = DrawTestKit.horizontal(DrawTestKit.page(), 100f)
        val locked = DrawReducer.reduce(drawn, DrawCommands.setReadOnly(true))
        val tried = DrawTestKit.horizontal(locked, 200f)
        assertEquals(1, tried.document.elements.size)
        assertEquals(tried.document, DrawReducer.reduce(tried, DrawCommands.undo()).document)
        assertFalse(DrawCommands.capturesTouches(locked))
    }

    @Test
    fun handToolPansAPageButAnOverlayLetsTouchesThrough() {
        val page = DrawTestKit.reduce(DrawTestKit.page(), DrawCommands.setTool(DrawToolId.HAND))
        val panned = DrawTestKit.stroke(page, listOf(DrawTestKit.point(100f, 100f), DrawTestKit.point(130f, 80f)))
        assertEquals(page.viewport.base.offsetX + 30f, panned.viewport.base.offsetX, 0.01f)
        assertTrue(panned.document.isEmpty)
        val overlay = DrawTestKit.reduce(DrawTestKit.overlay(), DrawCommands.setTool(DrawToolId.HAND))
        assertFalse(DrawCommands.capturesTouches(overlay))
        assertTrue(DrawCommands.capturesTouches(DrawTestKit.overlay()))
    }

    @Test
    fun stylusOnlyTurnsFingersIntoPanning() {
        val state = DrawTestKit.reduce(DrawTestKit.page(), DrawCommands.setStylusOnly(true))
        val finger = DrawTestKit.stroke(state, listOf(DrawTestKit.point(100f, 100f), DrawTestKit.point(100f, 150f)), DrawPointerType.FINGER)
        assertTrue(finger.document.isEmpty)
        val pen = DrawTestKit.stroke(state, listOf(DrawTestKit.point(100f, 100f), DrawTestKit.point(100f, 150f)), DrawPointerType.STYLUS)
        assertEquals(1, pen.document.elements.size)
    }

    @Test
    fun twoFingerNavigationMovesThePageAndCancelsTheStroke() {
        val state = DrawTestKit.page()
        val down = DrawReducer.reduce(state, DrawCommands.pointerDown(DrawTestKit.point(10f, 10f), DrawPointerType.FINGER, emptyList()))
        val navigated = DrawReducer.reduce(down, DrawCommands.navigate(10f, 0f, 2f, 0f, 200f, 300f))
        assertNull(navigated.gesture)
        assertEquals(state.viewport.scale * 2f, navigated.viewport.scale, 0.001f)
        assertTrue(navigated.navigating)
        val ignored = DrawTestKit.horizontal(navigated, 100f)
        assertTrue(ignored.document.isEmpty)
        val ended = DrawReducer.reduce(navigated, DrawCommands.navigateEnd())
        assertFalse(ended.navigating)
        assertFalse(DrawReducer.reduce(navigated, DrawCommands.pointerCancel()).navigating)
        val overlay = DrawTestKit.overlay()
        assertEquals(overlay.viewport, DrawReducer.reduce(overlay, DrawCommands.navigate(10f, 10f, 2f, 1f, 0f, 0f)).viewport)
    }

    @Test
    fun overlayStrokesAttachToTheAnchorUnderThem() {
        val anchors = listOf(
            DrawAnchoring.frame("block-1", 0f, 0f, 400f, 150f),
            DrawAnchoring.frame("block-2", 0f, 150f, 400f, 300f)
        )
        val drawn = DrawTestKit.stroke(
            DrawTestKit.overlay(),
            DrawTestKit.line(DrawTestKit.point(40f, 200f), DrawTestKit.point(140f, 220f), 10),
            DrawPointerType.STYLUS,
            anchors
        )
        val element = drawn.document.elements.single()
        assertEquals("block-2", element.anchorId)
        assertEquals(40f * 1000f / 400f, element.points.first().x, 0.01f)
        assertEquals(50f * 1000f / 400f, element.points.first().y, 0.01f)
        val scrolled = listOf(DrawAnchoring.frame("block-2", 0f, -50f, 400f, 300f))
        val entries = DrawCommands.frame(drawn, scrolled, DrawCommands.renderCache())
        assertEquals(1, entries.size)
        assertEquals(-50f, entries.single().matrix.ty, 0.001f)
        assertTrue(DrawCommands.frame(drawn, emptyList(), DrawCommands.renderCache()).isEmpty())
    }

    @Test
    fun anOverlayWithoutAnchorsDrawsInScreenSpace() {
        val drawn = DrawTestKit.horizontal(DrawTestKit.overlay(), 100f)
        val element = drawn.document.elements.single()
        assertNull(element.anchorId)
        assertEquals(50f, element.points.first().x, 0.01f)
    }

    @Test
    fun pickingAColorFromTheEraserReturnsToThePen() {
        val state = DrawTestKit.reduce(DrawTestKit.page(), DrawCommands.setEraserKind(DrawEraserKind.STROKE))
        val red = DrawColor.fromRgb(255, 0, 0)
        val next = DrawTestKit.reduce(state, DrawCommands.setColor(red))
        assertEquals(DrawToolId.PEN, next.tool)
        assertEquals(red, DrawCommands.currentColor(next))
        val drawn = DrawTestKit.horizontal(next, 100f)
        assertEquals(red, drawn.document.elements.single().color)
    }

    @Test
    fun brushSettingsApplyToTheNextStrokeOnly() {
        val state = DrawTestKit.horizontal(DrawTestKit.page(), 100f)
        val bigger = DrawTestKit.reduce(state, DrawCommands.setBrushKind(DrawBrushKind.MARKER))
        val sized = DrawTestKit.reduce(bigger, DrawCommands.setBrushSize(bigger, 40f))
        val drawn = DrawTestKit.horizontal(sized, 300f)
        val (first, second) = drawn.document.ordered
        assertEquals(DrawBrushKind.BALLPOINT, first.brush.kind)
        assertEquals(DrawBrushKind.MARKER, second.brush.kind)
        assertEquals(40f / sized.viewport.scale, second.brush.size, 0.001f)
    }

    @Test
    fun advancedModeKeepsBasicToolsAndBasicDropsAdvancedOnes() {
        val advanced = DrawTestKit.reduce(DrawTestKit.page(), DrawCommands.setMode(DrawToolMode.ADVANCED))
        assertTrue(DrawCommands.isAdvanced(advanced))
        assertTrue(DrawCommands.tools(advanced).size >= DrawCommands.tools(DrawTestKit.page()).size)
        assertTrue(DrawCommands.patterns(advanced).contains(DrawPattern.ISOMETRIC))
        assertFalse(DrawCommands.patterns(DrawTestKit.page()).contains(DrawPattern.ISOMETRIC))
    }

    @Test
    fun paperChangesAreUndoableOps() {
        val state = DrawTestKit.page()
        val gridded = DrawTestKit.reduce(state, DrawCommands.setPattern(state, DrawPattern.GRID))
        assertEquals(DrawPattern.GRID, gridded.document.paper.pattern)
        assertTrue(gridded.lastOp is DrawOp.Paper)
        val undone = DrawReducer.reduce(gridded, DrawCommands.undo())
        assertEquals(DrawPattern.NONE, undone.document.paper.pattern)
        assertTrue(DrawCommands.paperFrame(state).isNotEmpty())
        assertTrue(DrawCommands.paperFrame(DrawTestKit.overlay()).isEmpty())
    }

    @Test
    fun clearIsOneUndoableStep() {
        val drawn = DrawTestKit.horizontal(DrawTestKit.horizontal(DrawTestKit.page(), 100f), 200f)
        val cleared = DrawReducer.reduce(drawn, DrawCommands.clear())
        assertTrue(cleared.document.isEmpty)
        assertEquals(2, DrawReducer.reduce(cleared, DrawCommands.undo()).document.elements.size)
    }

    @Test
    fun firstMeasureFitsThePaper() {
        val state = DrawTestKit.page(400f, 600f)
        val center = state.viewport.toView(200f, 300f)
        assertEquals(200f, center.x, 0.01f)
        assertEquals(300f, center.y, 0.01f)
        val fitted = DrawReducer.reduce(DrawReducer.reduce(state, DrawCommands.zoomIn()), DrawCommands.fitPaper())
        assertEquals(state.viewport.scale, fitted.viewport.scale, 0.0001f)
    }

    @Test
    fun frameShowsPreviewAndSkipsHiddenOrOffscreenElements() {
        val drawn = DrawTestKit.horizontal(DrawTestKit.page(), 100f)
        val cache = DrawCommands.renderCache()
        assertEquals(1, DrawCommands.frame(drawn, emptyList(), cache).size)
        assertEquals(1, cache.size)
        val away = DrawReducer.reduce(drawn, DrawCommands.navigate(-5000f, 0f, 1f, 0f, 0f, 0f))
        assertTrue(DrawCommands.frame(away, emptyList(), cache).isEmpty())
        val erasing = DrawReducer.reduce(
            DrawTestKit.reduce(drawn, DrawCommands.setEraserKind(DrawEraserKind.STROKE)),
            DrawCommands.pointerDown(DrawTestKit.point(150f, 100f), DrawPointerType.STYLUS, emptyList())
        )
        assertTrue(DrawCommands.frame(erasing, emptyList(), cache).isEmpty())
        val drawing = DrawReducer.reduce(drawn, DrawCommands.pointerDown(DrawTestKit.point(30f, 300f), DrawPointerType.STYLUS, emptyList()))
        assertEquals(2, DrawCommands.frame(drawing, emptyList(), cache).size)
    }

    @Test
    fun loadingAnotherDocumentResetsHistory() {
        val drawn = DrawTestKit.horizontal(DrawTestKit.page(), 100f)
        val loaded = DrawReducer.reduce(drawn, DrawCommands.load(DrawTestKit.page().document))
        assertTrue(loaded.document.isEmpty)
        assertFalse(loaded.canUndo)
        assertNotNull(loaded.viewport)
    }
}
