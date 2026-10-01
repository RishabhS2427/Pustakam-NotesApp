package com.app.pustakam.core.drawing.geometry

import com.app.pustakam.core.drawing.model.DrawPoint
import com.app.pustakam.core.richtext.master.model.CanvasRect
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

object DrawGeometry {

    fun lerp(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction

    fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
        if (edge1 <= edge0) return if (value >= edge1) 1f else 0f
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared <= DrawVec.EPSILON) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    fun distanceToPolyline(px: Float, py: Float, points: List<DrawPoint>): Float {
        if (points.isEmpty()) return Float.MAX_VALUE
        if (points.size == 1) return hypot(px - points[0].x, py - points[0].y)
        var best = Float.MAX_VALUE
        for (index in 1 until points.size) {
            val a = points[index - 1]
            val b = points[index]
            best = min(best, distanceToSegment(px, py, a.x, a.y, b.x, b.y))
        }
        return best
    }

    fun distanceToVecs(px: Float, py: Float, points: List<DrawVec>): Float {
        if (points.isEmpty()) return Float.MAX_VALUE
        if (points.size == 1) return hypot(px - points[0].x, py - points[0].y)
        var best = Float.MAX_VALUE
        for (index in 1 until points.size) {
            val a = points[index - 1]
            val b = points[index]
            best = min(best, distanceToSegment(px, py, a.x, a.y, b.x, b.y))
        }
        return best
    }

    fun boundsOf(points: List<DrawPoint>, inflate: Float): CanvasRect {
        if (points.isEmpty()) return CanvasRect()
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (point in points) {
            left = min(left, point.x)
            top = min(top, point.y)
            right = max(right, point.x)
            bottom = max(bottom, point.y)
        }
        return CanvasRect(left - inflate, top - inflate, right - left + inflate * 2f, bottom - top + inflate * 2f)
    }

    fun boundsOfVecs(points: List<DrawVec>, inflate: Float): CanvasRect {
        if (points.isEmpty()) return CanvasRect()
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (point in points) {
            left = min(left, point.x)
            top = min(top, point.y)
            right = max(right, point.x)
            bottom = max(bottom, point.y)
        }
        return CanvasRect(left - inflate, top - inflate, right - left + inflate * 2f, bottom - top + inflate * 2f)
    }

    fun boundsOfPath(path: FloatArray, inflate: Float): CanvasRect =
        boundsOfVecs(DrawPathFlattener.polylines(path, CURVE_SEGMENTS).flatten(), inflate)

    fun length(points: List<DrawPoint>): Float {
        var total = 0f
        for (index in 1 until points.size) total += points[index - 1].distanceTo(points[index])
        return total
    }

    fun thinned(points: List<DrawPoint>, minDistance: Float): List<DrawPoint> {
        if (points.size < 3 || minDistance <= 0f) return points
        val kept = ArrayList<DrawPoint>(points.size)
        kept.add(points.first())
        for (index in 1 until points.size - 1) {
            if (points[index].distanceTo(kept.last()) >= minDistance) kept.add(points[index])
        }
        kept.add(points.last())
        return kept
    }

    fun containsPoint(polygon: List<DrawVec>, x: Float, y: Float): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var previous = polygon.last()
        for (current in polygon) {
            val crosses = (current.y > y) != (previous.y > y)
            if (crosses) {
                val atX = (previous.x - current.x) * (y - current.y) / (previous.y - current.y) + current.x
                if (x < atX) inside = !inside
            }
            previous = current
        }
        return inside
    }

    fun rectOf(start: DrawVec, end: DrawVec): CanvasRect =
        CanvasRect(min(start.x, end.x), min(start.y, end.y), kotlin.math.abs(end.x - start.x), kotlin.math.abs(end.y - start.y))

    const val CURVE_SEGMENTS = 8
}
