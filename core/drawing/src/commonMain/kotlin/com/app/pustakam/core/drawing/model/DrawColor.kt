package com.app.pustakam.core.drawing.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class DrawHsl(val hue: Float, val saturation: Float, val lightness: Float, val opacity: Float)

data class DrawHsv(val hue: Float, val saturation: Float, val value: Float, val opacity: Float)

data class DrawColor(val argb: Long) {

    val alpha: Int get() = ((argb shr 24) and 0xFFL).toInt()

    val red: Int get() = ((argb shr 16) and 0xFFL).toInt()

    val green: Int get() = ((argb shr 8) and 0xFFL).toInt()

    val blue: Int get() = (argb and 0xFFL).toInt()

    val opacity: Float get() = alpha / 255f

    val argbInt: Int get() = argb.toInt()

    val isOpaque: Boolean get() = alpha == 0xFF

    fun withOpacity(value: Float): DrawColor = fromArgb(channel(value), red, green, blue)

    fun opaque(): DrawColor = fromArgb(0xFF, red, green, blue)

    fun hex(): String =
        if (isOpaque) "#${byteHex(red)}${byteHex(green)}${byteHex(blue)}" else hexWithAlpha()

    fun hexWithAlpha(): String = "#${byteHex(alpha)}${byteHex(red)}${byteHex(green)}${byteHex(blue)}"

    fun hsl(): DrawHsl {
        val r = red / 255f
        val g = green / 255f
        val b = blue / 255f
        val high = max(r, max(g, b))
        val low = min(r, min(g, b))
        val lightness = (high + low) / 2f
        val delta = high - low
        if (delta == 0f) return DrawHsl(0f, 0f, lightness, opacity)
        val saturation = if (lightness > 0.5f) delta / (2f - high - low) else delta / (high + low)
        return DrawHsl(hueOf(r, g, b, high, delta), saturation, lightness, opacity)
    }

    fun hsv(): DrawHsv {
        val r = red / 255f
        val g = green / 255f
        val b = blue / 255f
        val high = max(r, max(g, b))
        val low = min(r, min(g, b))
        val delta = high - low
        val saturation = if (high == 0f) 0f else delta / high
        val hue = if (delta == 0f) 0f else hueOf(r, g, b, high, delta)
        return DrawHsv(hue, saturation, high, opacity)
    }

    fun luminance(): Float = (0.299f * red + 0.587f * green + 0.114f * blue) / 255f

    fun isSimilarTo(other: DrawColor, tolerance: Int): Boolean =
        abs(red - other.red) + abs(green - other.green) + abs(blue - other.blue) +
            abs(alpha - other.alpha) <= tolerance

    companion object {
        val BLACK: DrawColor = DrawColor(0xFF000000L)

        val WHITE: DrawColor = DrawColor(0xFFFFFFFFL)

        val TRANSPARENT: DrawColor = DrawColor(0L)

        fun fromArgb(alpha: Int, red: Int, green: Int, blue: Int): DrawColor = DrawColor(
            (alpha.coerceIn(0, 255).toLong() shl 24) or
                (red.coerceIn(0, 255).toLong() shl 16) or
                (green.coerceIn(0, 255).toLong() shl 8) or
                blue.coerceIn(0, 255).toLong()
        )

        fun fromRgb(red: Int, green: Int, blue: Int): DrawColor = fromArgb(0xFF, red, green, blue)

        fun fromArgbInt(value: Int): DrawColor = DrawColor(value.toLong() and 0xFFFFFFFFL)

        fun fromHex(hex: String): DrawColor? {
            val digits = hex.trim().removePrefix("#")
            if (digits.isEmpty() || digits.any { !isHexDigit(it) }) return null
            return when (digits.length) {
                3 -> fromRgb(nibble(digits[0]) * 17, nibble(digits[1]) * 17, nibble(digits[2]) * 17)
                4 -> fromArgb(
                    nibble(digits[0]) * 17,
                    nibble(digits[1]) * 17,
                    nibble(digits[2]) * 17,
                    nibble(digits[3]) * 17
                )
                6 -> DrawColor(0xFF000000L or digits.toLong(16))
                8 -> DrawColor(digits.toLong(16))
                else -> null
            }
        }

        fun fromHsl(hue: Float, saturation: Float, lightness: Float, opacity: Float): DrawColor {
            val s = saturation.coerceIn(0f, 1f)
            val l = lightness.coerceIn(0f, 1f)
            val chroma = (1f - abs(2f * l - 1f)) * s
            return fromChroma(hue, chroma, l - chroma / 2f, opacity)
        }

        fun fromHsv(hue: Float, saturation: Float, value: Float, opacity: Float): DrawColor {
            val s = saturation.coerceIn(0f, 1f)
            val v = value.coerceIn(0f, 1f)
            val chroma = v * s
            return fromChroma(hue, chroma, v - chroma, opacity)
        }

        fun channel(value: Float): Int = (value.coerceIn(0f, 1f) * 255f).roundToInt()

        private fun fromChroma(hue: Float, chroma: Float, match: Float, opacity: Float): DrawColor {
            val sector = (((hue % 360f) + 360f) % 360f) / 60f
            val second = chroma * (1f - abs(sector % 2f - 1f))
            val rgb = when (sector.toInt()) {
                0 -> floatArrayOf(chroma, second, 0f)
                1 -> floatArrayOf(second, chroma, 0f)
                2 -> floatArrayOf(0f, chroma, second)
                3 -> floatArrayOf(0f, second, chroma)
                4 -> floatArrayOf(second, 0f, chroma)
                else -> floatArrayOf(chroma, 0f, second)
            }
            return fromArgb(
                channel(opacity),
                channel(rgb[0] + match),
                channel(rgb[1] + match),
                channel(rgb[2] + match)
            )
        }

        private fun hueOf(r: Float, g: Float, b: Float, high: Float, delta: Float): Float {
            val sector = when (high) {
                r -> ((g - b) / delta) % 6f
                g -> (b - r) / delta + 2f
                else -> (r - g) / delta + 4f
            }
            val degrees = sector * 60f
            return if (degrees < 0f) degrees + 360f else degrees
        }

        private fun isHexDigit(char: Char): Boolean =
            char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F'

        private fun nibble(char: Char): Int = char.digitToInt(16)

        private fun byteHex(value: Int): String = value.toString(16).uppercase().padStart(2, '0')
    }
}
