package com.app.pustakam.core.richtext.master.model

import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.common.util.ContentType.*
import com.app.pustakam.core.common.util.UniqueIdGenerator
import com.app.pustakam.core.common.util.isDoc

// 📄 23-Sep-2026 — what a node IS, independent of what it holds. A PAGE is paper: it owns no
//   content of its own and carries the widgets dropped on it. Everything else is a WIDGET and
//   points at exactly one NoteContent. Before this, "page" meant "top-level TEXT node", which is
//   why one page could only ever show one text block.
enum class CanvasRole { PAGE, WIDGET }

data class CanvasNode(
    val id: String,
    val kind: ContentType,
    val name: String = "",
    val rect: CanvasRect,
    val z: Int = 0,
    val contentId: String? = null,
    val parentId: String? = null,
    val locked: Boolean = false,
    val hidden: Boolean = false,
    val links: List<String> = emptyList(),
    val role: CanvasRole = CanvasRole.WIDGET,
    // 📄 a WIDGET's place in the note's single linear order. Double on purpose, exactly like
    //   NoteContentModel.position: dropping between 3 and 4 writes 3.5 and renumbers nothing.
    val slotOrder: Double = 0.0,
    // 📄 a PAGE's place among pages. Same fractional trick, so inserting a page is one row.
    val pageOrder: Double = 0.0
) {
    fun movedBy(deltaX: Float, deltaY: Float): CanvasNode =
        copy(rect = rect.translated(deltaX, deltaY))

    fun resizedTo(width: Float, height: Float): CanvasNode =
        copy(rect = rect.copy(width = maxOf(width, MIN_SIZE), height = maxOf(height, MIN_SIZE)))

    fun movedTo(x: Float, y: Float): CanvasNode = copy(rect = rect.copy(x = x, y = y))

    fun renamedTo(newName: String): CanvasNode = copy(name = newName)

    fun displayName(fallbackIndex: Int = 0): String = when {
        name.isNotBlank() -> name
        isPage -> "Page ${fallbackIndex + 1}"
        else -> defaultName(kind, fallbackIndex)
    }

    fun linkedTo(other: String): CanvasNode =
        if (links.contains(other)) this else copy(links = links + other)

    fun reparentedTo(newParentId: String?): CanvasNode = copy(parentId = newParentId)

    fun raisedTo(newZ: Int): CanvasNode = copy(z = newZ)

    val isPage: Boolean get() = role == CanvasRole.PAGE

    val isWidget: Boolean get() = role == CanvasRole.WIDGET

    val isTextWidget: Boolean get() = role == CanvasRole.WIDGET && kind == TEXT

    // 🧱 24-Sep-2026 — text and audio: the height is the content's own, measured on the device, never user-sized
    val isMeasured: Boolean get() = role == CanvasRole.WIDGET && isMeasuredKind(kind)

    fun withSlotOrder(newOrder: Double): CanvasNode = copy(slotOrder = newOrder)

    fun withPageOrder(newOrder: Double): CanvasNode = copy(pageOrder = newOrder)

    /** Re-homes a widget onto [pageId] at [order], keeping its size and its offset on the paper. */
    fun placedOn(pageId: String, order: Double): CanvasNode =
        copy(parentId = pageId, slotOrder = order, role = CanvasRole.WIDGET)

    companion object {
        const val MIN_SIZE = 24f

        // 🔧 09-Aug-2026: a 720-wide page fit a phone at ~0.5 zoom, which rendered 16sp body
        //   text at 8sp. Narrower paper keeps the fitted zoom — and the type — readable.
        const val DEFAULT_TEXT_WIDTH = 360f
        const val DEFAULT_TEXT_HEIGHT = 640f
        const val DEFAULT_GAP = 48f

        // 🧱 24-Sep-2026 — 8 between the paper's edge and its widgets, and 8 between two widgets: nothing else
        const val PAGE_PADDING = 8f

        const val WIDGET_GAP = 8f

        // 🧱 24-Sep-2026 — a picture, a video or a document is a compact 3:2 landscape card
        const val CARD_WIDTH = 240f

        const val CARD_HEIGHT = 160f

        // 🧱 24-Sep-2026 — a link or a place is a short card of the same width
        const val SHORT_CARD_HEIGHT = 120f

        /** Where a measured widget starts before the device reports its true height. */
        const val MEASURED_START_HEIGHT = 96f

        // 🧱 24-Sep-2026 — text grows as it is typed and audio is as tall as its player; everything else keeps its size
        fun isMeasuredKind(kind: ContentType): Boolean = kind == TEXT || kind == AUDIO

        fun isCardKind(kind: ContentType): Boolean =
            kind == IMAGE || kind == GIF || kind == VIDEO || kind.isDoc()

        fun isShortCardKind(kind: ContentType): Boolean = kind == LINK || kind == LOCATION

        /** A new widget's width on paper [paperWidth] wide: the paper, less its margins. */
        fun widgetWidthOn(paperWidth: Float): Float =
            maxOf(paperWidth - PAGE_PADDING * 2f, MIN_SIZE)

        // 🧱 24-Sep-2026 — cards stay compact on any paper; text, audio, tables and drawings take the paper's width
        fun widgetWidthFor(kind: ContentType, paperWidth: Float): Float {
            val full = widgetWidthOn(paperWidth)
            return if (isCardKind(kind) || isShortCardKind(kind)) minOf(CARD_WIDTH, full) else full
        }

        /** A new widget's height. Fixed from then on, except for measured kinds. */
        fun widgetHeightFor(kind: ContentType, width: Float): Float = when {
            isCardKind(kind) -> width * CARD_HEIGHT / CARD_WIDTH
            isShortCardKind(kind) -> SHORT_CARD_HEIGHT
            isMeasuredKind(kind) -> MEASURED_START_HEIGHT
            else -> DEFAULT_MEDIA_HEIGHT
        }
        const val DEFAULT_MEDIA_WIDTH = 420f
        const val DEFAULT_MEDIA_HEIGHT = 320f

        /** Body type on canvas paper sits a notch above the note editor's. */
        const val BASE_FONT_SCALE = 1.15f

        fun defaultName(kind: ContentType, index: Int): String = when (kind) {
        TEXT -> "Page ${index + 1}"
        AUDIO, IMAGE, VIDEO, GIF -> "Media ${index + 1}"
            TABLE -> "Table ${index + 1}"
        DOCX, PDF, TXT, MD, EPUB, OTHER -> "Document ${index + 1}"
           LINK -> "Link ${index + 1}"
           LOCATION -> "Location ${index + 1}"
            DRAWING -> "Drawing ${index + 1}"
            FORMULA -> ""
        }

        fun of(
            kind: ContentType,
            contentId: String?,
            x: Float,
            y: Float,
            width: Float = DEFAULT_TEXT_WIDTH,
            height: Float = DEFAULT_TEXT_HEIGHT,
            parentId: String? = null,
            name: String = ""
        ): CanvasNode = CanvasNode(
            id = UniqueIdGenerator.generateUniqueId(),
            kind = kind,
            name = name,
            rect = CanvasRect(x, y, width, height),
            contentId = contentId,
            parentId = parentId
        )

        fun nextTo(
            anchor: CanvasNode,
            kind: ContentType,
            contentId: String?,
            width: Float = DEFAULT_TEXT_WIDTH,
            height: Float = DEFAULT_TEXT_HEIGHT,
            gap: Float = DEFAULT_GAP
        ): CanvasNode = of(
            kind = kind,
            contentId = contentId,
            x = anchor.rect.right + gap,
            y = anchor.rect.y,
            width = width,
            height = height,
            parentId = anchor.id
        )

        /** Empty paper. A page holds no content of its own — widgets sit on it by parentId. */
        fun page(
            order: Double,
            x: Float = 0f,
            y: Float = 0f,
            width: Float = DEFAULT_TEXT_WIDTH,
            height: Float = DEFAULT_TEXT_HEIGHT
        ): CanvasNode = CanvasNode(
            id = UniqueIdGenerator.generateUniqueId(),
            kind = ContentType.TEXT,
            rect = CanvasRect(x, y, width, height),
            contentId = null,
            parentId = null,
            role = CanvasRole.PAGE,
            pageOrder = order
        )

        fun masterText(
            contentId: String?,
            x: Float = 0f,
            y: Float = 0f,
            width: Float = DEFAULT_TEXT_WIDTH,
            height: Float = DEFAULT_TEXT_HEIGHT
        ): CanvasNode = CanvasNode(
            id = UniqueIdGenerator.generateUniqueId(),
            kind = ContentType.TEXT,
            rect = CanvasRect(x, y, width, height),
            contentId = contentId
        )
    }
}

