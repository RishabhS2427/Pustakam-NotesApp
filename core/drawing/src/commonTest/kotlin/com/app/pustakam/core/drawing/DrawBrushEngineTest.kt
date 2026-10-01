package com.app.pustakam.core.drawing

import com.app.pustakam.core.drawing.brush.DrawBrushCatalog
import com.app.pustakam.core.drawing.brush.DrawBrushStrategy
import com.app.pustakam.core.drawing.brush.DrawBrushTip
import com.app.pustakam.core.drawing.brush.DrawStampGenerator
import com.app.pustakam.core.drawing.brush.DrawStrokeDynamics
import com.app.pustakam.core.drawing.brush.DrawStrokeOutline
import com.app.pustakam.core.drawing.geometry.DrawPathBuilder
import com.app.pustakam.core.drawing.model.DrawBlend
import com.app.pustakam.core.drawing.model.DrawBrushKind
import com.app.pustakam.core.drawing.model.DrawPoint
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DrawBrushEngineTest {

    private val line = DrawTestKit.line(DrawTestKit.point(0f, 0f, 0L), DrawTestKit.point(200f, 0f, 400L), 50)

    @Test
    fun everyPenHasAPresetAndAProfile() {
        assertEquals(11, DrawBrushCatalog.kinds().size)
        DrawBrushCatalog.kinds().forEach { kind ->
            val brush = DrawBrushCatalog.defaultBrush(kind)
            assertEquals(kind, brush.kind)
            assertTrue(brush.size > 0f)
            DrawBrushCatalog.profile(kind)
            assertTrue(DrawBrushCatalog.label(kind).isNotBlank())
        }
    }

    @Test
    fun texturedPensStampAndInkPensOutline() {
        listOf(DrawBrushKind.CHALK, DrawBrushKind.CRAYON, DrawBrushKind.CHARCOAL, DrawBrushKind.AIRBRUSH, DrawBrushKind.PENCIL)
            .forEach { assertEquals(DrawBrushStrategy.STAMP, DrawBrushCatalog.profile(it).strategy) }
        listOf(DrawBrushKind.BALLPOINT, DrawBrushKind.FOUNTAIN, DrawBrushKind.MARKER, DrawBrushKind.BRUSH_PEN, DrawBrushKind.TECHNICAL, DrawBrushKind.HIGHLIGHTER)
            .forEach { assertEquals(DrawBrushStrategy.OUTLINE, DrawBrushCatalog.profile(it).strategy) }
        assertEquals(DrawBlend.MULTIPLY, DrawBrushCatalog.profile(DrawBrushKind.HIGHLIGHTER).blend)
    }

    @Test
    fun technicalPenKeepsAConstantWidth() {
        val brush = DrawBrushCatalog.defaultBrush(DrawBrushKind.TECHNICAL)
        val points = line.mapIndexed { index, point -> point.withPressure(if (index % 2 == 0) 0.1f else 1f) }
        val widths = DrawStrokeDynamics.widths(points, brush, DrawBrushCatalog.profile(DrawBrushKind.TECHNICAL), true)
        widths.forEach { assertEquals(brush.size, it, 0.0001f) }
    }

    @Test
    fun pressureWidensAPressureSensitivePen() {
        val brush = DrawBrushCatalog.defaultBrush(DrawBrushKind.BRUSH_PEN)
        val profile = DrawBrushCatalog.profile(DrawBrushKind.BRUSH_PEN)
        val light = DrawStrokeDynamics.widths(line.map { it.withPressure(0.2f) }, brush, profile, false)
        val heavy = DrawStrokeDynamics.widths(line.map { it.withPressure(0.9f) }, brush, profile, false)
        assertTrue(heavy[25] > light[25] * 2f)
    }

    @Test
    fun finishedBrushPenTapersAtBothEnds() {
        val brush = DrawBrushCatalog.defaultBrush(DrawBrushKind.BRUSH_PEN)
        val widths = DrawStrokeDynamics.widths(line, brush, DrawBrushCatalog.profile(DrawBrushKind.BRUSH_PEN), true)
        assertTrue(widths.first() < widths[25])
        assertTrue(widths.last() < widths[25])
    }

    @Test
    fun fastStrokesThinWithVelocitySensitivity() {
        val brush = DrawBrushCatalog.defaultBrush(DrawBrushKind.BALLPOINT).withVelocitySensitivity(1f)
        val profile = DrawBrushCatalog.profile(DrawBrushKind.BALLPOINT)
        val slow = DrawTestKit.line(DrawTestKit.point(0f, 0f, 0L), DrawTestKit.point(200f, 0f, 20000L), 50)
        val fast = DrawTestKit.line(DrawTestKit.point(0f, 0f, 0L), DrawTestKit.point(200f, 0f, 50L), 50)
        assertTrue(DrawStrokeDynamics.widths(fast, brush, profile, false)[40] < DrawStrokeDynamics.widths(slow, brush, profile, false)[40])
    }

    @Test
    fun outlineIsAClosedPathAndADotForOnePoint() {
        val brush = DrawBrushCatalog.defaultBrush(DrawBrushKind.BALLPOINT)
        val widths = DrawStrokeDynamics.widths(line, brush, DrawBrushCatalog.profile(DrawBrushKind.BALLPOINT), true)
        val path = DrawStrokeOutline.path(line, widths, DrawBrushTip.ROUND)
        assertEquals(DrawPathBuilder.MOVE, path.first())
        assertEquals(DrawPathBuilder.CLOSE, path.last())
        val dot = DrawStrokeOutline.path(listOf(DrawPoint.at(5f, 5f)), floatArrayOf(4f), DrawBrushTip.ROUND)
        assertEquals(DrawPathBuilder.MOVE, dot.first())
        assertEquals(DrawPathBuilder.CLOSE, dot.last())
    }

    @Test
    fun stampsAreDeterministicForTheSameSeed() {
        val brush = DrawBrushCatalog.defaultBrush(DrawBrushKind.CHALK)
        val profile = DrawBrushCatalog.profile(DrawBrushKind.CHALK)
        val widths = DrawStrokeDynamics.widths(line, brush, profile, true)
        val alphas = DrawStrokeDynamics.alphas(line, brush)
        val first = DrawStampGenerator.dabs(line, widths, alphas, brush, profile, 42)
        val second = DrawStampGenerator.dabs(line, widths, alphas, brush, profile, 42)
        val other = DrawStampGenerator.dabs(line, widths, alphas, brush, profile, 7)
        assertContentEquals(first, second)
        assertTrue(first.size % DrawStampGenerator.STRIDE == 0 && first.size > DrawStampGenerator.STRIDE * 10)
        assertTrue(!first.contentEquals(other))
    }

    @Test
    fun aGrowingStrokeKeepsItsEarlierDabs() {
        val brush = DrawBrushCatalog.defaultBrush(DrawBrushKind.CRAYON)
        val profile = DrawBrushCatalog.profile(DrawBrushKind.CRAYON)
        val short = line.take(20)
        val shortDabs = DrawStampGenerator.dabs(short, DrawStrokeDynamics.widths(short, brush, profile, false), DrawStrokeDynamics.alphas(short, brush), brush, profile, 9)
        val longDabs = DrawStampGenerator.dabs(line, DrawStrokeDynamics.widths(line, brush, profile, false), DrawStrokeDynamics.alphas(line, brush), brush, profile, 9)
        val prefix = shortDabs.size - DrawStampGenerator.STRIDE * 4
        assertContentEquals(shortDabs.copyOf(prefix), longDabs.copyOf(prefix))
    }
}
