package com.app.pustakam.android.widgets.masterEditor

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.pustakam.android.widgets.smartText.SmartTextTokens
import com.app.pustakam.core.richtext.master.model.CanvasNode
import com.app.pustakam.core.richtext.master.presentation.CanvasCommands
import com.app.pustakam.core.richtext.master.presentation.CanvasEditorIntent
import com.app.pustakam.core.richtext.master.presentation.CanvasEditorState
import com.app.pustakam.core.richtext.master.presentation.CanvasTool
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Height of a page's name bar. Fixed, so a drop can take it off the finger's position exactly. */
private val PAGE_HEADER_HEIGHT = 32.dp

/** A page's scroll, hoisted to the board so a drop can read the page it lands on. */
private class PageScroll {
    val vertical = ScrollState(0)
    val horizontal = ScrollState(0)
    var handledToken = 0
}

// 📐 24-Sep-2026 — a carried widget and the finger carrying it, in dp on the board
private data class LiftedWidget(
    val node: CanvasNode,
    val left: Float,
    val top: Float,
    val fingerX: Float,
    val fingerY: Float
) {
    fun movedBy(dx: Float, dy: Float): LiftedWidget =
        copy(left = left + dx, top = top + dy, fingerX = fingerX + dx, fingerY = fingerY + dy)
}

/** What a page's widgets call while one of them is carried. */
private class WidgetLift(
    val start: (CanvasNode, Offset, Offset) -> Boolean,
    val move: (Float, Float) -> Unit,
    val release: (Boolean) -> Unit
)

/**
 * The scaling layer. Everything to do with pan and zoom lives here and nowhere else: this Box
 * owns the pinch, the drag-to-pan and the viewport size, and every page below is positioned and
 * sized from [CanvasEditorState.viewport].
 *
 * 📄 23-Sep-2026: the board draws PAPER only. Each page draws its own widgets inside itself,
 * clipped to the paper and scrolled by it, so a widget can never appear outside its page at any
 * zoom. A widget only leaves its page while it is being carried — then the board draws it.
 */
