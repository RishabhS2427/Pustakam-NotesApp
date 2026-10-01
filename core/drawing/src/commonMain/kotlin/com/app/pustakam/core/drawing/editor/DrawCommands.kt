package com.app.pustakam.core.drawing.editor

import com.app.pustakam.core.drawing.anchor.DrawAnchorFrame
import com.app.pustakam.core.drawing.anchor.DrawAnchoring
import com.app.pustakam.core.drawing.brush.DrawBrushCatalog
import com.app.pustakam.core.drawing.codec.DrawCodec
import com.app.pustakam.core.drawing.color.DrawPaletteCatalog
import com.app.pustakam.core.drawing.color.DrawPalette
import com.app.pustakam.core.drawing.geometry.DrawMatrix
import com.app.pustakam.core.drawing.geometry.DrawPathBuilder
import com.app.pustakam.core.drawing.model.DrawBlend
import com.app.pustakam.core.drawing.model.DrawBrush
import com.app.pustakam.core.drawing.model.DrawBrushKind
import com.app.pustakam.core.drawing.model.DrawColor
import com.app.pustakam.core.drawing.model.DrawDocument
import com.app.pustakam.core.drawing.model.DrawOrientation
import com.app.pustakam.core.drawing.model.DrawPaper
import com.app.pustakam.core.drawing.model.DrawPaperSize
import com.app.pustakam.core.drawing.model.DrawPattern
import com.app.pustakam.core.drawing.model.DrawPoint
import com.app.pustakam.core.drawing.model.DrawPointerType
import com.app.pustakam.core.drawing.model.DrawShapeKind
import com.app.pustakam.core.drawing.model.DrawShapeSpec
import com.app.pustakam.core.drawing.model.DrawSurface
import com.app.pustakam.core.drawing.model.DrawTexture
import com.app.pustakam.core.drawing.model.DrawUnit
import com.app.pustakam.core.drawing.model.DrawUnits
import com.app.pustakam.core.drawing.ops.DrawOp
import com.app.pustakam.core.drawing.render.DrawCap
import com.app.pustakam.core.drawing.render.DrawFrameBuilder
import com.app.pustakam.core.drawing.render.DrawJoin
import com.app.pustakam.core.drawing.render.DrawPatternRenderer
import com.app.pustakam.core.drawing.render.DrawPreviews
import com.app.pustakam.core.drawing.render.DrawRenderCache
import com.app.pustakam.core.drawing.render.DrawRenderEntry
import com.app.pustakam.core.drawing.render.DrawRenderItem
import com.app.pustakam.core.drawing.render.DrawRenderKind
import com.app.pustakam.core.drawing.tool.DrawEraserKind
import com.app.pustakam.core.drawing.tool.DrawToolId
import com.app.pustakam.core.drawing.tool.DrawToolMode
import com.app.pustakam.core.drawing.tool.DrawToolRegistry
import com.app.pustakam.core.drawing.tool.DrawToolSettings
import com.app.pustakam.core.drawing.tool.DrawToolSpec
import com.app.pustakam.core.drawing.viewport.DrawSpace
import com.app.pustakam.core.richtext.master.model.CanvasRect

object DrawCommands {

    private const val PREVIEW_STROKE = 2f

    private const val ROTATION_EPSILON = 0.5f

    private const val FULL_TURN = 360f

    private const val COLOR_TOLERANCE = 4

    private const val MAX_CACHED_ZOOM = 1.05f

    private val DPI_CHOICES = listOf(72, 96, 150, 300, 600)

    fun overlayState(author: String, readOnly: Boolean): DrawEditorState =
        DrawEditorStates.initial(DrawDocuments.overlay(), author, readOnly)

    fun pageState(author: String, width: Float, height: Float, readOnly: Boolean): DrawEditorState =
        DrawEditorStates.initial(DrawDocuments.page(width, height), author, readOnly)

    fun widgetState(author: String, width: Float, height: Float, readOnly: Boolean): DrawEditorState =
        DrawEditorStates.initial(DrawDocuments.widget(width, height), author, readOnly)

    fun stateFor(
        payload: String?,
        surface: DrawSurface,
        author: String,
        width: Float,
        height: Float,
        readOnly: Boolean
    ): DrawEditorState {
        val fallback = DrawDocuments.of(surface, width, height)
        return DrawEditorStates.initial(DrawCodec.decode(payload, fallback), author, readOnly)
    }

    fun carrying(previous: DrawEditorState, document: DrawDocument, readOnly: Boolean): DrawEditorState =
        DrawEditorStates.carrying(previous, document, readOnly)

