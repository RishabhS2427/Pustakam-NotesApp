package com.app.pustakam.android.screen.noteEditor

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.app.pustakam.android.widgets.drawing.DrawingFrame
import com.app.pustakam.android.widgets.drawing.DrawingNavigator
import com.app.pustakam.android.widgets.drawing.DrawingSession
import com.app.pustakam.android.widgets.drawing.DrawingSessionNavigator
import com.app.pustakam.android.widgets.drawing.drawingInput
import com.app.pustakam.android.widgets.smartText.SmartTextTokens
import com.app.pustakam.core.drawing.anchor.DrawAnchorFrame
import com.app.pustakam.core.drawing.editor.DrawCommands
import com.app.pustakam.core.drawing.note.DrawNoteContents
import com.app.pustakam.core.model.models.response.notes.NoteContentModel

@Composable
fun rememberNoteAnchors(listState: LazyListState): () -> List<DrawAnchorFrame> {
    val density = LocalDensity.current.density
    return remember(listState, density) {
        {
            val info = listState.layoutInfo
            val width = info.viewportSize.width / density
            info.visibleItemsInfo.mapNotNull { item ->
                val id = item.key as? String
                if (id == null || item.size <= 0) {
                    null
                } else {
                    DrawCommands.anchor(id, 0f, (item.offset - info.viewportStartOffset) / density, width, item.size / density)
                }
            }
        }
    }
}

class NoteListNavigator(private val listState: LazyListState, private val density: Float) : DrawingNavigator {
    override fun navigate(panX: Float, panY: Float, zoom: Float, rotation: Float, focusX: Float, focusY: Float) {
        listState.dispatchRawDelta(-panY * density)
    }

    override fun end() = Unit
}

@Composable
fun NoteDrawingBlock(
    content: NoteContentModel.Drawing,
    session: DrawingSession,
    active: Boolean,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SmartTextTokens.colors
    val shape = RoundedCornerShape(8.dp)
    val state = session.state.collectAsState()
    val paperAspect by remember(session) { derivedStateOf { DrawCommands.paperAspect(state.value) } }
    val transparent by remember(session) { derivedStateOf { DrawCommands.isTransparent(state.value) } }
    val navigator = remember(session) { DrawingSessionNavigator(session) }
    val input = if (active) {
        Modifier.drawingInput(session, navigator = if (DrawCommands.navigates(session.current)) navigator else null)
    } else {
        Modifier.clickable(onClick = onActivate)
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        val aspect = if (content.isWidget()) {
            DrawNoteContents.frameHeight(content) / DrawNoteContents.frameWidth(content)
        } else {
            paperAspect
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxWidth * aspect)
                .clip(shape)
                .then(
                    when {
                        active -> Modifier.border(1.5.dp, colors.accent, shape)
                        transparent -> Modifier
                        else -> Modifier.border(1.dp, colors.divider, shape)
                    }
                )
        ) {
            DrawingFrame(content = content, session = session, modifier = Modifier.fillMaxSize(), input = input)
            if (active) {
                IconButton(onClick = onDelete, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete drawing", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