@Composable
fun MasterCanvas(
    state: CanvasEditorState,
    modifier: Modifier = Modifier,
    onIntent: (CanvasEditorIntent) -> Unit,
    onRename: (String, String) -> Unit = { _, _ -> },
    onMeasured: (String, Float) -> Unit = { _, _ -> },
    keyboardInsetPx: Float = 0f,
    nodeContent: @Composable (CanvasNode, Boolean) -> Unit
) {
    val colors = SmartTextTokens.colors
    val viewport = state.viewport
    val permits = state.permits
    // 📐 24-Sep-2026 — the canvas works in dp, as iOS works in points; pixels exist only at this boundary
    val pxPerDp = LocalDensity.current.density
    val liveState by rememberUpdatedState(state)
    val latestIntent by rememberUpdatedState(onIntent)
    val headerDp = PAGE_HEADER_HEIGHT.value
    val scrolls = remember { mutableMapOf<String, PageScroll>() }
    var lifted by remember { mutableStateOf<LiftedWidget?>(null) }

    val lift = remember(pxPerDp) {
        WidgetLift(
            start = { widget, topLeft, finger ->
                if (!CanvasCommands.canLift(liveState)) {
                    false
                } else {
                    latestIntent(CanvasCommands.beginDrag(widget.id))
                    lifted = LiftedWidget(widget, topLeft.x, topLeft.y, finger.x, finger.y)
                    true
                }
            },
            move = { dx, dy -> lifted = lifted?.movedBy(dx, dy) },
            release = { completed ->
                val carried = lifted
                lifted = null
                if (carried != null) {
                    val live = liveState
                    val scale = live.viewport.scale.takeIf { it > 0f } ?: 1f
                    // released over bare canvas, or cancelled: the widget goes back home
                    val target = if (!completed) null else CanvasCommands.dropTargetAt(
                        live,
                        carried.left + carried.node.rect.width * scale / 2f,
                        carried.top + carried.node.rect.height * scale / 2f
                    )
                    val scroll = target?.let { scrolls[it.id] }
                    latestIntent(
                        CanvasCommands.dropWidget(
                            live,
                            carried.node.id,
                            target,
                            carried.left,
                            carried.top,
                            (scroll?.horizontal?.value ?: 0) / pxPerDp,
                            (scroll?.vertical?.value ?: 0) / pxPerDp,
                            headerDp
                        )
                    )
                }
            }
        )
    }

    // 🧱 24-Sep-2026 — while a carried widget's finger is near a page's edge, that page scrolls the way the finger is going
    val carrying = lifted != null
    LaunchedEffect(carrying, pxPerDp) {
        if (!carrying) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val carried = lifted ?: break
            val live = liveState
            val target = CanvasCommands.dropTargetAt(live, carried.fingerX, carried.fingerY) ?: continue
            val scroll = scrolls[target.id] ?: continue
            val screen = CanvasCommands.screenRectOf(live.document, target, live.viewport)
            val stepY = CanvasCommands.autoScrollStep(
                carried.fingerY,
                screen.y + headerDp,
                screen.height - headerDp
            )
            val stepX = CanvasCommands.autoScrollStep(carried.fingerX, screen.x, screen.width)
            if (stepY != 0f) scroll.vertical.dispatchRawDelta(stepY * pxPerDp)
            if (stepX != 0f) scroll.horizontal.dispatchRawDelta(stepX * pxPerDp)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.page)
            // decorFitsSystemWindows is false and nothing here applies imePadding, so this
            // size - and the page fitted to it - does not change when the keyboard opens
            .onSizeChanged {
                onIntent(CanvasCommands.viewportResized(it.width / pxPerDp, it.height / pxPerDp))
            }
            // Intercepted on the INITIAL pass, before children see the event. detectTransformGestures
            // runs on the Main pass, so the BasicTextField filling each page consumed the pinch
            // first and the canvas never scaled. Two fingers are always ours; one finger is only
            // ours in hand mode, which leaves normal taps and text selection to the page.
            .pointerInput(permits, pxPerDp) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.count { it.pressed }
                        val multiTouch = pressed >= 2
                        val owns = (multiTouch && permits.canZoom) ||
                            (permits.canPan && state.tool != CanvasTool.SELECT)
                        if (owns) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (multiTouch && permits.canZoom && zoom != 1f) {
                                val centroid = event.calculateCentroid(useCurrent = false)
                                onIntent(CanvasCommands.zoom(zoom, centroid.x / pxPerDp, centroid.y / pxPerDp))
                            }
                            if (pan != Offset.Zero && permits.canPan) {
                                onIntent(CanvasCommands.pan(pan.x / pxPerDp, pan.y / pxPerDp))
                            }
                            if (multiTouch || pan != Offset.Zero) {
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(state.document, permits, pxPerDp) {
                detectTapGestures(
                    onTap = { onIntent(CanvasCommands.selectAt(it.x / pxPerDp, it.y / pxPerDp)) },
                    onDoubleTap = { position ->
                        val node = state.document.hitTest(
                            viewport.toCanvasX(position.x / pxPerDp),
                            viewport.toCanvasY(position.y / pxPerDp)
                        )
                        if (node != null) onIntent(CanvasCommands.setEditing(node.id))
                        else if (CanvasCommands.isEditing(state)) {
                            onIntent(CanvasCommands.exitEditing())
                        } else onIntent(CanvasCommands.zoomToFit())
                    }
                )
            }
    ) {
        // keyed by id: without this, a z-order change reorders the children and Compose
        // rebuilds the subtree, dropping focus out of whichever field was being typed in
        state.visibleNodes.forEach { page ->
            key(page.id) {
                MasterPage(
                    page = page,
                    state = state,
                    scroll = scrolls.getOrPut(page.id) { PageScroll() },
                    headerDp = headerDp,
                    keyboardInsetDp = keyboardInsetPx / pxPerDp,
                    onIntent = onIntent,
                    onRename = onRename,
                    onMeasured = onMeasured,
                    lift = lift,
                    nodeContent = nodeContent
                )
            }
        }
        lifted?.let { carried ->
            LiftedWidgetLayer(carried = carried, scale = viewport.scale, nodeContent = nodeContent)
        }
    }
}

/**
 * One sheet of paper: a name bar, then a viewport that scrolls vertically AND horizontally over
 * the page's content. Widgets keep their size and place on that content whatever the paper does
 * — when the paper shrinks they are simply scrolled to — and nothing is drawn outside it.
 */
