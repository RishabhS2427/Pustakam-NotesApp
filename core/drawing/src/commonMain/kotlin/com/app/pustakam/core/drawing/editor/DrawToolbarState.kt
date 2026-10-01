package com.app.pustakam.core.drawing.editor

import com.app.pustakam.core.drawing.model.DrawColor
import com.app.pustakam.core.drawing.model.DrawPaper
import com.app.pustakam.core.drawing.tool.DrawToolId
import com.app.pustakam.core.drawing.tool.DrawToolSpec
import com.app.pustakam.core.drawing.viewport.DrawViewport

data class DrawToolbarState(
    val tools: List<DrawToolSpec>,
    val tool: DrawToolId,
    val color: DrawColor,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val navigates: Boolean,
    val zoomPercent: Int,
    val rotated: Boolean,
    val showsPaper: Boolean,
    val advanced: Boolean,
    val readOnly: Boolean
) {
    fun isTool(value: DrawToolId): Boolean = tool == value
}

data class DrawPaperView(val paper: DrawPaper, val viewport: DrawViewport, val overlay: Boolean)
