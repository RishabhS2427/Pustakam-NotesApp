package com.app.pustakam.core.richtext.master.presentation

import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.richtext.master.model.CanvasDocument
import com.app.pustakam.core.richtext.master.model.CanvasNode
import com.app.pustakam.core.richtext.master.model.CanvasRole

import com.app.pustakam.core.richtext.master.model.CanvasRect
import com.app.pustakam.core.richtext.master.model.Viewport
import kotlin.math.abs

enum class CanvasTool {
    SELECT,
    HAND,
    ZOOM,
    LOCK
}

enum class CanvasGesture {
    NONE,
    DRAGGING,
    RESIZING,
    EDITING
}

data class CanvasPermits(
    val canSelect: Boolean,
    val canPan: Boolean,
    val canZoom: Boolean,
    val canDragNode: Boolean,
    val canResizeNode: Boolean,
    val canEditText: Boolean,
    val canMutateDocument: Boolean
)

/**
 * 📄 23-Sep-2026 — a one-shot ask for the platform to scroll [pageId]'s content to its end. The
 * scroll offset itself lives in the platform's scroll view; [token] only grows, so the platform
 * acts on each request exactly once.
 */
data class PageScrollRequest(val pageId: String, val token: Int)

data class CanvasEditorState(
    val document: CanvasDocument = CanvasDocument(),
    val viewport: Viewport = Viewport(),
    val tool: CanvasTool = CanvasTool.SELECT,
    val selectedNodeId: String? = null,
    val draggingNodeId: String? = null,
    val editingNodeId: String? = null,
    val resizingNodeId: String? = null,
    val focusedRect: CanvasRect? = null,
    val scrollRequest: PageScrollRequest? = null
) {
    val visibleNodes: List<CanvasNode>
        get() = document.visibleIn(viewport.visibleRect)

    val selectedNode: CanvasNode? get() = selectedNodeId?.let { document.nodeById(it) }

    val zoomPercent: Int get() = viewport.percent

    fun isVisible(nodeId: String): Boolean = visibleNodes.any { it.id == nodeId }

    val gesture: CanvasGesture
        get() = when {
            editingNodeId != null -> CanvasGesture.EDITING
            resizingNodeId != null -> CanvasGesture.RESIZING
            draggingNodeId != null -> CanvasGesture.DRAGGING
            else -> CanvasGesture.NONE
        }

    val permits: CanvasPermits
        get() = when (gesture) {
            CanvasGesture.EDITING -> CanvasPermits(
                canSelect = false,
                canPan = false,
                canZoom = false,
                canDragNode = false,
                canResizeNode = false,
                canEditText = true,
                canMutateDocument = true
            )

            CanvasGesture.RESIZING -> CanvasPermits(
                canSelect = false,
                canPan = false,
                canZoom = false,
                canDragNode = false,
                canResizeNode = true,
                canEditText = false,
                canMutateDocument = false
            )

            CanvasGesture.DRAGGING -> CanvasPermits(
                canSelect = false,
                canPan = false,
                canZoom = false,
                canDragNode = true,
                canResizeNode = false,
                canEditText = false,
                canMutateDocument = false
            )

            CanvasGesture.NONE -> when (tool) {
                CanvasTool.LOCK -> CanvasPermits(
                    canSelect = true,
                    canPan = false,
                    canZoom = false,
                    canDragNode = false,
                    canResizeNode = false,
                    canEditText = false,
                    canMutateDocument = false
                )

                CanvasTool.ZOOM -> CanvasPermits(
                    canSelect = false,
                    canPan = true,
                    canZoom = true,
                    canDragNode = false,
                    canResizeNode = false,
                    canEditText = false,
                    canMutateDocument = false
                )

                CanvasTool.HAND -> CanvasPermits(
                    canSelect = false,
                    canPan = true,
                    canZoom = true,
                    canDragNode = false,
                    canResizeNode = false,
                    canEditText = false,
                    canMutateDocument = false
                )

                CanvasTool.SELECT -> CanvasPermits(
                    canSelect = true,
                    canPan = true,
                    canZoom = true,
                    canDragNode = true,
                    canResizeNode = true,
                    canEditText = true,
                    canMutateDocument = true
                )
            }
        }
}

sealed class CanvasEditorIntent {

    data class ViewportResized(val width: Float, val height: Float) : CanvasEditorIntent()

    data class Pan(val deltaX: Float, val deltaY: Float) : CanvasEditorIntent()

    data class Zoom(val factor: Float, val focusX: Float, val focusY: Float) : CanvasEditorIntent()

    data class ZoomTo(val scale: Float, val focusX: Float, val focusY: Float) : CanvasEditorIntent()

    data object ZoomIn : CanvasEditorIntent()

    data object ZoomOut : CanvasEditorIntent()

    data object ZoomToFit : CanvasEditorIntent()

    data class FocusNode(val nodeId: String) : CanvasEditorIntent()

    /** A tap on paper: fit the page, scroll it to its end, keyboard away. */
    data class FocusPage(val pageId: String) : CanvasEditorIntent()

    /**
     * A lifted widget let go. [pageId] null means it was released over bare canvas, and it
     * goes back exactly where it came from; otherwise ([x], [y]) is its top-left on that page's
     * content.
     */
    data class DropWidget(
        val nodeId: String,
        val pageId: String?,
        val x: Float,
        val y: Float
    ) : CanvasEditorIntent()

    /** The device measured a widget's content; its height follows, never its width. */
    data class MeasureWidget(val nodeId: String, val height: Float) : CanvasEditorIntent()

    data class SelectAt(val screenX: Float, val screenY: Float) : CanvasEditorIntent()

    data class SelectNode(val nodeId: String?) : CanvasEditorIntent()

    data class BeginDrag(val nodeId: String) : CanvasEditorIntent()

    data class DragBy(val deltaX: Float, val deltaY: Float) : CanvasEditorIntent()

    data object EndDrag : CanvasEditorIntent()

    data class BeginResize(val nodeId: String) : CanvasEditorIntent()

    data object EndResize : CanvasEditorIntent()

    data class ResizeNode(val nodeId: String, val width: Float, val height: Float) :
        CanvasEditorIntent()

    data class AddNode(val node: CanvasNode) : CanvasEditorIntent()

    data class RemoveNode(val nodeId: String) : CanvasEditorIntent()

    data class SetTool(val tool: CanvasTool) : CanvasEditorIntent()

