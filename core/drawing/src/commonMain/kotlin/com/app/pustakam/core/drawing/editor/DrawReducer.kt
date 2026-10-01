package com.app.pustakam.core.drawing.editor

import com.app.pustakam.core.drawing.anchor.DrawAnchoring
import com.app.pustakam.core.drawing.model.DrawPoint
import com.app.pustakam.core.drawing.model.DrawPointerType
import com.app.pustakam.core.drawing.model.DrawSurface
import com.app.pustakam.core.drawing.ops.DrawOp
import com.app.pustakam.core.drawing.ops.DrawOpApplier
import com.app.pustakam.core.drawing.tool.DrawGesture
import com.app.pustakam.core.drawing.tool.DrawToolContext
import com.app.pustakam.core.drawing.tool.DrawToolId
import com.app.pustakam.core.drawing.tool.DrawToolMode
import com.app.pustakam.core.drawing.tool.DrawToolPreview
import com.app.pustakam.core.drawing.tool.DrawToolRegistry
import com.app.pustakam.core.drawing.viewport.DrawSpace
import com.app.pustakam.core.drawing.viewport.DrawViewport
import com.app.pustakam.core.richtext.master.model.CanvasRect

object DrawReducer {

    fun reduce(state: DrawEditorState, intent: DrawIntent): DrawEditorState = when (intent) {
        is DrawIntent.Load -> loaded(state, intent)
        is DrawIntent.Resize -> resized(state, intent.width, intent.height)
        is DrawIntent.PointerDown -> pointerDown(state, intent)
        is DrawIntent.PointerMove -> pointerMove(state, intent.points)
        DrawIntent.PointerUp -> pointerUp(state)
        DrawIntent.PointerCancel -> cancelled(state)
        is DrawIntent.Navigate -> navigated(state, intent)
        DrawIntent.NavigateEnd -> withViewport(state.copy(navigating = false)) { it.snappedRotation() }
        DrawIntent.ZoomIn -> withViewport(state) { it.zoomIn() }
        DrawIntent.ZoomOut -> withViewport(state) { it.zoomOut() }
        DrawIntent.FitPaper -> withViewport(state) { home(state, it) }
        DrawIntent.CenterPaper -> withViewport(state) { it.centeredOn(homeRect(state, it)) }
        DrawIntent.ResetRotation -> withViewport(state) { it.resetRotation() }
        is DrawIntent.SetTool -> idle(state).copy(tool = intent.tool)
        is DrawIntent.SetMode -> modeChanged(state, intent.mode)
        is DrawIntent.SetBrushKind -> idle(state).copy(settings = state.settings.withBrushKind(intent.kind), tool = DrawToolId.PEN)
        is DrawIntent.UpdateBrush -> state.copy(settings = state.settings.withBrush(intent.brush))
        is DrawIntent.SetEraserKind -> idle(state).copy(settings = state.settings.withEraserKind(intent.kind), tool = DrawToolId.ERASER)
        is DrawIntent.SetEraserSize -> state.copy(settings = state.settings.withEraserSize(intent.size))
        is DrawIntent.SetShapeKind -> idle(state).copy(settings = state.settings.withShapeKind(intent.kind), tool = DrawToolId.SHAPE)
        is DrawIntent.UpdateShape -> state.copy(settings = state.settings.withShape(intent.shape))
        is DrawIntent.SetStylusOnly -> state.copy(settings = state.settings.withStylusOnly(intent.enabled))
        is DrawIntent.SetColor -> colorPicked(state, intent)
        is DrawIntent.ToggleFavorite -> state.copy(colors = state.colors.toggledFavorite(intent.color))
        is DrawIntent.AddPalette -> state.copy(colors = state.colors.withPalette(intent.palette))
        is DrawIntent.AddToPalette -> state.copy(colors = state.colors.updatingPalette(intent.paletteId) { it.withColor(intent.color) })
        is DrawIntent.RemoveFromPalette -> state.copy(colors = state.colors.updatingPalette(intent.paletteId) { it.withoutColor(intent.color) })
        is DrawIntent.RemovePalette -> state.copy(colors = state.colors.withoutPalette(intent.paletteId))
        is DrawIntent.UpdatePaper -> paperChanged(state, intent)
        DrawIntent.Undo -> undone(state)
        DrawIntent.Redo -> redone(state)
        DrawIntent.Clear -> cleared(state)
        is DrawIntent.ApplyRemote -> remote(state, intent.op)
        is DrawIntent.SetReadOnly -> idle(state).copy(readOnly = intent.readOnly)
        is DrawIntent.RestoreToolbox -> state.copy(
            settings = DrawToolRegistry.fitted(intent.settings, intent.mode),
            colors = intent.colors,
            mode = intent.mode
        )
    }

