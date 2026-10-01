package com.app.pustakam.android.widgets.drawing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.app.pustakam.core.drawing.note.DrawNoteContents
import com.app.pustakam.core.model.models.response.notes.NoteContentModel

@Composable
fun DrawingFrame(
    content: NoteContentModel.Drawing,
    session: DrawingSession,
    modifier: Modifier = Modifier,
    input: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        if (content.isWidget()) {
            val frameWidth = DrawNoteContents.frameWidth(content)
            val frameHeight = DrawNoteContents.frameHeight(content)
            val boundedHeight = if (constraints.hasBoundedHeight) maxHeight.value else 0f
            val scale = DrawNoteContents.fitScale(frameWidth, frameHeight, maxWidth.value, boundedHeight)
            Box(modifier = Modifier.size((frameWidth * scale).dp, (frameHeight * scale).dp).clipToBounds()) {
                Box(
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .size(frameWidth.dp, frameHeight.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                ) {
                    DrawingCanvas(session = session, modifier = Modifier.fillMaxSize().then(input), zoom = scale)
                }
            }
        } else {
            DrawingCanvas(session = session, modifier = Modifier.fillMaxSize().then(input))
        }
    }
}
