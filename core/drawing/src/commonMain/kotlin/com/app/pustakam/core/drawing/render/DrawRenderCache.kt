package com.app.pustakam.core.drawing.render

import com.app.pustakam.core.drawing.model.DrawElement

class DrawRenderCache {

    private val items = HashMap<String, List<DrawRenderItem>>()

    val size: Int get() = items.size

    fun itemsFor(element: DrawElement): List<DrawRenderItem> =
        items.getOrPut(element.renderKey) { DrawRenderer.itemsFor(element, complete = true) }

    fun retain(elements: List<DrawElement>) {
        val keep = elements.mapTo(HashSet(elements.size)) { it.renderKey }
        items.keys.retainAll(keep)
    }

    fun clear() {
        items.clear()
    }
}
