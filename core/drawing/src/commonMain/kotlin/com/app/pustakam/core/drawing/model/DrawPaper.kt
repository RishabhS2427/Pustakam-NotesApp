package com.app.pustakam.core.drawing.model

import com.app.pustakam.core.richtext.master.model.CanvasRect
import kotlin.math.roundToInt

enum class DrawPaperSize { INFINITE, SCREEN, A0, A1, A2, A3, A4, LETTER, LEGAL, CUSTOM }

enum class DrawOrientation { PORTRAIT, LANDSCAPE }

enum class DrawPattern { NONE, GRID, RULED, DOTTED, ISOMETRIC, PERSPECTIVE }

enum class DrawUnit { PX, PT, MM, CM, INCH, METER }

enum class DrawTexture { NONE, PAPER, CANVAS, KRAFT }

object DrawUnits {

    const val POINTS_PER_INCH = 72f

    const val MM_PER_INCH = 25.4f

    fun toPoints(value: Float, unit: DrawUnit, dpi: Int): Float = when (unit) {
        DrawUnit.PX -> value * POINTS_PER_INCH / dpi.coerceAtLeast(1)
        DrawUnit.PT -> value
        DrawUnit.MM -> value * POINTS_PER_INCH / MM_PER_INCH
        DrawUnit.CM -> value * 10f * POINTS_PER_INCH / MM_PER_INCH
        DrawUnit.INCH -> value * POINTS_PER_INCH
        DrawUnit.METER -> value * 1000f * POINTS_PER_INCH / MM_PER_INCH
    }

    fun fromPoints(points: Float, unit: DrawUnit, dpi: Int): Float = when (unit) {
        DrawUnit.PX -> points * dpi.coerceAtLeast(1) / POINTS_PER_INCH
        DrawUnit.PT -> points
        DrawUnit.MM -> points * MM_PER_INCH / POINTS_PER_INCH
        DrawUnit.CM -> points * MM_PER_INCH / POINTS_PER_INCH / 10f
        DrawUnit.INCH -> points / POINTS_PER_INCH
        DrawUnit.METER -> points * MM_PER_INCH / POINTS_PER_INCH / 1000f
    }

    fun symbol(unit: DrawUnit): String = when (unit) {
        DrawUnit.PX -> "px"
        DrawUnit.PT -> "pt"
        DrawUnit.MM -> "mm"
        DrawUnit.CM -> "cm"
        DrawUnit.INCH -> "in"
        DrawUnit.METER -> "m"
    }

    fun units(): List<DrawUnit> = DrawUnit.entries
}