    data class SetEditing(val nodeId: String?) : CanvasEditorIntent()

    data class LinkNodes(val fromId: String, val toId: String) : CanvasEditorIntent()

    data class RenameNode(val nodeId: String, val name: String) : CanvasEditorIntent()

    data class ReparentNode(val nodeId: String, val parentId: String?) : CanvasEditorIntent()

    data class ReplaceDocument(val document: CanvasDocument) : CanvasEditorIntent()

    // 🔄 24-Sep-2026 — the canvas another device saved arrived; [keepLocal] are the nodes touched here since
    data class AdoptRemote(val nodes: List<CanvasNode>, val keepLocal: Set<String>) : CanvasEditorIntent()

    // 📄 24-Sep-2026 — scroll a page to its end without fitting it, e.g. after a widget was added there
    data class RevealEnd(val pageId: String) : CanvasEditorIntent()
}

const val EDIT_TOP_INSET = 12f

const val PAGE_SCREEN_MARGIN = 16f

// 🧱 24-Sep-2026 — how far a drop may land from where the widget was and still count as not moved
const val DROP_TOLERANCE = 2f

object CanvasEditorReducer {

    fun reduce(state: CanvasEditorState, intent: CanvasEditorIntent): CanvasEditorState =
        if (blocked(state, intent)) state else apply(state, intent)

    /**
     * 📄 23-Sep-2026 — fits [page] to the screen at 100%. Only the PAPER takes the screen's size;
     * the widgets on it keep theirs, and whatever no longer fits is scrolled to inside the page.
     * Neighbouring pages move aside, so a fitted page never lands on another one.
     */
    private fun fittedToScreen(state: CanvasEditorState, page: CanvasNode): CanvasEditorState {
        val viewport = state.viewport
        if (viewport.widthPx <= 0f || viewport.heightPx <= 0f) return state
        val width = viewport.widthPx - PAGE_SCREEN_MARGIN * 2f
        val height = viewport.heightPx - PAGE_SCREEN_MARGIN * 2f
        if (width <= CanvasNode.MIN_SIZE || height <= CanvasNode.MIN_SIZE) return state

        val resized = page.resizedTo(width, height)
        return state.copy(
            document = state.document.replacing(resized).pagesClearOf(resized.id),
            viewport = viewport.copy(
                scale = Viewport.DEFAULT_SCALE,
                offsetX = PAGE_SCREEN_MARGIN - resized.rect.x,
                offsetY = PAGE_SCREEN_MARGIN - resized.rect.y
            )
        )
    }

    /** A tap on paper: the page is fitted, scrolled to its end, and the keyboard goes away. */
    private fun focusedPage(state: CanvasEditorState, page: CanvasNode): CanvasEditorState =
        fittedToScreen(
            state.copy(
                selectedNodeId = page.id,
                editingNodeId = null,
                focusedRect = null,
                scrollRequest = PageScrollRequest(page.id, (state.scrollRequest?.token ?: 0) + 1)
            ),
            page
        )

    /**
     * A text widget taking the keyboard: its page is fitted to the screen and the platform keeps
     * the caret in view inside the page, exactly as when a page was one text field.
     */
    private fun editingText(state: CanvasEditorState, widget: CanvasNode): CanvasEditorState {
        val editing = state.copy(
            editingNodeId = widget.id,
            draggingNodeId = null,
            resizingNodeId = null,
            focusedRect = state.focusedRect ?: state.viewport.visibleRect
        )
        val page = state.document.pageOf(widget.id) ?: return editing
        return fittedToScreen(editing.copy(selectedNodeId = page.id), page)
    }

    /** The keyboard going away: back to the view the writer had before editing began. */
    private fun leftEditing(state: CanvasEditorState): CanvasEditorState = state.copy(
        editingNodeId = null,
        viewport = state.focusedRect?.let { state.viewport.focusedOn(it) } ?: state.viewport,
        focusedRect = null
    )

    /** The page that adopts the widgets of a page being removed: the one before it, else after. */
    private fun neighbourPage(document: CanvasDocument, page: CanvasNode): CanvasNode? {
        val others = document.pages.filterNot { it.id == page.id }
        return others.lastOrNull { it.pageOrder < page.pageOrder } ?: others.firstOrNull()
    }

    /**
     * Removing a page never deletes what is on it: its widgets move onto the neighbouring page,
     * under what that page already holds, keeping their own layout. The last page is never
     * removed — a note always shows at least one sheet of paper.
     */
    private fun withoutPage(document: CanvasDocument, page: CanvasNode): CanvasDocument {
        val host = neighbourPage(document, page) ?: return document
        val orphans = document.widgetsInReadingOrder(page.id)
        val remaining = document.removing(page.id)
        if (orphans.isEmpty()) return remaining
        val top = document.contentExtentOf(host.id).height
            .takeIf { it > 0f } ?: CanvasNode.PAGE_PADDING
        val shift = top - orphans.minOf { it.rect.y }
        return remaining
            .replacingAll(
                orphans.map { it.copy(parentId = host.id, rect = it.rect.translated(0f, shift)) }
            )
            .settledWidgetsOn(host.id)
    }

    /**
     * 🧱 23-Sep-2026 — a lifted widget released over [page] with its top-left at ([x], [y]) on the
     * page's content. It never lands left of or above the paper, never past its right edge while
     * it fits, and never on another widget — it slides down to the first free spot — and it
     * takes its place in the note's reading order.
     */
    private fun droppedOn(
        document: CanvasDocument,
        widget: CanvasNode,
        page: CanvasNode,
        x: Float,
        y: Float
    ): CanvasDocument {
        val reach = document.nodes
            .filter { it.isWidget && it.parentId == page.id && it.id != widget.id }
            .maxOfOrNull { it.rect.right + CanvasNode.PAGE_PADDING }
            ?.let { maxOf(it, page.rect.width) }
            ?: page.rect.width
        val wanted = CanvasRect(
            x = x.coerceIn(0f, maxOf(0f, reach - widget.rect.width)),
            y = maxOf(0f, y),
            width = widget.rect.width,
            height = widget.rect.height
        )
        val spot = document.freeSpotOn(page.id, wanted, widget.id)
        val order = document.slotOrderAt(page.id, spot.x, spot.y, widget.id)
        val previousPageId = widget.parentId
        val placed = document
            .replacing(widget.copy(parentId = page.id, rect = spot, slotOrder = order))
            .withReadingOrderOn(page.id)
        return if (previousPageId == null || previousPageId == page.id) placed
        else placed.withReadingOrderOn(previousPageId)
    }

