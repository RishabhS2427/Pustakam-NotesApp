package com.app.pustakam.core.drawing.geometry

object DrawPathFlattener {

    fun polylines(path: FloatArray, segmentsPerCurve: Int): List<List<DrawVec>> {
        val result = mutableListOf<List<DrawVec>>()
        var current = mutableListOf<DrawVec>()
        var cursor = DrawVec.ZERO
        var start = DrawVec.ZERO
        val steps = segmentsPerCurve.coerceAtLeast(1)
        var index = 0
        while (index < path.size) {
            val command = path[index]
            when (command) {
                DrawPathBuilder.MOVE -> {
                    if (current.size > 1) result.add(current)
                    cursor = DrawVec(path[index + 1], path[index + 2])
                    start = cursor
                    current = mutableListOf(cursor)
                }

                DrawPathBuilder.LINE -> {
                    cursor = DrawVec(path[index + 1], path[index + 2])
                    current.add(cursor)
                }

                DrawPathBuilder.QUAD -> {
                    val control = DrawVec(path[index + 1], path[index + 2])
                    val end = DrawVec(path[index + 3], path[index + 4])
                    for (step in 1..steps) current.add(quadAt(cursor, control, end, step.toFloat() / steps))
                    cursor = end
                }

                DrawPathBuilder.CUBIC -> {
                    val first = DrawVec(path[index + 1], path[index + 2])
                    val second = DrawVec(path[index + 3], path[index + 4])
                    val end = DrawVec(path[index + 5], path[index + 6])
                    for (step in 1..steps) current.add(cubicAt(cursor, first, second, end, step.toFloat() / steps))
                    cursor = end
                }

                else -> {
                    current.add(start)
                    cursor = start
                }
            }
            index += 1 + DrawPathBuilder.strideOf(command)
        }
        if (current.size > 1) result.add(current)
        if (current.size == 1 && result.isEmpty()) result.add(current)
        return result
    }

    private fun quadAt(from: DrawVec, control: DrawVec, to: DrawVec, t: Float): DrawVec {
        val inverse = 1f - t
        return DrawVec(
            inverse * inverse * from.x + 2f * inverse * t * control.x + t * t * to.x,
            inverse * inverse * from.y + 2f * inverse * t * control.y + t * t * to.y
        )
    }

    private fun cubicAt(from: DrawVec, first: DrawVec, second: DrawVec, to: DrawVec, t: Float): DrawVec {
        val inverse = 1f - t
        val a = inverse * inverse * inverse
        val b = 3f * inverse * inverse * t
        val c = 3f * inverse * t * t
        val d = t * t * t
        return DrawVec(
            a * from.x + b * first.x + c * second.x + d * to.x,
            a * from.y + b * first.y + c * second.y + d * to.y
        )
    }
}
