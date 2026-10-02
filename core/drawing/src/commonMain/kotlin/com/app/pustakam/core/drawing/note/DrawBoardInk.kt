package com.app.pustakam.core.drawing.note

import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.drawing.anchor.DrawAnchorFrame
import com.app.pustakam.core.drawing.anchor.DrawAnchoring
import com.app.pustakam.core.drawing.editor.DrawDocuments
import com.app.pustakam.core.drawing.geometry.DrawEraseGeometry
import com.app.pustakam.core.drawing.model.DrawDocument
import com.app.pustakam.core.drawing.model.DrawElement
import com.app.pustakam.core.drawing.model.DrawSurface
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import com.app.pustakam.core.richtext.master.model.CanvasDocument
import com.app.pustakam.core.richtext.master.model.CanvasNode
import com.app.pustakam.core.richtext.master.model.CanvasRect

data class DrawBoardPage(
    val pageId: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val contentX: Float,
    val contentY: Float,
    val scale: Float
)

data class DrawBoardBlock(val content: NoteContentModel.Drawing, val node: CanvasNode)

data class DrawBoardSettlement(val overlay: NoteContentModel.Drawing, val blocks: List<DrawBoardBlock>) {
    val isEmpty: Boolean get() = blocks.isEmpty()
}

object DrawBoardInk {

    const val BLOCK_PADDING = 12f

    const val CLUSTER_GAP = 32f

    private class Cluster(val page: CanvasNode, val elements: List<DrawElement>, val trailing: Boolean)

    fun anchors(document: CanvasDocument, pages: List<DrawBoardPage>): List<DrawAnchorFrame> =
        pages.map(::paperFrame) +
            pages.flatMap { rowFrames(document, it, exact = false) } +
            pages.flatMap { rowFrames(document, it, exact = true) }

    fun hasLooseInk(overlay: NoteContentModel.Drawing?, document: CanvasDocument): Boolean =
        overlay != null && looseOf(DrawNoteContents.documentOf(overlay), document).isNotEmpty()

    fun needsPage(overlay: NoteContentModel.Drawing?, document: CanvasDocument): Boolean =
        document.pages.isEmpty() && hasLooseInk(overlay, document)

    fun settle(overlay: NoteContentModel.Drawing, document: CanvasDocument, position: Double): DrawBoardSettlement {
        val ink = DrawNoteContents.documentOf(overlay)
        val loose = looseOf(ink, document)
        val last = document.pages.lastOrNull()
        if (loose.isEmpty() || last == null) return DrawBoardSettlement(overlay, emptyList())
        val rank = ink.ordered.withIndex().associate { it.value.id to it.index }
        val anchored = document.pages.flatMap { page ->
            clusters(loose.filter { it.anchorId == page.id }).map { Cluster(page, it, trailing = false) }
        }
        val drifting = clusters(loose.filter { it.anchorId == null }.map { it.transformed(1f, -last.rect.x, -last.rect.y) })
            .map { Cluster(last, it, trailing = true) }
        var board = document
        val blocks = mutableListOf<DrawBoardBlock>()
        (anchored + drifting).filter(::hasVisibleInk).forEach { cluster ->
            val block = place(board, cluster, overlay.noteId, position + blocks.size, rank)
            board = board.adding(block.node).withReadingOrderOn(cluster.page.id)
            blocks.add(block)
        }
        val moved = loose.map { it.id }.toSet()
        val remaining = ink.withElements(ink.elements.filterNot { it.id in moved })
        return DrawBoardSettlement(DrawNoteContents.written(overlay, remaining), blocks)
    }

    private fun paperFrame(page: DrawBoardPage): DrawAnchorFrame =
        DrawAnchoring.mapped(page.pageId, page.x, page.y, page.width, page.height, page.contentX, page.contentY, page.scale)

    private fun rowFrames(document: CanvasDocument, page: DrawBoardPage, exact: Boolean): List<DrawAnchorFrame> {
        val paper = document.nodeById(page.pageId)?.takeIf { it.isPage } ?: return emptyList()
        val scale = if (page.scale > 0f) page.scale else 1f
        val columnScale = CanvasNode.widgetWidthOn(paper.rect.width) * scale / DrawAnchoring.REFERENCE_WIDTH
        val originX = page.contentX + CanvasNode.PAGE_PADDING * scale
        val visible = CanvasRect(page.x, page.y, page.width, page.height)
        return document.widgetsInReadingOrder(page.pageId).mapNotNull { widget ->
            val contentId = widget.contentId?.takeUnless { widget.hidden } ?: return@mapNotNull null
            val top = page.contentY + widget.rect.y * scale
            val reach = if (exact) {
                CanvasRect(page.contentX + widget.rect.x * scale, top, widget.rect.width * scale, widget.rect.height * scale)
            } else {
                CanvasRect(page.x, top, page.width, widget.rect.height * scale)
            }
            val hit = clipped(reach, visible) ?: return@mapNotNull null
            DrawAnchoring.mapped(contentId, hit.x, hit.y, hit.width, hit.height, originX, top, columnScale)
        }
    }

