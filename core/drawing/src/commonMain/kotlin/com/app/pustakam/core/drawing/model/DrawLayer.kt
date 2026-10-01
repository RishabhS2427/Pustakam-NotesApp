package com.app.pustakam.core.drawing.model

enum class DrawBlend {
    NORMAL,
    MULTIPLY,
    SCREEN,
    OVERLAY,
    SOFT_LIGHT,
    HARD_LIGHT,
    DARKEN,
    LIGHTEN,
    COLOR,
    DIFFERENCE,
    CLEAR
}

enum class DrawLayerKind {
    PAINT,
    VECTOR,
    TEXT,
    SHAPE,
    IMAGE,
    VIDEO,
    PDF,
    REFERENCE,
    GUIDE,
    MEASUREMENT
}

data class DrawLayer(
    val id: String,
    val name: String,
    val kind: DrawLayerKind,
    val visible: Boolean,
    val locked: Boolean,
    val opacity: Float,
    val blend: DrawBlend,
    val order: Double
) {
    val isEditable: Boolean get() = visible && !locked

    fun withName(value: String): DrawLayer = copy(name = value)

    fun withVisible(value: Boolean): DrawLayer = copy(visible = value)

    fun withLocked(value: Boolean): DrawLayer = copy(locked = value)

    fun withOpacity(value: Float): DrawLayer = copy(opacity = value.coerceIn(0f, 1f))

    fun withBlend(value: DrawBlend): DrawLayer = copy(blend = value)

    fun withOrder(value: Double): DrawLayer = copy(order = value)

    companion object {
        const val BASE_ID = "layer-base"

        fun base(): DrawLayer = DrawLayer(
            id = BASE_ID,
            name = "Layer 1",
            kind = DrawLayerKind.VECTOR,
            visible = true,
            locked = false,
            opacity = 1f,
            blend = DrawBlend.NORMAL,
            order = 0.0
        )
    }
}
