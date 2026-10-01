package com.app.pustakam.core.drawing

import com.app.pustakam.core.drawing.viewport.DrawViewport
import com.app.pustakam.core.richtext.master.model.CanvasRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DrawViewportTest {

    private val rotated = DrawViewport.sized(400f, 800f)
        .zoomed(1.7f, 120f, 300f)
        .panned(33f, -21f)
        .rotatedBy(0.6f, 200f, 400f)

    @Test
    fun viewAndDocumentAreInverse() {
        val document = rotated.toDocument(123f, 456f)
        val view = rotated.toView(document.x, document.y)
        assertEquals(123f, view.x, 0.01f)
        assertEquals(456f, view.y, 0.01f)
    }

    @Test
    fun matrixMatchesToView() {
        val matrix = rotated.matrix()
        val view = rotated.toView(40f, 70f)
        assertEquals(view.x, matrix.mapX(40f, 70f), 0.001f)
        assertEquals(view.y, matrix.mapY(40f, 70f), 0.001f)
    }

    @Test
    fun rotatingKeepsTheFocusPointUnderTheFinger() {
        val before = rotated.toDocument(90f, 610f)
        val turned = rotated.rotatedBy(1.1f, 90f, 610f)
        val after = turned.toDocument(90f, 610f)
        assertEquals(before.x, after.x, 0.01f)
        assertEquals(before.y, after.y, 0.01f)
    }

    @Test
    fun zoomingKeepsTheFocusPointUnderTheFinger() {
        val before = rotated.toDocument(250f, 100f)
        val after = rotated.zoomed(2.3f, 250f, 100f).toDocument(250f, 100f)
        assertEquals(before.x, after.x, 0.01f)
        assertEquals(before.y, after.y, 0.01f)
    }

    @Test
    fun panningMovesContentWithTheFingerEvenWhenRotated() {
        val anchor = rotated.toDocument(200f, 200f)
        val moved = rotated.panned(15f, -40f)
        val view = moved.toView(anchor.x, anchor.y)
        assertEquals(215f, view.x, 0.01f)
        assertEquals(160f, view.y, 0.01f)
    }

    @Test
    fun fittingCentersThePaperAndKeepsItInside() {
        val paper = CanvasRect(0f, 0f, 595f, 842f)
        val fitted = DrawViewport.sized(400f, 800f).rotatedBy(0.4f, 200f, 400f).fitted(paper, 24f)
        val center = fitted.toView(paper.centerX, paper.centerY)
        assertEquals(200f, center.x, 0.01f)
        assertEquals(400f, center.y, 0.01f)
        listOf(0f to 0f, 595f to 0f, 0f to 842f, 595f to 842f).forEach { (x, y) ->
            val corner = fitted.toView(x, y)
            assertTrue(corner.x >= 23f && corner.x <= 377f && corner.y >= 23f && corner.y <= 777f)
        }
    }

    @Test
    fun resetRotationKeepsTheCenter() {
        val center = rotated.toDocument(200f, 400f)
        val upright = rotated.resetRotation()
        assertEquals(0f, upright.rotation, 0.0001f)
        val after = upright.toDocument(200f, 400f)
        assertEquals(center.x, after.x, 0.01f)
        assertEquals(center.y, after.y, 0.01f)
    }

    @Test
    fun nearlyStraightRotationSnaps() {
        val almost = DrawViewport.sized(400f, 800f).rotatedBy(1.5707964f + 0.03f, 200f, 400f)
        assertEquals(1.5707964f, almost.snappedRotation().rotation, 0.0001f)
        val tilted = DrawViewport.sized(400f, 800f).rotatedBy(0.5f, 200f, 400f)
        assertEquals(0.5f, tilted.snappedRotation().rotation, 0.0001f)
    }

    @Test
    fun visibleRectCoversTheScreenCorners() {
        val visible = rotated.visibleDocumentRect()
        listOf(0f to 0f, 400f to 0f, 0f to 800f, 400f to 800f).forEach { (x, y) ->
            val corner = rotated.toDocument(x, y)
            assertTrue(visible.inflated(0.01f).contains(corner.x, corner.y))
        }
    }
}
