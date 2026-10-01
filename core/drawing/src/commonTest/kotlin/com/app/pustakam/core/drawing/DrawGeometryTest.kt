package com.app.pustakam.core.drawing

import com.app.pustakam.core.drawing.geometry.DrawEraseGeometry
import com.app.pustakam.core.drawing.geometry.DrawGeometry
import com.app.pustakam.core.drawing.geometry.DrawMatrix
import com.app.pustakam.core.drawing.geometry.DrawPathBuilder
import com.app.pustakam.core.drawing.geometry.DrawPathFlattener
import com.app.pustakam.core.drawing.geometry.DrawSmoothing
import com.app.pustakam.core.drawing.geometry.DrawVec
import com.app.pustakam.core.drawing.model.DrawPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DrawGeometryTest {

    @Test
    fun distanceToSegmentClampsToTheEnds() {
        assertEquals(5f, DrawGeometry.distanceToSegment(3f, 5f, 0f, 0f, 10f, 0f), 0.001f)
        assertEquals(5f, DrawGeometry.distanceToSegment(-3f, 4f, 0f, 0f, 10f, 0f), 0.001f)
        assertEquals(0f, DrawGeometry.distanceToSegment(4f, 0f, 0f, 0f, 10f, 0f), 0.001f)
    }

    @Test
    fun thinningKeepsBothEndsAndDropsCrowdedPoints() {
        val points = (0..100).map { DrawPoint.at(it * 0.1f, 0f) }
        val thinned = DrawGeometry.thinned(points, 1f)
        assertEquals(points.first(), thinned.first())
        assertEquals(points.last(), thinned.last())
        assertTrue(thinned.size in 10..13)
    }

    @Test
    fun containsPointFollowsTheEvenOddRule() {
        val square = listOf(DrawVec(0f, 0f), DrawVec(10f, 0f), DrawVec(10f, 10f), DrawVec(0f, 10f))
        assertTrue(DrawGeometry.containsPoint(square, 5f, 5f))
        assertFalse(DrawGeometry.containsPoint(square, 15f, 5f))
    }

    @Test
    fun matrixComposeAndInvertAreConsistent() {
        val matrix = DrawMatrix.rotationAbout(0.7f, 20f, 30f).then(DrawMatrix.scaling(2f, 2f)).then(DrawMatrix.translation(5f, -9f))
        val inverse = assertNotNull(matrix.inverted())
        val mapped = matrix.map(13f, 17f)
        val back = inverse.map(mapped.x, mapped.y)
        assertEquals(13f, back.x, 0.001f)
        assertEquals(17f, back.y, 0.001f)
    }

    @Test
    fun smoothingLagsWhileDrawingAndCatchesUpWhenDone() {
        val zigzag = (0..20).map { DrawPoint.at(it * 5f, if (it % 2 == 0) 0f else 10f) }
        val live = DrawSmoothing.stabilize(zigzag, 0.8f, complete = false)
        val done = DrawSmoothing.stabilize(zigzag, 0.8f, complete = true)
        assertEquals(zigzag.size, live.size)
        assertEquals(zigzag.last(), done.last())
        val liveSpread = live.drop(5).maxOf { it.y } - live.drop(5).minOf { it.y }
        assertTrue(liveSpread < 10f)
    }

    @Test
    fun flattenerFollowsCurvesAndSubpaths() {
        val path = DrawPathBuilder().moveTo(0f, 0f).quadTo(5f, 10f, 10f, 0f).moveTo(20f, 0f).lineTo(30f, 0f).build()
        val lines = DrawPathFlattener.polylines(path, 4)
        assertEquals(2, lines.size)
        assertEquals(5, lines[0].size)
        assertEquals(DrawVec(10f, 0f), lines[0].last())
    }

    @Test
    fun crossingSegmentsIntersect() {
        assertTrue(DrawEraseGeometry.segmentsIntersect(DrawVec(0f, 0f), DrawVec(10f, 10f), DrawVec(0f, 10f), DrawVec(10f, 0f)))
        assertFalse(DrawEraseGeometry.segmentsIntersect(DrawVec(0f, 0f), DrawVec(10f, 0f), DrawVec(0f, 5f), DrawVec(10f, 5f)))
    }
}
