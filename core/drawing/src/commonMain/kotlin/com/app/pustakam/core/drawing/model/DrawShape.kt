package com.app.pustakam.core.drawing.model

enum class DrawShapeKind {
    NONE,
    LINE,
    RECTANGLE,
    ROUNDED_RECTANGLE,
    CIRCLE,
    ELLIPSE,
    TRIANGLE,
    POLYGON,
    STAR,
    ARROW,
    ARC
}

data class DrawShapeSpec(
    val kind: DrawShapeKind,
    val sides: Int,
    val innerRatio: Float,
    val cornerRadius: Float,
    val sweep: Float,
    val filled: Boolean
) {
    val isFillable: Boolean
        get() = kind != DrawShapeKind.NONE && kind != DrawShapeKind.LINE &&
            kind != DrawShapeKind.ARROW && kind != DrawShapeKind.ARC

    val isOpenPath: Boolean get() = !isFillable

    fun withKind(value: DrawShapeKind): DrawShapeSpec = copy(kind = value)

    fun withSides(value: Int): DrawShapeSpec = copy(sides = value.coerceIn(MIN_SIDES, MAX_SIDES))

    fun withInnerRatio(value: Float): DrawShapeSpec = copy(innerRatio = value.coerceIn(0.1f, 0.95f))

    fun withCornerRadius(value: Float): DrawShapeSpec = copy(cornerRadius = value.coerceAtLeast(0f))

    fun withSweep(value: Float): DrawShapeSpec = copy(sweep = value.coerceIn(1f, 359f))

    fun withFilled(value: Boolean): DrawShapeSpec = copy(filled = value)

    companion object {
        const val MIN_SIDES = 3
        const val MAX_SIDES = 24
        const val DEFAULT_POLYGON_SIDES = 6
        const val DEFAULT_STAR_POINTS = 5
        const val DEFAULT_INNER_RATIO = 0.5f
        const val DEFAULT_CORNER_RADIUS = 16f
        const val DEFAULT_SWEEP = 180f

        fun none(): DrawShapeSpec = of(DrawShapeKind.NONE)

        fun of(kind: DrawShapeKind): DrawShapeSpec = DrawShapeSpec(
            kind = kind,
            sides = if (kind == DrawShapeKind.STAR) DEFAULT_STAR_POINTS else DEFAULT_POLYGON_SIDES,
            innerRatio = DEFAULT_INNER_RATIO,
            cornerRadius = DEFAULT_CORNER_RADIUS,
            sweep = DEFAULT_SWEEP,
            filled = false
        )

        fun kinds(): List<DrawShapeKind> = DrawShapeKind.entries.filter { it != DrawShapeKind.NONE }
    }
}
