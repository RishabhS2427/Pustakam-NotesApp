package com.app.pustakam.core.drawing

import com.app.pustakam.core.drawing.geometry.DrawPathBuilder
import com.app.pustakam.core.drawing.geometry.DrawPathFlattener
import com.app.pustakam.core.drawing.geometry.DrawShapeBuilder
import com.app.pustakam.core.drawing.geometry.DrawVec
import com.app.pustakam.core.drawing.model.DrawColor
import com.app.pustakam.core.drawing.model.DrawOrientation
import com.app.pustakam.core.drawing.model.DrawPaper
import com.app.pustakam.core.drawing.model.DrawPaperSize
import com.app.pustakam.core.drawing.model.DrawPattern
import com.app.pustakam.core.drawing.model.DrawShapeKind
import com.app.pustakam.core.drawing.model.DrawShapeSpec
import com.app.pustakam.core.drawing.model.DrawUnit
import com.app.pustakam.core.drawing.model.DrawUnits
import com.app.pustakam.core.drawing.render.DrawPatternRenderer
import com.app.pustakam.core.drawing.render.DrawRenderKind
import com.app.pustakam.core.richtext.master.model.CanvasRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DrawShapeAndPaperTest {

    private val start = DrawVec(10f, 20f)

    private val end = DrawVec(110f, 220f)

    @Test
    fun everyShapeBuildsAPath() {
        DrawShapeSpec.kinds().forEach { kind ->
            val path = DrawShapeBuilder.path(DrawShapeSpec.of(kind), start, end, 2f)
            assertTrue(path.isNotEmpty(), "$kind built nothing")
            assertEquals(DrawPathBuilder.MOVE, path.first())
        }
    }

    @Test
    fun rectangleUsesTheDragBox() {
        val lines = DrawPathFlattener.polylines(DrawShapeBuilder.path(DrawShapeSpec.of(DrawShapeKind.RECTANGLE), end, start, 1f), 1)
        val corners = lines.single().distinct()
        assertEquals(setOf(DrawVec(10f, 20f), DrawVec(110f, 20f), DrawVec(110f, 220f), DrawVec(10f, 220f)), corners.toSet())
    }

    @Test
    fun polygonAndStarHonourTheirCounts() {
        assertEquals(7, DrawShapeBuilder.polygon(start, end, 7).size)
        assertEquals(10, DrawShapeBuilder.star(start, end, 5, 0.5f).size)
        assertEquals(3, DrawShapeBuilder.polygon(start, end, 1).size)
    }

    @Test
    fun circleIsCenteredOnTheFirstPoint() {
        val outline = DrawPathFlattener.polylines(DrawShapeBuilder.path(DrawShapeSpec.of(DrawShapeKind.CIRCLE), DrawVec(0f, 0f), DrawVec(30f, 40f), 1f), 16)
            .flatten()
        outline.forEach { assertEquals(50f, it.length, 0.6f) }
    }

    @Test
    fun arcStartsAndEndsOnTheDragPoints() {
        val lines = DrawPathFlattener.polylines(DrawShapeBuilder.path(DrawShapeSpec.of(DrawShapeKind.ARC), DrawVec(0f, 0f), DrawVec(100f, 0f), 1f), 8)
        val arc = lines.single()
        assertEquals(0f, arc.first().distanceTo(DrawVec(0f, 0f)), 0.01f)
        assertEquals(0f, arc.last().distanceTo(DrawVec(100f, 0f)), 0.01f)
        assertTrue(arc.any { kotlin.math.abs(it.y) > 45f })
    }

    @Test
    fun arrowHasAShaftAndAHead() {
        val lines = DrawPathFlattener.polylines(DrawShapeBuilder.path(DrawShapeSpec.of(DrawShapeKind.ARROW), start, end, 2f), 1)
        assertEquals(2, lines.size)
        assertEquals(end, lines[1][1])
    }

    @Test
    fun standardPapersHaveIsoSizesAndSwapForLandscape() {
        val a4 = DrawPaper.standard(DrawPaperSize.A4, DrawOrientation.PORTRAIT)
        assertEquals(210f, a4.widthIn(DrawUnit.MM), 0.01f)
        assertEquals(297f, a4.heightIn(DrawUnit.MM), 0.01f)
        val landscape = a4.withOrientation(DrawOrientation.LANDSCAPE)
        assertEquals(297f, landscape.widthIn(DrawUnit.MM), 0.01f)
        assertEquals(210f, landscape.heightIn(DrawUnit.MM), 0.01f)
        val a0 = landscape.withSize(DrawPaperSize.A0)
        assertEquals(1189f, a0.widthIn(DrawUnit.MM), 0.05f)
        assertEquals(DrawOrientation.LANDSCAPE, a0.orientation)
    }

    @Test
    fun unitsConvertThroughPoints() {
        assertEquals(72f, DrawUnits.toPoints(1f, DrawUnit.INCH, 300), 0.001f)
        assertEquals(25.4f, DrawUnits.fromPoints(72f, DrawUnit.MM, 300), 0.001f)
        assertEquals(300f, DrawUnits.fromPoints(72f, DrawUnit.PX, 300), 0.001f)
        assertEquals(1f, DrawUnits.fromPoints(DrawUnits.toPoints(1f, DrawUnit.METER, 72), DrawUnit.METER, 72), 0.0001f)
        val custom = DrawPaper.infinite().withCustomSize(100f, 50f, DrawUnit.MM)
        assertEquals(DrawOrientation.LANDSCAPE, custom.orientation)
        assertEquals(100f, custom.widthIn(DrawUnit.MM), 0.01f)
        assertEquals(2480, DrawPaper.standard(DrawPaperSize.A4, DrawOrientation.PORTRAIT).withDpi(300).pixelWidth)
    }

    @Test
    fun patternsDrawOnlyInsideThePaperAndSkipWhenTooDense() {
        val paper = DrawPaper.standard(DrawPaperSize.A4, DrawOrientation.PORTRAIT).withPattern(DrawPattern.GRID)
        val items = DrawPatternRenderer.items(paper, CanvasRect(-100f, -100f, 2000f, 2000f), 1f)
        assertEquals(DrawRenderKind.FILL_PATH, items.first().kind)
        val grid = items.last()
        assertEquals(DrawRenderKind.STROKE_PATH, grid.kind)
        DrawPathFlattener.polylines(grid.path, 1).flatten().forEach {
            assertTrue(it.x in 0f..595.3f && it.y in 0f..842f)
        }
        val tiny = DrawPatternRenderer.items(paper, CanvasRect(0f, 0f, 595.3f, 842f), 0.1f)
        assertEquals(1, tiny.size)
        val transparent = DrawPatternRenderer.items(paper.withTransparent(true).withPattern(DrawPattern.NONE), CanvasRect(0f, 0f, 100f, 100f), 1f)
        assertTrue(transparent.isEmpty())
    }

    @Test
    fun everyPatternRenders() {
        DrawPattern.entries.filter { it != DrawPattern.NONE }.forEach { pattern ->
            val paper = DrawPaper.standard(DrawPaperSize.A4, DrawOrientation.PORTRAIT).withPattern(pattern)
            val items = DrawPatternRenderer.items(paper, CanvasRect(0f, 0f, 595.3f, 842f), 1f)
            assertTrue(items.size >= 2, "$pattern drew nothing")
        }
        assertEquals(DrawColor.WHITE, DrawPaper.screen(10f, 10f).background)
    }
}
