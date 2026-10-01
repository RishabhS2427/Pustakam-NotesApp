package com.app.pustakam.core.drawing.geometry

import kotlin.math.hypot

data class DrawVec(val x: Float, val y: Float) {

    val length: Float get() = hypot(x, y)

    operator fun plus(other: DrawVec): DrawVec = DrawVec(x + other.x, y + other.y)

    operator fun minus(other: DrawVec): DrawVec = DrawVec(x - other.x, y - other.y)

    operator fun times(factor: Float): DrawVec = DrawVec(x * factor, y * factor)

    fun dot(other: DrawVec): Float = x * other.x + y * other.y

    fun distanceTo(other: DrawVec): Float = hypot(other.x - x, other.y - y)

    fun normalized(): DrawVec {
        val size = length
        return if (size <= EPSILON) ZERO else DrawVec(x / size, y / size)
    }

    fun perpendicular(): DrawVec = DrawVec(-y, x)

    fun midpoint(other: DrawVec): DrawVec = DrawVec((x + other.x) / 2f, (y + other.y) / 2f)

    companion object {
        const val EPSILON = 1e-6f

        val ZERO: DrawVec = DrawVec(0f, 0f)
    }
}