    fun overlaySurface(): DrawSurface = DrawSurface.OVERLAY

    fun pageSurface(): DrawSurface = DrawSurface.PAGE

    fun widgetSurface(): DrawSurface = DrawSurface.WIDGET

    fun surfaceKey(surface: DrawSurface): String = surface.name

    fun surfaceOf(key: String?): DrawSurface = DrawSurface.entries.firstOrNull { it.name == key } ?: DrawSurface.WIDGET

    fun sample(x: Float, y: Float, pressure: Float, tilt: Float, azimuth: Float, time: Long): DrawPoint =
        DrawPoint.sample(x, y, pressure, tilt, azimuth, time)

    fun anchor(id: String, x: Float, y: Float, width: Float, height: Float): DrawAnchorFrame =
        DrawAnchoring.frame(id, x, y, width, height)

    fun mappedAnchor(
        id: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        originX: Float,
        originY: Float,
        scale: Float
    ): DrawAnchorFrame = DrawAnchoring.mapped(id, x, y, width, height, originX, originY, scale)

    fun finger(): DrawPointerType = DrawPointerType.FINGER

    fun stylus(): DrawPointerType = DrawPointerType.STYLUS

    fun mouse(): DrawPointerType = DrawPointerType.MOUSE

    fun pointerDown(point: DrawPoint, pointer: DrawPointerType, anchors: List<DrawAnchorFrame>): DrawIntent =
        DrawIntent.PointerDown(point, pointer, anchors)

    fun pointerMove(points: List<DrawPoint>): DrawIntent = DrawIntent.PointerMove(points)

    fun pointerUp(): DrawIntent = DrawIntent.PointerUp

    fun pointerCancel(): DrawIntent = DrawIntent.PointerCancel

    fun navigate(panX: Float, panY: Float, zoom: Float, rotation: Float, focusX: Float, focusY: Float): DrawIntent =
        DrawIntent.Navigate(panX, panY, zoom, rotation, focusX, focusY)

    fun navigateEnd(): DrawIntent = DrawIntent.NavigateEnd

    fun resize(width: Float, height: Float): DrawIntent = DrawIntent.Resize(width, height)

    fun zoomIn(): DrawIntent = DrawIntent.ZoomIn

    fun zoomOut(): DrawIntent = DrawIntent.ZoomOut

    fun fitPaper(): DrawIntent = DrawIntent.FitPaper

    fun centerPaper(): DrawIntent = DrawIntent.CenterPaper

    fun resetRotation(): DrawIntent = DrawIntent.ResetRotation

    fun tools(state: DrawEditorState): List<DrawToolSpec> = DrawToolRegistry.specsFor(state.mode)

    fun setTool(tool: DrawToolId): DrawIntent = DrawIntent.SetTool(tool)

    fun isTool(state: DrawEditorState, tool: DrawToolId): Boolean = state.tool == tool

    fun toolKey(tool: DrawToolId): String = tool.name

    fun toolLabel(tool: DrawToolId): String = DrawToolRegistry.spec(tool).label

    fun toolIconKey(tool: DrawToolId): String = DrawToolRegistry.spec(tool).iconKey

    fun penTool(): DrawToolId = DrawToolId.PEN

    fun eraserTool(): DrawToolId = DrawToolId.ERASER

    fun shapeTool(): DrawToolId = DrawToolId.SHAPE

    fun handTool(): DrawToolId = DrawToolId.HAND

    fun modes(): List<DrawToolMode> = DrawToolMode.entries

    fun setMode(mode: DrawToolMode): DrawIntent = DrawIntent.SetMode(mode)

    fun toggleMode(state: DrawEditorState): DrawIntent =
        DrawIntent.SetMode(if (state.mode == DrawToolMode.BASIC) DrawToolMode.ADVANCED else DrawToolMode.BASIC)

    fun isAdvanced(state: DrawEditorState): Boolean = state.mode == DrawToolMode.ADVANCED

    fun modeLabel(mode: DrawToolMode): String = if (mode == DrawToolMode.BASIC) "Basic" else "Advanced"

    fun brushKinds(): List<DrawBrushKind> = DrawBrushCatalog.kinds()

    fun brushLabel(kind: DrawBrushKind): String = DrawBrushCatalog.label(kind)

    fun brushIconKey(kind: DrawBrushKind): String = DrawBrushCatalog.iconKey(kind)

    fun brushKey(kind: DrawBrushKind): String = kind.name

    fun setBrushKind(kind: DrawBrushKind): DrawIntent = DrawIntent.SetBrushKind(kind)

