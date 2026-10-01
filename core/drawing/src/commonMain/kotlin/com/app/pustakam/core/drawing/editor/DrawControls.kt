package com.app.pustakam.core.drawing.editor

import com.app.pustakam.core.drawing.model.DrawBrush
import com.app.pustakam.core.drawing.model.DrawShapeKind
import com.app.pustakam.core.drawing.model.DrawShapeSpec
import com.app.pustakam.core.drawing.tool.DrawToolMode
import com.app.pustakam.core.drawing.tool.DrawToolSettings
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class DrawBrushControl { SIZE, OPACITY, FLOW, HARDNESS, SPACING, SMOOTHING, PRESSURE, TILT, VELOCITY }

enum class DrawShapeControl { SIDES, INNER_RATIO, CORNER_RADIUS, SWEEP }

enum class DrawPanel { NONE, BRUSH, ERASER, SHAPE, COLOR, PAPER, SETTINGS }

object DrawControls {

    private const val MAX_CORNER = 120f

    private const val MIN_INNER = 0.1f

    private const val MAX_INNER = 0.95f

    private const val MIN_SWEEP = 1f

    private const val MAX_SWEEP = 359f

    private const val MIN_PATTERN_SPACING = 8f

    private const val MAX_PATTERN_SPACING = 120f

    private val BASIC_BRUSH = listOf(DrawBrushControl.SIZE, DrawBrushControl.OPACITY)

    fun brushControls(mode: DrawToolMode): List<DrawBrushControl> =
        if (mode == DrawToolMode.ADVANCED) DrawBrushControl.entries else BASIC_BRUSH

    fun brushLabel(control: DrawBrushControl): String = when (control) {
        DrawBrushControl.SIZE -> "Size"
        DrawBrushControl.OPACITY -> "Opacity"
        DrawBrushControl.FLOW -> "Flow"
        DrawBrushControl.HARDNESS -> "Hardness"
        DrawBrushControl.SPACING -> "Spacing"
        DrawBrushControl.SMOOTHING -> "Smoothing"
        DrawBrushControl.PRESSURE -> "Pressure"
        DrawBrushControl.TILT -> "Tilt"
        DrawBrushControl.VELOCITY -> "Velocity"
    }

    fun brushValue(brush: DrawBrush, control: DrawBrushControl): Float = when (control) {
        DrawBrushControl.SIZE -> brush.size
        DrawBrushControl.OPACITY -> brush.opacity
        DrawBrushControl.FLOW -> brush.flow
        DrawBrushControl.HARDNESS -> brush.hardness
        DrawBrushControl.SPACING -> brush.spacing
        DrawBrushControl.SMOOTHING -> brush.smoothing
        DrawBrushControl.PRESSURE -> brush.pressureSensitivity
        DrawBrushControl.TILT -> brush.tiltSensitivity
        DrawBrushControl.VELOCITY -> brush.velocitySensitivity
    }

    fun brushWith(brush: DrawBrush, control: DrawBrushControl, value: Float): DrawBrush = when (control) {
        DrawBrushControl.SIZE -> brush.withSize(value)
        DrawBrushControl.OPACITY -> brush.withOpacity(value)
        DrawBrushControl.FLOW -> brush.withFlow(value)
        DrawBrushControl.HARDNESS -> brush.withHardness(value)
        DrawBrushControl.SPACING -> brush.withSpacing(value)
        DrawBrushControl.SMOOTHING -> brush.withSmoothing(value)
        DrawBrushControl.PRESSURE -> brush.withPressureSensitivity(value)
        DrawBrushControl.TILT -> brush.withTiltSensitivity(value)
        DrawBrushControl.VELOCITY -> brush.withVelocitySensitivity(value)
    }

    fun brushPosition(brush: DrawBrush, control: DrawBrushControl): Float {
        val value = brushValue(brush, control)
        return if (control == DrawBrushControl.SIZE) {
            curvedPosition(value, DrawBrush.MIN_SIZE, DrawBrush.MAX_SIZE)
        } else {
            value.coerceIn(0f, 1f)
        }
    }

    fun brushValueAt(control: DrawBrushControl, position: Float): Float =
        if (control == DrawBrushControl.SIZE) curvedValue(position, DrawBrush.MIN_SIZE, DrawBrush.MAX_SIZE)
        else position.coerceIn(0f, 1f)

    fun brushText(brush: DrawBrush, control: DrawBrushControl): String {
        val value = brushValue(brush, control)
        return if (control == DrawBrushControl.SIZE) sizeText(value) else percentText(value)
    }