data class CanvasDocument(
    val nodes: List<CanvasNode> = emptyList()
) {
    /** 📄 the board's extent is its paper — widgets live INSIDE a page and never widen it. */
    val bounds: CanvasRect
        get() = pages.map { it.rect }.reduceOrNull { acc, rect -> acc.union(rect) } ?: CanvasRect()

    /** Top-level paper, in reading order. Widgets live on a page through [CanvasNode.parentId]. */
    val pages: List<CanvasNode>
        get() = nodes.filter { it.isPage }.sortedBy { it.pageOrder }

    /** Every widget in the note's single linear order — the order the reader pages through. */
    val orderedWidgets: List<CanvasNode>
        get() = nodes.filter { it.isWidget }.sortedBy { it.slotOrder }

    /** The widgets sitting on [pageId], in slot order. */
    fun widgetsOf(pageId: String): List<CanvasNode> =
        nodes.filter { it.isWidget && it.parentId == pageId }.sortedBy { it.slotOrder }

    /** The widgets on [pageId] top to bottom, then left to right — the order the eye reads them. */
    fun widgetsInReadingOrder(pageId: String): List<CanvasNode> =
        nodes.filter { it.isWidget && it.parentId == pageId }.sortedWith(readingOrder)

    /**
     * 📄 23-Sep-2026 — a page's rect is where it sits on the board. A WIDGET's rect is where it
     * sits on its page's CONTENT, measured from the content's top-left before any scroll — so a
     * page can move, shrink or scroll without its widgets ever changing.
     */
    fun absoluteRectOf(node: CanvasNode): CanvasRect {
        if (node.isPage) return node.rect
        val page = node.parentId?.let { nodeById(it) }?.takeIf { it.isPage } ?: return node.rect
        return node.rect.translated(page.rect.x, page.rect.y)
    }

    /**
     * How far a page's content reaches, from its top-left: far enough right and down to show
     * every widget on it with a margin. The platform takes the larger of this and the paper's
     * own viewport, and that is what the page scrolls over — vertically and horizontally.
     */
    fun contentExtentOf(pageId: String): CanvasRect {
        val widgets = nodes.filter { it.isWidget && it.parentId == pageId }
        val right = widgets.maxOfOrNull { it.rect.right }?.plus(CanvasNode.PAGE_PADDING) ?: 0f
        val bottom = widgets.maxOfOrNull { it.rect.bottom }?.plus(CanvasNode.PAGE_PADDING) ?: 0f
        return CanvasRect(0f, 0f, right, bottom)
    }

    fun nodeById(nodeId: String): CanvasNode? = nodes.firstOrNull { it.id == nodeId }

    fun nodeForContent(contentId: String): CanvasNode? =
        nodes.firstOrNull { it.contentId == contentId }

    fun childrenOf(nodeId: String): List<CanvasNode> = nodes.filter { it.parentId == nodeId }

    fun descendantsOf(nodeId: String): List<CanvasNode> {
        val direct = childrenOf(nodeId)
        return direct + direct.flatMap { descendantsOf(it.id) }
    }

    /** The page a point lands on, topmost first — this is what a drop re-parents to. */
    fun pageAt(canvasX: Float, canvasY: Float): CanvasNode? =
        pages.filterNot { it.hidden }.sortedBy { it.z }
            .lastOrNull { it.rect.contains(canvasX, canvasY) }

    fun pageOf(nodeId: String): CanvasNode? =
        nodeById(nodeId)?.let { node ->
            if (node.isPage) node else node.parentId?.let { pageOf(it) }
        }

    // 📄 24-Sep-2026 — the page covering most of [area]; null when no page is in it at all
    fun mostVisiblePage(area: CanvasRect): CanvasNode? =
        pages.filterNot { it.hidden }
            .map { it to it.rect.overlapArea(area) }
            .filter { it.second > 0f }
            .maxByOrNull { it.second }
            ?.first

    fun overlaps(node: CanvasNode): CanvasNode? =
        pages.firstOrNull { it.id != node.id && it.rect.intersects(node.rect) }

    /** Slides a page right past every page it would cover — used when the page itself moved. */
    fun withoutOverlap(node: CanvasNode, gap: Float): CanvasNode {
        var placed = node
        var guard = 0
        while (guard < MAX_SETTLE_STEPS) {
            val clash = overlaps(placed) ?: return placed
            placed = placed.movedTo(clash.rect.right + gap, placed.rect.y)
            guard++
        }
        return placed
    }

    // ---- 🧱 23-Sep-2026: nothing ever overlaps — not pages, not widgets, at any zoom ----

    /**
     * Where [rect] can sit on [pageId] without covering another widget: the same x, slid
     * straight down past whatever it would land on. This is how a dropped or newly added
     * widget finds its spot.
     */
    fun freeSpotOn(pageId: String, rect: CanvasRect, excluding: String?): CanvasRect {
        val obstacles = nodes
            .filter { it.isWidget && it.parentId == pageId && it.id != excluding }
            .map { it.rect }
        return slidDownClear(rect, obstacles)
    }

    /**
     * After [anchorId] grew — a text widget being typed into — every widget it now covers
     * moves down just clear of it, and whatever THAT one covers moves down in turn. The anchor
     * never moves, and nothing is ever moved up.
     */
    fun pushedClearOf(anchorId: String): CanvasDocument {
        val anchor = nodeById(anchorId)?.takeIf { it.isWidget } ?: return this
        val pageId = anchor.parentId ?: return this
        val settled = mutableListOf(anchor.rect)
        val moved = mutableListOf<CanvasNode>()
        nodes.filter { it.isWidget && it.parentId == pageId && it.id != anchorId }
            .sortedWith(readingOrder)
            .forEach { widget ->
                val rect = slidDownClear(widget.rect, settled)
                if (rect != widget.rect) moved.add(widget.copy(rect = rect))
                settled.add(rect)
            }
        return replacingAll(moved).withReadingOrderOn(pageId)
    }

    /**
     * After [anchorId] changed size — fitted to the screen or resized by its handle — every
     * page it now covers moves right just clear of it, and so on down the row. The anchor
     * itself never moves: it is the page the user is looking at.
     */
    fun pagesClearOf(anchorId: String): CanvasDocument {
        val anchor = nodeById(anchorId)?.takeIf { it.isPage } ?: return this
        val settled = mutableListOf(anchor.rect)
        val moved = mutableListOf<CanvasNode>()
        pages.filter { it.id != anchorId && !it.hidden }
            .sortedWith(compareBy({ it.rect.x }, { it.rect.y }))
            .forEach { page ->
                val rect = slidRightClear(page.rect, settled)
                if (rect != page.rect) moved.add(page.movedTo(rect.x, rect.y))
                settled.add(rect)
            }
        return replacingAll(moved)
    }

    // 🧱 24-Sep-2026 — one page laid out afresh: a single column in reading order, every widget at its kind's size
    fun restacked(pageId: String, paperWidth: Float): CanvasDocument {
        var top = CanvasNode.PAGE_PADDING
        val stacked = widgetsInReadingOrder(pageId).map { widget ->
            val width = CanvasNode.widgetWidthFor(widget.kind, paperWidth)
            val height = CanvasNode.widgetHeightFor(widget.kind, width)
            val placed = widget.copy(rect = CanvasRect(CanvasNode.PAGE_PADDING, top, width, height))
            top += height + CanvasNode.WIDGET_GAP
            placed
        }
        return replacingAll(stacked).withReadingOrderOn(pageId)
    }

    // 🔄 24-Sep-2026 — another device's canvas laid over this one: its nodes win, except the ones touched here meanwhile and this device's own measured heights
    fun mergedWith(remote: List<CanvasNode>, keepLocal: Set<String>): CanvasDocument {
        val local = nodes.associateBy { it.id }
        val remoteIds = remote.map { it.id }.toSet()
        // 🔄 a content the other device already placed never gets a second widget from here
        val placedThere = remote.filter { it.isWidget }.mapNotNull { it.contentId }.toSet()
        val merged = remote.mapNotNull { incoming ->
            val mine = local[incoming.id]
            when {
                incoming.id in keepLocal -> mine
                mine != null && mine.isMeasured && incoming.isMeasured ->
                    incoming.copy(rect = incoming.rect.copy(height = mine.rect.height))

                else -> incoming
            }
        } + nodes.filter { node ->
            node.id !in remoteIds && node.id in keepLocal &&
                !(node.isWidget && node.contentId != null && node.contentId in placedThere)
        }
        val document = CanvasDocument(merged)
        return document.pages.fold(document) { acc, page -> acc.settledWidgetsOn(page.id) }
    }

    /** Every widget on [pageId] settled so none overlaps another, top to bottom. */
    fun settledWidgetsOn(pageId: String): CanvasDocument {
        val settled = mutableListOf<CanvasRect>()
        val moved = mutableListOf<CanvasNode>()
        widgetsInReadingOrder(pageId).forEach { widget ->
            val rect = slidDownClear(widget.rect, settled)
            if (rect != widget.rect) moved.add(widget.copy(rect = rect))
            settled.add(rect)
        }
        return replacingAll(moved).withReadingOrderOn(pageId)
    }

    /**
     * Keeps a page's slot orders in step with what the eye sees. The page's own set of order
     * values is re-dealt top-to-bottom, so the page never borrows a value from its neighbours
     * and a page that already reads in order is returned untouched.
     */
    fun withReadingOrderOn(pageId: String): CanvasDocument {
        val reading = widgetsInReadingOrder(pageId)
        val values = reading.map { it.slotOrder }.sorted()
        val restamped = reading.zip(values)
            .filter { (widget, order) -> widget.slotOrder != order }
            .map { (widget, order) -> widget.withSlotOrder(order) }
        return replacingAll(restamped)
    }

    private fun slidDownClear(rect: CanvasRect, obstacles: List<CanvasRect>): CanvasRect {
        var placed = rect
        repeat(obstacles.size + 1) {
            val clashes = obstacles.filter { it.intersects(placed) }
            if (clashes.isEmpty()) return placed
            placed = placed.copy(y = clashes.maxOf { it.bottom } + CanvasNode.WIDGET_GAP)
        }
        return placed
    }

    private fun slidRightClear(rect: CanvasRect, obstacles: List<CanvasRect>): CanvasRect {
        var placed = rect
        repeat(obstacles.size + 1) {
            val clashes = obstacles.filter { it.intersects(placed) }
            if (clashes.isEmpty()) return placed
            placed = placed.copy(x = clashes.maxOf { it.right } + CanvasNode.DEFAULT_GAP)
        }
        return placed
    }

    fun replacingAll(updated: List<CanvasNode>): CanvasDocument {
        if (updated.isEmpty()) return this
        val byId = updated.associateBy { it.id }
        return copy(nodes = nodes.map { byId[it.id] ?: it })
    }

    fun replacing(node: CanvasNode): CanvasDocument {
        val index = nodes.indexOfFirst { it.id == node.id }
        if (index < 0) return copy(nodes = nodes + node)
        return copy(nodes = nodes.toMutableList().apply { set(index, node) })
    }

    fun adding(node: CanvasNode): CanvasDocument = copy(nodes = nodes + node)

    fun removing(nodeId: String): CanvasDocument = copy(nodes = nodes.filterNot { it.id == nodeId })

    fun broughtToFront(nodeId: String): CanvasDocument {
        val top = (nodes.maxOfOrNull { it.z } ?: 0) + 1
        return nodeById(nodeId)?.let { replacing(it.copy(z = top)) } ?: this
    }

    /** 📄 the board draws PAPER only; each page draws its own widgets inside itself. */
    fun inDrawOrder(): List<CanvasNode> = pages.filterNot { it.hidden }.sortedBy { it.z }

    fun visibleIn(rect: CanvasRect, overscan: Float = OVERSCAN): List<CanvasNode> {
        val area = rect.inflated(overscan)
        return inDrawOrder().filter { it.rect.intersects(area) }
    }

    fun hitTest(canvasX: Float, canvasY: Float): CanvasNode? =
        inDrawOrder().lastOrNull { !it.locked && it.rect.contains(canvasX, canvasY) }

    fun linkedTo(nodeId: String): List<CanvasNode> {
        val node = nodeById(nodeId) ?: return emptyList()
        val outgoing = node.links.mapNotNull { nodeById(it) }
        val incoming = nodes.filter { it.links.contains(nodeId) }
        return (outgoing + incoming).distinctBy { it.id }
    }

    /** Order for a page appended after the last one. */
    fun nextPageOrder(): Double = (pages.lastOrNull()?.pageOrder ?: -1.0) + 1.0

    /** Order for a widget appended after every existing one. */
    fun nextSlotOrder(): Double = (orderedWidgets.lastOrNull()?.slotOrder ?: -1.0) + 1.0

    /**
     * 📄 23-Sep-2026 — where a widget placed at ([x], [y]) on [pageId] lands in the note's linear
     * order: between its reading-order neighbours on that page, or between this page and the
     * next when it is first or last on it. Always a fractional midpoint, so placing a widget
     * writes ONE order value and renumbers nothing.
     */
    fun slotOrderAt(pageId: String, x: Float, y: Float, excluding: String?): Double {
        val onPage = widgetsInReadingOrder(pageId).filterNot { it.id == excluding }
        val index = onPage.indexOfFirst { it.rect.y > y || (it.rect.y == y && it.rect.x > x) }
        val before = if (index < 0) onPage.lastOrNull() else onPage.getOrNull(index - 1)
        val after = if (index < 0) null else onPage.getOrNull(index)
        val lower = before?.slotOrder ?: previousSlotBoundary(pageId, excluding)
        val upper = after?.slotOrder ?: nextSlotBoundary(pageId, excluding)
        return when {
            lower == null && upper == null -> 0.0
            lower == null -> upper!! - 1.0
            upper == null -> lower + 1.0
            lower < upper -> (lower + upper) / 2.0
            else -> lower
        }
    }

    /** Highest slot order still ahead of [pageId] — the floor for a drop at the top of it. */
    private fun previousSlotBoundary(pageId: String, excluding: String?): Double? {
        val order = nodeById(pageId)?.pageOrder ?: return null
        return pages.filter { it.pageOrder < order }
            .flatMap { widgetsOf(it.id) }
            .filterNot { it.id == excluding }
            .maxOfOrNull { it.slotOrder }
    }

    /** Lowest slot order after [pageId] — the ceiling for a drop at the bottom of it. */
    private fun nextSlotBoundary(pageId: String, excluding: String?): Double? {
        val order = nodeById(pageId)?.pageOrder ?: return null
        return pages.filter { it.pageOrder > order }
            .flatMap { widgetsOf(it.id) }
            .filterNot { it.id == excluding }
            .minOfOrNull { it.slotOrder }
    }

    companion object {
        const val OVERSCAN = 240f

        private const val MAX_SETTLE_STEPS = 256

        /** Top to bottom, then left to right. */
        val readingOrder: Comparator<CanvasNode> = compareBy({ it.rect.y }, { it.rect.x })
    }
}