data class DrawPaper(
    val size: DrawPaperSize,
    val orientation: DrawOrientation,
    val width: Float,
    val height: Float,
    val unit: DrawUnit,
    val dpi: Int,
    val background: DrawColor,
    val transparent: Boolean,
    val pattern: DrawPattern,
    val spacing: Float,
    val patternColor: DrawColor,
    val texture: DrawTexture
) {
    val isInfinite: Boolean get() = size == DrawPaperSize.INFINITE

    val bounds: CanvasRect get() = CanvasRect(0f, 0f, width, height)

    val aspect: Float get() = if (isInfinite || width <= 0f || height <= 0f) DEFAULT_ASPECT else height / width

    val pixelWidth: Int get() = DrawUnits.fromPoints(width, DrawUnit.PX, dpi).roundToInt()

    val pixelHeight: Int get() = DrawUnits.fromPoints(height, DrawUnit.PX, dpi).roundToInt()

    fun widthIn(target: DrawUnit): Float = DrawUnits.fromPoints(width, target, dpi)

    fun heightIn(target: DrawUnit): Float = DrawUnits.fromPoints(height, target, dpi)

    fun withSize(value: DrawPaperSize): DrawPaper {
        val standard = standardSize(value) ?: return copy(size = value)
        return oriented(copy(size = value, width = standard[0], height = standard[1]), orientation)
    }

    fun withOrientation(value: DrawOrientation): DrawPaper = oriented(copy(orientation = value), value)

    fun withCustomSize(customWidth: Float, customHeight: Float, customUnit: DrawUnit): DrawPaper {
        val w = DrawUnits.toPoints(customWidth, customUnit, dpi).coerceIn(MIN_EDGE, MAX_EDGE)
        val h = DrawUnits.toPoints(customHeight, customUnit, dpi).coerceIn(MIN_EDGE, MAX_EDGE)
        return copy(
            size = DrawPaperSize.CUSTOM,
            unit = customUnit,
            width = w,
            height = h,
            orientation = if (w > h) DrawOrientation.LANDSCAPE else DrawOrientation.PORTRAIT
        )
    }

    fun withScreenSize(screenWidth: Float, screenHeight: Float): DrawPaper = copy(
        size = DrawPaperSize.SCREEN,
        width = screenWidth.coerceIn(MIN_EDGE, MAX_EDGE),
        height = screenHeight.coerceIn(MIN_EDGE, MAX_EDGE),
        orientation = if (screenWidth > screenHeight) DrawOrientation.LANDSCAPE else DrawOrientation.PORTRAIT
    )

    fun withDpi(value: Int): DrawPaper = copy(dpi = value.coerceIn(MIN_DPI, MAX_DPI))

    fun withUnit(value: DrawUnit): DrawPaper = copy(unit = value)

    fun withBackground(value: DrawColor): DrawPaper = copy(background = value, transparent = false)

    fun withTransparent(value: Boolean): DrawPaper = copy(transparent = value)

    fun withPattern(value: DrawPattern): DrawPaper = copy(pattern = value)

    fun withSpacing(value: Float): DrawPaper = copy(spacing = value.coerceIn(MIN_SPACING, MAX_SPACING))

    fun withPatternColor(value: DrawColor): DrawPaper = copy(patternColor = value)

    fun withTexture(value: DrawTexture): DrawPaper = copy(texture = value)

    companion object {
        const val DEFAULT_DPI = 150
        const val MIN_DPI = 36
        const val MAX_DPI = 1200
        const val DEFAULT_SPACING = 24f
        const val MIN_SPACING = 4f
        const val MAX_SPACING = 400f
        const val MIN_EDGE = 16f
        const val MAX_EDGE = 20000f
        const val DEFAULT_ASPECT = 1.414f

        private val PATTERN_COLOR = DrawColor(0x33806A55L)

        fun standardSize(size: DrawPaperSize): FloatArray? = when (size) {
            DrawPaperSize.A0 -> floatArrayOf(2383.937f, 3370.394f)
            DrawPaperSize.A1 -> floatArrayOf(1683.780f, 2383.937f)
            DrawPaperSize.A2 -> floatArrayOf(1190.551f, 1683.780f)
            DrawPaperSize.A3 -> floatArrayOf(841.890f, 1190.551f)
            DrawPaperSize.A4 -> floatArrayOf(595.276f, 841.890f)
            DrawPaperSize.LETTER -> floatArrayOf(612f, 792f)
            DrawPaperSize.LEGAL -> floatArrayOf(612f, 1008f)
            else -> null
        }

        fun sizes(): List<DrawPaperSize> = DrawPaperSize.entries

        fun standard(size: DrawPaperSize, orientation: DrawOrientation): DrawPaper =
            blank(DrawPaperSize.A4, 595.276f, 841.890f).withSize(size).withOrientation(orientation)

        fun infinite(): DrawPaper = blank(DrawPaperSize.INFINITE, 0f, 0f).copy(transparent = true)

        fun screen(width: Float, height: Float): DrawPaper =
            blank(DrawPaperSize.SCREEN, width, height).withScreenSize(width, height)

        private fun blank(size: DrawPaperSize, width: Float, height: Float): DrawPaper = DrawPaper(
            size = size,
            orientation = DrawOrientation.PORTRAIT,
            width = width,
            height = height,
            unit = DrawUnit.PT,
            dpi = DEFAULT_DPI,
            background = DrawColor.WHITE,
            transparent = false,
            pattern = DrawPattern.NONE,
            spacing = DEFAULT_SPACING,
            patternColor = PATTERN_COLOR,
            texture = DrawTexture.NONE
        )

        private fun oriented(paper: DrawPaper, orientation: DrawOrientation): DrawPaper {
            val landscape = paper.width > paper.height
            val wantsLandscape = orientation == DrawOrientation.LANDSCAPE
            val swapped = if (landscape != wantsLandscape) paper.copy(width = paper.height, height = paper.width) else paper
            return swapped.copy(orientation = orientation)
        }
    }
}
