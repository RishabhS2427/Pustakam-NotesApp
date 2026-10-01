package com.app.pustakam.core.drawing.model

import com.app.pustakam.core.richtext.master.model.CanvasRect

enum class DrawSurface { OVERLAY, PAGE, WIDGET }

data class DrawDocument(
    val id: String,
    val surface: DrawSurface,
    val paper: DrawPaper,
    val layers: List<DrawLayer>,
    val elements: List<DrawElement>,
    val clock: Long
) {
    val orderedLayers: List<DrawLayer> by lazy { layers.sortedBy { it.order } }

    val ordered: List<DrawElement> by lazy {
        val layerOrder = orderedLayers.withIndex().associate { it.value.id to it.index }
        elements.sortedWith(
            compareBy<DrawElement> { layerOrder[it.layerId] ?: Int.MAX_VALUE }
                .thenBy { it.order }
                .thenBy { it.id }
        )
    }

    val visibleElements: List<DrawElement> by lazy {
        val hidden = layers.filterNot { it.visible }.map { it.id }.toSet()
        if (hidden.isEmpty()) ordered else ordered.filterNot { it.layerId in hidden }
    }

    val isEmpty: Boolean get() = elements.isEmpty()

    val activeLayer: DrawLayer
        get() = orderedLayers.lastOrNull { it.isEditable } ?: orderedLayers.lastOrNull() ?: DrawLayer.base()

    val contentBounds: CanvasRect
        get() = elements.filter { it.anchorId == null }
            .map { it.bounds }
            .reduceOrNull { acc, rect -> acc.union(rect) } ?: CanvasRect()

    fun elementById(elementId: String): DrawElement? = elements.firstOrNull { it.id == elementId }

    fun layerById(layerId: String): DrawLayer? = layers.firstOrNull { it.id == layerId }

    fun isEditableLayer(layerId: String): Boolean = layerById(layerId)?.isEditable ?: false

    fun nextOrder(layerId: String): Double =
        (elements.filter { it.layerId == layerId }.maxOfOrNull { it.order } ?: 0.0) + 1.0

    fun withElements(value: List<DrawElement>): DrawDocument = copy(elements = value)

    fun withPaper(value: DrawPaper): DrawDocument = copy(paper = value)

    fun withLayers(value: List<DrawLayer>): DrawDocument = copy(layers = value.ifEmpty { listOf(DrawLayer.base()) })

    fun withClock(value: Long): DrawDocument = copy(clock = maxOf(clock, value))

    fun withId(value: String): DrawDocument = copy(id = value)

    companion object {
        fun blank(id: String, surface: DrawSurface, paper: DrawPaper): DrawDocument = DrawDocument(
            id = id,
            surface = surface,
            paper = paper,
            layers = listOf(DrawLayer.base()),
            elements = emptyList(),
            clock = 0L
        )
    }
}