    /** A new node: paper finds a free place on the board, a widget a free spot on its page. */
    private fun added(document: CanvasDocument, node: CanvasNode): CanvasDocument {
        if (node.isPage) {
            val placed = document.withoutOverlap(node, CanvasNode.DEFAULT_GAP)
            return document.adding(placed).broughtToFront(placed.id)
        }
        val pageId = node.parentId ?: return document.adding(node)
        val spot = document.freeSpotOn(pageId, node.rect, node.id)
        return document.adding(node.copy(rect = spot)).withReadingOrderOn(pageId)
    }

    private fun blocked(state: CanvasEditorState, intent: CanvasEditorIntent): Boolean {
        val permits = state.permits
        return when (intent) {
            is CanvasEditorIntent.Pan -> !permits.canPan
            is CanvasEditorIntent.Zoom,
            is CanvasEditorIntent.ZoomTo,
            CanvasEditorIntent.ZoomIn,
            CanvasEditorIntent.ZoomOut,
            CanvasEditorIntent.ZoomToFit -> !permits.canZoom

            // 🧱 24-Sep-2026 — a long-press may pick a widget up while text is being typed; the keyboard goes away
            is CanvasEditorIntent.BeginDrag ->
                !permits.canDragNode && state.gesture != CanvasGesture.EDITING
            is CanvasEditorIntent.DragBy -> !permits.canDragNode
            is CanvasEditorIntent.BeginResize -> !permits.canResizeNode
            is CanvasEditorIntent.ResizeNode -> !permits.canResizeNode
            is CanvasEditorIntent.SetEditing ->
                intent.nodeId != null && !permits.canEditText

            // a tap on paper works while typing (it puts the keyboard away) but never mid-gesture
            is CanvasEditorIntent.FocusPage ->
                state.gesture == CanvasGesture.DRAGGING ||
                    state.gesture == CanvasGesture.RESIZING ||
                    (state.gesture == CanvasGesture.NONE && !permits.canSelect)

            is CanvasEditorIntent.AddNode,
            is CanvasEditorIntent.RemoveNode,
            is CanvasEditorIntent.ReparentNode,
            is CanvasEditorIntent.RenameNode -> !permits.canMutateDocument

            // a lift must always be able to finish, and content can always report its size
            else -> false
        }
    }

