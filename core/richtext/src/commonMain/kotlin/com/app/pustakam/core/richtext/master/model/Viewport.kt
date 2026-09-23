package com.app.pustakam.core.richtext.master.model


data class CanvasRect(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f
) {
    val right: Float get() = x + width

    val bottom: Float get() = y + height

    val centerX: Float get() = x + width / 2f

    val centerY: Float get() = y + height / 2f

    fun intersects(other: CanvasRect): Boolean =
        x < other.right && other.x < right && y < other.bottom && other.y < bottom

    fun overlapArea(other: CanvasRect): Float {
        val overlapWidth = minOf(right, other.right) - maxOf(x, other.x)
        val overlapHeight = minOf(bottom, other.bottom) - maxOf(y, other.y)
        return if (overlapWidth > 0f && overlapHeight > 0f) overlapWidth * overlapHeight else 0f
    }

    fun contains(pointX: Float, pointY: Float): Boolean =
        pointX in x..right && pointY in y..bottom

    fun translated(deltaX: Float, deltaY: Float): CanvasRect =
        copy(x = x + deltaX, y = y + deltaY)

    fun inflated(by: Float): CanvasRect =
        CanvasRect(x - by, y - by, width + by * 2f, height + by * 2f)

    fun union(other: CanvasRect): CanvasRect {
        val left = minOf(x, other.x)
        val top = minOf(y, other.y)
        return CanvasRect(left, top, maxOf(right, other.right) - left, maxOf(bottom, other.bottom) - top)
    }
}

data class Viewport(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = DEFAULT_SCALE,
    val widthPx: Float = 0f,
    val heightPx: Float = 0f
) {
    val visibleRect: CanvasRect
        get() = CanvasRect(
            x = -offsetX / scale,
            y = -offsetY / scale,
            width = if (scale <= 0f) 0f else widthPx / scale,
            height = if (scale <= 0f) 0f else heightPx / scale
        )

    val percent: Int get() = (scale * 100f).toInt()

    fun withSize(width: Float, height: Float): Viewport = copy(widthPx = width, heightPx = height)

    fun panned(deltaX: Float, deltaY: Float): Viewport =
        copy(offsetX = offsetX + deltaX, offsetY = offsetY + deltaY)

    fun zoomed(factor: Float, focusX: Float, focusY: Float): Viewport {
        val target = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        if (target == scale) return this
        val ratio = target / scale
        return copy(
            offsetX = focusX - (focusX - offsetX) * ratio,
            offsetY = focusY - (focusY - offsetY) * ratio,
            scale = target
        )
    }

    fun scaledTo(target: Float, focusX: Float, focusY: Float): Viewport {
        val safe = target.coerceIn(MIN_SCALE, MAX_SCALE)
        if (safe == scale) return this
        return zoomed(safe / scale, focusX, focusY)
    }

    fun focusedOn(rect: CanvasRect, padding: Float = FOCUS_PADDING): Viewport {
        if (rect.width <= 0f || rect.height <= 0f || widthPx <= 0f || heightPx <= 0f) return this
        val target = minOf(
            widthPx / (rect.width + padding * 2f),
            heightPx / (rect.height + padding * 2f)
        ).coerceIn(MIN_SCALE, MAX_SCALE)
        return copy(
            scale = target,
            offsetX = widthPx / 2f - rect.centerX * target,
            offsetY = heightPx / 2f - rect.centerY * target
        )
    }

    fun focusedOnTop(rect: CanvasRect, topInset: Float = 0f, padding: Float = FOCUS_PADDING): Viewport {
        if (rect.width <= 0f || widthPx <= 0f || heightPx <= 0f) return this
        val target = (widthPx / (rect.width + padding * 2f)).coerceIn(MIN_SCALE, MAX_SCALE)
        return copy(
            scale = target,
            offsetX = widthPx / 2f - rect.centerX * target,
            offsetY = topInset - rect.y * target
        )
    }

    fun toCanvasX(screenX: Float): Float = (screenX - offsetX) / scale

    fun toCanvasY(screenY: Float): Float = (screenY - offsetY) / scale

    fun toScreenX(canvasX: Float): Float = canvasX * scale + offsetX

    fun toScreenY(canvasY: Float): Float = canvasY * scale + offsetY

    companion object {
        const val MIN_SCALE = 0.1f
        const val MAX_SCALE = 20f
        const val DEFAULT_SCALE = 1f
        const val FOCUS_PADDING = 32f

        val zoomStops: List<Float> =
            listOf(0.1f, 0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 4f, 8f, 20f)

        fun nextZoomStop(current: Float): Float =
            zoomStops.firstOrNull { it > current + 0.001f } ?: MAX_SCALE

        fun previousZoomStop(current: Float): Float =
            zoomStops.lastOrNull { it < current - 0.001f } ?: MIN_SCALE
    }
}
