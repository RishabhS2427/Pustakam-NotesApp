package com.app.pustakam.core.drawing.brush

import com.app.pustakam.core.drawing.model.DrawBlend
import com.app.pustakam.core.drawing.model.DrawBrush
import com.app.pustakam.core.drawing.model.DrawBrushKind
import com.app.pustakam.core.drawing.model.DrawBrushKind.AIRBRUSH
import com.app.pustakam.core.drawing.model.DrawBrushKind.BALLPOINT
import com.app.pustakam.core.drawing.model.DrawBrushKind.BRUSH_PEN
import com.app.pustakam.core.drawing.model.DrawBrushKind.CHALK
import com.app.pustakam.core.drawing.model.DrawBrushKind.CHARCOAL
import com.app.pustakam.core.drawing.model.DrawBrushKind.CRAYON
import com.app.pustakam.core.drawing.model.DrawBrushKind.FOUNTAIN
import com.app.pustakam.core.drawing.model.DrawBrushKind.HIGHLIGHTER
import com.app.pustakam.core.drawing.model.DrawBrushKind.MARKER
import com.app.pustakam.core.drawing.model.DrawBrushKind.PENCIL
import com.app.pustakam.core.drawing.model.DrawBrushKind.TECHNICAL

object DrawBrushCatalog {

    private const val NIB_ANGLE = 0.785398f

    private const val CHISEL_ANGLE = 1.3962634f

    fun kinds(): List<DrawBrushKind> = DrawBrushKind.entries

    fun defaults(): List<DrawBrush> = kinds().map { defaultBrush(it) }

    fun defaultBrush(kind: DrawBrushKind): DrawBrush = when (kind) {
        PENCIL -> brush(kind, 2.2f, 0.9f, 0.85f, 0.9f, 0.12f, 0.35f, 0.8f, 0.8f, 0.2f)
        BALLPOINT -> brush(kind, 2.2f, 1f, 1f, 1f, 0.1f, 0.5f, 0.4f, 0f, 0.35f)
        FOUNTAIN -> brush(kind, 3.6f, 1f, 1f, 1f, 0.1f, 0.55f, 0.7f, 0.2f, 0.2f)
        MARKER -> brush(kind, 8f, 0.9f, 1f, 1f, 0.1f, 0.45f, 0.1f, 0f, 0f)
        BRUSH_PEN -> brush(kind, 6f, 1f, 1f, 1f, 0.1f, 0.5f, 1f, 0.2f, 0.45f)
        TECHNICAL -> brush(kind, 1.2f, 1f, 1f, 1f, 0.1f, 0.6f, 0f, 0f, 0f)
        CHALK -> brush(kind, 10f, 0.95f, 0.55f, 0.7f, 0.12f, 0.3f, 0.5f, 0.4f, 0f)
        CRAYON -> brush(kind, 7f, 0.95f, 0.7f, 0.85f, 0.1f, 0.35f, 0.6f, 0.3f, 0f)
        CHARCOAL -> brush(kind, 12f, 0.9f, 0.45f, 0.5f, 0.1f, 0.3f, 0.7f, 0.6f, 0f)
        AIRBRUSH -> brush(kind, 30f, 0.6f, 0.08f, 0f, 0.06f, 0.4f, 0.8f, 0f, 0f)
        HIGHLIGHTER -> brush(kind, 18f, 0.35f, 1f, 1f, 0.1f, 0.6f, 0f, 0f, 0f)
    }

    fun profile(kind: DrawBrushKind): DrawBrushProfile = when (kind) {
        PENCIL -> stamp(sizeJitter = 0.15f, alphaJitter = 0.4f, scatter = 0.12f)
        BALLPOINT -> outline(DrawBrushTip.ROUND, taperStart = 0.8f, taperEnd = 1.2f, minWidthRatio = 0.55f)
        FOUNTAIN -> outline(DrawBrushTip.ROUND, taperStart = 1f, taperEnd = 2f, minWidthRatio = 0.2f)
            .copy(nibAngle = NIB_ANGLE, nibMinRatio = 0.25f)
        MARKER -> outline(DrawBrushTip.ROUND, taperStart = 0f, taperEnd = 0f, minWidthRatio = 0.85f)
        BRUSH_PEN -> outline(DrawBrushTip.ROUND, taperStart = 2.5f, taperEnd = 4f, minWidthRatio = 0.08f)
        TECHNICAL -> outline(DrawBrushTip.ROUND, taperStart = 0f, taperEnd = 0f, minWidthRatio = 1f)
        CHALK -> stamp(sizeJitter = 0.25f, alphaJitter = 0.5f, scatter = 0.25f)
        CRAYON -> stamp(sizeJitter = 0.2f, alphaJitter = 0.45f, scatter = 0.15f)
        CHARCOAL -> stamp(sizeJitter = 0.35f, alphaJitter = 0.6f, scatter = 0.3f)
        AIRBRUSH -> stamp(sizeJitter = 0f, alphaJitter = 0.1f, scatter = 0f)
        HIGHLIGHTER -> outline(DrawBrushTip.FLAT, taperStart = 0f, taperEnd = 0f, minWidthRatio = 0.3f)
            .copy(nibAngle = CHISEL_ANGLE, nibMinRatio = 0.3f, blend = DrawBlend.MULTIPLY)
    }

    fun label(kind: DrawBrushKind): String = when (kind) {
        PENCIL -> "Pencil"
        BALLPOINT -> "Ballpoint"
        FOUNTAIN -> "Fountain pen"
        MARKER -> "Marker"
        BRUSH_PEN -> "Brush pen"
        TECHNICAL -> "Technical pen"
        CHALK -> "Chalk"
        CRAYON -> "Crayon"
        CHARCOAL -> "Charcoal"
        AIRBRUSH -> "Airbrush"
        HIGHLIGHTER -> "Highlighter"
    }

    fun iconKey(kind: DrawBrushKind): String = "brush_" + kind.name.lowercase()

    private fun brush(
        kind: DrawBrushKind,
        size: Float,
        opacity: Float,
        flow: Float,
        hardness: Float,
        spacing: Float,
        smoothing: Float,
        pressure: Float,
        tilt: Float,
        velocity: Float
    ): DrawBrush = DrawBrush(kind, size, opacity, flow, hardness, spacing, smoothing, pressure, tilt, velocity)

    private fun outline(
        tip: DrawBrushTip,
        taperStart: Float,
        taperEnd: Float,
        minWidthRatio: Float
    ): DrawBrushProfile = DrawBrushProfile(
        strategy = DrawBrushStrategy.OUTLINE,
        tip = tip,
        nibAngle = 0f,
        nibMinRatio = 1f,
        taperStart = taperStart,
        taperEnd = taperEnd,
        minWidthRatio = minWidthRatio,
        sizeJitter = 0f,
        alphaJitter = 0f,
        scatter = 0f,
        blend = DrawBlend.NORMAL
    )

    private fun stamp(sizeJitter: Float, alphaJitter: Float, scatter: Float): DrawBrushProfile = DrawBrushProfile(
        strategy = DrawBrushStrategy.STAMP,
        tip = DrawBrushTip.ROUND,
        nibAngle = 0f,
        nibMinRatio = 1f,
        taperStart = 0.6f,
        taperEnd = 0.6f,
        minWidthRatio = 0.3f,
        sizeJitter = sizeJitter,
        alphaJitter = alphaJitter,
        scatter = scatter,
        blend = DrawBlend.NORMAL
    )
}
