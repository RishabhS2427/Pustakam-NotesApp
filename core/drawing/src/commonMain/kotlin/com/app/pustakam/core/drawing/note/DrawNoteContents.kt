package com.app.pustakam.core.drawing.note

import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.common.util.UniqueIdGenerator
import com.app.pustakam.core.drawing.anchor.DrawAnchoring
import com.app.pustakam.core.drawing.codec.DrawCodec
import com.app.pustakam.core.drawing.editor.DrawCommands
import com.app.pustakam.core.drawing.editor.DrawDocuments
import com.app.pustakam.core.drawing.editor.DrawEditorState
import com.app.pustakam.core.drawing.editor.DrawEditorStates
import com.app.pustakam.core.drawing.editor.DrawIds
import com.app.pustakam.core.drawing.model.DrawDocument
import com.app.pustakam.core.drawing.model.DrawPaper
import com.app.pustakam.core.drawing.model.DrawSurface
import com.app.pustakam.core.drawing.ops.DrawOp
import com.app.pustakam.core.drawing.ops.DrawOpApplier
import com.app.pustakam.core.drawing.render.DrawRenderEntry
import com.app.pustakam.core.drawing.render.DrawRenderer
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import com.app.pustakam.core.model.models.response.notes.NoteContentObjectHelper
import kotlin.math.roundToInt

object DrawNoteContents {

    const val DEFAULT_ASPECT = NoteContentModel.Drawing.DEFAULT_ASPECT

    const val DEFAULT_FRAME_WIDTH = 360f

    private const val NO_TARGET = ""

    private const val PAGE_SEPARATOR = ":"

    private const val EDITED_MIME_TYPE = "image/png"

    private const val OVERLAY_SUFFIX = "-0f"

    private val CLIENT_ID = Regex("^\\d{10,17}-[0-9a-fA-F-]{8,61}$")

    fun surfaceOf(content: NoteContentModel.Drawing): DrawSurface = DrawCommands.surfaceOf(content.surface)

    fun documentOf(content: NoteContentModel.Drawing): DrawDocument {
        val fallback = DrawDocuments.of(
            surfaceOf(content),
            content.width.toFloat().coerceAtLeast(DrawPaper.MIN_EDGE),
            content.height.toFloat().coerceAtLeast(DrawPaper.MIN_EDGE)
        )
        return DrawCodec.decode(content.drawing, fallback)
    }

    fun stateFor(content: NoteContentModel.Drawing, author: String, readOnly: Boolean): DrawEditorState =
        DrawEditorStates.initial(documentOf(content), author, readOnly)

    fun stateCarrying(content: NoteContentModel.Drawing, previous: DrawEditorState, readOnly: Boolean): DrawEditorState =
        DrawEditorStates.carrying(previous, documentOf(content), readOnly)

    fun written(content: NoteContentModel.Drawing, document: DrawDocument): NoteContentModel.Drawing {
        val paper = document.paper
        val sized = if (document.surface == DrawSurface.PAGE && !paper.isInfinite && !isFramed(content, paper.width, paper.height)) {
            framed(content, paper.width, paper.height)
        } else {
            content
        }
        return sized.withDrawing(DrawCodec.encode(document))
    }

    fun create(noteId: String, position: Double, surface: DrawSurface, width: Float, height: Float): NoteContentModel.Drawing =
        created(noteId, position, surface, width, height, NO_TARGET)

    fun overlayId(noteId: String): String =
        if (CLIENT_ID.matches(noteId)) "$noteId$OVERLAY_SUFFIX" else UniqueIdGenerator.generateUniqueId()

    fun createOverlay(noteId: String, position: Double): NoteContentModel.Drawing =
        create(noteId, position, DrawSurface.OVERLAY, 0f, 0f).copy(id = overlayId(noteId))

    fun withOp(content: NoteContentModel.Drawing, op: DrawOp): NoteContentModel.Drawing =
        written(content, DrawOpApplier.apply(documentOf(content), op))

    fun createAnnotation(noteId: String, position: Double, targetId: String): NoteContentModel.Drawing =
        created(noteId, position, DrawSurface.OVERLAY, 0f, 0f, targetId)

    fun overlayOf(contents: List<NoteContentModel>): NoteContentModel.Drawing? =
        contents.firstNotNullOfOrNull { content ->
            (content as? NoteContentModel.Drawing)?.takeIf { it.isOverlay() && !it.isAnnotation() }
        }

    fun annotationOf(contents: List<NoteContentModel>, targetId: String): NoteContentModel.Drawing? =
        contents.firstNotNullOfOrNull { content -> (content as? NoteContentModel.Drawing)?.takeIf { it.annotates(targetId) } }

    fun pageAnchorId(targetId: String, pageIndex: Int): String = "$targetId$PAGE_SEPARATOR$pageIndex"

    fun imageAnchorId(imageId: String): String = pageAnchorId(imageId, 0)

