package com.app.pustakam.core.drawing.tool

import com.app.pustakam.core.drawing.brush.DrawBrushCatalog
import com.app.pustakam.core.drawing.model.DrawBrush
import com.app.pustakam.core.drawing.model.DrawBrushKind
import com.app.pustakam.core.drawing.model.DrawShapeKind
import com.app.pustakam.core.drawing.model.DrawShapeSpec

enum class DrawEraserKind { PIXEL, PARTIAL, STROKE, OBJECT }

data class DrawToolSettings(
    val brushKind: DrawBrushKind,
    val brushes: List<DrawBrush>,
    val eraserKind: DrawEraserKind,
    val eraserSize: Float,
    val shapeKind: DrawShapeKind,
    val shapes: List<DrawShapeSpec>,
    val stylusOnly: Boolean
) {
    val brush: DrawBrush
        get() = brushes.firstOrNull { it.kind == brushKind } ?: DrawBrushCatalog.defaultBrush(brushKind)

    val shape: DrawShapeSpec
        get() = shapes.firstOrNull { it.kind == shapeKind } ?: DrawShapeSpec.of(shapeKind)

    fun brushOf(kind: DrawBrushKind): DrawBrush =
        brushes.firstOrNull { it.kind == kind } ?: DrawBrushCatalog.defaultBrush(kind)

    fun shapeOf(kind: DrawShapeKind): DrawShapeSpec = shapes.firstOrNull { it.kind == kind } ?: DrawShapeSpec.of(kind)

    fun withBrushKind(value: DrawBrushKind): DrawToolSettings = copy(brushKind = value)

    fun withBrush(value: DrawBrush): DrawToolSettings =
        copy(brushes = brushes.filterNot { it.kind == value.kind } + value)

    fun withEraserKind(value: DrawEraserKind): DrawToolSettings = copy(eraserKind = value)

    fun withEraserSize(value: Float): DrawToolSettings = copy(eraserSize = value.coerceIn(MIN_ERASER, MAX_ERASER))

    fun withShapeKind(value: DrawShapeKind): DrawToolSettings =
        if (value == DrawShapeKind.NONE) this else copy(shapeKind = value)

    fun withShape(value: DrawShapeSpec): DrawToolSettings =
        if (value.kind == DrawShapeKind.NONE) this
        else copy(shapeKind = value.kind, shapes = shapes.filterNot { it.kind == value.kind } + value)

    fun withStylusOnly(value: Boolean): DrawToolSettings = copy(stylusOnly = value)

    companion object {
        const val MIN_ERASER = 4f
        const val MAX_ERASER = 120f
        const val DEFAULT_ERASER = 20f

        fun defaults(): DrawToolSettings = DrawToolSettings(
            brushKind = DrawBrushKind.BALLPOINT,
            brushes = DrawBrushCatalog.defaults(),
            eraserKind = DrawEraserKind.PARTIAL,
            eraserSize = DEFAULT_ERASER,
            shapeKind = DrawShapeKind.RECTANGLE,
            shapes = DrawShapeSpec.kinds().map { DrawShapeSpec.of(it) },
            stylusOnly = false
        )
    }
}
