package com.app.pustakam.core.drawing.history

import com.app.pustakam.core.drawing.ops.DrawOp

data class DrawHistoryStep(val history: DrawHistory, val op: DrawOp)

data class DrawHistory(
    val past: List<DrawOp>,
    val future: List<DrawOp>,
    val limit: Int
) {
    val canUndo: Boolean get() = past.isNotEmpty()

    val canRedo: Boolean get() = future.isNotEmpty()

    fun record(op: DrawOp): DrawHistory = copy(past = (past + op).takeLast(limit), future = emptyList())

    fun undoStep(): DrawHistoryStep? {
        val last = past.lastOrNull() ?: return null
        return DrawHistoryStep(copy(past = past.dropLast(1), future = listOf(last) + future), last)
    }

    fun redoStep(): DrawHistoryStep? {
        val next = future.firstOrNull() ?: return null
        return DrawHistoryStep(copy(past = (past + next).takeLast(limit), future = future.drop(1)), next)
    }

    fun cleared(): DrawHistory = empty(limit)

    companion object {
        const val DEFAULT_LIMIT = 100

        fun empty(limit: Int): DrawHistory = DrawHistory(emptyList(), emptyList(), limit)

        fun empty(): DrawHistory = empty(DEFAULT_LIMIT)
    }
}
