package com.app.pustakam.android.widgets.drawing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.app.pustakam.core.drawing.anchor.DrawAnchorFrame
import com.app.pustakam.core.drawing.editor.DrawCommands
import com.app.pustakam.core.drawing.render.DrawRenderEntry
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

val NoDrawingAnchors: () -> List<DrawAnchorFrame> = { emptyList() }

@Composable
fun DrawingCanvas(
    session: DrawingSession,
    modifier: Modifier = Modifier,
    anchors: () -> List<DrawAnchorFrame> = NoDrawingAnchors,
    zoom: Float = 1f
) {
    val state = session.state.collectAsState()
    val paper = remember(session) { derivedStateOf { DrawCommands.paperView(state.value) } }
    val density = LocalDensity.current.density
    val committed = remember(session) { DrawingCommittedLayer() }
    val paths = remember(session) { DrawingPathCache() }
    Box(
        modifier = modifier.onSizeChanged {
            session.dispatch(DrawCommands.resize(it.width / density, it.height / density))
        }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawEntries(DrawCommands.paperFrameOf(paper.value), null)
        }
        Canvas(modifier = Modifier.matchParentSize()) {
            val current = state.value
            val frames = anchors()
            val preview = DrawCommands.previewFrame(current, frames)
            if (DrawCommands.cachesInk(zoom)) {
                val key = DrawCommands.committedKey(current, frames, size.width, size.height)
                val ink = committed.bitmapFor(this, key) {
                    drawEntries(DrawCommands.committedFrame(current, frames, session.cache), paths)
                }
                isolated(DrawCommands.clears(preview)) {
                    drawImage(ink)
                    drawEntries(preview, null)
                }
            } else {
                val ink = DrawCommands.committedFrame(current, frames, session.cache)
                isolated(DrawCommands.clears(ink) || DrawCommands.clears(preview)) {
                    drawEntries(ink, paths)
                    drawEntries(preview, null)
                }
            }
        }
    }
}

@Composable
fun rememberCapturesTouches(session: DrawingSession?): Boolean {
    val flow = remember(session) {
        session?.state?.map { DrawCommands.capturesTouches(it) }?.distinctUntilChanged() ?: flowOf(false)
    }
    val captures by flow.collectAsState(session?.let { DrawCommands.capturesTouches(it.current) } ?: false)
    return captures
}

@Composable
fun DrawingEntries(entries: (Float, Float) -> List<DrawRenderEntry>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawEntries(entries(size.width / density, size.height / density), null)
    }
}

private inline fun DrawScope.isolated(needed: Boolean, block: DrawScope.() -> Unit) {
    if (!needed) {
        block()
        return
    }
    val canvas = drawContext.canvas
    canvas.saveLayer(Rect(Offset.Zero, size), Paint())
    block()
    canvas.restore()
}