    fun isBrush(state: DrawEditorState, kind: DrawBrushKind): Boolean =
        state.tool == DrawToolId.PEN && state.settings.brushKind == kind

    fun currentBrush(state: DrawEditorState): DrawBrush = state.settings.brush

    fun updateBrush(brush: DrawBrush): DrawIntent = DrawIntent.UpdateBrush(brush)

    fun setBrushSize(state: DrawEditorState, value: Float): DrawIntent = updateBrush(state.brush.withSize(value))

    fun setBrushOpacity(state: DrawEditorState, value: Float): DrawIntent = updateBrush(state.brush.withOpacity(value))

    fun setBrushFlow(state: DrawEditorState, value: Float): DrawIntent = updateBrush(state.brush.withFlow(value))

    fun setBrushHardness(state: DrawEditorState, value: Float): DrawIntent = updateBrush(state.brush.withHardness(value))

    fun setBrushSpacing(state: DrawEditorState, value: Float): DrawIntent = updateBrush(state.brush.withSpacing(value))

    fun setBrushSmoothing(state: DrawEditorState, value: Float): DrawIntent = updateBrush(state.brush.withSmoothing(value))

    fun setPressureSensitivity(state: DrawEditorState, value: Float): DrawIntent =
        updateBrush(state.brush.withPressureSensitivity(value))

    fun setTiltSensitivity(state: DrawEditorState, value: Float): DrawIntent =
        updateBrush(state.brush.withTiltSensitivity(value))

    fun setVelocitySensitivity(state: DrawEditorState, value: Float): DrawIntent =
        updateBrush(state.brush.withVelocitySensitivity(value))

    fun minBrushSize(): Float = DrawBrush.MIN_SIZE

    fun maxBrushSize(): Float = DrawBrush.MAX_SIZE

    fun eraserKinds(state: DrawEditorState): List<DrawEraserKind> = DrawToolRegistry.erasersFor(state.mode)

    fun eraserLabel(kind: DrawEraserKind): String = when (kind) {
        DrawEraserKind.PIXEL -> "Pixel"
        DrawEraserKind.PARTIAL -> "Portion"
        DrawEraserKind.STROKE -> "Whole stroke"
        DrawEraserKind.OBJECT -> "Whole object"
    }

    fun eraserKey(kind: DrawEraserKind): String = kind.name

    fun setEraserKind(kind: DrawEraserKind): DrawIntent = DrawIntent.SetEraserKind(kind)

    fun isEraser(state: DrawEditorState, kind: DrawEraserKind): Boolean =
        state.tool == DrawToolId.ERASER && state.settings.eraserKind == kind

    fun setEraserSize(size: Float): DrawIntent = DrawIntent.SetEraserSize(size)

    fun eraserSize(state: DrawEditorState): Float = state.settings.eraserSize

    fun currentTool(state: DrawEditorState): DrawToolId = state.tool

    fun currentBrushKind(state: DrawEditorState): DrawBrushKind = state.settings.brushKind

    fun currentEraserKind(state: DrawEditorState): DrawEraserKind = state.settings.eraserKind

    fun currentShapeKind(state: DrawEditorState): DrawShapeKind = state.settings.shapeKind

    fun brushControls(state: DrawEditorState): List<DrawBrushControl> = DrawControls.brushControls(state.mode)

    fun sizeControl(): DrawBrushControl = DrawBrushControl.SIZE

    fun brushControlLabel(control: DrawBrushControl): String = DrawControls.brushLabel(control)

    fun brushControlPosition(state: DrawEditorState, control: DrawBrushControl): Float =
        DrawControls.brushPosition(state.brush, control)

    fun brushControlText(state: DrawEditorState, control: DrawBrushControl): String =
        DrawControls.brushText(state.brush, control)

    fun setBrushControl(state: DrawEditorState, control: DrawBrushControl, position: Float): DrawIntent =
        updateBrush(DrawControls.brushWith(state.brush, control, DrawControls.brushValueAt(control, position)))

    fun eraserPosition(state: DrawEditorState): Float = DrawControls.eraserPosition(state.settings)

    fun eraserText(state: DrawEditorState): String = DrawControls.eraserText(state.settings)

    fun setEraserPosition(position: Float): DrawIntent = setEraserSize(DrawControls.eraserValueAt(position))

    fun shapeControls(state: DrawEditorState): List<DrawShapeControl> = DrawControls.shapeControls(state.settings.shapeKind)

    fun shapeControlLabel(control: DrawShapeControl): String = DrawControls.shapeLabel(control)

