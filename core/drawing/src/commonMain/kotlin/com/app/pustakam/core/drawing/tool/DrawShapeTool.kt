package com.app.pustakam.core.drawing.tool

import com.app.pustakam.core.drawing.model.DrawElement
import com.app.pustakam.core.drawing.model.DrawElementKind
import com.app.pustakam.core.drawing.model.DrawPoint
import com.app.pustakam.core.drawing.ops.DrawOp

object DrawShapeTool : DrawToolHandler {

    private const val MIN_VIEW_SIZE = 3f

    override fun extend(context: DrawToolContext, gesture: DrawGesture, points: List<DrawPoint>): DrawGesture {
        if (points.isEmpty()) return gesture
        val start = gesture.first ?: points.first()
        return gesture.withPoints(listOf(start, points.last()))
    }

    override fun preview(context: DrawToolContext, gesture: DrawGesture): DrawToolPreview =
        DrawToolPreview(listOfNotNull(element(context, gesture)), emptySet())

    override fun finish(context: DrawToolContext, gesture: DrawGesture): DrawOp? {
        val first = gesture.first ?: return null
        val last = gesture.last ?: return null
        if (first.distanceTo(last) < MIN_VIEW_SIZE) return null
        return element(context, gesture)?.let { DrawOp.Add(listOf(it), context.author, context.clock) }
    }

    private fun element(context: DrawToolContext, gesture: DrawGesture): DrawElement? {
        val local = context.space.toElement(gesture.anchorId, gesture.points)
        if (local.size < 2) return null
        val scale = context.space.scaleOf(gesture.anchorId)
        val layer = context.document.activeLayer
        return DrawElement(
            id = gesture.elementId,
            kind = DrawElementKind.SHAPE,
            layerId = layer.id,
            anchorId = gesture.anchorId,
            points = listOf(local.first(), local.last()),
            brush = context.settings.brush.scaledBy(1f / scale),
            color = context.color,
            shape = context.settings.shape,
            order = context.document.nextOrder(layer.id),
            author = context.author,
            clock = context.clock,
            seed = gesture.seed
        )
    }
}
