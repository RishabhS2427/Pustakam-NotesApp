package com.app.pustakam.core.drawing.model

import com.app.pustakam.core.drawing.geometry.DrawGeometry
import com.app.pustakam.core.drawing.geometry.DrawShapeBuilder
import com.app.pustakam.core.drawing.geometry.DrawVec
import com.app.pustakam.core.richtext.master.model.CanvasRect

enum class DrawElementKind { STROKE, SHAPE, ERASE }

data class DrawCut(val points: List<DrawPoint>, val radius: Float)

data class DrawElement(
    val id: String,
    val kind: DrawElementKind,
    val layerId: String,
    val anchorId: String?,
    val points: List<DrawPoint>,
    val brush: DrawBrush,
    val color: DrawColor,
    val shape: DrawShapeSpec,
    val order: Double,
    val author: String,
    val clock: Long,
    val seed: Int,
    val cuts: List<DrawCut> = emptyList()
) {
    val reach: Float get() = brush.size * REACH_FACTOR

    val bounds: CanvasRect by lazy {
        if (kind == DrawElementKind.SHAPE && points.size >= 2) {
            DrawGeometry.boundsOfPath(shapePath(), reach)
        } else {
            DrawGeometry.boundsOf(points, reach)
        }
    }

    fun shapePath(): FloatArray {
        if (points.size < 2) return FloatArray(0)
        val start = points.first()
        val end = points.last()
        return DrawShapeBuilder.path(shape, DrawVec(start.x, start.y), DrawVec(end.x, end.y), brush.size)
    }

    val isStroke: Boolean get() = kind == DrawElementKind.STROKE

    val isShape: Boolean get() = kind == DrawElementKind.SHAPE

    val isErase: Boolean get() = kind == DrawElementKind.ERASE

    val isCut: Boolean get() = cuts.isNotEmpty()

    val renderKey: String get() = "$id@$clock"

    fun withPoints(value: List<DrawPoint>): DrawElement = copy(points = value)

    fun withId(value: String): DrawElement = copy(id = value)

    fun withClock(value: Long): DrawElement = copy(clock = value)

    fun withOrder(value: Double): DrawElement = copy(order = value)

    fun withColor(value: DrawColor): DrawElement = copy(color = value)

    fun withCuts(value: List<DrawCut>): DrawElement = copy(cuts = value)

    fun wins(other: DrawElement): Boolean =
        clock > other.clock || (clock == other.clock && author > other.author)

    companion object {
        const val REACH_FACTOR = 1.2f
    }
}