    fun shapeControlPosition(state: DrawEditorState, control: DrawShapeControl): Float =
        DrawControls.shapePosition(state.settings.shape, control)

    fun shapeControlText(state: DrawEditorState, control: DrawShapeControl): String =
        DrawControls.shapeText(state.settings.shape, control)

    fun setShapeControl(state: DrawEditorState, control: DrawShapeControl, position: Float): DrawIntent =
        DrawIntent.UpdateShape(DrawControls.shapeWith(state.settings.shape, control, position))

    fun canFillShape(state: DrawEditorState): Boolean = state.settings.shape.isFillable

    fun isShapeFilled(state: DrawEditorState): Boolean = state.settings.shape.filled

    fun patternSpacingPosition(state: DrawEditorState): Float = DrawControls.spacingPosition(state.document.paper.spacing)

    fun patternSpacingText(state: DrawEditorState): String = DrawControls.spacingText(state.document.paper.spacing)

    fun setPatternSpacingPosition(state: DrawEditorState, position: Float): DrawIntent =
        setPatternSpacing(state, DrawControls.spacingValueAt(position))

    fun minEraserSize(): Float = DrawToolSettings.MIN_ERASER

    fun maxEraserSize(): Float = DrawToolSettings.MAX_ERASER

    fun shapeKinds(): List<DrawShapeKind> = DrawShapeSpec.kinds()

    fun shapeLabel(kind: DrawShapeKind): String = when (kind) {
        DrawShapeKind.NONE -> ""
        DrawShapeKind.LINE -> "Line"
        DrawShapeKind.RECTANGLE -> "Rectangle"
        DrawShapeKind.ROUNDED_RECTANGLE -> "Rounded rectangle"
        DrawShapeKind.CIRCLE -> "Circle"
        DrawShapeKind.ELLIPSE -> "Ellipse"
        DrawShapeKind.TRIANGLE -> "Triangle"
        DrawShapeKind.POLYGON -> "Polygon"
        DrawShapeKind.STAR -> "Star"
        DrawShapeKind.ARROW -> "Arrow"
        DrawShapeKind.ARC -> "Arc"
    }

    fun shapeKey(kind: DrawShapeKind): String = kind.name

    fun setShapeKind(kind: DrawShapeKind): DrawIntent = DrawIntent.SetShapeKind(kind)

    fun isShape(state: DrawEditorState, kind: DrawShapeKind): Boolean =
        state.tool == DrawToolId.SHAPE && state.settings.shapeKind == kind

    fun currentShape(state: DrawEditorState): DrawShapeSpec = state.settings.shape

    fun setShapeSides(state: DrawEditorState, value: Int): DrawIntent = DrawIntent.UpdateShape(state.settings.shape.withSides(value))

    fun setShapeFilled(state: DrawEditorState, value: Boolean): DrawIntent =
        DrawIntent.UpdateShape(state.settings.shape.withFilled(value))

    fun setShapeCornerRadius(state: DrawEditorState, value: Float): DrawIntent =
        DrawIntent.UpdateShape(state.settings.shape.withCornerRadius(value))

    fun setShapeInnerRatio(state: DrawEditorState, value: Float): DrawIntent =
        DrawIntent.UpdateShape(state.settings.shape.withInnerRatio(value))

    fun setShapeSweep(state: DrawEditorState, value: Float): DrawIntent =
        DrawIntent.UpdateShape(state.settings.shape.withSweep(value))

    fun setStylusOnly(enabled: Boolean): DrawIntent = DrawIntent.SetStylusOnly(enabled)

    fun colorFromHex(hex: String): DrawColor? = DrawColor.fromHex(hex)

    fun colorFromRgb(red: Int, green: Int, blue: Int, opacity: Float): DrawColor =
        DrawColor.fromArgb(DrawColor.channel(opacity), red, green, blue)

    fun colorFromHsl(hue: Float, saturation: Float, lightness: Float, opacity: Float): DrawColor =
        DrawColor.fromHsl(hue, saturation, lightness, opacity)

    fun colorFromHsv(hue: Float, saturation: Float, value: Float, opacity: Float): DrawColor =
        DrawColor.fromHsv(hue, saturation, value, opacity)

    fun colorFromArgbInt(value: Int): DrawColor = DrawColor.fromArgbInt(value)

    fun colorHex(color: DrawColor): String = color.hex()

    fun colorWithOpacity(color: DrawColor, opacity: Float): DrawColor = color.withOpacity(opacity)

    fun setColor(color: DrawColor): DrawIntent = DrawIntent.SetColor(color)

