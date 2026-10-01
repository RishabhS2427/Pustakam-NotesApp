package com.app.pustakam.core.drawing.tool

import com.app.pustakam.core.drawing.geometry.DrawGeometry
import com.app.pustakam.core.drawing.geometry.DrawSmoothing
import com.app.pustakam.core.drawing.model.DrawElement
import com.app.pustakam.core.drawing.model.DrawElementKind
import com.app.pustakam.core.drawing.model.DrawPoint
import com.app.pustakam.core.drawing.model.DrawShapeSpec
import com.app.pustakam.core.drawing.ops.DrawOp

object DrawPenTool : DrawToolHandler {

    private const val MIN_VIEW_SPACING = 0.6f

    override fun extend(context: DrawToolContext, gesture: DrawGesture, points: List<DrawPoint>): DrawGesture =
        gesture.withPoints(gesture.points + points)

    override fun preview(context: DrawToolContext, gesture: DrawGesture): DrawToolPreview =
        DrawToolPreview(listOfNotNull(element(context, gesture, complete = false)), emptySet())

    override fun finish(context: DrawToolContext, gesture: DrawGesture): DrawOp? =
        element(context, gesture, complete = true)?.let { DrawOp.Add(listOf(it), context.author, context.clock) }

    private fun element(context: DrawToolContext, gesture: DrawGesture, complete: Boolean): DrawElement? {
        val local = context.space.toElement(gesture.anchorId, gesture.points)
        if (local.isEmpty()) return null
        val scale = context.space.scaleOf(gesture.anchorId)
        val brush = context.settings.brush.scaledBy(1f / scale)
        val stabilized = DrawSmoothing.stabilize(local, brush.smoothing, complete)
        val points = if (complete) DrawGeometry.thinned(stabilized, MIN_VIEW_SPACING / scale) else stabilized
        val layer = context.document.activeLayer
        return DrawElement(
            id = gesture.elementId,
            kind = DrawElementKind.STROKE,
            layerId = layer.id,
            anchorId = gesture.anchorId,
            points = points,
            brush = brush,
            color = context.color,
            shape = DrawShapeSpec.none(),
            order = context.document.nextOrder(layer.id),
            author = context.author,
            clock = context.clock,
            seed = gesture.seed
        )
    }
}
