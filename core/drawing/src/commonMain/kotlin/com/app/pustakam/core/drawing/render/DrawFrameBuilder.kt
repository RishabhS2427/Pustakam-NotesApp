package com.app.pustakam.core.drawing.render

import com.app.pustakam.core.drawing.model.DrawElement
import com.app.pustakam.core.drawing.viewport.DrawSpace

object DrawFrameBuilder {

    fun entries(
        elements: List<DrawElement>,
        hiddenIds: Set<String>,
        preview: List<DrawElement>,
        space: DrawSpace,
        cache: DrawRenderCache
    ): List<DrawRenderEntry> = committed(elements, hiddenIds, space, cache) + previewed(preview, space)

    fun committed(
        elements: List<DrawElement>,
        hiddenIds: Set<String>,
        space: DrawSpace,
        cache: DrawRenderCache
    ): List<DrawRenderEntry> {
        val visible = if (space.viewport.isMeasured) space.viewport.visibleDocumentRect() else null
        val entries = ArrayList<DrawRenderEntry>(elements.size)
        for (element in elements) {
            if (element.id in hiddenIds) continue
            val matrix = space.matrixOf(element.anchorId) ?: continue
            if (element.anchorId == null && visible != null && !element.bounds.intersects(visible)) continue
            for (item in cache.itemsFor(element)) entries.add(DrawRenderEntry(item, matrix))
        }
        return entries
    }

    fun previewed(preview: List<DrawElement>, space: DrawSpace): List<DrawRenderEntry> {
        val entries = ArrayList<DrawRenderEntry>(preview.size)
        for (element in preview) {
            val matrix = space.matrixOf(element.anchorId) ?: continue
            for (item in DrawRenderer.itemsFor(element, complete = false)) entries.add(DrawRenderEntry(item, matrix))
        }
        return entries
    }
}