    fun currentColor(state: DrawEditorState): DrawColor = state.colors.current

    fun toggleFavorite(color: DrawColor): DrawIntent = DrawIntent.ToggleFavorite(color)

    fun isFavorite(state: DrawEditorState, color: DrawColor): Boolean = state.colors.isFavorite(color)

    fun recentColors(state: DrawEditorState): List<DrawColor> = state.colors.recent

    fun favoriteColors(state: DrawEditorState): List<DrawColor> = state.colors.favorites

    fun palettes(state: DrawEditorState): List<DrawPalette> = state.colors.palettes

    fun addPalette(name: String, colors: List<DrawColor>): DrawIntent =
        DrawIntent.AddPalette(DrawPaletteCatalog.custom(DrawIds.next(), name, colors))

    fun addToPalette(paletteId: String, color: DrawColor): DrawIntent = DrawIntent.AddToPalette(paletteId, color)

    fun removeFromPalette(paletteId: String, color: DrawColor): DrawIntent = DrawIntent.RemoveFromPalette(paletteId, color)

    fun removePalette(paletteId: String): DrawIntent = DrawIntent.RemovePalette(paletteId)

    fun paper(state: DrawEditorState): DrawPaper = state.document.paper

    fun paperSizes(): List<DrawPaperSize> = DrawPaper.sizes()

    fun patterns(state: DrawEditorState): List<DrawPattern> =
        if (state.mode == DrawToolMode.ADVANCED) DrawPattern.entries
        else DrawPattern.entries.filter { it != DrawPattern.ISOMETRIC && it != DrawPattern.PERSPECTIVE }

    fun orientations(): List<DrawOrientation> = DrawOrientation.entries

    fun units(): List<DrawUnit> = DrawUnits.units()

    fun textures(): List<DrawTexture> = DrawTexture.entries

    fun unitSymbol(unit: DrawUnit): String = DrawUnits.symbol(unit)

    fun paperLabel(size: DrawPaperSize): String = when (size) {
        DrawPaperSize.INFINITE -> "Infinite"
        DrawPaperSize.SCREEN -> "Screen"
        DrawPaperSize.LETTER -> "Letter"
        DrawPaperSize.LEGAL -> "Legal"
        DrawPaperSize.CUSTOM -> "Custom"
        else -> size.name
    }

    fun patternLabel(pattern: DrawPattern): String = when (pattern) {
        DrawPattern.NONE -> "Plain"
        DrawPattern.GRID -> "Grid"
        DrawPattern.RULED -> "Ruled"
        DrawPattern.DOTTED -> "Dotted"
        DrawPattern.ISOMETRIC -> "Isometric"
        DrawPattern.PERSPECTIVE -> "Perspective"
    }

    fun updatePaper(paper: DrawPaper): DrawIntent = DrawIntent.UpdatePaper(paper)

    fun setPaperSize(state: DrawEditorState, size: DrawPaperSize): DrawIntent = updatePaper(state.document.paper.withSize(size))

    fun setOrientation(state: DrawEditorState, orientation: DrawOrientation): DrawIntent =
        updatePaper(state.document.paper.withOrientation(orientation))

    fun setPattern(state: DrawEditorState, pattern: DrawPattern): DrawIntent = updatePaper(state.document.paper.withPattern(pattern))

    fun setPatternSpacing(state: DrawEditorState, spacing: Float): DrawIntent =
        updatePaper(state.document.paper.withSpacing(spacing))

    fun setBackground(state: DrawEditorState, color: DrawColor): DrawIntent =
        updatePaper(state.document.paper.withBackground(color))

    fun setTransparent(state: DrawEditorState, transparent: Boolean): DrawIntent =
        updatePaper(state.document.paper.withTransparent(transparent))

    fun setDpi(state: DrawEditorState, dpi: Int): DrawIntent = updatePaper(state.document.paper.withDpi(dpi))

    fun setUnit(state: DrawEditorState, unit: DrawUnit): DrawIntent = updatePaper(state.document.paper.withUnit(unit))

    fun setTexture(state: DrawEditorState, texture: DrawTexture): DrawIntent = updatePaper(state.document.paper.withTexture(texture))

    fun setCustomSize(state: DrawEditorState, width: Float, height: Float, unit: DrawUnit): DrawIntent =
        updatePaper(state.document.paper.withCustomSize(width, height, unit))

    fun undo(): DrawIntent = DrawIntent.Undo

    fun redo(): DrawIntent = DrawIntent.Redo

    fun clear(): DrawIntent = DrawIntent.Clear

