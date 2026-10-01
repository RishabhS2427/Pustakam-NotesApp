package com.app.pustakam.core.drawing.geometry

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class DrawMatrix(
    val a: Float,
    val b: Float,
    val c: Float,
    val d: Float,
    val tx: Float,
    val ty: Float
) {
    val scale: Float get() = sqrt(a * a + b * b)

    val isIdentity: Boolean get() = this == IDENTITY

    fun mapX(x: Float, y: Float): Float = a * x + c * y + tx

    fun mapY(x: Float, y: Float): Float = b * x + d * y + ty

    fun map(x: Float, y: Float): DrawVec = DrawVec(mapX(x, y), mapY(x, y))

    fun then(next: DrawMatrix): DrawMatrix = DrawMatrix(
        a = next.a * a + next.c * b,
        b = next.b * a + next.d * b,
        c = next.a * c + next.c * d,
        d = next.b * c + next.d * d,
        tx = next.a * tx + next.c * ty + next.tx,
        ty = next.b * tx + next.d * ty + next.ty
    )

    fun inverted(): DrawMatrix? {
        val determinant = a * d - b * c
        if (abs(determinant) < DrawVec.EPSILON) return null
        return DrawMatrix(
            a = d / determinant,
            b = -b / determinant,
            c = -c / determinant,
            d = a / determinant,
            tx = (c * ty - d * tx) / determinant,
            ty = (b * tx - a * ty) / determinant
        )
    }

    fun values(): FloatArray = floatArrayOf(a, b, c, d, tx, ty)

    companion object {
        val IDENTITY: DrawMatrix = DrawMatrix(1f, 0f, 0f, 1f, 0f, 0f)

        fun translation(tx: Float, ty: Float): DrawMatrix = DrawMatrix(1f, 0f, 0f, 1f, tx, ty)

        fun scaling(sx: Float, sy: Float): DrawMatrix = DrawMatrix(sx, 0f, 0f, sy, 0f, 0f)

        fun rotation(radians: Float): DrawMatrix {
            val cosine = cos(radians)
            val sine = sin(radians)
            return DrawMatrix(cosine, sine, -sine, cosine, 0f, 0f)
        }

        fun rotationAbout(radians: Float, pivotX: Float, pivotY: Float): DrawMatrix =
            translation(-pivotX, -pivotY).then(rotation(radians)).then(translation(pivotX, pivotY))
    }
}
