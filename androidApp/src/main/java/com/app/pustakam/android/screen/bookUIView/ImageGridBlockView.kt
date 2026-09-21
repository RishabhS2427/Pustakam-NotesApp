package com.app.pustakam.android.screen.bookUIView

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.app.pustakam.android.theme.typography
import com.app.pustakam.android.widgets.media.MediaDownloadOverlay
import com.app.pustakam.core.filesys.reader.BlockHeightEstimator
import com.app.pustakam.core.filesys.reader.PageLayoutPolicy
import com.app.pustakam.core.filesys.reader.ReaderBlock
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import com.app.pustakam.core.model.models.response.notes.getMediaUrl

// 📖 01-Aug-2026: 2-column grid. Cell counts and the "+N" overflow come from the SHARED estimator,
//   so what is drawn always matches the height the engine reserved for this block.
@Composable
fun ImageGridBlockView(
    block: ReaderBlock.ImageGrid,
    policy: PageLayoutPolicy,
    modifier: Modifier = Modifier,
    onImageClick: (NoteContentModel.MediaContent) -> Unit = {},
) {
    val visible = BlockHeightEstimator.visibleCells(block.items.size, policy)
    val overflow = BlockHeightEstimator.overflowCount(block.items.size, policy)

    if (visible == 1) {
        ImageCell(
            media = block.items[0],
            overflow = 0,
            onClick = onImageClick,
            modifier = modifier.fillMaxWidth().heightIn(max = policy.imageMaxHeight.dp),
        )
        return
    }
    val shown = block.items.take(visible)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(policy.gridSpacing.dp)) {
        shown.chunked(policy.gridColumns).forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(policy.gridSpacing.dp)) {
                row.forEachIndexed { columnIndex, media ->
                    val isLastCell = rowIndex * policy.gridColumns + columnIndex == visible - 1
                    ImageCell(
                        media = media,
                        overflow = if (isLastCell) overflow else 0,
                        onClick = onImageClick,
                        modifier = Modifier.weight(1f).aspectRatio(1f / policy.gridCellAspect),
                    )
                }
                // keeps a lone trailing cell at column width instead of stretching it across
                if (row.size < policy.gridColumns) repeat(policy.gridColumns - row.size) {
                    Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ImageCell(
    media: NoteContentModel.MediaContent,
    overflow: Int,
    onClick: (NoteContentModel.MediaContent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).clickable { onClick(media) }) {
        AsyncImage(
            model = media.getMediaUrl(),
            contentDescription = media.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth(),
        )
        if (overflow > 0) Box(
            Modifier.matchParentSize().background(Color.Black.copy(alpha = .45f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("+$overflow", style = typography.headlineSmall, color = Color.White)
        }
        // 📥 20-Sep-2026 — the generic transfer bar; draws nothing once the bytes are here
        MediaDownloadOverlay(media, Modifier.align(Alignment.BottomCenter))
    }
}