    fun canUndo(state: DrawEditorState): Boolean = state.canUndo

    fun canRedo(state: DrawEditorState): Boolean = state.canRedo

    fun setReadOnly(readOnly: Boolean): DrawIntent = DrawIntent.SetReadOnly(readOnly)

    fun load(document: DrawDocument): DrawIntent = DrawIntent.Load(document)

    fun applyRemote(op: DrawOp): DrawIntent = DrawIntent.ApplyRemote(op)

    fun producedOp(before: DrawEditorState, next: DrawEditorState): DrawOp? =
        if (before.opSequence != next.opSequence) next.lastOp else null

    fun documentChanged(before: DrawEditorState, next: DrawEditorState): Boolean =
        before.editSequence != next.editSequence

    fun encode(document: DrawDocument): String = DrawCodec.encode(document)

    fun decode(payload: String?, fallback: DrawDocument): DrawDocument = DrawCodec.decode(payload, fallback)

    fun encodeOp(op: DrawOp): String = DrawCodec.encodeOp(op)

    fun decodeOp(payload: String?): DrawOp? = DrawCodec.decodeOp(payload)

    fun encodeToolbox(state: DrawEditorState): String = DrawCodec.encodeToolbox(state.settings, state.colors)

    fun restoreToolbox(state: DrawEditorState, payload: String?): DrawIntent = DrawIntent.RestoreToolbox(
        settings = DrawCodec.decodeToolSettings(payload, state.settings),
        colors = DrawCodec.decodeColors(payload, state.colors),
        mode = state.mode
    )

    fun isEmpty(state: DrawEditorState): Boolean = state.document.isEmpty

    fun isDrawing(state: DrawEditorState): Boolean = state.isDrawing

    fun capturesTouches(state: DrawEditorState): Boolean =
        !state.readOnly && !(state.tool == DrawToolId.HAND && !state.navigates)

    fun capturesPointer(state: DrawEditorState, pointer: DrawPointerType): Boolean =
        capturesTouches(state) && !(state.settings.stylusOnly && pointer == DrawPointerType.FINGER && !state.navigates)

    fun navigates(state: DrawEditorState): Boolean = state.navigates

    fun canResizePaper(state: DrawEditorState): Boolean = state.surface == DrawSurface.PAGE

    fun paperAspect(state: DrawEditorState): Float = state.document.paper.aspect

    fun isStylusOnly(state: DrawEditorState): Boolean = state.settings.stylusOnly

    fun zoomPercent(state: DrawEditorState): Int = state.viewport.zoomPercent

    fun rotationDegrees(state: DrawEditorState): Float = state.viewport.rotationDegrees

    fun renderCache(): DrawRenderCache = DrawRenderCache()

    fun frame(state: DrawEditorState, anchors: List<DrawAnchorFrame>, cache: DrawRenderCache): List<DrawRenderEntry> =
        DrawFrameBuilder.entries(
            elements = state.document.visibleElements,
            hiddenIds = state.preview.hiddenIds,
            preview = state.preview.elements,
            space = DrawSpace(state.viewport, anchors),
            cache = cache
        )

    fun committedFrame(state: DrawEditorState, anchors: List<DrawAnchorFrame>, cache: DrawRenderCache): List<DrawRenderEntry> =
        DrawFrameBuilder.committed(state.document.visibleElements, state.preview.hiddenIds, DrawSpace(state.viewport, anchors), cache)

    fun previewFrame(state: DrawEditorState, anchors: List<DrawAnchorFrame>): List<DrawRenderEntry> =
        DrawFrameBuilder.previewed(state.preview.elements, DrawSpace(state.viewport, anchors))

    fun committedKey(state: DrawEditorState, anchors: List<DrawAnchorFrame>, width: Float, height: Float): String =
        "${state.document.id}|${state.revision}|${state.viewport}|${state.preview.hiddenIds.sorted()}|$anchors|$width|$height"

    fun paperView(state: DrawEditorState): DrawPaperView = DrawPaperView(state.document.paper, state.viewport, state.isOverlay)

    fun paperFrameOf(view: DrawPaperView): List<DrawRenderEntry> {
        if (view.overlay || !view.viewport.isMeasured) return emptyList()
        val matrix = view.viewport.matrix()
        return DrawPatternRenderer.items(view.paper, view.viewport.visibleDocumentRect(), view.viewport.scale)
            .map { DrawRenderEntry(it, matrix) }
    }

    fun paperFrame(state: DrawEditorState): List<DrawRenderEntry> = paperFrameOf(paperView(state))

