package com.app.pustakam.core.drawing.render

import com.app.pustakam.core.drawing.geometry.DrawMatrix
import com.app.pustakam.core.drawing.model.DrawBlend
import com.app.pustakam.core.drawing.model.DrawColor
import com.app.pustakam.core.richtext.master.model.CanvasRect

enum class DrawRenderKind { FILL_PATH, STROKE_PATH, DABS }

enum class DrawCap { BUTT, ROUND, SQUARE }

enum class DrawJoin { MITER, ROUND, BEVEL }

class DrawRenderItem(
    val key: String,
    val kind: DrawRenderKind,
    val path: FloatArray,
    val dabs: FloatArray,
    val color: DrawColor,
    val opacity: Float,
    val strokeWidth: Float,
    val cap: DrawCap,
    val join: DrawJoin,
    val blend: DrawBlend,
    val hardness: Float,
    val bounds: CanvasRect
) {
    val isClear: Boolean get() = blend == DrawBlend.CLEAR

    val dabCount: Int get() = dabs.size / DAB_STRIDE

    companion object {
        const val DAB_STRIDE = 4

        fun fill(key: String, path: FloatArray, color: DrawColor, opacity: Float, blend: DrawBlend, bounds: CanvasRect) =
            DrawRenderItem(key, DrawRenderKind.FILL_PATH, path, FloatArray(0), color, opacity, 0f, DrawCap.ROUND, DrawJoin.ROUND, blend, 1f, bounds)

        fun stroke(
            key: String,
            path: FloatArray,
            color: DrawColor,
            opacity: Float,
            width: Float,
            cap: DrawCap,
            join: DrawJoin,
            blend: DrawBlend,
            bounds: CanvasRect
        ) = DrawRenderItem(key, DrawRenderKind.STROKE_PATH, path, FloatArray(0), color, opacity, width, cap, join, blend, 1f, bounds)

        fun dabs(
            key: String,
            dabs: FloatArray,
            color: DrawColor,
            opacity: Float,
            hardness: Float,
            blend: DrawBlend,
            bounds: CanvasRect
        ) = DrawRenderItem(key, DrawRenderKind.DABS, FloatArray(0), dabs, color, opacity, 0f, DrawCap.ROUND, DrawJoin.ROUND, blend, hardness, bounds)
    }
}

class DrawRenderEntry(val item: DrawRenderItem, val matrix: DrawMatrix)
