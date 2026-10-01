package com.app.pustakam.core.drawing.model

import kotlin.math.hypot

enum class DrawPointerType { FINGER, STYLUS, MOUSE }

data class DrawPoint(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val tilt: Float,
    val azimuth: Float,
    val time: Long
) {
    fun movedTo(newX: Float, newY: Float): DrawPoint = copy(x = newX, y = newY)

    fun withPressure(value: Float): DrawPoint = copy(pressure = value.coerceIn(0f, 1f))

    fun distanceTo(other: DrawPoint): Float = hypot(other.x - x, other.y - y)

    companion object {
        const val DEFAULT_PRESSURE = 0.5f

        fun at(x: Float, y: Float): DrawPoint = DrawPoint(x, y, DEFAULT_PRESSURE, 0f, 0f, 0L)

        fun sample(
            x: Float,
            y: Float,
            pressure: Float,
            tilt: Float,
            azimuth: Float,
            time: Long
        ): DrawPoint = DrawPoint(x, y, pressure.coerceIn(0f, 1f), tilt, azimuth, time)
    }
}