    fun brushPreview(state: DrawEditorState, kind: DrawBrushKind, width: Float, height: Float): List<DrawRenderEntry> =
        unmoved(DrawPreviews.brush(state.settings.brushOf(kind), state.colors.current.opaque(), width, height))

    fun shapePreview(state: DrawEditorState, kind: DrawShapeKind, width: Float, height: Float): List<DrawRenderEntry> =
        unmoved(DrawPreviews.shape(state.settings.shapeOf(kind), state.colors.current.opaque(), PREVIEW_STROKE, width, height))

    private fun unmoved(items: List<DrawRenderItem>): List<DrawRenderEntry> = items.map { DrawRenderEntry(it, DrawMatrix.IDENTITY) }

    fun panelFor(tool: DrawToolId): DrawPanel = when (tool) {
        DrawToolId.PEN -> DrawPanel.BRUSH
        DrawToolId.ERASER -> DrawPanel.ERASER
        DrawToolId.SHAPE -> DrawPanel.SHAPE
        DrawToolId.HAND -> DrawPanel.NONE
    }

    fun opensPanel(state: DrawEditorState, tool: DrawToolId): Boolean =
        state.tool == tool && panelFor(tool) != DrawPanel.NONE

    fun noPanel(): DrawPanel = DrawPanel.NONE

    fun colorPanel(): DrawPanel = DrawPanel.COLOR

    fun paperPanel(): DrawPanel = DrawPanel.PAPER

    fun settingsPanel(): DrawPanel = DrawPanel.SETTINGS

    fun showsPaper(state: DrawEditorState): Boolean = !state.isOverlay

    fun isRotated(state: DrawEditorState): Boolean {
        val degrees = state.viewport.rotationDegrees
        return degrees > ROTATION_EPSILON && degrees < FULL_TURN - ROTATION_EPSILON
    }

    fun carryToolbox(from: DrawEditorState): DrawIntent = DrawIntent.RestoreToolbox(from.settings, from.colors, from.mode)

    fun toolbar(state: DrawEditorState): DrawToolbarState = DrawToolbarState(
        tools = state.toolSpecs,
        tool = state.tool,
        color = state.colors.current,
        canUndo = state.canUndo,
        canRedo = state.canRedo,
        navigates = state.navigates,
        zoomPercent = state.viewport.zoomPercent,
        rotated = isRotated(state),
        showsPaper = showsPaper(state),
        advanced = state.mode == DrawToolMode.ADVANCED,
        readOnly = state.readOnly
    )

    fun toolbarPlacement(): DrawToolbarPlacement = DrawToolbarPlacement()

    fun fitToolbar(
        placement: DrawToolbarPlacement,
        width: Float,
        height: Float,
        areaWidth: Float,
        areaHeight: Float
    ): DrawToolbarPlacement = DrawToolbarLayout.fitted(placement, width, height, areaWidth, areaHeight)

    fun moveToolbar(
        placement: DrawToolbarPlacement,
        deltaX: Float,
        deltaY: Float,
        width: Float,
        height: Float,
        areaWidth: Float,
        areaHeight: Float
    ): DrawToolbarPlacement = DrawToolbarLayout.moved(placement, deltaX, deltaY, width, height, areaWidth, areaHeight)

    fun rotateToolbar(placement: DrawToolbarPlacement): DrawToolbarPlacement = DrawToolbarLayout.rotated(placement)

    fun toolbarMaxLength(areaLength: Float): Float = DrawToolbarLayout.maxLength(areaLength)

    fun isCurrentColor(state: DrawEditorState, color: DrawColor): Boolean = state.colors.current == color

    fun isSameColor(first: DrawColor, second: DrawColor): Boolean = first.isSimilarTo(second, COLOR_TOLERANCE)

    fun isMode(state: DrawEditorState, mode: DrawToolMode): Boolean = state.mode == mode

    fun paperUnit(state: DrawEditorState): DrawUnit = state.document.paper.unit

    fun lengthText(value: Float): String = DrawControls.decimalText(value)

    fun nextPaletteName(state: DrawEditorState): String = "Palette ${state.colors.palettes.count { it.editable } + 1}"

    fun paperBackgrounds(): List<DrawColor> = DrawPaletteCatalog.paperColors()

    fun dpiChoices(): List<Int> = DPI_CHOICES

    fun isPaperSize(state: DrawEditorState, size: DrawPaperSize): Boolean = state.document.paper.size == size

    fun isOrientation(state: DrawEditorState, orientation: DrawOrientation): Boolean =
        state.document.paper.orientation == orientation