    private fun apply(state: CanvasEditorState, intent: CanvasEditorIntent): CanvasEditorState =
        when (intent) {

            is CanvasEditorIntent.ViewportResized ->
                state.copy(viewport = state.viewport.withSize(intent.width, intent.height))

            is CanvasEditorIntent.Pan ->
                state.copy(viewport = state.viewport.panned(intent.deltaX, intent.deltaY))

            is CanvasEditorIntent.Zoom -> state.copy(
                viewport = state.viewport.zoomed(intent.factor, intent.focusX, intent.focusY)
            )

            is CanvasEditorIntent.ZoomTo -> state.copy(
                viewport = state.viewport.scaledTo(intent.scale, intent.focusX, intent.focusY)
            )

            CanvasEditorIntent.ZoomIn -> state.copy(
                viewport = state.viewport.scaledTo(
                    Viewport.nextZoomStop(state.viewport.scale),
                    state.viewport.widthPx / 2f,
                    state.viewport.heightPx / 2f
                )
            )

            CanvasEditorIntent.ZoomOut -> state.copy(
                viewport = state.viewport.scaledTo(
                    Viewport.previousZoomStop(state.viewport.scale),
                    state.viewport.widthPx / 2f,
                    state.viewport.heightPx / 2f
                )
            )

            CanvasEditorIntent.ZoomToFit -> {
                val bounds = state.document.bounds
                if (bounds.width <= 0f) state
                else state.copy(viewport = state.viewport.focusedOn(bounds))
            }

            is CanvasEditorIntent.FocusNode -> {
                val page = state.document.pageOf(intent.nodeId)
                if (page == null) state
                else fittedToScreen(state.copy(selectedNodeId = page.id), page)
            }

            is CanvasEditorIntent.FocusPage ->
                state.document.nodeById(intent.pageId)
                    ?.takeIf { it.isPage }
                    ?.let { focusedPage(state, it) }
                    ?: state

            // 📄 paper and widgets answer their own taps. A tap that still reaches the board
            //   over a page came through a widget, and must not undo what that widget just did.
            is CanvasEditorIntent.SelectAt -> {
                val hit = state.document.hitTest(
                    state.viewport.toCanvasX(intent.screenX),
                    state.viewport.toCanvasY(intent.screenY)
                )
                if (hit != null) state
                else leftEditing(state).copy(selectedNodeId = null)
            }

            is CanvasEditorIntent.SelectNode -> {
                val node = intent.nodeId?.let { state.document.nodeById(it) }
                when {
                    node == null -> state.copy(selectedNodeId = null, editingNodeId = null)
                    node.isPage -> focusedPage(state, node)
                    node.isTextWidget -> editingText(state, node)
                    else -> state.copy(
                        selectedNodeId = state.document.pageOf(node.id)?.id ?: state.selectedNodeId
                    )
                }
            }

            // 📄 lifting a widget only marks it: the platform carries it on its own layer, so a
            //   drag costs no document update per frame and the page just hides the lifted one
            is CanvasEditorIntent.BeginDrag -> {
                val node = state.document.nodeById(intent.nodeId)
                val carrying = state.copy(editingNodeId = null, focusedRect = null)
                when {
                    node == null || node.locked || node.id == state.editingNodeId -> state
                    node.isPage -> carrying.copy(
                        draggingNodeId = node.id,
                        selectedNodeId = node.id,
                        document = state.document.broughtToFront(node.id)
                    )

                    else -> carrying.copy(draggingNodeId = node.id)
                }
            }

            // only paper moves frame by frame; a widget's rect is relative, so it rides along
            is CanvasEditorIntent.DragBy -> {
                val page = state.draggingNodeId
                    ?.let { state.document.nodeById(it) }
                    ?.takeIf { it.isPage && !it.locked }
                if (page == null) {
                    state
                } else {
                    val dx = intent.deltaX / state.viewport.scale
                    val dy = intent.deltaY / state.viewport.scale
                    state.copy(document = state.document.replacing(page.movedBy(dx, dy)))
                }
            }

            // a page let go slides right past any page it landed on — pages never overlap
            CanvasEditorIntent.EndDrag -> {
                val page = state.draggingNodeId
                    ?.let { state.document.nodeById(it) }
                    ?.takeIf { it.isPage }
                val document = page
                    ?.let { state.document.withoutOverlap(it, CanvasNode.DEFAULT_GAP) }
                    ?.let { state.document.replacing(it) }
                    ?: state.document
                state.copy(draggingNodeId = null, document = document)
            }

            is CanvasEditorIntent.DropWidget -> {
                val widget = state.document.nodeById(intent.nodeId)?.takeIf { it.isWidget }
                val page = intent.pageId?.let { state.document.nodeById(it) }?.takeIf { it.isPage }
                // 🧱 24-Sep-2026 — let go where it was picked up: nothing moved, so nothing is written or sent
                val stayed = widget != null && page != null && widget.parentId == page.id &&
                    abs(widget.rect.x - intent.x) < DROP_TOLERANCE &&
                    abs(widget.rect.y - intent.y) < DROP_TOLERANCE
                val document =
                    if (widget == null || page == null || stayed) state.document
                    else droppedOn(state.document, widget, page, intent.x, intent.y)
                state.copy(draggingNodeId = null, document = document)
            }

            is CanvasEditorIntent.ReparentNode -> {
                val node = state.document.nodeById(intent.nodeId)
                if (node == null || node.id == intent.parentId) state
                else state.copy(
                    document = state.document
                        .replacing(node.reparentedTo(intent.parentId))
                        .broughtToFront(node.id)
                )
            }

            // 📄 paper resized by its handle pushes neighbouring pages aside; its widgets never
            //   change — whatever no longer fits is scrolled to
            is CanvasEditorIntent.ResizeNode -> {
                val node = state.document.nodeById(intent.nodeId)
                if (node == null) {
                    state
                } else {
                    val resized = node.resizedTo(intent.width, intent.height)
                    val document = state.document.replacing(resized)
                    state.copy(
                        document =
                            if (resized.isPage) document.pagesClearOf(resized.id)
                            else document.pushedClearOf(resized.id)
                    )
                }
            }

            // 🧱 a text widget grew as it was typed into: whatever it now covers moves down
            is CanvasEditorIntent.MeasureWidget -> {
                val widget = state.document.nodeById(intent.nodeId)?.takeIf { it.isWidget }
                if (widget == null || widget.rect.height == intent.height) {
                    state
                } else {
                    val measured = widget.resizedTo(widget.rect.width, intent.height)
                    state.copy(
                        document = state.document.replacing(measured).pushedClearOf(measured.id)
                    )
                }
            }

            is CanvasEditorIntent.AddNode -> state.copy(
                document = added(state.document, intent.node),
                selectedNodeId = if (intent.node.isPage) intent.node.id else state.selectedNodeId
            )

            is CanvasEditorIntent.RemoveNode -> {
                val removed = state.document.nodeById(intent.nodeId)
                val document = when {
                    removed == null -> state.document
                    removed.isPage -> withoutPage(state.document, removed)
                    else -> state.document.removing(removed.id)
                }
                val gone = document.nodeById(intent.nodeId) == null
                state.copy(
                    document = document,
                    selectedNodeId = state.selectedNodeId.takeUnless { gone && it == intent.nodeId },
                    editingNodeId = state.editingNodeId.takeUnless { gone && it == intent.nodeId },
                    draggingNodeId = state.draggingNodeId.takeUnless { gone && it == intent.nodeId }
                )
            }

            is CanvasEditorIntent.BeginResize ->
                state.copy(resizingNodeId = intent.nodeId, selectedNodeId = intent.nodeId)

            CanvasEditorIntent.EndResize -> state.copy(resizingNodeId = null)

            is CanvasEditorIntent.SetTool -> state.copy(
                tool = intent.tool,
                draggingNodeId = null,
                resizingNodeId = null,
                editingNodeId = null
            )

            is CanvasEditorIntent.SetEditing -> {
                val node = intent.nodeId?.let { state.document.nodeById(it) }
                when {
                    node == null -> leftEditing(state)
                    node.isPage -> focusedPage(state, node)
                    node.isTextWidget -> editingText(state, node)
                    else -> state
                }
            }

            is CanvasEditorIntent.LinkNodes -> {
                val from = state.document.nodeById(intent.fromId)
                if (from == null) state
                else state.copy(document = state.document.replacing(from.linkedTo(intent.toId)))
            }

            is CanvasEditorIntent.RenameNode -> {
                val node = state.document.nodeById(intent.nodeId)
                if (node == null) state
                else state.copy(document = state.document.replacing(node.renamedTo(intent.name)))
            }

            is CanvasEditorIntent.ReplaceDocument ->
                state.copy(document = intent.document, selectedNodeId = null, editingNodeId = null)

            is CanvasEditorIntent.AdoptRemote -> adopted(state, intent.nodes, intent.keepLocal)

            is CanvasEditorIntent.RevealEnd ->
                if (state.document.nodeById(intent.pageId)?.isPage != true) state
                else state.copy(
                    scrollRequest = PageScrollRequest(intent.pageId, (state.scrollRequest?.token ?: 0) + 1)
                )
        }

    // 🔄 24-Sep-2026 — a canvas without paper is never taken; whatever was selected or edited stays so while it still exists
    private fun adopted(
        state: CanvasEditorState,
        remote: List<CanvasNode>,
        keepLocal: Set<String>
    ): CanvasEditorState {
        if (remote.none { it.isPage }) return state
        val document = state.document.mergedWith(remote, keepLocal)
        if (document.pages.isEmpty()) return state
        fun live(nodeId: String?): String? = nodeId?.takeIf { document.nodeById(it) != null }
        return state.copy(
            document = document,
            selectedNodeId = live(state.selectedNodeId),
            editingNodeId = live(state.editingNodeId),
            draggingNodeId = live(state.draggingNodeId),
            resizingNodeId = live(state.resizingNodeId)
        )
    }
}

object CanvasCommands {

    fun viewportResized(width: Float, height: Float): CanvasEditorIntent =
        CanvasEditorIntent.ViewportResized(width, height)

    fun pan(deltaX: Float, deltaY: Float): CanvasEditorIntent =
        CanvasEditorIntent.Pan(deltaX, deltaY)

