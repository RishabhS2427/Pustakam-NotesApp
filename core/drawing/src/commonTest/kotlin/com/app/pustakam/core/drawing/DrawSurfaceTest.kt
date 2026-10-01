package com.app.pustakam.core.drawing

import com.app.pustakam.core.drawing.editor.DrawBrushControl
import com.app.pustakam.core.drawing.editor.DrawCommands
import com.app.pustakam.core.drawing.editor.DrawEditorState
import com.app.pustakam.core.drawing.editor.DrawPanel
import com.app.pustakam.core.drawing.editor.DrawReducer
import com.app.pustakam.core.drawing.editor.DrawShapeControl
import com.app.pustakam.core.drawing.model.DrawBrush
import com.app.pustakam.core.drawing.model.DrawBrushKind
import com.app.pustakam.core.drawing.model.DrawColor
import com.app.pustakam.core.drawing.model.DrawPaperSize
import com.app.pustakam.core.drawing.model.DrawPattern
import com.app.pustakam.core.drawing.model.DrawPointerType
import com.app.pustakam.core.drawing.model.DrawShapeKind
import com.app.pustakam.core.drawing.model.DrawTexture
import com.app.pustakam.core.drawing.model.DrawUnit
import com.app.pustakam.core.drawing.tool.DrawToolId
import com.app.pustakam.core.drawing.tool.DrawToolMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DrawSurfaceTest {

    private fun widget(width: Float, height: Float): DrawEditorState =
        DrawTestKit.reduce(DrawCommands.widgetState(DrawTestKit.AUTHOR, 120f, 80f, readOnly = false), DrawCommands.resize(width, height))

    @Test
    fun aWidgetDrawsAtScaleOneAndItsPaperFollowsTheFrame() {
        val framed = widget(300f, 200f)
        assertEquals(1f, framed.viewport.scale, 0.0001f)
        assertEquals(300f, framed.document.paper.width, 0.001f)
        assertEquals(200f, framed.document.paper.height, 0.001f)
        val drawn = DrawTestKit.horizontal(framed, 100f)
        val grown = DrawReducer.reduce(drawn, DrawCommands.resize(500f, 260f))
        assertEquals(1f, grown.viewport.scale, 0.0001f)
        assertEquals(500f, grown.document.paper.width, 0.001f)
        assertEquals(260f, grown.document.paper.height, 0.001f)
        assertFalse(DrawCommands.documentChanged(drawn, grown))
        assertEquals(50f, grown.document.elements.single().points.first().x, 0.01f)
    }

    @Test
    fun aWidgetNeverNavigatesAndLetsHandTouchesThrough() {
        val framed = widget(300f, 200f)
        assertFalse(DrawCommands.navigates(framed))
        val tried = DrawTestKit.reduce(framed, DrawCommands.navigate(40f, 10f, 2f, 0.5f, 10f, 10f), DrawCommands.zoomIn())
        assertEquals(framed.viewport, tried.viewport)
        assertFalse(DrawCommands.capturesTouches(DrawTestKit.reduce(framed, DrawCommands.setTool(DrawToolId.HAND))))
        assertTrue(DrawCommands.capturesTouches(framed))
    }

    @Test
    fun undoingAWidgetPaperChangeKeepsTheFrame() {
        val framed = widget(300f, 200f)
        val gridded = DrawTestKit.reduce(framed, DrawCommands.setPattern(framed, DrawPattern.GRID))
        val grown = DrawReducer.reduce(gridded, DrawCommands.resize(420f, 300f))
        val undone = DrawReducer.reduce(grown, DrawCommands.undo())
        assertEquals(DrawPattern.NONE, undone.document.paper.pattern)
        assertEquals(420f, undone.document.paper.width, 0.001f)
        assertEquals(300f, undone.document.paper.height, 0.001f)
    }

    @Test
    fun stylusOnlyLetsFingersThroughOverlaysButPansPages() {
        val overlay = DrawTestKit.reduce(DrawTestKit.overlay(), DrawCommands.setStylusOnly(true))
        assertFalse(DrawCommands.capturesPointer(overlay, DrawPointerType.FINGER))
        assertTrue(DrawCommands.capturesPointer(overlay, DrawPointerType.STYLUS))
        val page = DrawTestKit.reduce(DrawTestKit.page(), DrawCommands.setStylusOnly(true))
        assertTrue(DrawCommands.capturesPointer(page, DrawPointerType.FINGER))
        assertTrue(DrawCommands.capturesPointer(DrawTestKit.overlay(), DrawPointerType.FINGER))
    }

    @Test
    fun aPageFitsFinitePaperEdgeToEdge() {
        val page = DrawTestKit.page(400f, 600f)
        assertEquals(1f, page.viewport.scale, 0.0001f)
        val a4 = DrawTestKit.reduce(page, DrawCommands.setPaperSize(page, DrawPaperSize.A4))
        assertEquals(minOf(400f / 595.276f, 600f / 841.890f), a4.viewport.scale, 0.0001f)
        assertEquals(841.890f / 595.276f, DrawCommands.paperAspect(a4), 0.0001f)
        assertEquals(1.414f, DrawCommands.paperAspect(DrawTestKit.overlay()), 0.0001f)
    }

    @Test
    fun aPatternChangeKeepsThePageZoom() {
        val zoomed = DrawTestKit.reduce(DrawTestKit.page(), DrawCommands.zoomIn())
        val gridded = DrawTestKit.reduce(zoomed, DrawCommands.setPattern(zoomed, DrawPattern.GRID))
        assertEquals(zoomed.viewport, gridded.viewport)
    }

    @Test
    fun brushControlsFollowTheMode() {
        val basic = DrawTestKit.page()
        assertEquals(listOf(DrawBrushControl.SIZE, DrawBrushControl.OPACITY), DrawCommands.brushControls(basic))
        val advanced = DrawTestKit.reduce(basic, DrawCommands.setMode(DrawToolMode.ADVANCED))
        assertEquals(DrawBrushControl.entries, DrawCommands.brushControls(advanced))
        val biggest = DrawTestKit.reduce(basic, DrawCommands.setBrushControl(basic, DrawBrushControl.SIZE, 1f))
        assertEquals(DrawBrush.MAX_SIZE, biggest.brush.size, 0.001f)
        val half = DrawTestKit.reduce(basic, DrawCommands.setBrushControl(basic, DrawBrushControl.SIZE, 0.5f))
        assertEquals(0.5f, DrawCommands.brushControlPosition(half, DrawBrushControl.SIZE), 0.001f)
        val faint = DrawTestKit.reduce(basic, DrawCommands.setBrushControl(basic, DrawBrushControl.OPACITY, 0.25f))
        assertEquals("25%", DrawCommands.brushControlText(faint, DrawBrushControl.OPACITY))
        val eraser = DrawTestKit.reduce(basic, DrawCommands.setEraserPosition(1f))
        assertEquals(DrawCommands.maxEraserSize(), DrawCommands.eraserSize(eraser), 0.001f)
    }

    @Test
    fun shapeControlsFollowTheShape() {
        val star = DrawTestKit.reduce(DrawTestKit.page(), DrawCommands.setShapeKind(DrawShapeKind.STAR))
        assertEquals(listOf(DrawShapeControl.SIDES, DrawShapeControl.INNER_RATIO), DrawCommands.shapeControls(star))
        val triangle = DrawTestKit.reduce(star, DrawCommands.setShapeControl(star, DrawShapeControl.SIDES, 0f))
        assertEquals(3, DrawCommands.currentShape(triangle).sides)
        assertEquals("3", DrawCommands.shapeControlText(triangle, DrawShapeControl.SIDES))
        val line = DrawTestKit.reduce(star, DrawCommands.setShapeKind(DrawShapeKind.LINE))
        assertTrue(DrawCommands.shapeControls(line).isEmpty())
        assertFalse(DrawCommands.canFillShape(line))
        assertTrue(DrawCommands.canFillShape(star))
    }

    @Test
    fun previewsRenderEveryBrushAndShapeInsideTheirBox() {
        val state = DrawTestKit.page()
        DrawCommands.brushKinds().forEach { kind ->
            val entries = DrawCommands.brushPreview(state, kind, 120f, 48f)
            assertTrue(entries.isNotEmpty(), "brush $kind")
            entries.forEach { assertTrue(it.item.bounds.x >= -1f && it.item.bounds.right <= 121f, "brush $kind bounds") }
        }
        DrawCommands.shapeKinds().forEach { kind ->
            val entries = DrawCommands.shapePreview(state, kind, 48f, 48f)
            assertTrue(entries.isNotEmpty(), "shape $kind")
            assertTrue(entries.all { it.matrix.isIdentity }, "shape $kind matrix")
        }
    }

    @Test
    fun tappingTheActiveToolOpensItsPanel() {
        val state = DrawTestKit.page()
        assertTrue(DrawCommands.opensPanel(state, DrawToolId.PEN))
        assertFalse(DrawCommands.opensPanel(state, DrawToolId.ERASER))
        assertEquals(DrawPanel.ERASER, DrawCommands.panelFor(DrawToolId.ERASER))
        assertFalse(DrawCommands.opensPanel(DrawTestKit.reduce(state, DrawCommands.setTool(DrawToolId.HAND)), DrawToolId.HAND))
    }

    @Test
    fun theToolboxTravelsBetweenSurfaces() {
        val source = DrawTestKit.reduce(
            DrawTestKit.overlay(),
            DrawCommands.setBrushKind(DrawBrushKind.MARKER),
            DrawCommands.setMode(DrawToolMode.ADVANCED)
        )
        val target = DrawTestKit.reduce(DrawTestKit.page(), DrawCommands.carryToolbox(source))
        assertEquals(DrawBrushKind.MARKER, DrawCommands.currentBrushKind(target))
        assertTrue(DrawCommands.isAdvanced(target))
        assertTrue(DrawCommands.showsPaper(target))
        assertFalse(DrawCommands.showsPaper(source))
        assertFalse(DrawCommands.isRotated(target))
    }

    @Test
    fun toolbarAndPaperTextsDescribeTheState() {
        val page = DrawTestKit.page()
        val toolbar = DrawCommands.toolbar(page)
        assertTrue(toolbar.navigates)
        assertTrue(toolbar.showsPaper)
        assertTrue(toolbar.isTool(DrawToolId.PEN))
        assertFalse(toolbar.canUndo)
        assertEquals(100, toolbar.zoomPercent)
        assertFalse(DrawCommands.toolbar(DrawTestKit.overlay()).showsPaper)
        val a4 = DrawTestKit.reduce(page, DrawCommands.setPaperSize(page, DrawPaperSize.A4))
        val millimetres = DrawTestKit.reduce(a4, DrawCommands.setUnit(a4, DrawUnit.MM))
        assertEquals("210 × 297 mm", DrawCommands.paperSizeText(millimetres))
        assertEquals(12.5f, DrawCommands.parseLength(" 12,5 "))
        assertEquals(null, DrawCommands.parseLength("-3"))
        assertEquals("Palette 1", DrawCommands.nextPaletteName(page))
    }

    @Test
    fun texturesDrawBetweenTheBackgroundAndThePattern() {
        val page = DrawTestKit.page()
        assertEquals(1, DrawCommands.paperFrame(page).size)
        DrawCommands.textures().filter { it != DrawTexture.NONE }.forEach { texture ->
            val textured = DrawTestKit.reduce(page, DrawCommands.setTexture(page, texture))
            val frame = DrawCommands.paperFrame(textured)
            assertEquals(2, frame.size, "texture $texture")
            assertTrue(frame.last().item.key == "paper:texture", "texture $texture key")
        }
        val stable = DrawTestKit.reduce(page, DrawCommands.setTexture(page, DrawTexture.PAPER))
        val first = DrawCommands.paperFrame(stable).last().item.dabs
        val second = DrawCommands.paperFrame(stable).last().item.dabs
        assertTrue(first.contentEquals(second))
        assertTrue(first.isNotEmpty())
        assertTrue(DrawCommands.isSameColor(DrawColor.fromRgb(10, 10, 10), DrawColor.fromRgb(11, 10, 9)))
        assertFalse(DrawCommands.isSameColor(DrawColor.BLACK, DrawColor.WHITE))
    }

    @Test
    fun mappedAnchorsKeepTheHostScaleAndScrollOrigin() {
        val page = DrawCommands.mappedAnchor("page-1", 20f, 40f, 300f, 500f, 20f, 72f - 120f, 0.5f)
        val drawn = DrawTestKit.stroke(
            DrawTestKit.overlay(),
            DrawTestKit.line(DrawTestKit.point(60f, 100f), DrawTestKit.point(160f, 100f), 10),
            DrawPointerType.STYLUS,
            listOf(page)
        )
        val element = drawn.document.elements.single()
        assertEquals("page-1", element.anchorId)
        assertEquals((60f - 20f) / 0.5f, element.points.first().x, 0.01f)
        assertEquals((100f - (72f - 120f)) / 0.5f, element.points.first().y, 0.01f)
        val scrolledBack = DrawCommands.mappedAnchor("page-1", 20f, 40f, 300f, 500f, 20f, 72f, 0.5f)
        val entry = DrawCommands.frame(drawn, listOf(scrolledBack), DrawCommands.renderCache()).single()
        assertEquals(72f, entry.matrix.ty, 0.001f)
        assertEquals(0.5f, entry.matrix.a, 0.0001f)
        val plain = DrawCommands.anchor("block", 0f, 10f, 400f, 100f)
        assertEquals(0.4f, plain.scale, 0.0001f)
        assertEquals(10f, plain.originY, 0.0001f)
    }
}
