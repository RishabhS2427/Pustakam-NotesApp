package com.app.pustakam.core.filesys.reader

import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import com.app.pustakam.core.richtext.model.RichBlock
import kotlin.math.ceil
import kotlin.math.max


object BlockHeightEstimator {

    /** Stage 2 — full measurement. Media never breaks across pages; only text may. */
    fun measure(block: ReaderBlock, policy: PageLayoutPolicy): MeasuredBlock = MeasuredBlock(
        block = block,
        size = size(block, policy),
        breakable = block is ReaderBlock.Paragraph,
    )

    /** Width AND height a block occupies. Most blocks span the usable width; a lone image may not. */
    fun size(block: ReaderBlock, policy: PageLayoutPolicy): BlockSize {
        val height = estimate(block, policy)
        val width = when (block) {
            // a portrait image is height-clamped, so it renders narrower than the column
            is ReaderBlock.ImageGrid ->
                if (block.items.size == 1) singleImageWidth(block.items[0], height, policy)
                else policy.usableWidth

            else -> policy.usableWidth
        }
        return BlockSize(width, height)
    }

    fun estimate(block: ReaderBlock, policy: PageLayoutPolicy): Float = when (block) {
        is ReaderBlock.Title ->
            policy.titleHeight + if (block.subtitle != null) policy.captionHeight else 0f

        is ReaderBlock.Paragraph -> textHeight(block.text, policy)

        // 📖 15-Aug-2026: measured with the editor's own numbers — style size, its own line height,
        //   and the width left after its indent and list marker
        is ReaderBlock.RichParagraph -> richHeight(block.block, policy)

        // a lone image is aspect-aware and full width; two or more use uniform 4:3 grid cells
        is ReaderBlock.ImageGrid ->
            if (block.items.size == 1) singleImageHeight(block.items[0], policy)
            else gridHeight(block.items.size, policy.gridCellWidth * policy.gridCellAspect, policy)

        // a lone video is full width at 16:9; grid cells keep the same ratio
        is ReaderBlock.VideoGrid ->
            if (block.items.size == 1) policy.usableWidth * VIDEO_ASPECT
            else gridHeight(block.items.size, policy.gridCellWidth * VIDEO_ASPECT, policy)

        is ReaderBlock.Audio -> policy.audioHeight
        is ReaderBlock.Document -> policy.documentHeight
        // 📖 15-Aug-2026: an injected sheet owns the whole page, so it is never packed with others
        is ReaderBlock.DocumentPage -> policy.usableHeight
        is ReaderBlock.Link -> policy.linkHeight
        is ReaderBlock.Location -> policy.locationHeight
        is ReaderBlock.Drawing -> drawingHeight(block.item, policy)
    }

    fun richHeight(block: RichBlock, policy: PageLayoutPolicy): Float = when (block) {
        is RichBlock.Text -> {
            val size = policy.bodyFontSize * block.style.relativeSize
            val lineHeight = size * block.lineHeight
            lineCount(block, policy) * lineHeight * policy.overflowGuard + block.paragraphSpacing
        }

        is RichBlock.Divider -> policy.blockGap * 2f + 1f
        is RichBlock.Code -> (max(1, block.code.split('\n').size) * policy.bodyLineHeight) + 20f
        is RichBlock.Table -> (block.data.rows.size * (policy.bodyLineHeight + 8f)) + 12f
    }

    /** Width left for text once the block's indent and its list marker are taken out. */
    fun textWidth(block: RichBlock.Text, policy: PageLayoutPolicy): Float {
        val indent = policy.indentStep * (block.listLevel + block.indent) +
            if (block.list != null) policy.markerGutter else 0f
        return (policy.usableWidth - indent).coerceAtLeast(policy.bodyFontSize)
    }

    /** Characters per line for THIS block — its own font size, its own usable width. */
    fun charsPerLine(block: RichBlock.Text, policy: PageLayoutPolicy): Int {
        val size = policy.bodyFontSize * block.style.relativeSize
        return (textWidth(block, policy) / (size * PageLayoutPolicy.AVG_CHAR_WIDTH_RATIO))
            .toInt().coerceAtLeast(1)
    }

    fun lineCount(block: RichBlock.Text, policy: PageLayoutPolicy): Int {
        val perLine = charsPerLine(block, policy)
        val lines = block.text.split('\n').sumOf { line ->
            if (line.isEmpty()) 1 else ceil(line.length.toDouble() / perLine).toInt()
        }
        return max(1, lines)
    }

    /** Lines × line height. A hard newline and a wrapped line each consume one line. */
    fun textHeight(text: String, policy: PageLayoutPolicy): Float {
        if (text.isEmpty()) return 0f
        val lines = text.split('\n').sumOf { line ->
            if (line.isEmpty()) 1 else ceil(line.length.toDouble() / policy.charsPerLine).toInt()
        }
        return max(1, lines) * policy.bodyLineHeight
    }

    /** Every item is drawn — a grid too tall for a page is split by the builder instead. */
    fun visibleCells(total: Int, policy: PageLayoutPolicy): Int = total.coerceAtLeast(1)

    fun overflowCount(total: Int, policy: PageLayoutPolicy): Int = 0

    fun rowsFor(total: Int, policy: PageLayoutPolicy): Int =
        ceil(total.toDouble() / policy.gridColumns).toInt().coerceAtLeast(1)

    /** Items that still fit on one page for a grid of [cellHeight] cells — drives the split. */
    fun maxItemsPerPage(cellHeight: Float, policy: PageLayoutPolicy): Int {
        val rowPitch = cellHeight + policy.gridSpacing
        if (rowPitch <= 0f) return policy.gridColumns
        val rows = (policy.usableHeight / rowPitch).toInt().coerceAtLeast(1)
        return rows * policy.gridColumns
    }

    private fun gridHeight(total: Int, cellHeight: Float, policy: PageLayoutPolicy): Float {
        val rows = rowsFor(total, policy)
        return rows * cellHeight + (rows - 1) * policy.gridSpacing
    }

    /** Aspect-aware height for a single image; falls back to the max when the size is unknown. */
    fun singleImageHeight(media: NoteContentModel.MediaContent, policy: PageLayoutPolicy): Float {
        if (media.width <= 0 || media.height <= 0) return policy.imageMaxHeight
        // toFloat() first — integer division here silently collapsed every portrait image
        val scaled = policy.usableWidth * (media.height.toFloat() / media.width.toFloat())
        return scaled.coerceIn(policy.imageMinHeight, policy.imageMaxHeight)
    }

    /** Rendered width of a lone image once its height has been clamped. */
    fun singleImageWidth(
        media: NoteContentModel.MediaContent,
        height: Float,
        policy: PageLayoutPolicy,
    ): Float {
        if (media.width <= 0 || media.height <= 0) return policy.usableWidth
        val ratio = media.width.toFloat() / media.height.toFloat()
        return (height * ratio).coerceAtMost(policy.usableWidth)
    }

    fun drawingHeight(drawing: NoteContentModel.Drawing, policy: PageLayoutPolicy): Float =
        (policy.usableWidth * drawing.aspect()).coerceIn(policy.imageMinHeight, policy.usableHeight)

    const val VIDEO_ASPECT = 9f / 16f

    /** Swift-facing accessor — a `const val` inside an object has no guaranteed export shape. */
    fun videoAspect(): Float = VIDEO_ASPECT
}
