package com.app.pustakam.core.drawing.anchor

import com.app.pustakam.core.drawing.geometry.DrawMatrix
import com.app.pustakam.core.drawing.model.DrawPoint
import kotlin.math.hypot
import kotlin.math.max

data class DrawAnchorFrame(
    val id: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val originX: Float,
    val originY: Float,
    val scale: Float
) {
    val right: Float get() = x + width

    val bottom: Float get() = y + height

    fun contains(pointX: Float, pointY: Float): Boolean =
        pointX >= x && pointX <= right && pointY >= y && pointY <= bottom

    fun distanceTo(pointX: Float, pointY: Float): Float {
        val dx = max(max(x - pointX, 0f), pointX - right)
        val dy = max(max(y - pointY, 0f), pointY - bottom)
        return hypot(dx, dy)
    }
}

object DrawAnchoring {

    const val REFERENCE_WIDTH = 1000f

    fun scaleOf(frame: DrawAnchorFrame): Float = if (frame.scale > 0f) frame.scale else 1f

    fun matrixFor(frame: DrawAnchorFrame): DrawMatrix {
        val scale = scaleOf(frame)
        return DrawMatrix(scale, 0f, 0f, scale, frame.originX, frame.originY)
    }

    fun pick(frames: List<DrawAnchorFrame>, x: Float, y: Float): DrawAnchorFrame? =
        frames.lastOrNull { it.contains(x, y) } ?: frames.minByOrNull { it.distanceTo(x, y) }

    fun toLocal(frame: DrawAnchorFrame, point: DrawPoint): DrawPoint {
        val scale = scaleOf(frame)
        return point.movedTo((point.x - frame.originX) / scale, (point.y - frame.originY) / scale)
    }

    fun toView(frame: DrawAnchorFrame, point: DrawPoint): DrawPoint {
        val scale = scaleOf(frame)
        return point.movedTo(point.x * scale + frame.originX, point.y * scale + frame.originY)
    }

    fun frame(id: String, x: Float, y: Float, width: Float, height: Float): DrawAnchorFrame =
        DrawAnchorFrame(id, x, y, width, height, x, y, if (width > 0f) width / REFERENCE_WIDTH else 1f)

    fun mapped(
        id: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        originX: Float,
        originY: Float,
        scale: Float
    ): DrawAnchorFrame = DrawAnchorFrame(id, x, y, width, height, originX, originY, scale)
}
