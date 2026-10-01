package com.app.pustakam.core.drawing.geometry

import com.app.pustakam.core.drawing.model.DrawPoint

object DrawSmoothing {

    private const val MAX_DRAG = 0.82f

    private const val CATCH_UP_DISTANCE = 0.01f

    fun stabilize(points: List<DrawPoint>, smoothing: Float, complete: Boolean): List<DrawPoint> {
        if (points.size < 3 || smoothing <= 0f) return points
        val follow = 1f - smoothing.coerceIn(0f, 1f) * MAX_DRAG
        val result = ArrayList<DrawPoint>(points.size + 1)
        var x = points[0].x
        var y = points[0].y
        result.add(points[0])
        for (index in 1 until points.size) {
            val point = points[index]
            x += (point.x - x) * follow
            y += (point.y - y) * follow
            result.add(point.movedTo(x, y))
        }
        val last = points.last()
        if (complete && kotlin.math.hypot(last.x - x, last.y - y) > CATCH_UP_DISTANCE) result.add(last)
        return result
    }
}