    fun zoom(factor: Float, focusX: Float, focusY: Float): CanvasEditorIntent =
        CanvasEditorIntent.Zoom(factor, focusX, focusY)

    fun zoomIn(): CanvasEditorIntent = CanvasEditorIntent.ZoomIn

    fun zoomOut(): CanvasEditorIntent = CanvasEditorIntent.ZoomOut

    fun zoomToFit(): CanvasEditorIntent = CanvasEditorIntent.ZoomToFit

    fun focusNode(nodeId: String): CanvasEditorIntent = CanvasEditorIntent.FocusNode(nodeId)

    /** A tap on paper: fit the page, scroll it to its end, keyboard away. */
    fun focusPage(pageId: String): CanvasEditorIntent = CanvasEditorIntent.FocusPage(pageId)

    /** The device measured a widget's content height, in canvas units. */
    fun widgetMeasured(nodeId: String, height: Float): CanvasEditorIntent =
        CanvasEditorIntent.MeasureWidget(nodeId, height)

    /** A lift that ended anywhere but over a page: the widget goes back where it was. */
    fun cancelLift(nodeId: String): CanvasEditorIntent =
        CanvasEditorIntent.DropWidget(nodeId, null, 0f, 0f)

    fun selectAt(screenX: Float, screenY: Float): CanvasEditorIntent =
        CanvasEditorIntent.SelectAt(screenX, screenY)

    fun selectNode(nodeId: String?): CanvasEditorIntent = CanvasEditorIntent.SelectNode(nodeId)

    fun beginDrag(nodeId: String): CanvasEditorIntent = CanvasEditorIntent.BeginDrag(nodeId)

    fun dragBy(deltaX: Float, deltaY: Float): CanvasEditorIntent =
        CanvasEditorIntent.DragBy(deltaX, deltaY)

    fun endDrag(): CanvasEditorIntent = CanvasEditorIntent.EndDrag

    fun addNode(node: CanvasNode): CanvasEditorIntent = CanvasEditorIntent.AddNode(node)

    fun linkNodes(fromId: String, toId: String): CanvasEditorIntent =
        CanvasEditorIntent.LinkNodes(fromId, toId)

    fun anchorOf(state: CanvasEditorState): CanvasNode? =
        (state.editingNodeId ?: state.selectedNodeId)?.let { state.document.nodeById(it) }

    fun linkedNodes(state: CanvasEditorState, nodeId: String): List<CanvasNode> =
        state.document.linkedTo(nodeId)

    fun removeNode(nodeId: String): CanvasEditorIntent = CanvasEditorIntent.RemoveNode(nodeId)

    fun renameNode(nodeId: String, name: String): CanvasEditorIntent =
        CanvasEditorIntent.RenameNode(nodeId, name)

    fun replaceDocument(document: CanvasDocument): CanvasEditorIntent =
        CanvasEditorIntent.ReplaceDocument(document)

    fun setEditing(nodeId: String?): CanvasEditorIntent = CanvasEditorIntent.SetEditing(nodeId)

    fun useHandTool(): CanvasEditorIntent = CanvasEditorIntent.SetTool(CanvasTool.HAND)

    fun useSelectTool(): CanvasEditorIntent = CanvasEditorIntent.SetTool(CanvasTool.SELECT)

    fun useZoomTool(): CanvasEditorIntent = CanvasEditorIntent.SetTool(CanvasTool.ZOOM)

    fun useLockTool(): CanvasEditorIntent = CanvasEditorIntent.SetTool(CanvasTool.LOCK)

    fun setTool(tool: CanvasTool): CanvasEditorIntent = CanvasEditorIntent.SetTool(tool)

    fun tools(): List<CanvasTool> = listOf(
        CanvasTool.SELECT,
        CanvasTool.HAND,
        CanvasTool.ZOOM,
        CanvasTool.LOCK
    )

    fun toolLabel(tool: CanvasTool): String = when (tool) {
        CanvasTool.SELECT -> "Select"
        CanvasTool.HAND -> "Hand"
        CanvasTool.ZOOM -> "Zoom"
        CanvasTool.LOCK -> "Lock"
    }

    fun toolKey(tool: CanvasTool): String = tool.name

    fun isTool(state: CanvasEditorState, tool: CanvasTool): Boolean = state.tool == tool

    fun isHandTool(state: CanvasEditorState): Boolean = state.tool == CanvasTool.HAND

    fun permitsOf(state: CanvasEditorState): CanvasPermits = state.permits

    fun isEditing(state: CanvasEditorState): Boolean =
        state.gesture == CanvasGesture.EDITING

    fun beginResize(nodeId: String): CanvasEditorIntent = CanvasEditorIntent.BeginResize(nodeId)

    /** Swift-facing factory — nested sealed subtypes are awkward to construct from Swift. */
    fun resizeNode(nodeId: String, width: Float, height: Float): CanvasEditorIntent =
        CanvasEditorIntent.ResizeNode(nodeId, width, height)

    fun endResize(): CanvasEditorIntent = CanvasEditorIntent.EndResize

    fun exitEditing(): CanvasEditorIntent = CanvasEditorIntent.SetEditing(null)

    /** 📄 goes through the document because a widget's rect is relative to its page. */
    fun screenRectOf(
        document: CanvasDocument,
        node: CanvasNode,
        viewport: Viewport
    ): CanvasRect {
        val rect = document.absoluteRectOf(node)
        return CanvasRect(
            x = viewport.toScreenX(rect.x),
            y = viewport.toScreenY(rect.y),
            width = rect.width * viewport.scale,
            height = rect.height * viewport.scale
        )
    }

    // 🔧 09-Aug-2026 G8: Swift cannot see Kotlin default arguments or nested sealed subtypes,
    //   so every construction and every intent test below is exposed as a plain function
    fun initialState(): CanvasEditorState = CanvasEditorState()

    fun loaded(
        state: CanvasEditorState,
        nodes: List<CanvasNode>,
        viewport: Viewport?
    ): CanvasEditorState {
        val restored = viewport
            ?.takeIf { it.scale in SANE_MIN_SCALE..SANE_MAX_SCALE }
            ?.withSize(state.viewport.widthPx, state.viewport.heightPx)
        return state.copy(
            document = CanvasDocument(nodes),
            viewport = restored ?: state.viewport.copy(
                scale = Viewport.DEFAULT_SCALE,
                offsetX = 0f,
                offsetY = 0f
            )
        )
    }