@Composable
private fun BoxScope.MasterPage(
    page: CanvasNode,
    state: CanvasEditorState,
    scroll: PageScroll,
    headerDp: Float,
    keyboardInsetDp: Float,
    onIntent: (CanvasEditorIntent) -> Unit,
    onRename: (String, String) -> Unit,
    onMeasured: (String, Float) -> Unit,
    lift: WidgetLift,
    nodeContent: @Composable (CanvasNode, Boolean) -> Unit
) {
    val colors = SmartTextTokens.colors
    val pxPerDp = LocalDensity.current.density
    val liveState by rememberUpdatedState(state)
    val latestIntent by rememberUpdatedState(onIntent)
    val screen = CanvasCommands.screenRectOf(state.document, page, state.viewport)
    val scale = state.viewport.scale.takeIf { it > 0f } ?: 1f
    val isSelected = page.id == state.selectedNodeId
    val permits = state.permits
    val widgets = CanvasCommands.widgetsOnPage(state, page.id)
    val extent = CanvasCommands.contentExtentOf(state, page.id)
    val viewportHeight = (screen.height - headerDp).coerceAtLeast(0f)
    val editingHere = widgets.any { it.id == state.editingNodeId }
    // while typing on this page there must be room to scroll the caret clear of the keyboard
    val keyboardRoom =
        if (editingHere && keyboardInsetDp > 0f) keyboardInsetDp + viewportHeight / 3f else 0f
    val contentWidth = maxOf(screen.width, extent.width * scale)
    val contentHeight = maxOf(viewportHeight, extent.height * scale) + keyboardRoom

    // a tap on paper asks for its end; each request is acted on once, after the fit is laid out
    val scrollToken = CanvasCommands.scrollTokenFor(state, page.id)
    LaunchedEffect(scrollToken) {
        if (scrollToken <= scroll.handledToken) return@LaunchedEffect
        scroll.handledToken = scrollToken
        withFrameNanos { }
        launch { scroll.horizontal.animateScrollTo(0) }
        launch { scroll.vertical.animateScrollTo(scroll.vertical.maxValue) }
    }

    val paperGestures = Modifier.pointerInput(page.id, pxPerDp) {
        detectTapAndLift(
            onTap = { latestIntent(CanvasCommands.focusPage(page.id)) },
            onLift = {
                CanvasCommands.canLift(liveState).also { can ->
                    if (can) latestIntent(CanvasCommands.beginDrag(page.id))
                }
            },
            onMove = { delta -> latestIntent(CanvasCommands.dragBy(delta.x / pxPerDp, delta.y / pxPerDp)) },
            onRelease = { latestIntent(CanvasCommands.endDrag()) }
        )
    }

    Box(
        modifier = Modifier
            .graphicsLayer {
                translationX = screen.x * pxPerDp
                translationY = screen.y * pxPerDp
            }
            // zoomed in, paper is bigger than the board — never clamp it to the board's size
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .size(width = screen.width.dp, height = screen.height.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.page)
            .border(
                if (isSelected) 1.5.dp else 1.dp,
                if (isSelected) colors.accent else colors.divider,
                RoundedCornerShape(8.dp)
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PAGE_HEADER_HEIGHT)
                    .then(paperGestures)
            ) {
                MasterNodeNameBar(
                    node = page,
                    index = state.document.pages.indexOfFirst { it.id == page.id },
                    isSelected = isSelected,
                    onRename = { onRename(page.id, it) }
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .verticalScroll(scroll.vertical)
                    .horizontalScroll(scroll.horizontal)
            ) {
                Box(modifier = Modifier.size(width = contentWidth.dp, height = contentHeight.dp)) {
                    // the paper sits UNDER the widgets as a sibling, so a touch on a widget
                    // never reaches it — only bare paper focuses or carries the page
                    Box(modifier = Modifier.matchParentSize().then(paperGestures))
                    widgets.forEach { widget ->
                        key(widget.id) {
                            MasterPageWidget(
                                widget = widget,
                                scale = scale,
                                isEditing = widget.id == state.editingNodeId,
                                isLifted = CanvasCommands.isLifted(state, widget.id),
                                origin = {
                                    Offset(
                                        screen.x + widget.rect.x * scale - scroll.horizontal.value / pxPerDp,
                                        screen.y + headerDp + widget.rect.y * scale -
                                            scroll.vertical.value / pxPerDp
                                    )
                                },
                                onIntent = onIntent,
                                onMeasured = onMeasured,
                                lift = lift,
                                nodeContent = nodeContent
                            )
                        }
                    }
                }
            }
        }

        if (isSelected && permits.canResizeNode && !page.locked) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .size(22.dp)
                    .background(colors.accent, RoundedCornerShape(4.dp))
                    .pointerInput(page.id, pxPerDp) {
                        detectDragGestures(
                            onDragStart = { latestIntent(CanvasCommands.beginResize(page.id)) },
                            onDragEnd = { latestIntent(CanvasCommands.endResize()) },
                            onDragCancel = { latestIntent(CanvasCommands.endResize()) },
                            onDrag = { change, drag ->
                                change.consume()
                                CanvasCommands
                                    .resizedTo(liveState, page.id, drag.x / pxPerDp, drag.y / pxPerDp)
                                    ?.let(latestIntent)
                            }
                        )
                    }
            )
        }
    }
}

