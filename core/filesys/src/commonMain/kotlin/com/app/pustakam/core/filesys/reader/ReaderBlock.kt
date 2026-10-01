package com.app.pustakam.core.filesys.reader

import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import com.app.pustakam.core.richtext.model.RichBlock

// 📖 01-Aug-2026 — ONE renderable widget on a page. A page is a list of these, so a page can hold a
//   title + paragraph + image grid + three audio players instead of one widget per page.
//   Platform UI renders a block; it never decides what goes where.
sealed class ReaderBlock {
    /** Content ids behind this block — powers jump-to-content and reading progress. */
    abstract val sourceContentIds: List<String>

    data class Title(
        val text: String,
        val subtitle: String?,
    ) : ReaderBlock() {
        override val sourceContentIds: List<String> get() = emptyList()
    }

    data class Paragraph(
        val text: String,
        val chunkIndex: Int,
        val chunkCount: Int,
        override val sourceContentIds: List<String>,
    ) : ReaderBlock()

    /**
     * 📖 15-Aug-2026: one paragraph of a formatted note, carried WHOLE — text, spans, style, list,
     * indent, line height. The reader draws it through the same mapper the editor uses, so it
     * shows exactly as written. Long ones arrive pre-split by RichTextSplitter.
     */
    data class RichParagraph(
        val block: RichBlock,
        val chunkIndex: Int,
        val chunkCount: Int,
        override val sourceContentIds: List<String>,
    ) : ReaderBlock() {
        val text: String get() = block.plainText
    }

    /** Consecutive images — every item renders; the builder splits a grid too tall for a page. */
    data class ImageGrid(
        val items: List<NoteContentModel.MediaContent>,
        override val sourceContentIds: List<String>,
    ) : ReaderBlock()

    /** Consecutive videos — cells reuse the existing VideoCard, so one ExoPlayer still serves all. */
    data class VideoGrid(
        val items: List<NoteContentModel.MediaContent>,
        override val sourceContentIds: List<String>,
    ) : ReaderBlock()

    data class Audio(
        val item: NoteContentModel.MediaContent,
        override val sourceContentIds: List<String>,
    ) : ReaderBlock()

    /** pdf / docx / epub / txt / other — rendered by the existing document widgets. */
    data class Document(
        val item: NoteContentModel.MediaContent,
        override val sourceContentIds: List<String>,
    ) : ReaderBlock()

    data class Link(
        val url: String,
        override val sourceContentIds: List<String>,
    ) : ReaderBlock()

    data class Location(
        val latitude: Double,
        val longitude: Double,
        val address: String?,
        override val sourceContentIds: List<String>,
    ) : ReaderBlock()

    data class Drawing(
        val item: NoteContentModel.Drawing,
        override val sourceContentIds: List<String>,
    ) : ReaderBlock()

    /** 📖 15-Aug-2026: one sheet of a document read inside the note; the platform draws the sheet. */
    data class DocumentPage(
        val item: NoteContentModel.MediaContent,
        val ref: EmbeddedPageRef,
        val text: String?,
        override val sourceContentIds: List<String>,
    ) : ReaderBlock()
}

// 📖 01-Aug-2026 — one A4 sheet. Page mode renders pages[i]; scroll mode stacks the same list.
data class ReaderPage(
    val index: Int,
    val blocks: List<ReaderBlock>,
    /** Space this page actually occupies — width AND height, in A4 units. */
    val occupied: BlockSize,
    /** 📖 15-Aug-2026: >0 when this is the remainder of a page split by an open document. */
    val continuation: Int = 0,
) {
    val usedHeight: Float get() = occupied.height
    val usedWidth: Float get() = occupied.width

    val sourceContentIds: List<String> get() = blocks.flatMap { it.sourceContentIds }

    /** True when the page carries [contentId] — used to resolve a jump target. */
    fun contains(contentId: String?): Boolean =
        contentId != null && sourceContentIds.any { it == contentId }

    /** 📖 15-Aug-2026: set only on injected document sheets — null on every engine-built page. */
    val embedded: EmbeddedPageRef?
        get() = (blocks.firstOrNull() as? ReaderBlock.DocumentPage)?.ref

    val isDocumentSheet: Boolean get() = embedded != null

    /** 📖 list identity that survives expand/collapse, so untouched pages are never re-rendered. */
    val stableKey: String
        get() = embedded?.let { "doc:${it.contentId}:${it.pageNumber}" }
            ?: if (continuation > 0) "base:$index+$continuation" else "base:$index"
}