    const val SANE_MIN_SCALE = 0.25f

    const val SANE_MAX_SCALE = 4f

    fun isEndDrag(intent: CanvasEditorIntent): Boolean = intent is CanvasEditorIntent.EndDrag

    fun addedNode(intent: CanvasEditorIntent): CanvasNode? =
        (intent as? CanvasEditorIntent.AddNode)?.node

    fun removedNodeId(intent: CanvasEditorIntent): String? =
        (intent as? CanvasEditorIntent.RemoveNode)?.nodeId

    fun resizedNodeId(intent: CanvasEditorIntent): String? =
        (intent as? CanvasEditorIntent.ResizeNode)?.nodeId

    fun reparentedNodeId(intent: CanvasEditorIntent): String? =
        (intent as? CanvasEditorIntent.ReparentNode)?.nodeId

    fun affectsViewport(intent: CanvasEditorIntent): Boolean = when (intent) {
        is CanvasEditorIntent.Pan,
        is CanvasEditorIntent.Zoom,
        is CanvasEditorIntent.ZoomTo,
        CanvasEditorIntent.ZoomIn,
        CanvasEditorIntent.ZoomOut,
        CanvasEditorIntent.ZoomToFit,
        is CanvasEditorIntent.SelectAt,
        is CanvasEditorIntent.SelectNode,
        is CanvasEditorIntent.FocusPage,
        is CanvasEditorIntent.SetEditing,
        is CanvasEditorIntent.FocusNode -> true

        else -> false
    }

    /** The text field a freshly opened canvas should land in — the last text widget written. */
    fun lastTextNodeId(state: CanvasEditorState): String? =
        state.document.orderedWidgets.lastOrNull { it.isTextWidget }?.id

    /** The paper width a page fitted to this screen gets — what new pages are cut to. */
    fun fittedPageWidth(state: CanvasEditorState): Float =
        (state.viewport.widthPx - PAGE_SCREEN_MARGIN * 2f)
            .takeIf { it > CanvasNode.MIN_SIZE } ?: CanvasNode.DEFAULT_TEXT_WIDTH

    /** The paper height a page fitted to this screen gets. */
    fun fittedPageHeight(state: CanvasEditorState): Float =
        (state.viewport.heightPx - PAGE_SCREEN_MARGIN * 2f)
            .takeIf { it > CanvasNode.MIN_SIZE } ?: CanvasNode.DEFAULT_TEXT_HEIGHT

    /**
     * 📕 25-Sep-2026 — where a note's title sits as a hard-cover page: paper the same size as
     * the first real page, immediately to its left — the same way [pageNode] places a new page
     * immediately to the right of the last one. Never a stored node: the note's title is the
     * only copy of this text, so there is nothing here to keep in sync or send over the wire.
     */
    fun coverPageRect(state: CanvasEditorState): CanvasRect {
        val first = state.document.pages.firstOrNull()
            ?: return CanvasRect(0f, 0f, fittedPageWidth(state), fittedPageHeight(state))
        return CanvasRect(
            x = first.rect.x - CanvasNode.DEFAULT_GAP - first.rect.width,
            y = first.rect.y,
            width = first.rect.width,
            height = first.rect.height
        )
    }

    /** [coverPageRect], translated to the screen exactly like [screenRectOf] does for a real page. */
    fun coverScreenRectOf(state: CanvasEditorState): CanvasRect {
        val rect = coverPageRect(state)
        val viewport = state.viewport
        return CanvasRect(
            x = viewport.toScreenX(rect.x),
            y = viewport.toScreenY(rect.y),
            width = rect.width * viewport.scale,
            height = rect.height * viewport.scale
        )
    }

    /** The page a freshly opened canvas shows: the last one. */
    fun lastPageId(state: CanvasEditorState): String? = state.document.pages.lastOrNull()?.id

    /** The widgets a page draws inside itself, top to bottom. */
    fun widgetsOnPage(state: CanvasEditorState, pageId: String): List<CanvasNode> =
        state.document.widgetsInReadingOrder(pageId)

    /** How far [pageId]'s content reaches, in canvas units — the page scrolls over this. */
    fun contentExtentOf(state: CanvasEditorState, pageId: String): CanvasRect =
        state.document.contentExtentOf(pageId)

    /** The token of the newest "scroll to end" request for [pageId], or 0 when there is none. */
    fun scrollTokenFor(state: CanvasEditorState, pageId: String): Int =
        state.scrollRequest?.takeIf { it.pageId == pageId }?.token ?: 0

    /** True while [nodeId] is lifted off its page and carried by the finger. */
    fun isLifted(state: CanvasEditorState, nodeId: String): Boolean = state.draggingNodeId == nodeId

    // 🧱 24-Sep-2026 — long-press picks something up when nothing else is in progress, or while text is being typed
    fun canLift(state: CanvasEditorState): Boolean =
        state.permits.canDragNode || state.gesture == CanvasGesture.EDITING

    /** The page under the centre of a carried widget, in screen units — null over bare canvas. */
    fun dropTargetAt(state: CanvasEditorState, screenX: Float, screenY: Float): CanvasNode? =
        state.document.pageAt(state.viewport.toCanvasX(screenX), state.viewport.toCanvasY(screenY))

    /**
     * 🧱 23-Sep-2026 — a lifted widget let go with its top-left at ([screenX], [screenY]). The
     * platform supplies only what it alone can measure — how far [target] is scrolled and how
     * tall its name bar is, all in screen units — and the conversion to a spot on the page's
     * content happens here, so both platforms place a drop identically. A null [target] means
     * bare canvas: the widget goes back where it came from.
     */
    fun dropWidget(
        state: CanvasEditorState,
        widgetId: String,
        target: CanvasNode?,
        screenX: Float,
        screenY: Float,
        scrollX: Float,
        scrollY: Float,
        headerHeight: Float
    ): CanvasEditorIntent {
        if (target == null) return cancelLift(widgetId)
        val scale = state.viewport.scale.takeIf { it > 0f } ?: 1f
        val x = state.viewport.toCanvasX(screenX) - target.rect.x + scrollX / scale
        val y = state.viewport.toCanvasY(screenY) - target.rect.y + (scrollY - headerHeight) / scale
        return CanvasEditorIntent.DropWidget(widgetId, target.id, x, y)
    }

