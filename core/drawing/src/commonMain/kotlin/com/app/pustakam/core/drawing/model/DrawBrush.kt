package com.app.pustakam.core.drawing.model

enum class DrawBrushKind {
    PENCIL,
    BALLPOINT,
    FOUNTAIN,
    MARKER,
    BRUSH_PEN,
    TECHNICAL,
    CHALK,
    CRAYON,
    CHARCOAL,
    AIRBRUSH,
    HIGHLIGHTER
}

data class DrawBrush(
    val kind: DrawBrushKind,
    val size: Float,
    val opacity: Float,
    val flow: Float,
    val hardness: Float,
    val spacing: Float,
    val smoothing: Float,
    val pressureSensitivity: Float,
    val tiltSensitivity: Float,
    val velocitySensitivity: Float
) {
    fun withSize(value: Float): DrawBrush = copy(size = value.coerceIn(MIN_SIZE, MAX_SIZE))

    fun withOpacity(value: Float): DrawBrush = copy(opacity = value.coerceIn(MIN_OPACITY, 1f))

    fun withFlow(value: Float): DrawBrush = copy(flow = value.coerceIn(MIN_OPACITY, 1f))

    fun withHardness(value: Float): DrawBrush = copy(hardness = value.coerceIn(0f, 1f))

    fun withSpacing(value: Float): DrawBrush = copy(spacing = value.coerceIn(MIN_SPACING, 1f))

    fun withSmoothing(value: Float): DrawBrush = copy(smoothing = value.coerceIn(0f, 1f))

    fun withPressureSensitivity(value: Float): DrawBrush = copy(pressureSensitivity = value.coerceIn(0f, 1f))

    fun withTiltSensitivity(value: Float): DrawBrush = copy(tiltSensitivity = value.coerceIn(0f, 1f))

    fun withVelocitySensitivity(value: Float): DrawBrush = copy(velocitySensitivity = value.coerceIn(0f, 1f))

    fun scaledBy(factor: Float): DrawBrush = copy(size = size * factor)

    companion object {
        const val MIN_SIZE = 0.3f
        const val MAX_SIZE = 200f
        const val MIN_OPACITY = 0.02f
        const val MIN_SPACING = 0.02f
    }
}