/**
 * One widget on the page's content, at its own fixed size and place. A text widget measures
 * itself so its page knows how far down to scroll. While carried it stays composed but
 * invisible — its gesture has to keep running.
 */
@Composable
private fun MasterPageWidget(
    widget: CanvasNode,
    scale: Float,
    isEditing: Boolean,
    isLifted: Boolean,
    origin: () -> Offset,
    onIntent: (CanvasEditorIntent) -> Unit,
    onMeasured: (String, Float) -> Unit,
    lift: WidgetLift,
    nodeContent: @Composable (CanvasNode, Boolean) -> Unit
) {
    val pxPerDp = LocalDensity.current.density
    // the gesture below is keyed on the id and outlives recompositions — read everything live
    val latestWidget by rememberUpdatedState(widget)
    val latestOrigin by rememberUpdatedState(origin)
    val latestIntent by rememberUpdatedState(onIntent)

    val liftGesture: suspend PointerInputScope.(onTap: (() -> Unit)?) -> Unit = { onTap ->
        detectTapAndLift(
            onTap = onTap,
            onLift = { touch ->
                val topLeft = latestOrigin()
                lift.start(
                    latestWidget,
                    topLeft,
                    Offset(topLeft.x + touch.x / pxPerDp, topLeft.y + touch.y / pxPerDp)
                )
            },
            onMove = { delta -> lift.move(delta.x / pxPerDp, delta.y / pxPerDp) },
            onRelease = { completed -> lift.release(completed) }
        )
    }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (widget.rect.x * scale * pxPerDp).roundToInt(),
                    (widget.rect.y * scale * pxPerDp).roundToInt()
                )
            }
            .width((widget.rect.width * scale).dp)
            .then(
                if (widget.isTextWidget) {
                    Modifier
                        .wrapContentHeight(Alignment.Top, unbounded = true)
                        .onSizeChanged { measured ->
                            if (measured.height > 0) onMeasured(widget.id, measured.height / pxPerDp / scale)
                        }
                } else {
                    // 🧱 a card never spills onto its neighbours, whatever it is asked to draw
                    Modifier.height((widget.rect.height * scale).dp).clipToBounds()
                }
            )
            .graphicsLayer { alpha = if (isLifted) 0f else 1f }
            .then(
                // a text field owns its own long-press (selection), so text is lifted through
                // the shield below instead; everything else lifts from here
                if (widget.isTextWidget) Modifier
                else Modifier.pointerInput(widget.id, pxPerDp) { liftGesture(null) }
            )
    ) {
        if (widget.isTextWidget) {
            nodeContent(widget, isEditing)
            if (!isEditing) {
                // until it has the caret, a text widget is a block: tap starts editing it,
                // long-press carries it — the field underneath never sees either touch
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(widget.id, pxPerDp) {
                            liftGesture { latestIntent(CanvasCommands.setEditing(widget.id)) }
                        }
                )
            }
        } else {
            ScaledCard(widget = widget, scale = scale, onMeasured = onMeasured) {
                nodeContent(widget, isEditing)
            }
        }
    }
}

