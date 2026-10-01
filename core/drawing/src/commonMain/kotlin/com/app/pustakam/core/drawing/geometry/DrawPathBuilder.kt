package com.app.pustakam.core.drawing.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

class DrawPathBuilder {

    private var data = FloatArray(INITIAL_CAPACITY)

    private var size = 0

    val isEmpty: Boolean get() = size == 0

    fun moveTo(x: Float, y: Float): DrawPathBuilder {
        ensure(3)
        data[size++] = MOVE
        data[size++] = x
        data[size++] = y
        return this
    }

    fun lineTo(x: Float, y: Float): DrawPathBuilder {
        ensure(3)
        data[size++] = LINE
        data[size++] = x
        data[size++] = y
        return this
    }

    fun quadTo(controlX: Float, controlY: Float, x: Float, y: Float): DrawPathBuilder {
        ensure(5)
        data[size++] = QUAD
        data[size++] = controlX
        data[size++] = controlY
        data[size++] = x
        data[size++] = y
        return this
    }

    fun cubicTo(
        control1X: Float,
        control1Y: Float,
        control2X: Float,
        control2Y: Float,
        x: Float,
        y: Float
    ): DrawPathBuilder {
        ensure(7)
        data[size++] = CUBIC
        data[size++] = control1X
        data[size++] = control1Y
        data[size++] = control2X
        data[size++] = control2Y
        data[size++] = x
        data[size++] = y
        return this
    }

    fun close(): DrawPathBuilder {
        ensure(1)
        data[size++] = CLOSE
        return this
    }

    fun ellipse(centerX: Float, centerY: Float, radiusX: Float, radiusY: Float): DrawPathBuilder {
        val kx = radiusX * KAPPA
        val ky = radiusY * KAPPA
        moveTo(centerX + radiusX, centerY)
        cubicTo(centerX + radiusX, centerY + ky, centerX + kx, centerY + radiusY, centerX, centerY + radiusY)
        cubicTo(centerX - kx, centerY + radiusY, centerX - radiusX, centerY + ky, centerX - radiusX, centerY)
        cubicTo(centerX - radiusX, centerY - ky, centerX - kx, centerY - radiusY, centerX, centerY - radiusY)
        cubicTo(centerX + kx, centerY - radiusY, centerX + radiusX, centerY - ky, centerX + radiusX, centerY)
        return close()
    }

    fun arc(
        centerX: Float,
        centerY: Float,
        radius: Float,
        startAngle: Float,
        sweep: Float,
        connect: Boolean
    ): DrawPathBuilder {
        val startX = centerX + radius * cos(startAngle)
        val startY = centerY + radius * sin(startAngle)
        if (connect) lineTo(startX, startY) else moveTo(startX, startY)
        if (abs(sweep) <= DrawVec.EPSILON || radius <= 0f) return this
        val segments = ceil(abs(sweep) / (PI.toFloat() / 2f)).toInt().coerceAtLeast(1)
        val step = sweep / segments
        val handle = 4f / 3f * tan(step / 4f) * radius
        var angle = startAngle
        repeat(segments) {
            val next = angle + step
            val fromX = centerX + radius * cos(angle)
            val fromY = centerY + radius * sin(angle)
            val toX = centerX + radius * cos(next)
            val toY = centerY + radius * sin(next)
            cubicTo(
                fromX - handle * sin(angle),
                fromY + handle * cos(angle),
                toX + handle * sin(next),
                toY - handle * cos(next),
                toX,
                toY
            )
            angle = next
        }
        return this
    }

    fun polygon(points: List<DrawVec>, closed: Boolean): DrawPathBuilder {
        if (points.isEmpty()) return this
        moveTo(points[0].x, points[0].y)
        for (index in 1 until points.size) lineTo(points[index].x, points[index].y)
        if (closed) close()
        return this
    }

    fun smoothThrough(points: List<DrawVec>, connect: Boolean): DrawPathBuilder {
        if (points.isEmpty()) return this
        if (connect) lineTo(points[0].x, points[0].y) else moveTo(points[0].x, points[0].y)
        if (points.size == 1) return this
        for (index in 1 until points.size - 1) {
            val current = points[index]
            val next = points[index + 1]
            quadTo(current.x, current.y, (current.x + next.x) / 2f, (current.y + next.y) / 2f)
        }
        val last = points.last()
        return lineTo(last.x, last.y)
    }

    fun build(): FloatArray = data.copyOf(size)

    private fun ensure(extra: Int) {
        if (size + extra <= data.size) return
        data = data.copyOf(maxOf(data.size * 2, size + extra))
    }

    companion object {
        const val MOVE = 0f
        const val LINE = 1f
        const val QUAD = 2f
        const val CUBIC = 3f
        const val CLOSE = 4f
        const val KAPPA = 0.5522848f
        private const val INITIAL_CAPACITY = 64

        fun strideOf(command: Float): Int = when (command) {
            MOVE, LINE -> 2
            QUAD -> 4
            CUBIC -> 6
            else -> 0
        }
    }
}
