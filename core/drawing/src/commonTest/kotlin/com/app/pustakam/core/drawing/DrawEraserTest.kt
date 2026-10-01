package com.app.pustakam.core.drawing

import com.app.pustakam.core.drawing.codec.DrawCodec
import com.app.pustakam.core.drawing.editor.DrawCommands
import com.app.pustakam.core.drawing.editor.DrawReducer
import com.app.pustakam.core.drawing.geometry.DrawMatrix
import com.app.pustakam.core.drawing.model.DrawElementKind
import com.app.pustakam.core.drawing.model.DrawShapeKind
import com.app.pustakam.core.drawing.ops.DrawOp
import com.app.pustakam.core.drawing.render.DrawRenderEntry
import com.app.pustakam.core.drawing.render.DrawRenderer
import com.app.pustakam.core.drawing.tool.DrawEraserKind
import com.app.pustakam.core.drawing.tool.DrawToolMode
import com.app.pustakam.core.drawing.tool.DrawToolSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DrawEraserTest {

    @Test
    fun portionIsTheDefaultAndBasicModeOffersPortionAndWhole() {
        val state = DrawTestKit.page()
        assertEquals(DrawEraserKind.PARTIAL, DrawToolSettings.defaults().eraserKind)
        assertEquals(listOf(DrawEraserKind.PARTIAL, DrawEraserKind.OBJECT), DrawCommands.eraserKinds(state))
        val advanced = DrawTestKit.reduce(state, DrawCommands.setMode(DrawToolMode.ADVANCED))
        assertEquals(4, DrawCommands.eraserKinds(advanced).size)
        assertEquals("Portion", DrawCommands.eraserLabel(DrawEraserKind.PARTIAL))
        assertEquals("Whole object", DrawCommands.eraserLabel(DrawEraserKind.OBJECT))
    }

    @Test
    fun basicModeNeverKeepsAnAdvancedOnlyEraser() {
        var state = DrawTestKit.reduce(
            DrawTestKit.page(),
            DrawCommands.setMode(DrawToolMode.ADVANCED),
            DrawCommands.setEraserKind(DrawEraserKind.PIXEL)
        )
        state = DrawTestKit.reduce(state, DrawCommands.setMode(DrawToolMode.BASIC))
        assertEquals(DrawEraserKind.PARTIAL, DrawCommands.currentEraserKind(state))
    }

    @Test
    fun legacyToolboxesMoveToThePortionEraser() {
        val state = DrawTestKit.page()
        val chosen = DrawCodec.encodeToolbox(DrawToolSettings.defaults().withEraserKind(DrawEraserKind.OBJECT), state.colors)
        val kept = DrawTestKit.reduce(state, DrawCommands.restoreToolbox(state, chosen))
        assertEquals(DrawEraserKind.OBJECT, DrawCommands.currentEraserKind(kept))
        val legacy = DrawTestKit.reduce(state, DrawCommands.restoreToolbox(state, "{\"v\":1,\"eraserKind\":\"STROKE\"}"))
        assertEquals(DrawEraserKind.PARTIAL, DrawCommands.currentEraserKind(legacy))
    }

    @Test
    fun portionEraserCutsOnlyTheTouchedPartOfAStroke() {
        var state = DrawTestKit.horizontal(DrawTestKit.page(), 200f)
        state = DrawTestKit.reduce(state, DrawCommands.setEraserKind(DrawEraserKind.PARTIAL))
        val original = state.document.elements.single()
        val erased = DrawTestKit.stroke(state, DrawTestKit.line(DrawTestKit.point(150f, 170f), DrawTestKit.point(150f, 230f), 10))
        val carved = erased.document.elements.single()
        assertEquals(original.id, carved.id)
        assertEquals(original.points, carved.points)
        assertEquals(1, carved.cuts.size)
        assertTrue(erased.lastOp is DrawOp.Replace)
        val middle = state.viewport.toDocument(150f, 200f).x
        assertTrue(carved.cuts.single().points.all { kotlin.math.abs(it.x - middle) < 1f })
        val restored = DrawReducer.reduce(erased, DrawCommands.undo())
        assertTrue(restored.document.elements.single().cuts.isEmpty())
    }

    @Test
    fun portionEraserCutsShapesToo() {
        var state = DrawTestKit.reduce(DrawTestKit.page(), DrawCommands.setShapeKind(DrawShapeKind.RECTANGLE))
        state = DrawTestKit.stroke(state, listOf(DrawTestKit.point(50f, 50f), DrawTestKit.point(200f, 200f)))
        state = DrawTestKit.reduce(state, DrawCommands.setEraserKind(DrawEraserKind.PARTIAL))
        val erased = DrawTestKit.stroke(state, DrawTestKit.line(DrawTestKit.point(40f, 120f), DrawTestKit.point(60f, 120f), 6))
        val shape = erased.document.elements.single()
        assertEquals(DrawElementKind.SHAPE, shape.kind)
        assertTrue(shape.isCut)
    }

    @Test
    fun portionEraserRemovesAnObjectItErasesCompletely() {
        var state = DrawTestKit.stroke(
            DrawTestKit.page(),
            DrawTestKit.line(DrawTestKit.point(100f, 100f), DrawTestKit.point(104f, 100f), 4)
        )
        state = DrawTestKit.reduce(
            state,
            DrawCommands.setEraserKind(DrawEraserKind.PARTIAL),
            DrawCommands.setEraserSize(DrawCommands.maxEraserSize())
        )
        val erased = DrawTestKit.stroke(state, DrawTestKit.line(DrawTestKit.point(102f, 80f), DrawTestKit.point(102f, 120f), 8))
        assertTrue(erased.document.elements.isEmpty())
        val op = erased.lastOp as DrawOp.Replace
        assertTrue(op.after.isEmpty())
        assertEquals(1, DrawReducer.reduce(erased, DrawCommands.undo()).document.elements.size)
    }

    @Test
    fun wholeEraserRemovesStrokesAndShapes() {
        var state = DrawTestKit.reduce(DrawTestKit.page(), DrawCommands.setShapeKind(DrawShapeKind.RECTANGLE))
        state = DrawTestKit.stroke(state, listOf(DrawTestKit.point(50f, 50f), DrawTestKit.point(200f, 200f)))
        state = DrawTestKit.reduce(state, DrawCommands.setTool(DrawCommands.penTool()))
        state = DrawTestKit.horizontal(state, 400f)
        state = DrawTestKit.reduce(state, DrawCommands.setEraserKind(DrawEraserKind.OBJECT))
        state = DrawTestKit.stroke(state, DrawTestKit.line(DrawTestKit.point(40f, 120f), DrawTestKit.point(60f, 120f), 6))
        assertEquals(listOf(DrawElementKind.STROKE), state.document.elements.map { it.kind })
        state = DrawTestKit.stroke(state, DrawTestKit.line(DrawTestKit.point(150f, 380f), DrawTestKit.point(150f, 420f), 6))
        assertTrue(state.document.elements.isEmpty())
    }

    @Test
    fun wholeEraserIgnoresInkThatIsAlreadyCutAway() {
        var state = DrawTestKit.horizontal(DrawTestKit.page(), 200f)
        state = DrawTestKit.reduce(state, DrawCommands.setEraserKind(DrawEraserKind.PARTIAL))
        state = DrawTestKit.stroke(state, DrawTestKit.line(DrawTestKit.point(150f, 170f), DrawTestKit.point(150f, 230f), 10))
        state = DrawTestKit.reduce(
            state,
            DrawCommands.setEraserKind(DrawEraserKind.OBJECT),
            DrawCommands.setEraserSize(DrawToolSettings.MIN_ERASER)
        )
        val missed = DrawTestKit.stroke(state, DrawTestKit.line(DrawTestKit.point(150f, 195f), DrawTestKit.point(150f, 205f), 4))
        assertEquals(1, missed.document.elements.size)
        val hit = DrawTestKit.stroke(missed, DrawTestKit.line(DrawTestKit.point(90f, 190f), DrawTestKit.point(90f, 210f), 4))
        assertTrue(hit.document.elements.isEmpty())
    }

    @Test
    fun cutsSurviveTheCodec() {
        var state = DrawTestKit.horizontal(DrawTestKit.page(), 200f)
        state = DrawTestKit.reduce(state, DrawCommands.setEraserKind(DrawEraserKind.PARTIAL))
        state = DrawTestKit.stroke(state, DrawTestKit.line(DrawTestKit.point(150f, 170f), DrawTestKit.point(150f, 230f), 10))
        val decoded = DrawCodec.decode(DrawCodec.encode(state.document), DrawTestKit.page().document)
        val before = state.document.elements.single().cuts.single()
        val after = decoded.elements.single().cuts.single()
        assertEquals(before.radius, after.radius, 0.01f)
        assertEquals(before.points.size, after.points.size)
    }

    @Test
    fun aCutElementRendersAsAnIsolatedGroup() {
        var state = DrawTestKit.horizontal(DrawTestKit.page(), 200f)
        state = DrawTestKit.reduce(state, DrawCommands.setEraserKind(DrawEraserKind.PARTIAL))
        state = DrawTestKit.stroke(state, DrawTestKit.line(DrawTestKit.point(150f, 170f), DrawTestKit.point(150f, 230f), 10))
        val items = DrawRenderer.itemsFor(state.document.elements.single(), complete = true)
        assertTrue(items.first().isGroupBegin)
        assertTrue(items.last().isGroupEnd)
        assertTrue(items.any { it.isClear })
        val entries = items.map { DrawRenderEntry(it, DrawMatrix.IDENTITY) }
        assertFalse(DrawCommands.clears(entries))
    }

    @Test
    fun pixelMarksStillNeedTheCanvasIsolated() {
        var state = DrawTestKit.horizontal(DrawTestKit.page(), 200f)
        state = DrawTestKit.reduce(state, DrawCommands.setEraserKind(DrawEraserKind.PIXEL))
        state = DrawTestKit.stroke(state, DrawTestKit.line(DrawTestKit.point(150f, 170f), DrawTestKit.point(150f, 230f), 10))
        val entries = state.document.elements.flatMap { element ->
            DrawRenderer.itemsFor(element, complete = true).map { DrawRenderEntry(it, DrawMatrix.IDENTITY) }
        }
        assertTrue(DrawCommands.clears(entries))
    }

    @Test
    fun groupBoundsFollowTheEntryMatrix() {
        var state = DrawTestKit.horizontal(DrawTestKit.page(), 200f)
        state = DrawTestKit.reduce(state, DrawCommands.setEraserKind(DrawEraserKind.PARTIAL))
        state = DrawTestKit.stroke(state, DrawTestKit.line(DrawTestKit.point(150f, 170f), DrawTestKit.point(150f, 230f), 10))
        val begin = DrawRenderer.itemsFor(state.document.elements.single(), complete = true).first()
        val moved = DrawCommands.groupBounds(DrawRenderEntry(begin, DrawMatrix(2f, 0f, 0f, 2f, 10f, 20f)))
        assertEquals(begin.bounds.x * 2f + 10f, moved.x, 0.01f)
        assertEquals(begin.bounds.width * 2f, moved.width, 0.01f)
    }
}
