package com.app.pustakam.core.drawing.brush

import com.app.pustakam.core.drawing.geometry.DrawGeometry
import com.app.pustakam.core.drawing.model.DrawBrush
import com.app.pustakam.core.drawing.model.DrawPoint
import kotlin.math.max

object DrawStampGenerator {

    const val STRIDE = 4

    private const val MIN_STEP = 0.25f

    private const val MAX_DABS = 40000

    fun dabs(
        points: List<DrawPoint>,
        widths: FloatArray,
        alphas: FloatArray,
        brush: DrawBrush,
        profile: DrawBrushProfile,
        seed: Int
    ): FloatArray {
        if (points.isEmpty()) return FloatArray(0)
        val random = DrawRandom(seed)
        val sink = DabSink()
        val first = points.first()
        sink.add(first.x, first.y, widths[0], alphas[0], profile, random)
        var carried = step(widths[0], brush)
        for (index in 0 until points.size - 1) {
            val from = points[index]
            val to = points[index + 1]
            val length = from.distanceTo(to)
            if (length <= 0f) continue
            var position = carried
            while (position <= length && sink.count < MAX_DABS) {
                val t = position / length
                val width = DrawGeometry.lerp(widths[index], widths[index + 1], t)
                val alpha = DrawGeometry.lerp(alphas[index], alphas[index + 1], t)
                sink.add(
                    DrawGeometry.lerp(from.x, to.x, t),
                    DrawGeometry.lerp(from.y, to.y, t),
                    width,
                    alpha,
                    profile,
                    random
                )
                position += step(width, brush)
            }
            carried = position - length
        }
        return sink.build()
    }

    private fun step(width: Float, brush: DrawBrush): Float = max(width * brush.spacing, MIN_STEP)

    private class DabSink {
        private var data = FloatArray(STRIDE * 64)
        var count = 0
            private set

        fun add(x: Float, y: Float, width: Float, alpha: Float, profile: DrawBrushProfile, random: DrawRandom) {
            val radius = width / 2f * (1f + profile.sizeJitter * random.nextSigned())
            val scatterX = profile.scatter * width * random.nextSigned()
            val scatterY = profile.scatter * width * random.nextSigned()
            val fade = 1f - profile.alphaJitter * random.nextFloat()
            if (data.size < (count + 1) * STRIDE) data = data.copyOf(data.size * 2)
            val offset = count * STRIDE
            data[offset] = x + scatterX
            data[offset + 1] = y + scatterY
            data[offset + 2] = max(radius, 0.05f)
            data[offset + 3] = (alpha * fade).coerceIn(0f, 1f)
            count++
        }

        fun build(): FloatArray = data.copyOf(count * STRIDE)
    }
}