    fun editTargetOf(contents: List<NoteContentModel>, mediaId: String): NoteContentModel.MediaContent? {
        val tapped = contents.firstOrNull { it.id == mediaId } as? NoteContentModel.MediaContent ?: return null
        val origin = tapped.editedFrom?.takeIf { it.isNotEmpty() } ?: return tapped
        return contents.firstOrNull { it.id == origin } as? NoteContentModel.MediaContent ?: tapped
    }

    fun editedCopyOf(contents: List<NoteContentModel>, imageId: String): NoteContentModel.MediaContent? =
        contents.firstNotNullOfOrNull { content ->
            (content as? NoteContentModel.MediaContent)?.takeIf { it.editedFrom == imageId }
        }

    fun showsWithOriginal(contents: List<NoteContentModel>, content: NoteContentModel): Boolean {
        val origin = (content as? NoteContentModel.MediaContent)?.editedFrom?.takeIf { it.isNotEmpty() } ?: return false
        return contents.any { it.id == origin }
    }

    fun dependentsOf(contents: List<NoteContentModel>, removedId: String): List<NoteContentModel> {
        val removed = contents.firstOrNull { it.id == removedId } ?: return emptyList()
        val origin = (removed as? NoteContentModel.MediaContent)?.editedFrom?.takeIf { it.isNotEmpty() }
        val targets = setOfNotNull(removedId, origin)
        return contents.filter { content ->
            content.id != removedId && content is NoteContentModel.Drawing && content.isAnnotation() && content.target in targets
        }
    }

    fun hasInk(content: NoteContentModel.Drawing?, imageId: String): Boolean {
        if (content == null) return false
        val anchor = imageAnchorId(imageId)
        return documentOf(content).visibleElements.any { it.anchorId == anchor }
    }

    fun inkFrame(content: NoteContentModel.Drawing?, imageId: String, width: Float, height: Float): List<DrawRenderEntry> {
        if (content == null || width <= 0f || height <= 0f) return emptyList()
        val anchor = imageAnchorId(imageId)
        val matrix = DrawAnchoring.matrixFor(DrawAnchoring.frame(anchor, 0f, 0f, width, height))
        return documentOf(content).visibleElements
            .filter { it.anchorId == anchor }
            .flatMap { element -> DrawRenderer.itemsFor(element, complete = true).map { DrawRenderEntry(it, matrix) } }
    }

    fun editedCopy(
        contents: List<NoteContentModel>,
        original: NoteContentModel.MediaContent,
        localPath: String,
        width: Int,
        height: Int
    ): NoteContentModel.MediaContent = NoteContentObjectHelper.createMedia(
        contentType = ContentType.IMAGE,
        noteId = original.noteId,
        positionedAt = editedCopyOf(contents, original.id)?.position ?: positionAfter(contents, original.id),
        localPath = localPath,
        title = original.title,
        mimeType = EDITED_MIME_TYPE,
        width = width,
        height = height
    ).withEditedFrom(original.id)

    fun positionAfter(contents: List<NoteContentModel>, contentId: String): Double {
        val anchor = contents.firstOrNull { it.id == contentId }?.position ?: return nextPosition(contents)
        val next = contents.map { it.position }.filter { it > anchor }.minOrNull() ?: return anchor + 1.0
        return (anchor + next) / 2.0
    }

    fun withoutOverlays(contents: List<NoteContentModel>): List<NoteContentModel> =
        contents.filterNot { it.isOverlayDrawing() }

    fun nextPosition(contents: List<NoteContentModel>): Double =
        (contents.maxOfOrNull { it.position } ?: -1.0) + 1.0

    fun frameWidth(content: NoteContentModel.Drawing): Float =
        if (content.width > 0) content.width.toFloat() else DEFAULT_FRAME_WIDTH

    fun frameHeight(content: NoteContentModel.Drawing): Float =
        if (content.height > 0) content.height.toFloat() else frameWidth(content) * DEFAULT_ASPECT

    fun isFramed(content: NoteContentModel.Drawing, width: Float, height: Float): Boolean =
        content.width == width.roundToInt() && content.height == height.roundToInt()

    fun framed(content: NoteContentModel.Drawing, width: Float, height: Float): NoteContentModel.Drawing =
        content.withSize(width.roundToInt(), height.roundToInt())

    private fun created(
        noteId: String,
        position: Double,
        surface: DrawSurface,
        width: Float,
        height: Float,
        target: String
    ): NoteContentModel.Drawing = NoteContentObjectHelper.createDrawing(
        noteId = noteId,
        positionedAt = position,
        surface = surface.name,
        width = width.roundToInt(),
        height = height.roundToInt(),
        drawing = DrawCodec.encode(DrawDocuments.of(surface, width, height)),
        target = target
    )

    fun aspectOf(content: NoteContentModel.Drawing): Float = content.aspect()

    fun fitScale(frameWidth: Float, frameHeight: Float, width: Float, height: Float): Float {
        if (frameWidth <= 0f || frameHeight <= 0f || width <= 0f) return 1f
        return minOf(width / frameWidth, if (height > 0f) height / frameHeight else Float.POSITIVE_INFINITY)
    }
}

object DrawAuthors {

    private val session: String by lazy { DrawIds.next() }

    fun local(): String = session
}