    private fun clipped(rect: CanvasRect, bounds: CanvasRect): CanvasRect? {
        val left = maxOf(rect.x, bounds.x)
        val top = maxOf(rect.y, bounds.y)
        val right = minOf(rect.right, bounds.right)
        val bottom = minOf(rect.bottom, bounds.bottom)
        return if (right > left && bottom > top) CanvasRect(left, top, right - left, bottom - top) else null
    }

    private fun looseOf(ink: DrawDocument, document: CanvasDocument): List<DrawElement> {
        val pageIds = document.pages.map { it.id }.toSet()
        return ink.visibleElements.filter { it.anchorId == null || it.anchorId in pageIds }
    }

    private fun hasVisibleInk(cluster: Cluster): Boolean =
        cluster.elements.any { !it.isErase && !DrawEraseGeometry.isErasedAway(it) }

    private fun clusters(elements: List<DrawElement>): List<List<DrawElement>> {
        val groups = mutableListOf<MutableList<DrawElement>>()
        var bottom = Float.NEGATIVE_INFINITY
        elements.sortedBy { it.bounds.y }.forEach { element ->
            if (groups.isEmpty() || element.bounds.y > bottom + CLUSTER_GAP) {
                groups.add(mutableListOf(element))
                bottom = element.bounds.bottom
            } else {
                groups.last().add(element)
                bottom = maxOf(bottom, element.bounds.bottom)
            }
        }
        return groups
    }

    private fun place(
        board: CanvasDocument,
        cluster: Cluster,
        noteId: String,
        position: Double,
        rank: Map<String, Int>
    ): DrawBoardBlock {
        val page = cluster.page
        val bounds = cluster.elements.map { it.bounds }.reduce { acc, rect -> acc.union(rect) }
        val columnX = CanvasNode.PAGE_PADDING
        val columnWidth = CanvasNode.widgetWidthOn(page.rect.width)
        val scale = if (bounds.width > columnWidth) columnWidth / bounds.width else 1f
        val width = bounds.width * scale
        val left = bounds.x.coerceIn(columnX, maxOf(columnX, columnX + columnWidth - width))
        val trailingTop = board.contentExtentOf(page.id).height.takeIf { it > 0f } ?: CanvasNode.PAGE_PADDING
        val drawnTop = bounds.y - BLOCK_PADDING
        val top = when {
            cluster.trailing -> trailingTop
            drawnTop > maxOf(trailingTop, page.rect.height) -> trailingTop
            else -> drawnTop.coerceAtLeast(0f)
        }
        val height = maxOf(bounds.height * scale + BLOCK_PADDING * 2f, CanvasNode.MIN_SIZE)
        val spot = board.freeSpotOn(page.id, CanvasRect(columnX, top, columnWidth, height), null)
        val offsetX = left - spot.x - bounds.x * scale
        val offsetY = BLOCK_PADDING - bounds.y * scale
        val blank = DrawDocuments.widget(spot.width, spot.height)
        val layer = blank.activeLayer.id
        val elements = cluster.elements
            .sortedBy { rank[it.id] ?: Int.MAX_VALUE }
            .mapIndexed { index, element ->
                element.transformed(scale, offsetX, offsetY).rehomed(layer, null).withOrder(index + 1.0)
            }
        val drawing = blank
            .withPaper(blank.paper.withTransparent(true))
            .withElements(elements)
            .withClock(elements.maxOf { it.clock })
        val content = DrawNoteContents.written(
            DrawNoteContents.create(noteId, position, DrawSurface.WIDGET, spot.width, spot.height),
            drawing
        )
        val node = CanvasNode.of(ContentType.DRAWING, content.id, spot.x, spot.y, spot.width, spot.height, parentId = page.id)
            .withSlotOrder(board.slotOrderAt(page.id, spot.x, spot.y, null))
        return DrawBoardBlock(content, node)
    }
}
