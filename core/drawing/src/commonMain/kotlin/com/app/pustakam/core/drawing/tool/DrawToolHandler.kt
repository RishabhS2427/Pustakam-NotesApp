package com.app.pustakam.core.drawing.tool

import com.app.pustakam.core.drawing.anchor.DrawAnchorFrame
import com.app.pustakam.core.drawing.model.DrawColor
import com.app.pustakam.core.drawing.model.DrawDocument
import com.app.pustakam.core.drawing.model.DrawElement
import com.app.pustakam.core.drawing.model.DrawPoint
import com.app.pustakam.core.drawing.model.DrawPointerType
import com.app.pustakam.core.drawing.ops.DrawOp
import com.app.pustakam.core.drawing.viewport.DrawSpace

data class DrawGesture(
    val tool: DrawToolId,
    val elementId: String,
    val seed: Int,
    val anchorId: String?,
    val frames: List<DrawAnchorFrame>,
    val pointer: DrawPointerType,
    val points: List<DrawPoint>,
    val touchedIds: List<String>
) {
    val first: DrawPoint? get() = points.firstOrNull()

    val last: DrawPoint? get() = points.lastOrNull()

    fun withPoints(value: List<DrawPoint>): DrawGesture = copy(points = value)

    fun withTouched(value: List<String>): DrawGesture = copy(touchedIds = value)
}

data class DrawToolPreview(val elements: List<DrawElement>, val hiddenIds: Set<String>) {
    val isEmpty: Boolean get() = elements.isEmpty() && hiddenIds.isEmpty()

    companion object {
        val EMPTY: DrawToolPreview = DrawToolPreview(emptyList(), emptySet())
    }
}

class DrawToolContext(
    val document: DrawDocument,
    val settings: DrawToolSettings,
    val color: DrawColor,
    val author: String,
    val clock: Long,
    val space: DrawSpace,
    val newId: () -> String
)

interface DrawToolHandler {

    fun extend(context: DrawToolContext, gesture: DrawGesture, points: List<DrawPoint>): DrawGesture

    fun preview(context: DrawToolContext, gesture: DrawGesture): DrawToolPreview

    fun finish(context: DrawToolContext, gesture: DrawGesture): DrawOp?
}