// 🧱 24-Sep-2026 — a card is laid out once at its own canvas size and zoomed like a picture, so a pinch never changes its height
@Composable
private fun ScaledCard(
    widget: CanvasNode,
    scale: Float,
    onMeasured: (String, Float) -> Unit,
    content: @Composable () -> Unit
) {
    val pxPerDp = LocalDensity.current.density
    Box(
        modifier = Modifier
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .width(widget.rect.width.dp)
            .then(
                if (widget.isMeasured) {
                    Modifier.onSizeChanged { measured ->
                        if (measured.height > 0) onMeasured(widget.id, measured.height / pxPerDp)
                    }
                } else {
                    Modifier.height(widget.rect.height.dp)
                }
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
    ) {
        content()
    }
}

/** The carried widget, drawn above every page while the finger moves it. */
@Composable
private fun LiftedWidgetLayer(
    carried: LiftedWidget,
    scale: Float,
    nodeContent: @Composable (CanvasNode, Boolean) -> Unit
) {
    val colors = SmartTextTokens.colors
    val pxPerDp = LocalDensity.current.density
    val node = carried.node
    Box(
        modifier = Modifier
            .offset {
                IntOffset((carried.left * pxPerDp).roundToInt(), (carried.top * pxPerDp).roundToInt())
            }
            .size(width = (node.rect.width * scale).dp, height = (node.rect.height * scale).dp)
            .graphicsLayer {
                shadowElevation = 12.dp.toPx()
                shape = RoundedCornerShape(6.dp)
                clip = true
            }
            .background(colors.page)
    ) {
        if (node.isTextWidget) {
            nodeContent(node, false)
        } else {
            ScaledCard(widget = node, scale = scale, onMeasured = { _, _ -> }) {
                nodeContent(node, false)
            }
        }
    }
}

/**
 * One finger: a tap (when [onTap] is set), or a long-press that picks something up and carries
 * it. A swipe is neither and is left to the page's scroll. One detector rather than the stock
 * two, because the stock tap detector swallows a long-press and would starve the carry.
 */
private suspend fun PointerInputScope.detectTapAndLift(
    onTap: (() -> Unit)?,
    onLift: (Offset) -> Boolean,
    onMove: (Offset) -> Unit,
    onRelease: (Boolean) -> Unit
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    // 🔧 24-Sep-2026 — claim the touch: the board's tap detector otherwise sees an unclaimed
    //   down, eats the up, and this tap never fires. Scrolling ignores a consumed down.
    down.consume()
    val held = awaitLongPressOrCancellation(down.id)
    if (held == null) {
        val up = currentEvent.changes.firstOrNull { it.id == down.id }
        val still = up != null &&
            (up.position - down.position).getDistance() < viewConfiguration.touchSlop
        if (onTap != null && up != null && up.changedToUp() && still) {
            up.consume()
            onTap()
        }
        return@awaitEachGesture
    }
    if (!onLift(held.position)) return@awaitEachGesture
    held.consume()
    // 🧱 24-Sep-2026 — once something is picked up the finger belongs to the carry, so the card under it never takes the drop for a tap
    var completed = false
    try {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == held.id } ?: break
            if (!change.pressed) {
                change.consume()
                completed = true
                break
            }
            val delta = change.positionChange()
            if (delta != Offset.Zero) onMove(delta)
            change.consume()
        }
    } finally {
        onRelease(completed)
    }
}

@Composable
private fun MasterNodeNameBar(
    node: CanvasNode,
    index: Int,
    isSelected: Boolean,
    onRename: (String) -> Unit
) {
    val colors = SmartTextTokens.colors
    var editing by remember(node.id) { mutableStateOf(false) }
    var draft by remember(node.id, node.name) { mutableStateOf(node.name) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isSelected) colors.accentSoft else colors.surface)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (editing) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = TextStyle(color = colors.onSurface, fontSize = 12.sp),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    editing = false
                    onRename(draft.trim())
                }),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                text = node.displayName(index.coerceAtLeast(0)),
                style = TextStyle(
                    color = if (isSelected) colors.accent else colors.onSurfaceMuted,
                    fontSize = 12.sp
                ),
                modifier = Modifier.clickable { editing = true }
            )
        }
    }
}
