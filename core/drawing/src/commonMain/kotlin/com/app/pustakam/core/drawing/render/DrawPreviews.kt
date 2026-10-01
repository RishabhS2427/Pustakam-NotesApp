package com.app.pustakam.core.drawing.render

import com.app.pustakam.core.drawing.brush.DrawBrushCatalog
import com.app.pustakam.core.drawing.model.DrawBrush
import com.app.pustakam.core.drawing.model.DrawBrushKind
import com.app.pustakam.core.drawing.model.DrawColor
import com.app.pustakam.core.drawing.model.DrawElement
import com.app.pustakam.core.drawing.model.DrawElementKind
import com.app.pustakam.core.drawing.model.DrawLayer
import com.app.pustakam.core.drawing.model.DrawPoint
import com.app.pustakam.core.drawing.model.DrawShapeKind
import com.app.pustakam.core.drawing.model.DrawShapeSpec
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object DrawPreviews {

    private const val SAMPLES = 48

    private const val PADDING_RATIO = 0.12f

    private const val WAVE_RATIO = 0.22f

    private const val MAX_SIZE_RATIO = 0.28f

    private const val MIN_PRESSURE = 0.25f

    private const val DURATION_MILLIS = 480f

    private const val SEED = 7

    private const val PREVIEW_ID = "preview"

    fun brush(brush: DrawBrush, color: DrawColor, width: Float, height: Float): List<DrawRenderItem> {
        if (width <= 0f || height <= 0f) return emptyList()
        val sized = brush.withSize(min(brush.size, height * MAX_SIZE_RATIO))
        val reach = sized.size * DrawElement.REACH_FACTOR
        val padding = max(width * PADDING_RATIO, reach)
        val span = width - padding * 2f
        val middle = height / 2f
        val wave = (height / 2f - reach).coerceIn(0f, height * WAVE_RATIO)
        val turn = (PI * 2.0).toFloat()
        val half = PI.toFloat()
        val points = (0..SAMPLES).map { index ->
            val t = index.toFloat() / SAMPLES
            DrawPoint(
                x = padding + span * t,
                y = middle - wave * sin(t * turn),
                pressure = MIN_PRESSURE + (1f - MIN_PRESSURE) * sin(t * half),
                tilt = 0f,
                azimuth = 0f,
                time = (t * DURATION_MILLIS).toLong()
            )
        }
        return DrawRenderer.itemsFor(element(DrawElementKind.STROKE, points, sized, color, DrawShapeSpec.none()), complete = true)
    }

    fun shape(spec: DrawShapeSpec, color: DrawColor, strokeWidth: Float, width: Float, height: Float): List<DrawRenderItem> {
        if (width <= 0f || height <= 0f || spec.kind == DrawShapeKind.NONE) return emptyList()
        val padding = min(width, height) * PADDING_RATIO + strokeWidth
        val points = if (spec.kind == DrawShapeKind.CIRCLE) {
            val radius = min(width, height) / 2f - padding
            listOf(DrawPoint.at(width / 2f, height / 2f), DrawPoint.at(width / 2f + radius, height / 2f))
        } else {
            listOf(DrawPoint.at(padding, padding), DrawPoint.at(width - padding, height - padding))
        }
        val brush = DrawBrushCatalog.defaultBrush(DrawBrushKind.TECHNICAL).withSize(strokeWidth)
        return DrawRenderer.itemsFor(element(DrawElementKind.SHAPE, points, brush, color, spec), complete = true)
    }

    private fun element(
        kind: DrawElementKind,
        points: List<DrawPoint>,
        brush: DrawBrush,
        color: DrawColor,
        shape: DrawShapeSpec
    ): DrawElement = DrawElement(
        id = PREVIEW_ID,
        kind = kind,
        layerId = DrawLayer.BASE_ID,
        anchorId = null,
        points = points,
        brush = brush,
        color = color,
        shape = shape,
        order = 0.0,
        author = PREVIEW_ID,
        clock = 0L,
        seed = SEED
    )
}
