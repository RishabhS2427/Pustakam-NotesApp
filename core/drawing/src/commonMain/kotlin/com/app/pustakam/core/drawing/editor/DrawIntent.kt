package com.app.pustakam.core.drawing.editor

import com.app.pustakam.core.drawing.anchor.DrawAnchorFrame
import com.app.pustakam.core.drawing.color.DrawColorState
import com.app.pustakam.core.drawing.color.DrawPalette
import com.app.pustakam.core.drawing.model.DrawBrush
import com.app.pustakam.core.drawing.model.DrawBrushKind
import com.app.pustakam.core.drawing.model.DrawColor
import com.app.pustakam.core.drawing.model.DrawDocument
import com.app.pustakam.core.drawing.model.DrawPaper
import com.app.pustakam.core.drawing.model.DrawPoint
import com.app.pustakam.core.drawing.model.DrawPointerType
import com.app.pustakam.core.drawing.model.DrawShapeKind
import com.app.pustakam.core.drawing.model.DrawShapeSpec
import com.app.pustakam.core.drawing.ops.DrawOp
import com.app.pustakam.core.drawing.tool.DrawEraserKind
import com.app.pustakam.core.drawing.tool.DrawToolId
import com.app.pustakam.core.drawing.tool.DrawToolMode
import com.app.pustakam.core.drawing.tool.DrawToolSettings

sealed class DrawIntent {

    data class Load(val document: DrawDocument) : DrawIntent()

    data class Resize(val width: Float, val height: Float) : DrawIntent()

    data class PointerDown(
        val point: DrawPoint,
        val pointer: DrawPointerType,
        val anchors: List<DrawAnchorFrame>
    ) : DrawIntent()

    data class PointerMove(val points: List<DrawPoint>) : DrawIntent()

    data object PointerUp : DrawIntent()

    data object PointerCancel : DrawIntent()

    data class Navigate(
        val panX: Float,
        val panY: Float,
        val zoom: Float,
        val rotation: Float,
        val focusX: Float,
        val focusY: Float
    ) : DrawIntent()

    data object NavigateEnd : DrawIntent()

    data object ZoomIn : DrawIntent()

    data object ZoomOut : DrawIntent()

    data object FitPaper : DrawIntent()

    data object CenterPaper : DrawIntent()

    data object ResetRotation : DrawIntent()

    data class SetTool(val tool: DrawToolId) : DrawIntent()

    data class SetMode(val mode: DrawToolMode) : DrawIntent()

    data class SetBrushKind(val kind: DrawBrushKind) : DrawIntent()

    data class UpdateBrush(val brush: DrawBrush) : DrawIntent()

    data class SetEraserKind(val kind: DrawEraserKind) : DrawIntent()

    data class SetEraserSize(val size: Float) : DrawIntent()

    data class SetShapeKind(val kind: DrawShapeKind) : DrawIntent()

    data class UpdateShape(val shape: DrawShapeSpec) : DrawIntent()

    data class SetStylusOnly(val enabled: Boolean) : DrawIntent()

    data class SetColor(val color: DrawColor) : DrawIntent()

    data class ToggleFavorite(val color: DrawColor) : DrawIntent()

    data class AddPalette(val palette: DrawPalette) : DrawIntent()

    data class AddToPalette(val paletteId: String, val color: DrawColor) : DrawIntent()

    data class RemoveFromPalette(val paletteId: String, val color: DrawColor) : DrawIntent()

    data class RemovePalette(val paletteId: String) : DrawIntent()

    data class UpdatePaper(val paper: DrawPaper) : DrawIntent()

    data object Undo : DrawIntent()

    data object Redo : DrawIntent()

    data object Clear : DrawIntent()

    data class ApplyRemote(val op: DrawOp) : DrawIntent()

    data class SetReadOnly(val readOnly: Boolean) : DrawIntent()

    data class RestoreToolbox(
        val settings: DrawToolSettings,
        val colors: DrawColorState,
        val mode: DrawToolMode
    ) : DrawIntent()
}
