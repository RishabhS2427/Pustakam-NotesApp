package com.app.pustakam.core.drawing.geometry

import com.app.pustakam.core.drawing.model.DrawElement
import com.app.pustakam.core.drawing.model.DrawElementKind
import com.app.pustakam.core.drawing.model.DrawPoint

object DrawEraseGeometry {

    fun hits(element: DrawElement, eraser: List<DrawPoint>, radius: Float): Boolean {
        if (eraser.isEmpty() || element.points.isEmpty()) return false
        if (!DrawGeometry.boundsOf(eraser, radius).intersects(element.bounds)) return false
        val reach = radius + element.brush.size / 2f
        return when (element.kind) {
            DrawElementKind.SHAPE -> hitsShape(element, eraser, reach)
            else -> hitsPolyline(element.points.map { DrawVec(it.x, it.y) }, eraser, reach)
        }
    }

    fun keptRuns(element: DrawElement, eraser: List<DrawPoint>, radius: Float): List<List<DrawPoint>>? {
        if (element.kind != DrawElementKind.STROKE || eraser.isEmpty()) return null
        if (!DrawGeometry.boundsOf(eraser, radius).intersects(element.bounds)) return null
        val reach = radius + element.brush.size / 2f
        val runs = mutableListOf<List<DrawPoint>>()
        var current = mutableListOf<DrawPoint>()
        var cut = false
        for (point in element.points) {
            if (DrawGeometry.distanceToPolyline(point.x, point.y, eraser) <= reach) {
                cut = true
                if (current.isNotEmpty()) runs.add(current)
                current = mutableListOf()
            } else {
                current.add(point)
            }
        }
        if (current.isNotEmpty()) runs.add(current)
        return if (cut) runs else null
    }

    fun segmentsIntersect(a: DrawVec, b: DrawVec, c: DrawVec, d: DrawVec): Boolean {
        val d1 = cross(c, d, a)
        val d2 = cross(c, d, b)
        val d3 = cross(a, b, c)
        val d4 = cross(a, b, d)
        return ((d1 > 0f && d2 < 0f) || (d1 < 0f && d2 > 0f)) && ((d3 > 0f && d4 < 0f) || (d3 < 0f && d4 > 0f))
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
}
