package com.app.pustakam.core.filesys.canvas

import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.common.util.UniqueIdGenerator
import com.app.pustakam.core.filesys.reader.PageLayoutEngine
import com.app.pustakam.core.filesys.reader.PageLayoutPolicy
import com.app.pustakam.core.filesys.reader.ReaderBlockBuilder
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import com.app.pustakam.core.richtext.master.model.CanvasDocument
import com.app.pustakam.core.richtext.master.model.CanvasNode
import com.app.pustakam.core.richtext.master.model.CanvasRect
import com.app.pustakam.core.richtext.master.model.CanvasRole

/**
 * 📄 23-Sep-2026 — decides which canvas PAGE each note content sits on.
 *
 * It does not invent a layout algorithm: it feeds the note's contents through the SAME
 * [ReaderBlockBuilder] + [PageLayoutEngine] the book reader uses, so a run of consecutive images
 * is one block on one page, a run of text is one block, and a page holds as many blocks as fit.
 * The reader and the editor therefore break pages in the same places.
 *
 * Once the pages exist they are stable. A page is a viewport onto its content: a text widget that
 * grows while it is typed into pushes the widgets under it down and the page scrolls, it never
 * spills onto the next page. Re-running this object is an explicit action ("rebuild layout"), the
 * seeding of a note that has no canvas yet, or the upgrade of an old one.
 */
object CanvasPaginator {

    /** Space between two pages laid out left to right on the canvas. */
    const val PAGE_GAP = CanvasNode.DEFAULT_GAP

    fun policy(pageWidth: Float, pageHeight: Float): PageLayoutPolicy =
        PageLayoutPolicy.forScreen(pageWidth, pageHeight)

    fun defaultPolicy(): PageLayoutPolicy =
        policy(CanvasNode.DEFAULT_TEXT_WIDTH, CanvasNode.DEFAULT_TEXT_HEIGHT)

    /**
     * contentId -> page index, from the reading-mode grouping.
     *
     * [ReaderBlockBuilder] drops blank text blocks because a reader has nothing to show for them;
     * an editor must still show them, so anything the builder left out inherits the page of the
     * content before it.
     */
    fun pageIndexOfContents(
        contents: List<NoteContentModel>,
        policy: PageLayoutPolicy
    ): Map<String, Int> {
        val ordered = contents.sortedBy { it.position }
        val blocks = ReaderBlockBuilder.buildBlocks(ordered, policy)
        val byBlock = mutableMapOf<String, Int>()
        PageLayoutEngine.paginate(blocks, policy).forEach { page ->
            page.sourceContentIds.forEach { id -> byBlock[id] = page.index }
        }
        val resolved = mutableMapOf<String, Int>()
        var previous = 0
        ordered.forEach { content ->
            val index = byBlock[content.id]
            if (index == null) {
                resolved[content.id] = previous
            } else {
                resolved[content.id] = index
                previous = index
            }
        }
        return resolved
    }

    /** How many pages [contents] need — never fewer than one, so an empty note still shows paper. */
    fun pageCountFor(contents: List<NoteContentModel>, policy: PageLayoutPolicy): Int {
        if (contents.isEmpty()) return 1
        val highest = pageIndexOfContents(contents, policy).values.maxOrNull() ?: 0
        return highest + 1
    }

    /** Swift-facing overload — Kotlin default arguments are not exposed to Swift. */
    fun buildDefault(contents: List<NoteContentModel>): CanvasDocument =
        build(contents, CanvasNode.DEFAULT_TEXT_WIDTH, CanvasNode.DEFAULT_TEXT_HEIGHT)

    /**
     * A whole canvas from a note's contents: pages laid left to right, each carrying the contents
     * the reading-mode engine grouped onto it, stacked down the paper in reading order.
     */
    fun build(
        contents: List<NoteContentModel>,
        pageWidth: Float,
        pageHeight: Float
    ): CanvasDocument {
        val policy = policy(pageWidth, pageHeight)
        val ordered = contents.sortedBy { it.position }
        val index = pageIndexOfContents(ordered, policy)
        val pages = pagesFor(pageCountFor(ordered, policy), pageWidth, pageHeight)

        val cursor = HashMap<String, Float>()
        var z = pages.size
        val widgets = ordered.mapIndexed { slot, content ->
            val page = pages.getOrElse(index[content.id] ?: 0) { pages.first() }
            widgetOn(page, content, slot.toDouble(), z++, cursor)
        }
        return settledPages(CanvasDocument(pages + widgets))
    }

    /** An empty canvas still shows one page, so there is always somewhere to start writing. */
    fun ensurePage(
        document: CanvasDocument,
        pageWidth: Float,
        pageHeight: Float
    ): CanvasDocument =
        if (document.pages.isNotEmpty()) document
        else document.copy(
            nodes = pagesFor(1, pageWidth, pageHeight) + document.nodes
        )

    /**
     * Swift-facing overload: Swift must not construct [CanvasDocument] itself, because it carries
     * a default argument (interop rule 2).
     */
    fun ensurePageIn(
        nodes: List<CanvasNode>,
        pageWidth: Float,
        pageHeight: Float
    ): CanvasDocument = ensurePage(CanvasDocument(nodes), pageWidth, pageHeight)

