package com.app.pustakam.core.drawing.codec

import com.app.pustakam.core.drawing.model.DrawPoint
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.roundToInt

@OptIn(ExperimentalEncodingApi::class)
object DrawPointCodec {

    private const val POSITION_SCALE = 10f

    private const val UNIT_SCALE = 100f

    private const val HAS_PRESSURE = 1

    private const val HAS_TILT = 2

    private const val HAS_AZIMUTH = 4

    private const val HAS_TIME = 8

    private const val MIN_BYTES_PER_POINT = 2

    fun encode(points: List<DrawPoint>): String {
        if (points.isEmpty()) return ""
        val flags = flagsOf(points)
        val sink = ByteSink(points.size * 4 + 8)
        sink.unsigned(points.size)
        sink.unsigned(flags)
        var x = 0
        var y = 0
        var pressure = 0
        var tilt = 0
        var azimuth = 0
        var time = points.first().time
        for (point in points) {
            val nextX = quantize(point.x, POSITION_SCALE)
            val nextY = quantize(point.y, POSITION_SCALE)
            sink.signed(nextX - x)
            sink.signed(nextY - y)
            x = nextX
            y = nextY
            if (flags and HAS_PRESSURE != 0) {
                val next = quantize(point.pressure, UNIT_SCALE)
                sink.signed(next - pressure)
                pressure = next
            }
            if (flags and HAS_TILT != 0) {
                val next = quantize(point.tilt, UNIT_SCALE)
                sink.signed(next - tilt)
                tilt = next
            }
            if (flags and HAS_AZIMUTH != 0) {
                val next = quantize(point.azimuth, UNIT_SCALE)
                sink.signed(next - azimuth)
                azimuth = next
            }
            if (flags and HAS_TIME != 0) {
                sink.unsigned((point.time - time).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
                time = point.time
            }
        }
        return Base64.Default.encode(sink.bytes())
    }

    fun decode(text: String): List<DrawPoint> {
        if (text.isBlank()) return emptyList()
        val source = ByteSource(Base64.Default.decode(text))
        val count = source.unsigned()
        if (count <= 0 || count * MIN_BYTES_PER_POINT > source.size) return emptyList()
        val flags = source.unsigned()
        val points = ArrayList<DrawPoint>(count)
        var x = 0
        var y = 0
        var pressure = if (flags and HAS_PRESSURE != 0) 0 else quantize(DrawPoint.DEFAULT_PRESSURE, UNIT_SCALE)
        var tilt = 0
        var azimuth = 0
        var time = 0L
        repeat(count) {
            x += source.signed()
            y += source.signed()
            if (flags and HAS_PRESSURE != 0) pressure += source.signed()
            if (flags and HAS_TILT != 0) tilt += source.signed()
            if (flags and HAS_AZIMUTH != 0) azimuth += source.signed()
            if (flags and HAS_TIME != 0) time += source.unsigned()
            points.add(
                DrawPoint(
                    x = x / POSITION_SCALE,
                    y = y / POSITION_SCALE,
                    pressure = pressure / UNIT_SCALE,
                    tilt = tilt / UNIT_SCALE,
                    azimuth = azimuth / UNIT_SCALE,
                    time = time
                )
            )
        }
        return points
    }

    private fun flagsOf(points: List<DrawPoint>): Int {
        var flags = 0
        val start = points.first().time
        for (point in points) {
            if (point.pressure != DrawPoint.DEFAULT_PRESSURE) flags = flags or HAS_PRESSURE
            if (point.tilt != 0f) flags = flags or HAS_TILT
            if (point.azimuth != 0f) flags = flags or HAS_AZIMUTH
            if (point.time != start) flags = flags or HAS_TIME
        }
        return flags
    }

    private fun quantize(value: Float, scale: Float): Int =
        if (value.isFinite()) (value * scale).roundToInt() else 0

    private class ByteSink(capacity: Int) {
        private var data = ByteArray(capacity.coerceAtLeast(16))
        private var size = 0

        fun signed(value: Int) = unsigned((value shl 1) xor (value shr 31))

        fun unsigned(value: Int) {
            var remaining = value
            while (true) {
                if (size + 1 > data.size) data = data.copyOf(data.size * 2)
                if (remaining and 0x7F.inv() == 0) {
                    data[size++] = remaining.toByte()
                    return
                }
                data[size++] = ((remaining and 0x7F) or 0x80).toByte()
                remaining = remaining ushr 7
            }
        }

        fun bytes(): ByteArray = data.copyOf(size)
    }

    private class ByteSource(private val data: ByteArray) {
        private var index = 0

        val size: Int get() = data.size

        fun signed(): Int {
            val raw = unsigned()
            return (raw ushr 1) xor -(raw and 1)
        }

        fun unsigned(): Int {
            var result = 0
            var shift = 0
            while (index < data.size && shift < 35) {
                val byte = data[index++].toInt()
                result = result or ((byte and 0x7F) shl shift)
                if (byte and 0x80 == 0) return result
                shift += 7
            }
            return result
        }
    }
}
