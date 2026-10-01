@file:OptIn(ExperimentalComposeUiApi::class)

package com.app.pustakam.android.widgets.drawing

import android.view.MotionEvent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.unit.Density
import com.app.pustakam.core.drawing.anchor.DrawAnchorFrame
import com.app.pustakam.core.drawing.editor.DrawCommands
import com.app.pustakam.core.drawing.model.DrawPoint
import com.app.pustakam.core.drawing.model.DrawPointerType
import kotlin.math.PI

private const val RADIANS_PER_DEGREE = (PI / 180.0).toFloat()

private class DrawingStylusProbe {
    var tilt = 0f
    var azimuth = 0f

    fun record(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            tilt = event.getAxisValue(MotionEvent.AXIS_TILT)
            azimuth = event.getAxisValue(MotionEvent.AXIS_ORIENTATION)
        }
        return false
    }
}

@Composable
fun Modifier.drawingInput(
    session: DrawingSession,
    anchors: () -> List<DrawAnchorFrame> = NoDrawingAnchors,
    navigator: DrawingNavigator? = null
): Modifier {
    val probe = remember(session) { DrawingStylusProbe() }
    val latestAnchors by rememberUpdatedState(anchors)
    val latestNavigator by rememberUpdatedState(navigator)
    return this
        .pointerInteropFilter(onTouchEvent = probe::record)
        .pointerInput(session) { drawingGestures(session, probe, { latestAnchors() }, { latestNavigator }) }
}

private suspend fun PointerInputScope.drawingGestures(
    session: DrawingSession,
    probe: DrawingStylusProbe,
    anchors: () -> List<DrawAnchorFrame>,
    navigator: () -> DrawingNavigator?
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    val pointer = pointerTypeOf(down.type)
    if (!DrawCommands.capturesPointer(session.current, pointer)) return@awaitEachGesture
    down.consume()
    session.dispatch(DrawCommands.pointerDown(sampleOf(down.position, down.pressure, down.uptimeMillis, probe), pointer, anchors()))
    var settled = false
    try {
        settled = followStroke(session, probe, down, navigator)
    } finally {
        if (!settled) session.dispatch(DrawCommands.pointerCancel())
    }
}

private suspend fun AwaitPointerEventScope.followStroke(
    session: DrawingSession,
    probe: DrawingStylusProbe,
    down: PointerInputChange,
    navigator: () -> DrawingNavigator?
): Boolean {
    var navigating = false
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val pressed = event.changes.count { it.pressed }
        if (navigating || pressed > 1) {
            if (!navigating) {
                navigating = true
                session.dispatch(DrawCommands.pointerCancel())
            }
            if (pressed > 1) {
                val centroid = event.calculateCentroid(useCurrent = false)
                val pan = event.calculatePan()
                navigator()?.navigate(
                    pan.x / density,
                    pan.y / density,
                    event.calculateZoom(),
                    event.calculateRotation() * RADIANS_PER_DEGREE,
                    centroid.x / density,
                    centroid.y / density
                )
            }
            event.changes.forEach { it.consume() }
            if (pressed == 0) {
                navigator()?.end()
                return true
            }
            continue
        }
        val change = event.changes.firstOrNull { it.id == down.id } ?: return false
        if (!change.pressed) {
            change.consume()
            session.dispatch(DrawCommands.pointerUp())
            return true
        }
        val samples = ArrayList<DrawPoint>(change.historical.size + 1)
        change.historical.forEach { samples.add(sampleOf(it.position, change.pressure, it.uptimeMillis, probe)) }
        samples.add(sampleOf(change.position, change.pressure, change.uptimeMillis, probe))
        session.dispatch(DrawCommands.pointerMove(samples))
        change.consume()
    }
}

private fun Density.sampleOf(position: Offset, pressure: Float, time: Long, probe: DrawingStylusProbe): DrawPoint =
    DrawCommands.sample(position.x / density, position.y / density, pressure, probe.tilt, probe.azimuth, time)

private fun pointerTypeOf(type: PointerType): DrawPointerType = when (type) {
    PointerType.Stylus, PointerType.Eraser -> DrawCommands.stylus()
    PointerType.Mouse -> DrawCommands.mouse()
    else -> DrawCommands.finger()
}