    /**
     * What one reduce changed that must reach storage. Nothing is written while a drag or a
     * resize is in progress; the node is written once when the finger lifts, together with
     * every neighbour the settle moved — so a gesture costs one write, not one per frame.
     */
    fun nodesToSave(before: CanvasEditorState, next: CanvasEditorState): List<CanvasNode> {
        if (next.gesture == CanvasGesture.DRAGGING || next.gesture == CanvasGesture.RESIZING) {
            return emptyList()
        }
        val previous = before.document.nodes.associateBy { it.id }
        val changed = next.document.nodes.filter { previous[it.id] != it }
        val finished = listOfNotNull(before.draggingNodeId, before.resizingNodeId)
            .mapNotNull { next.document.nodeById(it) }
        // a resize pushes pages aside frame by frame, all of it unwritten until the finger lifts
        val ended = before.gesture == CanvasGesture.DRAGGING || before.gesture == CanvasGesture.RESIZING
        val settled = if (ended) next.document.pages else emptyList()
        return (changed + finished + settled).distinctBy { it.id }
    }

    /** Ids a reduce removed from the board. */
    fun removedNodeIds(before: CanvasEditorState, next: CanvasEditorState): List<String> {
        val kept = next.document.nodes.map { it.id }.toSet()
        return before.document.nodes.map { it.id }.filterNot { it in kept }
    }

    /** True when the note's reading order changed, so content positions must be re-stamped. */
    fun orderChanged(before: CanvasEditorState, next: CanvasEditorState): Boolean {
        if (next.gesture == CanvasGesture.DRAGGING) return false
        return before.document.orderedWidgets.mapNotNull { it.contentId } !=
            next.document.orderedWidgets.mapNotNull { it.contentId }
    }

    fun widgetsOf(state: CanvasEditorState, pageId: String): List<CanvasNode> =
        state.document.widgetsOf(pageId)

    fun pagesOf(state: CanvasEditorState): List<CanvasNode> = state.document.pages

    fun absoluteRectOf(state: CanvasEditorState, node: CanvasNode): CanvasRect =
        state.document.absoluteRectOf(node)

    /** The page a select has just fitted to the screen, so its new size can be stored. */
    fun fittedPageId(state: CanvasEditorState, intent: CanvasEditorIntent): String? =
        when (intent) {
            is CanvasEditorIntent.SelectAt,
            is CanvasEditorIntent.SelectNode,
            is CanvasEditorIntent.FocusPage,
            is CanvasEditorIntent.SetEditing -> state.selectedNode?.takeIf { it.isPage }?.id

            else -> null
        }

    fun defaultWidth(kind: ContentType): Float =
        if (kind == ContentType.TEXT) CanvasNode.DEFAULT_TEXT_WIDTH
        else CanvasNode.DEFAULT_MEDIA_WIDTH

    fun defaultHeight(kind: ContentType): Float =
        if (kind == ContentType.TEXT) CanvasNode.DEFAULT_TEXT_HEIGHT
        else CanvasNode.DEFAULT_MEDIA_HEIGHT

    fun nodeNextTo(anchor: CanvasNode, kind: ContentType, contentId: String?): CanvasNode =
        CanvasNode.nextTo(
            anchor = anchor,
            kind = kind,
            contentId = contentId,
            width = defaultWidth(kind),
            height = defaultHeight(kind)
        )

    fun nodeAtEdge(
        state: CanvasEditorState,
        kind: ContentType,
        contentId: String?
    ): CanvasNode {
        val bounds = state.document.bounds
        return CanvasNode.of(
            kind = kind,
            contentId = contentId,
            x = bounds.right + CanvasNode.DEFAULT_GAP,
            y = bounds.y,
            width = defaultWidth(kind),
            height = defaultHeight(kind)
        )
    }

    /** The page new widgets land on: the selected node's page, else the first page. */
    fun selectedPage(state: CanvasEditorState): CanvasNode? {
        val focus = anchorOf(state) ?: return state.document.pages.firstOrNull()
        return state.document.pageOf(focus.id) ?: state.document.pages.firstOrNull()
    }

    fun pageAt(state: CanvasEditorState, canvasX: Float, canvasY: Float): CanvasNode? =
        state.document.pageAt(canvasX, canvasY)

    fun childrenOf(state: CanvasEditorState, nodeId: String): List<CanvasNode> =
        state.document.childrenOf(nodeId)

    fun isPage(node: CanvasNode): Boolean = node.isPage

    fun reparentNode(nodeId: String, parentId: String?): CanvasEditorIntent =
        CanvasEditorIntent.ReparentNode(nodeId, parentId)

    /**
     * A new widget, placed under everything already on the page, as wide as the paper less its
     * margins. Its size is fixed from here on — only measured kinds take their content's height.
     * The rect is relative to the page's content, and the slot order is the gap after the page's
     * last widget.
     */
    fun childNodeIn(
        state: CanvasEditorState,
        page: CanvasNode,
        kind: ContentType,
        contentId: String?
    ): CanvasNode {
        val width = CanvasNode.widgetWidthFor(kind, page.rect.width)
        val top = state.document.contentExtentOf(page.id).height
            .takeIf { it > 0f } ?: PAGE_PADDING
        return CanvasNode.of(
            kind = kind,
            contentId = contentId,
            x = PAGE_PADDING,
            y = top,
            width = width,
            height = CanvasNode.widgetHeightFor(kind, width),
            parentId = page.id
        ).copy(
            role = CanvasRole.WIDGET,
            slotOrder = state.document.slotOrderAt(page.id, PAGE_PADDING, top, null)
        )
    }

    /** Every content — text included — is a widget on the page the user is working on. */
    fun nodeFor(state: CanvasEditorState, kind: ContentType, contentId: String?): CanvasNode {
        val page = pageForSpawn(state) ?: return nodeAtEdge(state, kind, contentId)
        return childNodeIn(state, page, kind, contentId)
    }

    fun resizedTo(
        state: CanvasEditorState,
        nodeId: String,
        deltaXPx: Float,
        deltaYPx: Float
    ): CanvasEditorIntent? {
        val node = state.document.nodeById(nodeId)?.takeIf { !it.locked } ?: return null
        val scale = if (state.viewport.scale <= 0f) 1f else state.viewport.scale
        return CanvasEditorIntent.ResizeNode(
            nodeId = nodeId,
            width = node.rect.width + deltaXPx / scale,
            height = node.rect.height + deltaYPx / scale
        )
    }

