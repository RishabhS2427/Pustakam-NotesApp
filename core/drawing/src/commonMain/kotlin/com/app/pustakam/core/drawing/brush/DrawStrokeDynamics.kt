package com.app.pustakam.core.drawing.brush

import com.app.pustakam.core.drawing.geometry.DrawGeometry
import com.app.pustakam.core.drawing.model.DrawBrush
import com.app.pustakam.core.drawing.model.DrawPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object DrawStrokeDynamics {

    private const val FAST_VELOCITY = 1.2f

    private const val VELOCITY_EASING = 0.35f

    private const val VELOCITY_THINNING = 0.6f

    private const val TILT_WIDENING = 1.5f

    private const val TILT_FADING = 0.45f

    private const val MAX_TILT = 1.5707964f

    private const val TAPER_FLOOR = 0.15f

    private const val MIN_WIDTH = 0.05f

    fun widths(points: List<DrawPoint>, brush: DrawBrush, profile: DrawBrushProfile, complete: Boolean): FloatArray {
        val count = points.size
        val widths = FloatArray(count)
        if (count == 0) return widths
        val size = max(brush.size, MIN_WIDTH)
        val travelled = cumulativeDistances(points)
        val total = travelled[count - 1]
        var velocity = 0f
        for (index in 0 until count) {
            val point = points[index]
            velocity = easedVelocity(points, index, size, velocity)
            val factor = pressureFactor(point.pressure, brush.pressureSensitivity) *
                velocityFactor(velocity, brush.velocitySensitivity) *
                tiltFactor(point.tilt, brush.tiltSensitivity) *
                nibFactor(points, index, profile) *
                taperFactor(travelled[index], profile.taperStart * size) *
                (if (complete) taperFactor(total - travelled[index], profile.taperEnd * size) else 1f)
            widths[index] = max(size * max(factor, profile.minWidthRatio), MIN_WIDTH)
        }
        return widths
    }

    fun alphas(points: List<DrawPoint>, brush: DrawBrush): FloatArray {
        val alphas = FloatArray(points.size)
        for (index in points.indices) {
            val point = points[index]
            val pressure = DrawGeometry.lerp(1f, min(0.25f + 1.5f * point.pressure, 1f), brush.pressureSensitivity)
            val tilt = 1f - brush.tiltSensitivity * TILT_FADING * (point.tilt / MAX_TILT).coerceIn(0f, 1f)
            alphas[index] = (brush.flow * pressure * tilt).coerceIn(0f, 1f)
        }
        return alphas
    }

    fun cumulativeDistances(points: List<DrawPoint>): FloatArray {
        val distances = FloatArray(points.size)
        for (index in 1 until points.size) {
            distances[index] = distances[index - 1] + points[index - 1].distanceTo(points[index])
        }
        return distances
    }

    private fun pressureFactor(pressure: Float, sensitivity: Float): Float =
        DrawGeometry.lerp(1f, 0.2f + 1.6f * pressure.coerceIn(0f, 1f), sensitivity)

    private fun velocityFactor(velocity: Float, sensitivity: Float): Float =
        1f - sensitivity * VELOCITY_THINNING * DrawGeometry.smoothstep(0f, FAST_VELOCITY, velocity)

    private fun tiltFactor(tilt: Float, sensitivity: Float): Float =
        1f + sensitivity * TILT_WIDENING * (tilt / MAX_TILT).coerceIn(0f, 1f)

    private fun taperFactor(distance: Float, length: Float): Float {
        if (length <= 0f) return 1f
        return TAPER_FLOOR + (1f - TAPER_FLOOR) * DrawGeometry.smoothstep(0f, length, distance)
    }

    private fun nibFactor(points: List<DrawPoint>, index: Int, profile: DrawBrushProfile): Float {
        if (!profile.hasNib || points.size < 2) return 1f
        val before = points[max(index - 1, 0)]
        val after = points[min(index + 1, points.size - 1)]
        val angle = atan2(after.y - before.y, after.x - before.x)
        return profile.nibMinRatio + (1f - profile.nibMinRatio) * abs(sin(angle - profile.nibAngle))
    }

    private fun easedVelocity(points: List<DrawPoint>, index: Int, size: Float, previous: Float): Float {
        if (index == 0) return 0f
        val from = points[index - 1]
        val to = points[index]
        val elapsed = to.time - from.time
        if (elapsed <= 0L) return previous
        val raw = from.distanceTo(to) / size / elapsed.toFloat()
        return previous + (raw - previous) * VELOCITY_EASING
    }
}
