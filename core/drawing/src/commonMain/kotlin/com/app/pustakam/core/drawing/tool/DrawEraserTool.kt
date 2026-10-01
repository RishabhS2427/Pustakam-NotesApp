package com.app.pustakam.core.drawing.tool

import com.app.pustakam.core.drawing.brush.DrawBrushCatalog
import com.app.pustakam.core.drawing.geometry.DrawEraseGeometry
import com.app.pustakam.core.drawing.model.DrawBrushKind
import com.app.pustakam.core.drawing.model.DrawColor
import com.app.pustakam.core.drawing.model.DrawElement
import com.app.pustakam.core.drawing.model.DrawElementKind
import com.app.pustakam.core.drawing.model.DrawPoint
import com.app.pustakam.core.drawing.model.DrawShapeSpec
import com.app.pustakam.core.drawing.ops.DrawOp

object DrawEraserTool : DrawToolHandler {

    override fun extend(context: DrawToolContext, gesture: DrawGesture, points: List<DrawPoint>): DrawGesture {
        if (points.isEmpty()) return gesture
        val next = gesture.withPoints(gesture.points + points)
        if (context.settings.eraserKind == DrawEraserKind.PIXEL) return next
        return next.withTouched(touched(context, gesture.touchedIds, listOfNotNull(gesture.last) + points))
    }

    override fun preview(context: DrawToolContext, gesture: DrawGesture): DrawToolPreview =
        when (context.settings.eraserKind) {
            DrawEraserKind.PIXEL -> DrawToolPreview(listOfNotNull(eraseMark(context, gesture)), emptySet())
            DrawEraserKind.STROKE, DrawEraserKind.OBJECT -> DrawToolPreview(emptyList(), gesture.touchedIds.toSet())
            DrawEraserKind.PARTIAL -> carve(context, gesture).let { carved ->
                DrawToolPreview(carved.after, carved.before.map { it.id }.toSet())
            }
        }

    override fun finish(context: DrawToolContext, gesture: DrawGesture): DrawOp? =
        when (context.settings.eraserKind) {
            DrawEraserKind.PIXEL -> eraseMark(context, gesture)?.let {
                DrawOp.Add(listOf(it), context.author, context.clock)
            }

            DrawEraserKind.STROKE, DrawEraserKind.OBJECT -> gesture.touchedIds
                .mapNotNull { context.document.elementById(it) }
                .takeIf { it.isNotEmpty() }
                ?.let { DrawOp.Remove(it, context.author, context.clock) }

            DrawEraserKind.PARTIAL -> carve(context, gesture)
                .takeIf { it.before.isNotEmpty() }
                ?.let { DrawOp.Replace(it.before, it.after, context.author, context.clock) }
        }

    private fun touched(context: DrawToolContext, already: List<String>, probe: List<DrawPoint>): List<String> {
        val known = already.toMutableSet()
        val result = already.toMutableList()
        for (element in context.document.visibleElements) {
            if (element.id in known) continue
            if (!accepts(context.settings.eraserKind, element)) continue
            if (!context.document.isEditableLayer(element.layerId)) continue
            val local = context.space.toElement(element.anchorId, probe)
            if (local.isEmpty()) continue
            if (DrawEraseGeometry.hits(element, local, radiusFor(context, element))) {
                result.add(element.id)
                known.add(element.id)
            }
        }
        return result
    }

    private fun accepts(kind: DrawEraserKind, element: DrawElement): Boolean = when (kind) {
        DrawEraserKind.STROKE -> element.isStroke
        DrawEraserKind.PARTIAL -> !element.isErase
        DrawEraserKind.OBJECT -> true
        DrawEraserKind.PIXEL -> false
    }

    private fun radiusFor(context: DrawToolContext, element: DrawElement): Float =
        context.settings.eraserSize / 2f / context.space.scaleOf(element.anchorId)

    private class Carved(val before: List<DrawElement>, val after: List<DrawElement>)

    private fun carve(context: DrawToolContext, gesture: DrawGesture): Carved {
        val before = mutableListOf<DrawElement>()
        val after = mutableListOf<DrawElement>()
        for (id in gesture.touchedIds) {
            val element = context.document.elementById(id) ?: continue
            val local = context.space.toElement(element.anchorId, gesture.points)
            val cuts = DrawEraseGeometry.cutsFor(element, local, radiusFor(context, element))
            if (cuts.isEmpty()) continue
            val carved = element.copy(cuts = element.cuts + cuts, clock = context.clock)
            before.add(element)
            if (!DrawEraseGeometry.isErasedAway(carved)) after.add(carved)
        }
        return Carved(before, after)
    }

    private fun eraseMark(context: DrawToolContext, gesture: DrawGesture): DrawElement? {
        val local = context.space.toElement(gesture.anchorId, gesture.points)
        if (local.isEmpty()) return null
        val scale = context.space.scaleOf(gesture.anchorId)
        val layer = context.document.activeLayer
        return DrawElement(
            id = gesture.elementId,
            kind = DrawElementKind.ERASE,
            layerId = layer.id,
            anchorId = gesture.anchorId,
            points = local,
            brush = DrawBrushCatalog.defaultBrush(DrawBrushKind.TECHNICAL)
                .copy(size = context.settings.eraserSize)
                .scaledBy(1f / scale),
            color = DrawColor.BLACK,
            shape = DrawShapeSpec.none(),
            order = context.document.nextOrder(layer.id),
            author = context.author,
            clock = context.clock,
            seed = gesture.seed
        )
    }
}