    fun isPattern(state: DrawEditorState, pattern: DrawPattern): Boolean = state.document.paper.pattern == pattern

    fun isTexture(state: DrawEditorState, texture: DrawTexture): Boolean = state.document.paper.texture == texture

    fun isUnit(state: DrawEditorState, unit: DrawUnit): Boolean = state.document.paper.unit == unit

    fun isDpi(state: DrawEditorState, dpi: Int): Boolean = state.document.paper.dpi == dpi

    fun isTransparent(state: DrawEditorState): Boolean = state.document.paper.transparent

    fun isBackground(state: DrawEditorState, color: DrawColor): Boolean =
        !state.document.paper.transparent && state.document.paper.background == color

    fun hasPattern(state: DrawEditorState): Boolean = state.document.paper.pattern != DrawPattern.NONE

    fun orientationLabel(orientation: DrawOrientation): String =
        if (orientation == DrawOrientation.PORTRAIT) "Portrait" else "Landscape"

    fun textureLabel(texture: DrawTexture): String = when (texture) {
        DrawTexture.NONE -> "None"
        DrawTexture.PAPER -> "Paper"
        DrawTexture.CANVAS -> "Canvas"
        DrawTexture.KRAFT -> "Kraft"
    }

    fun dpiLabel(dpi: Int): String = "$dpi dpi"

    fun paperWidthIn(state: DrawEditorState): Float = state.document.paper.widthIn(state.document.paper.unit)

    fun paperHeightIn(state: DrawEditorState): Float = state.document.paper.heightIn(state.document.paper.unit)

    fun paperSizeText(state: DrawEditorState): String {
        val paper = state.document.paper
        if (paper.isInfinite) return paperLabel(paper.size)
        val symbol = DrawUnits.symbol(paper.unit)
        return "${DrawControls.decimalText(paper.widthIn(paper.unit))} × ${DrawControls.decimalText(paper.heightIn(paper.unit))} $symbol"
    }

    fun parseLength(text: String): Float? = text.trim().replace(',', '.').toFloatOrNull()?.takeIf { it > 0f }

    fun clears(entries: List<DrawRenderEntry>): Boolean {
        var depth = 0
        for (entry in entries) {
            val item = entry.item
            when {
                item.isGroupBegin -> depth++
                item.isGroupEnd -> depth--
                depth == 0 && item.isClear -> return true
            }
        }
        return false
    }

    fun isGroupBegin(item: DrawRenderItem): Boolean = item.isGroupBegin

    fun isGroupEnd(item: DrawRenderItem): Boolean = item.isGroupEnd

    fun groupBounds(entry: DrawRenderEntry): CanvasRect {
        val bounds = entry.item.bounds
        val matrix = entry.matrix
        val corners = listOf(
            matrix.map(bounds.x, bounds.y),
            matrix.map(bounds.right, bounds.y),
            matrix.map(bounds.x, bounds.bottom),
            matrix.map(bounds.right, bounds.bottom)
        )
        val left = corners.minOf { it.x }
        val top = corners.minOf { it.y }
        return CanvasRect(left, top, corners.maxOf { it.x } - left, corners.maxOf { it.y } - top)
    }

    fun cachesInk(zoom: Float): Boolean = zoom <= MAX_CACHED_ZOOM

    fun matrixValues(matrix: DrawMatrix): FloatArray = matrix.values()

    fun referenceWidth(): Float = DrawAnchoring.REFERENCE_WIDTH

    fun dabStride(): Int = DrawRenderItem.DAB_STRIDE

    fun pathStride(command: Float): Int = DrawPathBuilder.strideOf(command)

    fun isMove(command: Float): Boolean = command == DrawPathBuilder.MOVE

    fun isLine(command: Float): Boolean = command == DrawPathBuilder.LINE

    fun isQuad(command: Float): Boolean = command == DrawPathBuilder.QUAD

    fun isCubic(command: Float): Boolean = command == DrawPathBuilder.CUBIC

    fun isClose(command: Float): Boolean = command == DrawPathBuilder.CLOSE

    fun isFill(item: DrawRenderItem): Boolean = item.kind == DrawRenderKind.FILL_PATH

    fun isStroke(item: DrawRenderItem): Boolean = item.kind == DrawRenderKind.STROKE_PATH

    fun isDabs(item: DrawRenderItem): Boolean = item.kind == DrawRenderKind.DABS

    fun blendKey(blend: DrawBlend): String = blend.name

    fun capKey(cap: DrawCap): String = cap.name

    fun joinKey(join: DrawJoin): String = join.name
}
