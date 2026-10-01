package com.app.pustakam.core.drawing.viewport

import com.app.pustakam.core.drawing.anchor.DrawAnchorFrame
import com.app.pustakam.core.drawing.anchor.DrawAnchoring
import com.app.pustakam.core.drawing.geometry.DrawMatrix
import com.app.pustakam.core.drawing.model.DrawPoint

class DrawSpace(val viewport: DrawViewport, val frames: List<DrawAnchorFrame>) {

    private val byId: Map<String, DrawAnchorFrame> = frames.associateBy { it.id }

    private val documentMatrix: DrawMatrix by lazy { viewport.matrix() }

    fun frameOf(anchorId: String?): DrawAnchorFrame? = anchorId?.let { byId[it] }

    fun isVisible(anchorId: String?): Boolean = anchorId == null || byId.containsKey(anchorId)

    fun matrixOf(anchorId: String?): DrawMatrix? {
        if (anchorId == null) return documentMatrix
        return byId[anchorId]?.let { DrawAnchoring.matrixFor(it) }
    }

    fun scaleOf(anchorId: String?): Float {
        if (anchorId == null) return viewport.scale.takeIf { it > 0f } ?: 1f
        return byId[anchorId]?.let { DrawAnchoring.scaleOf(it) } ?: 1f
    }

    fun toElement(anchorId: String?, point: DrawPoint): DrawPoint? {
        if (anchorId == null) {
            val document = viewport.toDocument(point.x, point.y)
            return point.movedTo(document.x, document.y)
        }
        val frame = byId[anchorId] ?: return null
        return DrawAnchoring.toLocal(frame, point)
    }

    fun toElement(anchorId: String?, points: List<DrawPoint>): List<DrawPoint> =
        points.mapNotNull { toElement(anchorId, it) }
}
