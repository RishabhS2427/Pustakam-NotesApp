package com.app.pustakam.core.drawing.tool

enum class DrawToolId { PEN, ERASER, SHAPE, HAND }

enum class DrawToolMode { BASIC, ADVANCED }

enum class DrawToolGroup { DRAW, ERASE, SHAPE, NAVIGATE }

data class DrawToolSpec(
    val id: DrawToolId,
    val mode: DrawToolMode,
    val group: DrawToolGroup,
    val label: String,
    val iconKey: String
)

object DrawToolRegistry {

    private val handlers: Map<DrawToolId, DrawToolHandler> = mapOf(
        DrawToolId.PEN to DrawPenTool,
        DrawToolId.ERASER to DrawEraserTool,
        DrawToolId.SHAPE to DrawShapeTool
    )

    private val specs: List<DrawToolSpec> = listOf(
        DrawToolSpec(DrawToolId.PEN, DrawToolMode.BASIC, DrawToolGroup.DRAW, "Pen", "tool_pen"),
        DrawToolSpec(DrawToolId.ERASER, DrawToolMode.BASIC, DrawToolGroup.ERASE, "Eraser", "tool_eraser"),
        DrawToolSpec(DrawToolId.SHAPE, DrawToolMode.BASIC, DrawToolGroup.SHAPE, "Shapes", "tool_shape"),
        DrawToolSpec(DrawToolId.HAND, DrawToolMode.BASIC, DrawToolGroup.NAVIGATE, "Hand", "tool_hand")
    )

    fun specs(): List<DrawToolSpec> = specs

    fun specsFor(mode: DrawToolMode): List<DrawToolSpec> =
        if (mode == DrawToolMode.ADVANCED) specs else specs.filter { it.mode == DrawToolMode.BASIC }

    fun spec(id: DrawToolId): DrawToolSpec = specs.first { it.id == id }

    fun handler(id: DrawToolId): DrawToolHandler? = handlers[id]

    fun isAvailable(id: DrawToolId, mode: DrawToolMode): Boolean = specsFor(mode).any { it.id == id }

    fun erasersFor(mode: DrawToolMode): List<DrawEraserKind> =
        if (mode == DrawToolMode.ADVANCED) advancedErasers else basicErasers

    fun fitted(settings: DrawToolSettings, mode: DrawToolMode): DrawToolSettings {
        val erasers = erasersFor(mode)
        return if (settings.eraserKind in erasers) settings else settings.withEraserKind(erasers.first())
    }

    private val basicErasers = listOf(DrawEraserKind.PARTIAL, DrawEraserKind.OBJECT)

    private val advancedErasers = basicErasers + listOf(DrawEraserKind.STROKE, DrawEraserKind.PIXEL)
}
