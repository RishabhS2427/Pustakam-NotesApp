package com.app.pustakam.core.drawing

import com.app.pustakam.core.drawing.anchor.DrawAnchorFrame
import com.app.pustakam.core.drawing.editor.DrawCommands
import com.app.pustakam.core.drawing.editor.DrawEditorState
import com.app.pustakam.core.drawing.editor.DrawIntent
import com.app.pustakam.core.drawing.editor.DrawReducer
import com.app.pustakam.core.drawing.model.DrawPoint
import com.app.pustakam.core.drawing.model.DrawPointerType

object DrawTestKit {

    const val AUTHOR = "author-a"

    fun page(width: Float = 400f, height: Float = 600f): DrawEditorState =
        reduce(DrawCommands.pageState(AUTHOR, width, height, readOnly = false), DrawCommands.resize(width, height))

    fun overlay(width: Float = 400f, height: Float = 800f): DrawEditorState =
        reduce(DrawCommands.overlayState(AUTHOR, readOnly = false), DrawCommands.resize(width, height))

    fun reduce(state: DrawEditorState, vararg intents: DrawIntent): DrawEditorState =
        intents.fold(state) { acc, intent -> DrawReducer.reduce(acc, intent) }

    fun point(x: Float, y: Float, time: Long = 0L, pressure: Float = 0.5f): DrawPoint =
        DrawPoint(x, y, pressure, 0f, 0f, time)

    fun line(from: DrawPoint, to: DrawPoint, steps: Int): List<DrawPoint> = (0..steps).map { index ->
        val t = index.toFloat() / steps
        DrawPoint(
            from.x + (to.x - from.x) * t,
            from.y + (to.y - from.y) * t,
            from.pressure,
            0f,
            0f,
            from.time + ((to.time - from.time) * t).toLong()
        )
    }

    fun stroke(
        state: DrawEditorState,
        points: List<DrawPoint>,
        pointer: DrawPointerType = DrawPointerType.STYLUS,
        anchors: List<DrawAnchorFrame> = emptyList()
    ): DrawEditorState {
        val down = DrawReducer.reduce(state, DrawCommands.pointerDown(points.first(), pointer, anchors))
        val moved = if (points.size > 1) DrawReducer.reduce(down, DrawCommands.pointerMove(points.drop(1))) else down
        return DrawReducer.reduce(moved, DrawCommands.pointerUp())
    }

    fun horizontal(state: DrawEditorState, y: Float, fromX: Float = 50f, toX: Float = 250f): DrawEditorState =
        stroke(state, line(point(fromX, y, 0L), point(toX, y, 200L), 40))
}