    fun nextClock(state: DrawEditorState): Long = state.document.clock + 1

    private fun idle(state: DrawEditorState): DrawEditorState =
        if (state.gesture == null && state.preview.isEmpty) state else state.copy(gesture = null, preview = DrawToolPreview.EMPTY)

    private fun cancelled(state: DrawEditorState): DrawEditorState =
        if (state.navigating) idle(state).copy(navigating = false) else idle(state)

    private fun loaded(state: DrawEditorState, intent: DrawIntent.Load): DrawEditorState = framed(
        idle(state).copy(
            document = intent.document,
            history = state.history.cleared(),
            revision = state.revision + 1,
            lastOp = null
        )
    )

    private fun resized(state: DrawEditorState, width: Float, height: Float): DrawEditorState {
        val firstMeasure = !state.viewport.isMeasured
        val sized = state.copy(viewport = state.viewport.resized(width, height))
        return if (firstMeasure || state.surface == DrawSurface.WIDGET) framed(sized) else sized
    }

    private fun framed(state: DrawEditorState): DrawEditorState {
        val viewport = state.viewport
        if (!viewport.isMeasured) return state
        return when (state.surface) {
            DrawSurface.OVERLAY -> state
            DrawSurface.PAGE -> state.copy(viewport = home(state, viewport))
            DrawSurface.WIDGET -> state.copy(
                viewport = DrawViewport.sized(viewport.width, viewport.height),
                document = state.document.withPaper(state.document.paper.withScreenSize(viewport.width, viewport.height))
            )
        }
    }

    private fun reframed(state: DrawEditorState): DrawEditorState =
        if (state.surface == DrawSurface.WIDGET) framed(state) else state

    private fun pointerDown(state: DrawEditorState, intent: DrawIntent.PointerDown): DrawEditorState {
        if (state.readOnly || state.navigating) return state
        val point = normalized(intent.point, intent.pointer)
        val panning = state.tool == DrawToolId.HAND ||
            (state.settings.stylusOnly && intent.pointer == DrawPointerType.FINGER)
        if (panning) return if (state.navigates) state.copy(gesture = handGesture(point, intent.pointer)) else state
        val handler = DrawToolRegistry.handler(state.tool) ?: return state
        if (!state.document.activeLayer.isEditable) return state
        val elementId = DrawIds.next()
        val gesture = DrawGesture(
            tool = state.tool,
            elementId = elementId,
            seed = elementId.hashCode(),
            anchorId = DrawAnchoring.pick(intent.anchors, point.x, point.y)?.id,
            frames = intent.anchors,
            pointer = intent.pointer,
            points = emptyList(),
            touchedIds = emptyList()
        )
        val context = contextOf(state, gesture)
        val started = handler.extend(context, gesture, listOf(point))
        return state.copy(gesture = started, preview = handler.preview(context, started))
    }

    private fun pointerMove(state: DrawEditorState, points: List<DrawPoint>): DrawEditorState {
        val gesture = state.gesture ?: return state
        if (points.isEmpty()) return state
        if (gesture.tool == DrawToolId.HAND) {
            val last = gesture.last ?: return state
            val target = points.last()
            return state.copy(
                viewport = state.viewport.panned(target.x - last.x, target.y - last.y),
                gesture = gesture.withPoints(listOf(target))
            )
        }
        val handler = DrawToolRegistry.handler(gesture.tool) ?: return state
        val context = contextOf(state, gesture)
        val extended = handler.extend(context, gesture, points.map { normalized(it, gesture.pointer) })
        return state.copy(gesture = extended, preview = handler.preview(context, extended))
    }

    private fun pointerUp(state: DrawEditorState): DrawEditorState {
        val gesture = state.gesture ?: return state
        val released = idle(state)
        if (gesture.tool == DrawToolId.HAND) return released
        val handler = DrawToolRegistry.handler(gesture.tool) ?: return released
        val op = handler.finish(contextOf(state, gesture), gesture) ?: return released
        val committed = commit(released, op, record = true)
        val inked = gesture.tool == DrawToolId.PEN || gesture.tool == DrawToolId.SHAPE
        return if (inked) committed.copy(colors = committed.colors.used(state.colors.current)) else committed
    }

    private fun navigated(state: DrawEditorState, intent: DrawIntent.Navigate): DrawEditorState {
        if (!state.navigates) return idle(state)
        val viewport = state.viewport
            .panned(intent.panX, intent.panY)
            .zoomed(intent.zoom, intent.focusX, intent.focusY)
            .rotatedBy(intent.rotation, intent.focusX, intent.focusY)
        return idle(state).copy(viewport = viewport, navigating = true)
    }

