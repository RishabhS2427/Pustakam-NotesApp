package com.app.pustakam.android.widgets.drawing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.app.pustakam.core.drawing.anchor.DrawAnchorFrame
import com.app.pustakam.core.drawing.editor.DrawCommands

private class DrawingPageFrame(private val anchorId: String) {
    var width = 0f
    var height = 0f

    fun anchors(): List<DrawAnchorFrame> = listOf(DrawCommands.anchor(anchorId, 0f, 0f, width, height))
}

@Composable
fun DrawingPageLayer(
    session: DrawingSession,
    anchorId: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    val frame = remember(anchorId) { DrawingPageFrame(anchorId) }
    val anchors: () -> List<DrawAnchorFrame> = remember(frame) { frame::anchors }
    DrawingCanvas(
        session = session,
        anchors = anchors,
        modifier = modifier
            .onSizeChanged {
                frame.width = it.width / density
                frame.height = it.height / density
            }
            .then(if (active) Modifier.drawingInput(session, anchors) else Modifier)
    )
}
