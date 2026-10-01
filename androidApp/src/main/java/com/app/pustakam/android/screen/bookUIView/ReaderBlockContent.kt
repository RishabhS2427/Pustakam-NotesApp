package com.app.pustakam.android.screen.bookUIView

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.app.pustakam.core.filesys.reader.PageLayoutPolicy
import com.app.pustakam.core.filesys.reader.ReaderBlock
import com.app.pustakam.core.model.models.response.notes.NoteContentModel

@Composable
fun ReaderBlockContent(
    block: ReaderBlock,
    policy: PageLayoutPolicy,
    modifier: Modifier = Modifier,
    documents: InlineDocumentUiState = InlineDocumentUiState.Disabled,
    onOpenDocument: (NoteContentModel.MediaContent) -> Unit = {},
    onOpenImage: (NoteContentModel.MediaContent) -> Unit = {},
) {
    when (block) {
        is ReaderBlock.Title -> TitleBlockView(block, modifier)
        is ReaderBlock.Paragraph -> ParagraphBlockView(block, modifier)

        is ReaderBlock.RichParagraph -> RichParagraphBlockView(block, modifier)
        is ReaderBlock.ImageGrid -> ImageGridBlockView(block, policy, modifier, onOpenImage)
        is ReaderBlock.VideoGrid -> VideoGridBlockView(block, policy, modifier, onOpenImage)
        is ReaderBlock.Audio -> AudioBlockView(block, modifier)
        is ReaderBlock.Document -> DocumentBlockView(block, modifier, documents, onOpenDocument)
        is ReaderBlock.DocumentPage -> DocumentPageBlockView(block, modifier, documents)
        is ReaderBlock.Link -> LinkBlockView(block, modifier)
        is ReaderBlock.Location -> LocationBlockView(block, modifier)
        is ReaderBlock.Drawing -> DrawingBlockView(block, policy, modifier)
    }
}
