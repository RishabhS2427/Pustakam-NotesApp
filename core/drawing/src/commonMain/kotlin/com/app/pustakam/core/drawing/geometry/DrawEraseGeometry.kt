package com.app.pustakam.core.drawing.geometry

import com.app.pustakam.core.drawing.model.DrawCut
import com.app.pustakam.core.drawing.model.DrawElement
import com.app.pustakam.core.drawing.model.DrawElementKind
import com.app.pustakam.core.drawing.model.DrawPoint
import com.app.pustakam.core.richtext.master.model.CanvasRect
import kotlin.math.hypot

object DrawEraseGeometry {

    fun hits(element: DrawElement, eraser: List<DrawPoint>, radius: Float): Boolean {
        if (eraser.isEmpty() || element.points.isEmpty()) return false
        if (!DrawGeometry.boundsOf(eraser, radius).intersects(element.bounds)) return false
        val reach = radius + element.brush.size / 2f
        if (element.isCut) return hitsVisible(element, eraser, reach)
        return when (element.kind) {
            DrawElementKind.SHAPE -> hitsShape(element, eraser, reach)
            else -> hitsPolyline(element.points.map { DrawVec(it.x, it.y) }, eraser, reach)
        }
    }

    fun cutsFor(element: DrawElement, eraser: List<DrawPoint>, radius: Float): List<DrawCut> {
        if (eraser.isEmpty() || element.points.isEmpty()) return emptyList()
        val area = element.bounds.inflated(radius)
        val near = BooleanArray(eraser.size) { index -> touchesArea(eraser, index, area) }
        val cuts = mutableListOf<DrawCut>()
        var run = mutableListOf<DrawPoint>()
        for (index in eraser.indices) {
            if (near[index]) {
                run.add(eraser[index])
            } else if (run.isNotEmpty()) {
                cuts.add(DrawCut(run, radius))
                run = mutableListOf()
            }
        }
        if (run.isNotEmpty()) cuts.add(DrawCut(run, radius))
        return cuts.filterNot { cut -> cut.points.all { isCovered(element, it.x, it.y, radius * REDUNDANT_SLACK) } }
    }

    fun isErasedAway(element: DrawElement): Boolean {
        if (!element.isCut) return false
        val samples = coverageSamples(element)
        return samples.isNotEmpty() && samples.all { isCovered(element, it.x, it.y, it.slack) }
    }

    fun isCovered(element: DrawElement, x: Float, y: Float, slack: Float): Boolean =
        element.cuts.any { cut -> DrawGeometry.distanceToPolyline(x, y, cut.points) <= cut.radius - slack }

    private class Sample(val x: Float, val y: Float, val slack: Float)

    private fun touchesArea(points: List<DrawPoint>, index: Int, area: CanvasRect): Boolean {
        val point = points[index]
        if (area.contains(point.x, point.y)) return true
        val before = points.getOrNull(index - 1)
        val after = points.getOrNull(index + 1)
        return (before != null && segmentBox(before, point).intersects(area)) ||
            (after != null && segmentBox(point, after).intersects(area))
    }

    private fun segmentBox(a: DrawPoint, b: DrawPoint): CanvasRect =
        DrawGeometry.rectOf(DrawVec(a.x, a.y), DrawVec(b.x, b.y)).inflated(SEGMENT_SLOP)

    private fun coverageSamples(element: DrawElement): List<Sample> = when (element.kind) {
        DrawElementKind.SHAPE -> shapeSamples(element)
        else -> along(element.points.map { DrawVec(it.x, it.y) }, element.brush.size, element.brush.size)
    }

    private fun shapeSamples(element: DrawElement): List<Sample> {
        val outlines = DrawPathFlattener.polylines(element.shapePath(), DrawGeometry.CURVE_SEGMENTS)
        val half = element.brush.size / 2f
        val edges = outlines.flatMap { along(it, element.brush.size, half) }
        if (!element.shape.filled || !element.shape.isFillable) return edges
        val bounds = element.bounds
        val interior = mutableListOf<Sample>()
        for (row in 0..FILL_GRID) {
            for (column in 0..FILL_GRID) {
                val x = bounds.x + bounds.width * column / FILL_GRID
                val y = bounds.y + bounds.height * row / FILL_GRID
                if (outlines.any { DrawGeometry.containsPoint(it, x, y) }) interior.add(Sample(x, y, 0f))
            }
        }
        return edges + interior
    }