    const val PAGE_PADDING = CanvasNode.PAGE_PADDING

    const val CARET_MARGIN = 24f

    /** Lines of clearance kept between the caret and the keyboard. */
    const val CARET_TRAILING_LINES = 4

    fun caretRevealPadding(lineHeightPx: Float): Float =
        if (lineHeightPx > 0f) lineHeightPx * CARET_TRAILING_LINES else CARET_MARGIN

    // 📄 24-Sep-2026 — the page being typed on, else the tapped page while it is on screen, else the page most in view, else the last
    fun pageForSpawn(state: CanvasEditorState): CanvasNode? {
        val document = state.document
        state.editingNodeId?.let { editing -> document.pageOf(editing)?.let { return it } }
        val viewport = state.viewport
        val measured = viewport.widthPx > 0f && viewport.heightPx > 0f
        val visible = viewport.visibleRect
        val selected = state.selectedNodeId?.let { document.pageOf(it) }
        if (selected != null && (!measured || selected.rect.intersects(visible))) return selected
        if (measured) document.mostVisiblePage(visible)?.let { return it }
        return document.pages.lastOrNull()
    }

    fun revealEnd(pageId: String): CanvasEditorIntent = CanvasEditorIntent.RevealEnd(pageId)

    fun adoptRemote(nodes: List<CanvasNode>, keepLocal: Set<String>): CanvasEditorIntent =
        CanvasEditorIntent.AdoptRemote(nodes, keepLocal)

    fun isRemoteAdoption(intent: CanvasEditorIntent): Boolean =
        intent is CanvasEditorIntent.AdoptRemote

    // 🔄 24-Sep-2026 — another device's canvas waits while a finger is carrying or resizing something here
    fun canAdoptRemote(state: CanvasEditorState): Boolean =
        state.draggingNodeId == null && state.resizingNodeId == null

    private fun isCarrying(state: CanvasEditorState): Boolean =
        state.draggingNodeId != null || state.resizingNodeId != null

    fun gestureStarted(before: CanvasEditorState, next: CanvasEditorState): Boolean =
        !isCarrying(before) && isCarrying(next)

    fun gestureEnded(before: CanvasEditorState, next: CanvasEditorState): Boolean =
        isCarrying(before) && !isCarrying(next)

    // 🔄 24-Sep-2026 — a layout change the user made travels to the other devices; a fit, a measure, a zoom or a remote canvas never does
    fun editsLayout(
        before: CanvasEditorState,
        next: CanvasEditorState,
        intent: CanvasEditorIntent,
        gestureStart: CanvasDocument?
    ): Boolean = when {
        gestureEnded(before, next) -> (gestureStart ?: before.document) != next.document
        else -> when (intent) {
            is CanvasEditorIntent.AddNode,
            is CanvasEditorIntent.RemoveNode,
            is CanvasEditorIntent.RenameNode,
            is CanvasEditorIntent.ReparentNode,
            is CanvasEditorIntent.LinkNodes,
            is CanvasEditorIntent.ReplaceDocument -> before.document != next.document

            else -> false
        }
    }

    // 🧱 24-Sep-2026 — how far a page scrolls this frame while a carried widget's finger is near an edge of its viewport
    fun autoScrollStep(position: Float, start: Float, length: Float): Float {
        if (length <= 0f) return 0f
        val band = minOf(AUTO_SCROLL_BAND, length / 4f)
        val fromStart = position - start
        val fromEnd = start + length - position
        return when {
            fromStart < 0f || fromEnd < 0f -> 0f
            fromStart < band -> -AUTO_SCROLL_MAX_STEP * (1f - fromStart / band)
            fromEnd < band -> AUTO_SCROLL_MAX_STEP * (1f - fromEnd / band)
            else -> 0f
        }
    }

    // 📐 24-Sep-2026 — the one-time upgrade of a stored canvas: units to dp/pt, then every page restacked with compact cards and 8 spacing
    fun upgradedLayout(nodes: List<CanvasNode>, unitScale: Float, maxPaperWidth: Float): List<CanvasNode> {
        val scaled = if (unitScale == 1f) nodes else nodes.map { node ->
            node.copy(
                rect = CanvasRect(
                    node.rect.x * unitScale,
                    node.rect.y * unitScale,
                    node.rect.width * unitScale,
                    node.rect.height * unitScale
                )
            )
        }
        val document = CanvasDocument(scaled)
        return document.pages.fold(document) { acc, page ->
            val paper = if (maxPaperWidth > 0f) minOf(page.rect.width, maxPaperWidth) else page.rect.width
            acc.restacked(page.id, paper)
        }.nodes
    }

    const val AUTO_SCROLL_BAND = 56f

    const val AUTO_SCROLL_MAX_STEP = 14f

    fun needsPage(state: CanvasEditorState): Boolean = state.document.pages.isEmpty()

    /** Fresh empty paper, placed to the right of the last page. */
    fun pageNode(state: CanvasEditorState): CanvasNode {
        val anchor = state.document.pages.lastOrNull()
            ?: return CanvasNode.page(order = 0.0, x = 0f, y = 0f)
        return CanvasNode.page(
            order = state.document.nextPageOrder(),
            x = anchor.rect.right + CanvasNode.DEFAULT_GAP,
            y = anchor.rect.y,
            width = anchor.rect.width,
            height = anchor.rect.height
        )
    }

    fun widgetIn(
        state: CanvasEditorState,
        page: CanvasNode,
        kind: ContentType,
        contentId: String?
    ): CanvasNode = childNodeIn(state, page, kind, contentId)

    /** One page carrying the given text contents, stacked down the paper. */
    fun stackedTextNodes(contentIds: List<String>): List<CanvasNode> {
        val page = CanvasNode.page(order = 0.0, x = 0f, y = 0f)
        val width = CanvasNode.widgetWidthOn(page.rect.width)
        var y = PAGE_PADDING
        val widgets = contentIds.mapIndexed { index, contentId ->
            val node = CanvasNode.of(
                kind = ContentType.TEXT,
                contentId = contentId,
                x = PAGE_PADDING,
                y = y,
                width = width,
                height = CanvasNode.MEASURED_START_HEIGHT,
                parentId = page.id
            ).copy(z = index + 1, slotOrder = index.toDouble())
            y += node.rect.height + CanvasNode.WIDGET_GAP
            node
        }
        return listOf(page) + widgets
    }
}
