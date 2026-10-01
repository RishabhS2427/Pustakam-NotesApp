package com.app.pustakam.core.drawing.brush

import com.app.pustakam.core.drawing.geometry.DrawGeometry
import com.app.pustakam.core.drawing.geometry.DrawPathBuilder
import com.app.pustakam.core.drawing.geometry.DrawVec
import com.app.pustakam.core.drawing.model.DrawPoint
import kotlin.math.PI
import kotlin.math.atan2

object DrawStrokeOutline {

    private const val HALF_TURN = PI.toFloat()

    private const val DOT_RATIO = 0.25f

    fun path(points: List<DrawPoint>, widths: FloatArray, tip: DrawBrushTip): FloatArray {
        if (points.isEmpty() || widths.isEmpty()) return FloatArray(0)
        val widest = widths.maxOrNull() ?: 0f
        if (points.size == 1 || DrawGeometry.length(points) < widest * DOT_RATIO) {
            return dot(points.first(), widest / 2f)
        }
        val normals = normalsOf(points)
        val left = ArrayList<DrawVec>(points.size)
        val right = ArrayList<DrawVec>(points.size)
        for (index in points.indices) {
            val center = DrawVec(points[index].x, points[index].y)
            val offset = normals[index] * (widths[index] / 2f)
            left.add(center + offset)
            right.add(center - offset)
        }
        val builder = DrawPathBuilder()
        builder.smoothThrough(left, connect = false)
        cap(builder, points.last(), normals.last(), widths.last() / 2f, right.last(), tip)
        builder.smoothThrough(right.asReversed(), connect = true)
        cap(builder, points.first(), normals.first() * -1f, widths.first() / 2f, left.first(), tip)
        return builder.close().build()
    }

    fun dot(point: DrawPoint, radius: Float): FloatArray =
        DrawPathBuilder().ellipse(point.x, point.y, radius, radius).build()

    private fun cap(
        builder: DrawPathBuilder,
        point: DrawPoint,
        normal: DrawVec,
        radius: Float,
        target: DrawVec,
        tip: DrawBrushTip
    ) {
        if (tip == DrawBrushTip.FLAT || radius <= 0f) {
            builder.lineTo(target.x, target.y)
            return
        }
        builder.arc(point.x, point.y, radius, atan2(normal.y, normal.x), -HALF_TURN, connect = true)
    }

    private fun normalsOf(points: List<DrawPoint>): List<DrawVec> {
        val normals = ArrayList<DrawVec>(points.size)
        var last = DrawVec(0f, 1f)
        for (index in points.indices) {
            val before = points[maxOf(index - 1, 0)]
            val after = points[minOf(index + 1, points.size - 1)]
            val tangent = DrawVec(after.x - before.x, after.y - before.y).normalized()
            val normal = if (tangent == DrawVec.ZERO) last else tangent.perpendicular()
            normals.add(normal)
            last = normal
        }
        return normals
    }
}