    private fun along(polyline: List<DrawVec>, size: Float, slack: Float): List<Sample> {
        if (polyline.isEmpty()) return emptyList()
        val step = maxOf(size / 2f, MIN_STEP)
        val samples = mutableListOf(Sample(polyline.first().x, polyline.first().y, slack))
        for (index in 1 until polyline.size) {
            val from = polyline[index - 1]
            val to = polyline[index]
            val length = hypot(to.x - from.x, to.y - from.y)
            val count = (length / step).toInt().coerceIn(1, MAX_SAMPLES_PER_SEGMENT)
            for (part in 1..count) {
                val t = part.toFloat() / count
                samples.add(Sample(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t, slack))
            }
        }
        return samples
    }

    fun segmentsIntersect(a: DrawVec, b: DrawVec, c: DrawVec, d: DrawVec): Boolean {
        val d1 = cross(c, d, a)
        val d2 = cross(c, d, b)
        val d3 = cross(a, b, c)
        val d4 = cross(a, b, d)
        return ((d1 > 0f && d2 < 0f) || (d1 < 0f && d2 > 0f)) && ((d3 > 0f && d4 < 0f) || (d3 < 0f && d4 > 0f))
    }

    private fun hitsVisible(element: DrawElement, eraser: List<DrawPoint>, reach: Float): Boolean {
        val visible = coverageSamples(element).filterNot { isCovered(element, it.x, it.y, it.slack) }
        if (visible.any { DrawGeometry.distanceToPolyline(it.x, it.y, eraser) <= reach }) return true
        if (!element.isShape || !element.shape.filled || !element.shape.isFillable) return false
        val outlines = DrawPathFlattener.polylines(element.shapePath(), DrawGeometry.CURVE_SEGMENTS)
        return eraser.any { point ->
            !isCovered(element, point.x, point.y, 0f) && outlines.any { DrawGeometry.containsPoint(it, point.x, point.y) }
        }
    }

    private fun hitsShape(element: DrawElement, eraser: List<DrawPoint>, reach: Float): Boolean {
        val outlines = DrawPathFlattener.polylines(element.shapePath(), DrawGeometry.CURVE_SEGMENTS)
        if (outlines.any { hitsPolyline(it, eraser, reach) }) return true
        if (!element.shape.filled || !element.shape.isFillable) return false
        return eraser.any { point -> outlines.any { DrawGeometry.containsPoint(it, point.x, point.y) } }
    }

    private fun hitsPolyline(polyline: List<DrawVec>, eraser: List<DrawPoint>, reach: Float): Boolean {
        if (polyline.isEmpty()) return false
        val area = DrawGeometry.boundsOfVecs(polyline, reach)
        for (point in eraser) {
            if (!area.contains(point.x, point.y)) continue
            if (DrawGeometry.distanceToVecs(point.x, point.y, polyline) <= reach) return true
        }
        if (eraser.size < 2 || polyline.size < 2) return false
        for (index in 1 until eraser.size) {
            val from = DrawVec(eraser[index - 1].x, eraser[index - 1].y)
            val to = DrawVec(eraser[index].x, eraser[index].y)
            for (segment in 1 until polyline.size) {
                if (segmentsIntersect(from, to, polyline[segment - 1], polyline[segment])) return true
            }
        }
        return false
    }

    private fun cross(origin: DrawVec, target: DrawVec, point: DrawVec): Float =
        (target.x - origin.x) * (point.y - origin.y) - (target.y - origin.y) * (point.x - origin.x)

    private const val REDUNDANT_SLACK = 0.25f

    private const val SEGMENT_SLOP = 0.5f

    private const val MIN_STEP = 1f

    private const val MAX_SAMPLES_PER_SEGMENT = 256

    private const val FILL_GRID = 12
}
