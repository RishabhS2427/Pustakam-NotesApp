package com.app.pustakam.core.drawing.editor

import com.app.pustakam.core.common.util.UniqueIdGenerator
import com.app.pustakam.core.drawing.color.DrawColorState
import com.app.pustakam.core.drawing.history.DrawHistory
import com.app.pustakam.core.drawing.model.DrawBrush
import com.app.pustakam.core.drawing.model.DrawColor
import com.app.pustakam.core.drawing.model.DrawDocument
import com.app.pustakam.core.drawing.model.DrawPaper
import com.app.pustakam.core.drawing.model.DrawSurface
import com.app.pustakam.core.drawing.ops.DrawOp
import com.app.pustakam.core.drawing.tool.DrawGesture
import com.app.pustakam.core.drawing.tool.DrawToolId
import com.app.pustakam.core.drawing.tool.DrawToolMode
import com.app.pustakam.core.drawing.tool.DrawToolPreview
import com.app.pustakam.core.drawing.tool.DrawToolRegistry
import com.app.pustakam.core.drawing.tool.DrawToolSettings
import com.app.pustakam.core.drawing.tool.DrawToolSpec
import com.app.pustakam.core.drawing.viewport.DrawViewport

data class DrawEditorState(
    val document: DrawDocument,
    val viewport: DrawViewport,
    val mode: DrawToolMode,
    val tool: DrawToolId,
    val settings: DrawToolSettings,
    val colors: DrawColorState,
    val gesture: DrawGesture?,
    val preview: DrawToolPreview,
    val history: DrawHistory,
    val author: String,
    val readOnly: Boolean,
    val navigating: Boolean,
    val revision: Long,
    val editSequence: Long,
    val lastOp: DrawOp?,
    val opSequence: Long
) {
    val canUndo: Boolean get() = !readOnly && history.canUndo

    val canRedo: Boolean get() = !readOnly && history.canRedo

    val isDrawing: Boolean get() = gesture != null && gesture.tool != DrawToolId.HAND

    val brush: DrawBrush get() = settings.brush

    val color: DrawColor get() = colors.current

    val surface: DrawSurface get() = document.surface

    val isOverlay: Boolean get() = document.surface == DrawSurface.OVERLAY

    val navigates: Boolean get() = document.surface == DrawSurface.PAGE

    val toolSpecs: List<DrawToolSpec> get() = DrawToolRegistry.specsFor(mode)
}

object DrawIds {
    fun next(): String = UniqueIdGenerator.generateUniqueId()
}

object DrawDocuments {

    fun overlay(): DrawDocument = DrawDocument.blank(DrawIds.next(), DrawSurface.OVERLAY, DrawPaper.infinite())

    fun page(width: Float, height: Float): DrawDocument =
        DrawDocument.blank(DrawIds.next(), DrawSurface.PAGE, DrawPaper.screen(width, height))

    fun widget(width: Float, height: Float): DrawDocument =
        DrawDocument.blank(DrawIds.next(), DrawSurface.WIDGET, DrawPaper.screen(width, height))

    fun of(surface: DrawSurface, width: Float, height: Float): DrawDocument = when (surface) {
        DrawSurface.OVERLAY -> overlay()
        DrawSurface.PAGE -> page(width, height)
        DrawSurface.WIDGET -> widget(width, height)
    }
}

object DrawEditorStates {

    fun initial(document: DrawDocument, author: String, readOnly: Boolean): DrawEditorState = DrawEditorState(
        document = document,
        viewport = DrawViewport.identity(),
        mode = DrawToolMode.BASIC,
        tool = DrawToolId.PEN,
        settings = DrawToolSettings.defaults(),
        colors = DrawColorState.defaults(),
        gesture = null,
        preview = DrawToolPreview.EMPTY,
        history = DrawHistory.empty(),
        author = author,
        readOnly = readOnly,
        navigating = false,
        revision = 0L,
        editSequence = 0L,
        lastOp = null,
        opSequence = 0L
    )

    fun carrying(previous: DrawEditorState, document: DrawDocument, readOnly: Boolean): DrawEditorState =
        initial(document, previous.author, readOnly).copy(
            mode = previous.mode,
            tool = previous.tool,
            settings = previous.settings,
            colors = previous.colors
        )
}
