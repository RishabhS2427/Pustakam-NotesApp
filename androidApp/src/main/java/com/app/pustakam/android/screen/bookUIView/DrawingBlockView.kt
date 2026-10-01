package com.app.pustakam.android.screen.bookUIView

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.app.pustakam.android.widgets.drawing.DrawingFrame
import com.app.pustakam.android.widgets.drawing.DrawingSession
import com.app.pustakam.core.drawing.note.DrawAuthors
import com.app.pustakam.core.drawing.note.DrawNoteContents
import com.app.pustakam.core.filesys.reader.BlockHeightEstimator
import com.app.pustakam.core.filesys.reader.PageLayoutPolicy
import com.app.pustakam.core.filesys.reader.ReaderBlock

@Composable
fun DrawingBlockView(block: ReaderBlock.Drawing, policy: PageLayoutPolicy, modifier: Modifier = Modifier) {
    val content = block.item
    val session = remember(content.id, content.drawing) {
        DrawingSession(DrawNoteContents.stateFor(content, DrawAuthors.local(), readOnly = true)) {}
    }
    DrawingFrame(
        content = content,
        session = session,
        modifier = modifier
            .fillMaxWidth()
            .height(BlockHeightEstimator.estimate(block, policy).dp)
    )
}