    fun eraserPosition(settings: DrawToolSettings): Float =
        curvedPosition(settings.eraserSize, DrawToolSettings.MIN_ERASER, DrawToolSettings.MAX_ERASER)

    fun eraserValueAt(position: Float): Float =
        curvedValue(position, DrawToolSettings.MIN_ERASER, DrawToolSettings.MAX_ERASER)

    fun eraserText(settings: DrawToolSettings): String = sizeText(settings.eraserSize)

    fun shapeControls(kind: DrawShapeKind): List<DrawShapeControl> = when (kind) {
        DrawShapeKind.POLYGON -> listOf(DrawShapeControl.SIDES)
        DrawShapeKind.STAR -> listOf(DrawShapeControl.SIDES, DrawShapeControl.INNER_RATIO)
        DrawShapeKind.ROUNDED_RECTANGLE -> listOf(DrawShapeControl.CORNER_RADIUS)
        DrawShapeKind.ARC -> listOf(DrawShapeControl.SWEEP)
        else -> emptyList()
    }

    fun shapeLabel(control: DrawShapeControl): String = when (control) {
        DrawShapeControl.SIDES -> "Sides"
        DrawShapeControl.INNER_RATIO -> "Inner radius"
        DrawShapeControl.CORNER_RADIUS -> "Corner radius"
        DrawShapeControl.SWEEP -> "Sweep"
    }

    fun shapePosition(shape: DrawShapeSpec, control: DrawShapeControl): Float = when (control) {
        DrawShapeControl.SIDES -> linearPosition(shape.sides.toFloat(), DrawShapeSpec.MIN_SIDES.toFloat(), DrawShapeSpec.MAX_SIDES.toFloat())
        DrawShapeControl.INNER_RATIO -> linearPosition(shape.innerRatio, MIN_INNER, MAX_INNER)
        DrawShapeControl.CORNER_RADIUS -> linearPosition(shape.cornerRadius, 0f, MAX_CORNER)
        DrawShapeControl.SWEEP -> linearPosition(shape.sweep, MIN_SWEEP, MAX_SWEEP)
    }

    fun shapeWith(shape: DrawShapeSpec, control: DrawShapeControl, position: Float): DrawShapeSpec = when (control) {
        DrawShapeControl.SIDES -> shape.withSides(
            linearValue(position, DrawShapeSpec.MIN_SIDES.toFloat(), DrawShapeSpec.MAX_SIDES.toFloat()).roundToInt()
        )
        DrawShapeControl.INNER_RATIO -> shape.withInnerRatio(linearValue(position, MIN_INNER, MAX_INNER))
        DrawShapeControl.CORNER_RADIUS -> shape.withCornerRadius(linearValue(position, 0f, MAX_CORNER))
        DrawShapeControl.SWEEP -> shape.withSweep(linearValue(position, MIN_SWEEP, MAX_SWEEP))
    }

    fun shapeText(shape: DrawShapeSpec, control: DrawShapeControl): String = when (control) {
        DrawShapeControl.SIDES -> shape.sides.toString()
        DrawShapeControl.INNER_RATIO -> percentText(shape.innerRatio)
        DrawShapeControl.CORNER_RADIUS -> sizeText(shape.cornerRadius)
        DrawShapeControl.SWEEP -> "${shape.sweep.roundToInt()}°"
    }

    fun spacingPosition(spacing: Float): Float = curvedPosition(spacing, MIN_PATTERN_SPACING, MAX_PATTERN_SPACING)

    fun spacingValueAt(position: Float): Float = curvedValue(position, MIN_PATTERN_SPACING, MAX_PATTERN_SPACING)

    fun spacingText(spacing: Float): String = sizeText(spacing)

    fun sizeText(value: Float): String = "${decimalText(value)} pt"

    fun percentText(value: Float): String = "${(value * 100f).roundToInt()}%"

    fun decimalText(value: Float): String {
        val tenths = abs((value * 10f).roundToInt())
        val sign = if (value < 0f && tenths > 0) "-" else ""
        return if (tenths % 10 == 0) "$sign${tenths / 10}" else "$sign${tenths / 10}.${tenths % 10}"
    }

    private fun curvedPosition(value: Float, min: Float, max: Float): Float =
        sqrt(((value - min) / (max - min)).coerceIn(0f, 1f))

    private fun curvedValue(position: Float, min: Float, max: Float): Float {
        val p = position.coerceIn(0f, 1f)
        return min + (max - min) * p * p
    }

    private fun linearPosition(value: Float, min: Float, max: Float): Float =
        ((value - min) / (max - min)).coerceIn(0f, 1f)

    private fun linearValue(position: Float, min: Float, max: Float): Float =
        min + (max - min) * position.coerceIn(0f, 1f)
}
