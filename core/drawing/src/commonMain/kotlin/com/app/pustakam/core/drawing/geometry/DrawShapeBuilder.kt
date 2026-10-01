package com.app.pustakam.core.drawing.geometry

import com.app.pustakam.core.drawing.model.DrawShapeKind
import com.app.pustakam.core.drawing.model.DrawShapeSpec
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object DrawShapeBuilder {

    private const val HALF_PI = (PI / 2.0).toFloat()

    private const val FULL_TURN = (PI * 2.0).toFloat()

    private const val ARROW_HEAD_ANGLE = 0.49f

    private const val ARROW_HEAD_WIDTHS = 4f

    private const val ARROW_HEAD_MIN = 10f

    fun path(spec: DrawShapeSpec, start: DrawVec, end: DrawVec, strokeWidth: Float): FloatArray {
        val builder = DrawPathBuilder()
        when (spec.kind) {
            DrawShapeKind.NONE -> Unit
            DrawShapeKind.LINE -> builder.moveTo(start.x, start.y).lineTo(end.x, end.y)
            DrawShapeKind.RECTANGLE -> builder.polygon(rectangleCorners(start, end), closed = true)
            DrawShapeKind.ROUNDED_RECTANGLE -> roundedRectangle(builder, start, end, spec.cornerRadius)
            DrawShapeKind.CIRCLE -> {
                val radius = start.distanceTo(end)
                builder.ellipse(start.x, start.y, radius, radius)
            }

            DrawShapeKind.ELLIPSE -> {
                val box = DrawGeometry.rectOf(start, end)
                builder.ellipse(box.centerX, box.centerY, box.width / 2f, box.height / 2f)
            }

            DrawShapeKind.TRIANGLE -> builder.polygon(triangle(start, end), closed = true)
            DrawShapeKind.POLYGON -> builder.polygon(polygon(start, end, spec.sides), closed = true)
            DrawShapeKind.STAR -> builder.polygon(star(start, end, spec.sides, spec.innerRatio), closed = true)
            DrawShapeKind.ARROW -> arrow(builder, start, end, strokeWidth)
            DrawShapeKind.ARC -> arc(builder, start, end, spec.sweep)
        }
        return builder.build()
    }

    fun rectangleCorners(start: DrawVec, end: DrawVec): List<DrawVec> {
        val box = DrawGeometry.rectOf(start, end)
        return listOf(
            DrawVec(box.x, box.y),
            DrawVec(box.right, box.y),
            DrawVec(box.right, box.bottom),
            DrawVec(box.x, box.bottom)
        )
    }

    fun triangle(start: DrawVec, end: DrawVec): List<DrawVec> {
        val box = DrawGeometry.rectOf(start, end)
        return listOf(DrawVec(box.centerX, box.y), DrawVec(box.right, box.bottom), DrawVec(box.x, box.bottom))
    }

    fun polygon(start: DrawVec, end: DrawVec, sides: Int): List<DrawVec> {
        val box = DrawGeometry.rectOf(start, end)
        val count = sides.coerceIn(DrawShapeSpec.MIN_SIDES, DrawShapeSpec.MAX_SIDES)
        return (0 until count).map { index ->
            val angle = -HALF_PI + index * FULL_TURN / count
            DrawVec(box.centerX + box.width / 2f * cos(angle), box.centerY + box.height / 2f * sin(angle))
        }
    }

    fun star(start: DrawVec, end: DrawVec, points: Int, innerRatio: Float): List<DrawVec> {
        val box = DrawGeometry.rectOf(start, end)
        val count = points.coerceIn(DrawShapeSpec.MIN_SIDES, DrawShapeSpec.MAX_SIDES)
        val ratio = innerRatio.coerceIn(0.1f, 0.95f)
        return (0 until count * 2).map { index ->
            val angle = -HALF_PI + index * FULL_TURN / (count * 2)
            val factor = if (index % 2 == 0) 1f else ratio
            DrawVec(
                box.centerX + box.width / 2f * factor * cos(angle),
                box.centerY + box.height / 2f * factor * sin(angle)
            )
        }
    }

    private fun roundedRectangle(builder: DrawPathBuilder, start: DrawVec, end: DrawVec, cornerRadius: Float) {
        val box = DrawGeometry.rectOf(start, end)
        val radius = min(cornerRadius, min(box.width, box.height) / 2f)
        if (radius <= 0f) {
            builder.polygon(rectangleCorners(start, end), closed = true)
            return
        }
        builder.moveTo(box.x + radius, box.y)
        builder.lineTo(box.right - radius, box.y)
        builder.arc(box.right - radius, box.y + radius, radius, -HALF_PI, HALF_PI, connect = true)
        builder.lineTo(box.right, box.bottom - radius)
        builder.arc(box.right - radius, box.bottom - radius, radius, 0f, HALF_PI, connect = true)
        builder.lineTo(box.x + radius, box.bottom)
        builder.arc(box.x + radius, box.bottom - radius, radius, HALF_PI, HALF_PI, connect = true)
        builder.lineTo(box.x, box.y + radius)
        builder.arc(box.x + radius, box.y + radius, radius, PI.toFloat(), HALF_PI, connect = true)
        builder.close()
    }

    private fun arrow(builder: DrawPathBuilder, start: DrawVec, end: DrawVec, strokeWidth: Float) {
        builder.moveTo(start.x, start.y).lineTo(end.x, end.y)
        val shaft = start.distanceTo(end)
        if (shaft <= DrawVec.EPSILON) return
        val head = min(max(strokeWidth * ARROW_HEAD_WIDTHS, ARROW_HEAD_MIN), shaft * 0.5f)
        val angle = atan2(end.y - start.y, end.x - start.x)
        val left = DrawVec(end.x - head * cos(angle - ARROW_HEAD_ANGLE), end.y - head * sin(angle - ARROW_HEAD_ANGLE))
        val right = DrawVec(end.x - head * cos(angle + ARROW_HEAD_ANGLE), end.y - head * sin(angle + ARROW_HEAD_ANGLE))
        builder.moveTo(left.x, left.y).lineTo(end.x, end.y).lineTo(right.x, right.y)
    }

    private fun arc(builder: DrawPathBuilder, start: DrawVec, end: DrawVec, sweepDegrees: Float) {
        val chord = start.distanceTo(end)
        if (chord <= DrawVec.EPSILON) return
        val sweep = sweepDegrees.coerceIn(1f, 359f) * PI.toFloat() / 180f
        val half = sweep / 2f
        val radius = chord / 2f / sin(half)
        val direction = (end - start).normalized()
        val normal = direction.perpendicular()
        val middle = start.midpoint(end)
        val center = middle + normal * (radius * cos(half))
        val startAngle = atan2(start.y - center.y, start.x - center.x)
        val bulgeAngle = atan2(-normal.y, -normal.x)
        val signed = if (angleDistance(startAngle + half, bulgeAngle) < angleDistance(startAngle - half, bulgeAngle)) sweep else -sweep
        builder.arc(center.x, center.y, abs(radius), startAngle, signed, connect = false)
    }

    private fun angleDistance(first: Float, second: Float): Float {
        val raw = abs(first - second) % FULL_TURN
        return if (raw > FULL_TURN / 2f) FULL_TURN - raw else raw
    }
}
