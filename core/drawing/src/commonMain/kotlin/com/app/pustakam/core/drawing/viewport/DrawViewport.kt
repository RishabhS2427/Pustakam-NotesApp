package com.app.pustakam.core.drawing.viewport

import com.app.pustakam.core.drawing.geometry.DrawGeometry
import com.app.pustakam.core.drawing.geometry.DrawMatrix
import com.app.pustakam.core.drawing.geometry.DrawVec
import com.app.pustakam.core.richtext.master.model.CanvasRect
import com.app.pustakam.core.richtext.master.model.Viewport
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

data class DrawViewport(val base: Viewport, val rotation: Float) {

    val scale: Float get() = base.scale

    val width: Float get() = base.widthPx

    val height: Float get() = base.heightPx

    val isMeasured: Boolean get() = width > 0f && height > 0f

    val zoomPercent: Int get() = base.percent

    val rotationDegrees: Float get() = normalized(rotation) * DEGREES_PER_RADIAN

    private val centerX: Float get() = width / 2f

    private val centerY: Float get() = height / 2f

    fun matrix(): DrawMatrix {
        val cosine = cos(rotation)
        val sine = sin(rotation)
        val s = base.scale
        val shiftX = base.offsetX - centerX
        val shiftY = base.offsetY - centerY
        return DrawMatrix(
            a = s * cosine,
            b = s * sine,
            c = -s * sine,
            d = s * cosine,
            tx = cosine * shiftX - sine * shiftY + centerX,
            ty = sine * shiftX + cosine * shiftY + centerY
        )
    }

    fun toDocument(viewX: Float, viewY: Float): DrawVec {
        val upright = unrotated(viewX, viewY)
        return DrawVec(base.toCanvasX(upright.x), base.toCanvasY(upright.y))
    }

    fun toView(documentX: Float, documentY: Float): DrawVec = matrix().map(documentX, documentY)

    fun resized(newWidth: Float, newHeight: Float): DrawViewport = copy(base = base.withSize(newWidth, newHeight))

    fun panned(deltaX: Float, deltaY: Float): DrawViewport {
        val delta = rotateVector(deltaX, deltaY, -rotation)
        return copy(base = base.panned(delta.x, delta.y))
    }

    fun zoomed(factor: Float, focusX: Float, focusY: Float): DrawViewport {
        if (factor == 1f) return this
        val focus = unrotated(focusX, focusY)
        return copy(base = base.zoomed(factor, focus.x, focus.y))
    }

    fun scaledTo(target: Float, focusX: Float, focusY: Float): DrawViewport {
        val focus = unrotated(focusX, focusY)
        return copy(base = base.scaledTo(target, focus.x, focus.y))
    }

    fun rotatedBy(delta: Float, focusX: Float, focusY: Float): DrawViewport {
        if (delta == 0f) return this
        return rotatedTo(rotation + delta, focusX, focusY)
    }

    fun rotatedTo(angle: Float, focusX: Float, focusY: Float): DrawViewport {
        val anchor = toDocument(focusX, focusY)
        val upright = rotateVector(focusX - centerX, focusY - centerY, -angle)
        return copy(
            rotation = angle,
            base = base.copy(
                offsetX = upright.x + centerX - anchor.x * base.scale,
                offsetY = upright.y + centerY - anchor.y * base.scale
            )
        )
    }

    fun resetRotation(): DrawViewport = rotatedTo(0f, centerX, centerY)

    fun snappedRotation(): DrawViewport {
        val quarter = (PI / 2.0).toFloat()
        val nearest = round(rotation / quarter) * quarter
        return if (abs(rotation - nearest) <= SNAP_RADIANS) rotatedTo(nearest, centerX, centerY) else this
    }

    fun zoomIn(): DrawViewport = scaledTo(Viewport.nextZoomStop(scale), centerX, centerY)

    fun zoomOut(): DrawViewport = scaledTo(Viewport.previousZoomStop(scale), centerX, centerY)

    fun fitted(rect: CanvasRect, padding: Float): DrawViewport {
        if (!isMeasured || rect.width <= 0f || rect.height <= 0f) return this
        val cosine = abs(cos(rotation))
        val sine = abs(sin(rotation))
        val boundsWidth = rect.width * cosine + rect.height * sine
        val boundsHeight = rect.width * sine + rect.height * cosine
        val target = minOf(
            (width - padding * 2f).coerceAtLeast(1f) / boundsWidth,
            (height - padding * 2f).coerceAtLeast(1f) / boundsHeight
        ).coerceIn(Viewport.MIN_SCALE, Viewport.MAX_SCALE)
        return centeredOn(rect, target)
    }

    fun centeredOn(rect: CanvasRect): DrawViewport = centeredOn(rect, scale)

    fun visibleDocumentRect(): CanvasRect {
        if (!isMeasured) return CanvasRect()
        val corners = listOf(toDocument(0f, 0f), toDocument(width, 0f), toDocument(0f, height), toDocument(width, height))
        return DrawGeometry.boundsOfVecs(corners, 0f)
    }

    private fun centeredOn(rect: CanvasRect, targetScale: Float): DrawViewport = copy(
        base = base.copy(
            scale = targetScale,
            offsetX = centerX - rect.centerX * targetScale,
            offsetY = centerY - rect.centerY * targetScale
        )
    )

    private fun unrotated(viewX: Float, viewY: Float): DrawVec {
        val turned = rotateVector(viewX - centerX, viewY - centerY, -rotation)
        return DrawVec(turned.x + centerX, turned.y + centerY)
    }

    companion object {
        const val DEFAULT_PADDING = 24f

        private const val SNAP_RADIANS = 0.087266f

        private const val DEGREES_PER_RADIAN = (180.0 / PI).toFloat()

        fun identity(): DrawViewport = DrawViewport(Viewport(), 0f)

        fun sized(width: Float, height: Float): DrawViewport = DrawViewport(Viewport().withSize(width, height), 0f)

        private fun rotateVector(x: Float, y: Float, angle: Float): DrawVec {
            val cosine = cos(angle)
            val sine = sin(angle)
            return DrawVec(x * cosine - y * sine, x * sine + y * cosine)
        }

        private fun normalized(angle: Float): Float {
            val turn = (PI * 2.0).toFloat()
            var value = angle % turn
            if (value < 0f) value += turn
            return value
        }
    }
}
