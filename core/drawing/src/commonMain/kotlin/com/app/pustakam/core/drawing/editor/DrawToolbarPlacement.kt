package com.app.pustakam.core.drawing.editor

data class DrawToolbarPlacement(
    val centerX: Float = 0f,
    val centerY: Float = 0f,
    val vertical: Boolean = false,
    val placed: Boolean = false
) {
    fun left(width: Float): Float = centerX - width / 2f

    fun top(height: Float): Float = centerY - height / 2f
}

object DrawToolbarLayout {

    const val MARGIN = 8f

    fun fitted(
        placement: DrawToolbarPlacement,
        width: Float,
        height: Float,
        areaWidth: Float,
        areaHeight: Float
    ): DrawToolbarPlacement {
        if (width <= 0f || height <= 0f || areaWidth <= 0f || areaHeight <= 0f) return placement
        val origin = if (placement.placed) placement else home(placement.vertical, width, height, areaWidth, areaHeight)
        return clamped(origin, width, height, areaWidth, areaHeight)
    }

    fun moved(
        placement: DrawToolbarPlacement,
        deltaX: Float,
        deltaY: Float,
        width: Float,
        height: Float,
        areaWidth: Float,
        areaHeight: Float
    ): DrawToolbarPlacement {
        val start = fitted(placement, width, height, areaWidth, areaHeight)
        if (!start.placed) return start
        return clamped(start.copy(centerX = start.centerX + deltaX, centerY = start.centerY + deltaY), width, height, areaWidth, areaHeight)
    }

    fun rotated(placement: DrawToolbarPlacement): DrawToolbarPlacement = placement.copy(vertical = !placement.vertical)

    fun maxLength(areaLength: Float): Float = (areaLength - MARGIN * 2f).coerceAtLeast(0f)

    private fun home(
        vertical: Boolean,
        width: Float,
        height: Float,
        areaWidth: Float,
        areaHeight: Float
    ): DrawToolbarPlacement = if (vertical) {
        DrawToolbarPlacement(areaWidth - MARGIN - width / 2f, areaHeight / 2f, vertical = true)
    } else {
        DrawToolbarPlacement(areaWidth / 2f, areaHeight - MARGIN - height / 2f, vertical = false)
    }

    private fun clamped(
        placement: DrawToolbarPlacement,
        width: Float,
        height: Float,
        areaWidth: Float,
        areaHeight: Float
    ): DrawToolbarPlacement = placement.copy(
        centerX = axis(placement.centerX, width, areaWidth),
        centerY = axis(placement.centerY, height, areaHeight),
        placed = true
    )

    private fun axis(center: Float, length: Float, area: Float): Float {
        val low = MARGIN + length / 2f
        val high = area - MARGIN - length / 2f
        return if (low > high) area / 2f else center.coerceIn(low, high)
    }
}