    private fun modeChanged(state: DrawEditorState, mode: DrawToolMode): DrawEditorState {
        val tool = if (DrawToolRegistry.isAvailable(state.tool, mode)) state.tool else DrawToolId.PEN
        return idle(state).copy(mode = mode, tool = tool, settings = DrawToolRegistry.fitted(state.settings, mode))
    }

    private fun colorPicked(state: DrawEditorState, intent: DrawIntent.SetColor): DrawEditorState {
        val tool = if (state.tool == DrawToolId.ERASER || state.tool == DrawToolId.HAND) DrawToolId.PEN else state.tool
        return state.copy(colors = state.colors.withCurrent(intent.color), tool = tool)
    }

    private fun paperChanged(state: DrawEditorState, intent: DrawIntent.UpdatePaper): DrawEditorState {
        if (state.readOnly || intent.paper == state.document.paper) return state
        val op = DrawOp.Paper(state.document.paper, intent.paper, state.author, nextClock(state))
        val committed = commit(state, op, record = true)
        return if (intent.paper.bounds != state.document.paper.bounds) framed(committed) else reframed(committed)
    }

    private fun undone(state: DrawEditorState): DrawEditorState {
        if (state.readOnly || state.gesture != null) return state
        val step = state.history.undoStep() ?: return state
        return reframed(commit(state.copy(history = step.history), step.op.inverse(state.author, nextClock(state)), record = false))
    }

    private fun redone(state: DrawEditorState): DrawEditorState {
        if (state.readOnly || state.gesture != null) return state
        val step = state.history.redoStep() ?: return state
        return reframed(commit(state.copy(history = step.history), step.op.restamped(state.author, nextClock(state)), record = false))
    }

    private fun cleared(state: DrawEditorState): DrawEditorState {
        if (state.readOnly) return state
        val removable = state.document.elements.filter { state.document.isEditableLayer(it.layerId) }
        if (removable.isEmpty()) return idle(state)
        return commit(idle(state), DrawOp.Remove(removable, state.author, nextClock(state)), record = true)
    }

    private fun remote(state: DrawEditorState, op: DrawOp): DrawEditorState = reframed(
        state.copy(
            document = DrawOpApplier.apply(state.document, op),
            revision = state.revision + 1,
            editSequence = state.editSequence + 1
        )
    )

    private fun commit(state: DrawEditorState, op: DrawOp, record: Boolean): DrawEditorState = state.copy(
        document = DrawOpApplier.apply(state.document, op),
        history = if (record) state.history.record(op) else state.history,
        revision = state.revision + 1,
        editSequence = state.editSequence + 1,
        lastOp = op,
        opSequence = state.opSequence + 1
    )

    private fun contextOf(state: DrawEditorState, gesture: DrawGesture): DrawToolContext = DrawToolContext(
        document = state.document,
        settings = state.settings,
        color = state.colors.current,
        author = state.author,
        clock = nextClock(state),
        space = DrawSpace(state.viewport, gesture.frames),
        newId = { DrawIds.next() }
    )

    private fun handGesture(point: DrawPoint, pointer: DrawPointerType): DrawGesture = DrawGesture(
        tool = DrawToolId.HAND,
        elementId = "",
        seed = 0,
        anchorId = null,
        frames = emptyList(),
        pointer = pointer,
        points = listOf(point),
        touchedIds = emptyList()
    )

    private fun normalized(point: DrawPoint, pointer: DrawPointerType): DrawPoint =
        if (pointer == DrawPointerType.STYLUS) point else point.withPressure(DrawPoint.DEFAULT_PRESSURE)

    private fun withViewport(state: DrawEditorState, transform: (DrawViewport) -> DrawViewport): DrawEditorState =
        if (!state.navigates || !state.viewport.isMeasured) state else state.copy(viewport = transform(state.viewport))

    private fun homeRect(state: DrawEditorState, viewport: DrawViewport): CanvasRect {
        val paper = state.document.paper
        if (!paper.isInfinite) return paper.bounds
        val content = state.document.contentBounds
        return if (content.width > 0f && content.height > 0f) content else CanvasRect(0f, 0f, viewport.width, viewport.height)
    }

    private fun home(state: DrawEditorState, viewport: DrawViewport): DrawViewport {
        if (state.document.paper.isInfinite && state.document.contentBounds.width <= 0f) {
            return DrawViewport.sized(viewport.width, viewport.height)
        }
        val padding = if (state.document.paper.isInfinite) DrawViewport.DEFAULT_PADDING else 0f
        return viewport.fitted(homeRect(state, viewport), padding)
    }
}
