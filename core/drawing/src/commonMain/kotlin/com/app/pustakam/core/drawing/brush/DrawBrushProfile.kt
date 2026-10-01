package com.app.pustakam.core.drawing.brush

import com.app.pustakam.core.drawing.model.DrawBlend

enum class DrawBrushStrategy { OUTLINE, STAMP }

enum class DrawBrushTip { ROUND, FLAT }

data class DrawBrushProfile(
    val strategy: DrawBrushStrategy,
    val tip: DrawBrushTip,
    val nibAngle: Float,
    val nibMinRatio: Float,
    val taperStart: Float,
    val taperEnd: Float,
    val minWidthRatio: Float,
    val sizeJitter: Float,
    val alphaJitter: Float,
    val scatter: Float,
    val blend: DrawBlend
) {
    val hasNib: Boolean get() = nibMinRatio < 1f
}