    // ---- old canvases ----

    /**
     * True for a canvas written before pages became containers: every node carried its own
     * content, so nothing in it is a PAGE.
     */
    fun needsUpgrade(nodes: List<CanvasNode>): Boolean =
        nodes.isNotEmpty() && nodes.none { it.isPage }

    /** True when [ensurePage] would have to mint paper — the canvas has none. */
    fun pagesMissing(nodes: List<CanvasNode>): Boolean = nodes.none { it.isPage }

    /**
     * 📄 Upgrades a stored canvas in place. Each old top-level node KEEPS its id, name and rect
     * and becomes the paper; the content it used to carry becomes the first widget on it, and
     * everything that hung off it becomes the widgets after that, in the order they were drawn.
     * Nothing is dropped — every content that had a node still has one.
     */
    fun upgrade(nodes: List<CanvasNode>, pageWidth: Float, pageHeight: Float): CanvasDocument {
        if (!needsUpgrade(nodes)) return ensurePage(CanvasDocument(nodes), pageWidth, pageHeight)
        val old = CanvasDocument(nodes)
        val roots = nodes.filter { it.parentId == null }.sortedWith(readingOrder)
        if (roots.isEmpty()) return ensurePage(CanvasDocument(), pageWidth, pageHeight)

        // only a TEXT root was ever paper; a stray media node at the top level is a widget that
        // lost its page, and promoting it would turn a photo into a sheet of paper
        val pageRoots = roots.filter { it.kind == ContentType.TEXT }.ifEmpty { roots.take(1) }
        val strays = roots.filterNot { root -> pageRoots.any { it.id == root.id } }
            .flatMap { listOf(it) + old.descendantsOf(it.id) }

        val pages = mutableListOf<CanvasNode>()
        val widgets = mutableListOf<CanvasNode>()
        var slot = 0.0
        var z = pageRoots.size

        pageRoots.forEachIndexed { pageIndex, root ->
            val page = root.copy(
                role = CanvasRole.PAGE,
                contentId = null,
                parentId = null,
                pageOrder = pageIndex.toDouble(),
                z = pageIndex,
                links = emptyList()
            )
            pages.add(page)
            // the text the old page stood for becomes the first thing written on it
            root.contentId?.let { contentId ->
                widgets.add(
                    CanvasNode(
                        id = UniqueIdGenerator.generateUniqueId(),
                        kind = root.kind,
                        rect = CanvasRect(
                            x = CanvasNode.PAGE_PADDING,
                            y = CanvasNode.PAGE_PADDING,
                            width = CanvasNode.widgetWidthOn(root.rect.width),
                            height = CanvasNode.MEASURED_START_HEIGHT
                        ),
                        z = z++,
                        contentId = contentId,
                        parentId = page.id,
                        role = CanvasRole.WIDGET,
                        slotOrder = slot++
                    )
                )
            }
            val adopted =
                if (pageIndex == 0) old.descendantsOf(root.id) + strays
                else old.descendantsOf(root.id)
            adopted.distinctBy { it.id }.sortedWith(readingOrder).forEach { child ->
                widgets.add(
                    child.copy(
                        parentId = page.id,
                        role = CanvasRole.WIDGET,
                        slotOrder = slot++,
                        z = z++,
                        links = emptyList(),
                        // absolute -> relative to the paper it now belongs to
                        rect = child.rect.translated(-root.rect.x, -root.rect.y)
                    )
                )
            }
        }
        return settledPages(CanvasDocument(pages + widgets))
    }

    // ---- internals ----

    private fun pagesFor(count: Int, pageWidth: Float, pageHeight: Float): List<CanvasNode> =
        (0 until maxOf(count, 1)).map { index ->
            CanvasNode.page(
                order = index.toDouble(),
                x = index * (pageWidth + PAGE_GAP),
                y = 0f,
                width = pageWidth,
                height = pageHeight
            ).copy(z = index)
        }

    private fun widgetOn(
        page: CanvasNode,
        content: NoteContentModel,
        slot: Double,
        z: Int,
        cursor: HashMap<String, Float>
    ): CanvasNode {
        val top = cursor[page.id] ?: CanvasNode.PAGE_PADDING
        val width = CanvasNode.widgetWidthFor(content.type, page.rect.width)
        val height = CanvasNode.widgetHeightFor(content.type, width)
        cursor[page.id] = top + height + CanvasNode.WIDGET_GAP
        return CanvasNode.of(
            kind = content.type,
            contentId = content.id,
            x = CanvasNode.PAGE_PADDING,
            y = top,
            width = width,
            height = height,
            parentId = page.id
        ).copy(z = z, role = CanvasRole.WIDGET, slotOrder = slot)
    }

    /** 🧱 no widget ever overlaps another — an upgraded canvas may have stacked them. */
    private fun settledPages(document: CanvasDocument): CanvasDocument =
        document.pages.fold(document) { acc, page -> acc.settledWidgetsOn(page.id) }

    private val readingOrder =
        compareBy<CanvasNode>({ it.rect.y }, { it.rect.x })
}
