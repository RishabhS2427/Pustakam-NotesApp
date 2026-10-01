package com.app.pustakam.core.drawing.render

import com.app.pustakam.core.drawing.brush.DrawBrushCatalog
import com.app.pustakam.core.drawing.brush.DrawBrushStrategy
import com.app.pustakam.core.drawing.brush.DrawStampGenerator
import com.app.pustakam.core.drawing.brush.DrawStrokeDynamics
import com.app.pustakam.core.drawing.brush.DrawStrokeOutline
import com.app.pustakam.core.drawing.geometry.DrawPathBuilder
import com.app.pustakam.core.drawing.geometry.DrawVec
import com.app.pustakam.core.drawing.model.DrawBlend
import com.app.pustakam.core.drawing.model.DrawColor
import com.app.pustakam.core.drawing.model.DrawElement
import com.app.pustakam.core.drawing.model.DrawElementKind
import com.app.pustakam.core.drawing.model.DrawPoint
import com.app.pustakam.core.richtext.master.model.CanvasRect

object DrawRenderer {

    fun itemsFor(element: DrawElement, complete: Boolean): List<DrawRenderItem> {
        val items = when (element.kind) {
            DrawElementKind.STROKE -> strokeItems(element, complete)
            DrawElementKind.ERASE -> eraseItems(element)
            DrawElementKind.SHAPE -> shapeItems(element)
        }
        if (!element.isCut || items.isEmpty() || element.isErase) return items
        val cuts = element.cuts.mapIndexedNotNull { index, cut ->
            clearItem("${element.renderKey}$CUT_SUFFIX$index", cut.points, cut.radius * 2f, element.bounds)
        }
        val area = element.bounds.inflated(element.brush.size * GROUP_SLACK)
        return listOf(DrawRenderItem.groupBegin(element.renderKey + GROUP_SUFFIX, area)) +
            items + cuts + DrawRenderItem.groupEnd(element.renderKey + END_SUFFIX, area)
    }

    private fun strokeItems(element: DrawElement, complete: Boolean): List<DrawRenderItem> {
        if (element.points.isEmpty()) return emptyList()
        val brush = element.brush
        val profile = DrawBrushCatalog.profile(brush.kind)
        val widths = DrawStrokeDynamics.widths(element.points, brush, profile, complete)
        val item = when (profile.strategy) {
            DrawBrushStrategy.OUTLINE -> DrawRenderItem.fill(
                key = element.renderKey,
                path = DrawStrokeOutline.path(element.points, widths, profile.tip),
                color = element.color,
                opacity = brush.opacity,
                blend = profile.blend,
                bounds = element.bounds
            )

            DrawBrushStrategy.STAMP -> DrawRenderItem.dabs(
                key = element.renderKey,
                dabs = DrawStampGenerator.dabs(
                    element.points,
                    widths,
                    DrawStrokeDynamics.alphas(element.points, brush),
                    brush,
                    profile,
                    element.seed
                ),
                color = element.color,
                opacity = brush.opacity,
                hardness = brush.hardness,
                blend = profile.blend,
                bounds = element.bounds
            )
        }
        return listOf(item)
    }

    private fun eraseItems(element: DrawElement): List<DrawRenderItem> =
        listOfNotNull(clearItem(element.renderKey, element.points, element.brush.size, element.bounds))

    private fun clearItem(key: String, points: List<DrawPoint>, width: Float, bounds: CanvasRect): DrawRenderItem? {
        if (points.isEmpty()) return null
        if (points.size == 1) {
            return DrawRenderItem.fill(
                key = key,
                path = DrawStrokeOutline.dot(points.first(), width / 2f),
                color = DrawColor.BLACK,
                opacity = 1f,
                blend = DrawBlend.CLEAR,
                bounds = bounds
            )
        }
        val path = DrawPathBuilder().smoothThrough(points.map { DrawVec(it.x, it.y) }, connect = false).build()
        return DrawRenderItem.stroke(
            key = key,
            path = path,
            color = DrawColor.BLACK,
            opacity = 1f,
            width = width,
            cap = DrawCap.ROUND,
            join = DrawJoin.ROUND,
            blend = DrawBlend.CLEAR,
            bounds = bounds
        )
    }

    private fun shapeItems(element: DrawElement): List<DrawRenderItem> {
        val path = element.shapePath()
        if (path.isEmpty()) return emptyList()
        val stroke = DrawRenderItem.stroke(
            key = element.renderKey + STROKE_SUFFIX,
            path = path,
            color = element.color,
            opacity = element.brush.opacity,
            width = element.brush.size,
            cap = DrawCap.ROUND,
            join = DrawJoin.ROUND,
            blend = DrawBlend.NORMAL,
            bounds = element.bounds
        )
        if (!element.shape.filled || !element.shape.isFillable) return listOf(stroke)
        val fill = DrawRenderItem.fill(
            key = element.renderKey + FILL_SUFFIX,
            path = path,
            color = element.color,
            opacity = element.brush.opacity,
            blend = DrawBlend.NORMAL,
            bounds = element.bounds
        )
        return listOf(fill, stroke)
    }

    private const val STROKE_SUFFIX = ":stroke"

    private const val CUT_SUFFIX = ":cut"

    private const val GROUP_SLACK = 2f

    private const val GROUP_SUFFIX = ":group"

    private const val END_SUFFIX = ":end"

    private const val FILL_SUFFIX = ":fill"
}
