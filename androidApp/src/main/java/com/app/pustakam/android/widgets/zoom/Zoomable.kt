package com.app.pustakam.android.widgets.zoom

// 🔧 19-Jul-2026: NEW — reusable pinch-zoom modifier (zoom feature request).
//   Pinch 1x..5x, pan clamped to bounds, double-tap toggles 1x ↔ 2.5x.
//   IMPORTANT: consumes events ONLY when pinching (2 fingers) or already zoomed — so the
//   book pager's single-finger page-flip keeps working on zoomable pages (image/PDF).
//   Used by the book reader pages and the image preview screen (DRY).
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

fun Modifier.zoomable(enabled: Boolean = true): Modifier = composed {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var boxWidth by remember { mutableFloatStateOf(1f) }
    var boxHeight by remember { mutableFloatStateOf(1f) }

    // 🔧 19-Jul-2026: pan never reveals empty space — clamp to (scale-1) * half size
    fun clamp() {
        val maxX = (scale - 1f) * boxWidth / 2f
        val maxY = (scale - 1f) * boxHeight / 2f
        offsetX = offsetX.coerceIn(-maxX, maxX)
        offsetY = offsetY.coerceIn(-maxY, maxY)
    }

    fun setScale(newScale: Float) {
        scale = newScale.coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (scale == MIN_ZOOM) { offsetX = 0f; offsetY = 0f }
        clamp()
    }

    this
        .then(
            if (!enabled) {
                Modifier
            } else {
                Modifier
                    .pointerInput(Unit) {
                        boxWidth = size.width.toFloat(); boxHeight = size.height.toFloat()
                        // manual loop: intercept ONLY pinches or gestures made while zoomed —
                        // single-finger drags at 1x fall through to the page-flip pager
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.count { it.pressed }
                                if (pressed == 0) break
                                val pinching = pressed > 1
                                if (pinching || scale > MIN_ZOOM) {
                                    val zoomChange = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    val centroid = event.calculateCentroid()
                                    if (zoomChange != 1f) {
                                        setScale(scale * zoomChange)
                                        // zoom toward the pinch centroid
                                        offsetX += (boxWidth / 2f + offsetX - centroid.x) * (zoomChange - 1f)
                                        offsetY += (boxHeight / 2f + offsetY - centroid.y) * (zoomChange - 1f)
                                        clamp()
                                    }
                                    if (scale > MIN_ZOOM && (pan.x != 0f || pan.y != 0f)) {
                                        offsetX += pan.x; offsetY += pan.y; clamp()
                                    }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { tap: Offset ->
                            if (scale > MIN_ZOOM) setScale(MIN_ZOOM)
                            else {
                                setScale(DOUBLE_TAP_ZOOM)
                                offsetX = (boxWidth / 2f - tap.x) * (DOUBLE_TAP_ZOOM - 1f)
                                offsetY = (boxHeight / 2f - tap.y) * (DOUBLE_TAP_ZOOM - 1f)
                                clamp()
                            }
                        })
                    }
            }
        )
        .graphicsLayer {
            scaleX = scale; scaleY = scale
            translationX = offsetX; translationY = offsetY
        }
}
