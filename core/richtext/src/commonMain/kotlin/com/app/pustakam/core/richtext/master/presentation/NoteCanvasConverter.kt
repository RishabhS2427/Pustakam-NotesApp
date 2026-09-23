package com.app.pustakam.core.richtext.master.presentation

import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import com.app.pustakam.core.richtext.master.model.CanvasDocument
import com.app.pustakam.core.richtext.master.model.CanvasNode
import com.app.pustakam.core.richtext.master.model.CanvasRole

/**
 * 📄 23-Sep-2026 — the bridge between a note's LINEAR content list and the canvas.
 *
 * A canvas page is now paper that carries many contents, so the ordering lives in
 * [CanvasNode.slotOrder] (fractional, like [NoteContentModel.position]) rather than in the link
 * chains this object used to walk. Which page a content lands on is decided by
 * `CanvasPaginator` in :core:filesys, which reuses the reading-mode layout engine; this object
 * only converts between orders.
 */
object NoteCanvasConverter {

    const val COLUMN_GAP = 48f
    const val ROW_GAP = 32f
    const val MEDIA_PER_ROW = 3

    /**
     * A flat fallback layout: one page with every content stacked down it. Real page splitting
     * goes through `CanvasPaginator.rebuild`, which measures blocks the way the reader does.
     */
    fun toCanvas(contents: List<NoteContentModel>): CanvasDocument {
        val ordered = contents.sortedBy { it.position }
        val page = CanvasNode.page(order = 0.0, x = 0f, y = 0f)
        var y = PAGE_PADDING
        val widgets = ordered.mapIndexed { index, content ->
            val width = CanvasNode.widgetWidthFor(content.type, page.rect.width)
            val height = CanvasNode.widgetHeightFor(content.type, width)
            val node = CanvasNode.of(
                kind = content.type,
                contentId = content.id,
                x = PAGE_PADDING,
                y = y,
                width = width,
                height = height,
                parentId = page.id,
                name = CanvasNode.defaultName(content.type, index)
            ).copy(z = index + 1, role = CanvasRole.WIDGET, slotOrder = index.toDouble())
            y += height + CanvasNode.WIDGET_GAP
            node
        }
        return CanvasDocument(listOf(page) + widgets)
    }

    /** Canvas back to a linear note: slot order IS the reading order. */
    fun toOrderedContentIds(document: CanvasDocument): List<String> =
        document.orderedWidgets.mapNotNull { it.contentId }

    /**
     * Re-stamps [NoteContentModel.position] from the canvas order, so the book reader, the note
     * editor and the server all page through the note in the order the canvas shows.
     */
    fun reorderContents(
        contents: List<NoteContentModel>,
        document: CanvasDocument
    ): List<NoteContentModel> {
        val order = toOrderedContentIds(document)
        if (order.isEmpty()) return contents
        val rank = order.withIndex().associate { (index, id) -> id to index }
        return contents
            .sortedBy { rank[it.id] ?: Int.MAX_VALUE }
            .mapIndexed { index, content -> content.repositioned(index.toDouble()) }
    }

    private fun NoteContentModel.repositioned(newPosition: Double): NoteContentModel = when (this) {
        is NoteContentModel.TextContent -> copy(position = newPosition)
        is NoteContentModel.MediaContent -> copy(position = newPosition)
        is NoteContentModel.Link -> copy(position = newPosition)
        is NoteContentModel.Location -> copy(position = newPosition)
    }

    private const val PAGE_PADDING = CanvasNode.PAGE_PADDING
}
