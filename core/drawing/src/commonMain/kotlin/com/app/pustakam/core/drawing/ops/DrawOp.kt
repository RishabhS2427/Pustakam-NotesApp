package com.app.pustakam.core.drawing.ops

import com.app.pustakam.core.drawing.model.DrawElement
import com.app.pustakam.core.drawing.model.DrawPaper

enum class DrawOpKind { ADD, REMOVE, REPLACE, PAPER }

sealed class DrawOp {

    abstract val author: String

    abstract val clock: Long

    abstract val kind: DrawOpKind

    abstract fun inverse(author: String, clock: Long): DrawOp

    abstract fun restamped(author: String, clock: Long): DrawOp

    open val touchedIds: List<String> get() = emptyList()

    data class Add(
        val elements: List<DrawElement>,
        override val author: String,
        override val clock: Long
    ) : DrawOp() {
        override val kind: DrawOpKind get() = DrawOpKind.ADD

        override val touchedIds: List<String> get() = elements.map { it.id }

        override fun inverse(author: String, clock: Long): DrawOp = Remove(elements, author, clock)

        override fun restamped(author: String, clock: Long): DrawOp = copy(author = author, clock = clock)
    }

    data class Remove(
        val elements: List<DrawElement>,
        override val author: String,
        override val clock: Long
    ) : DrawOp() {
        override val kind: DrawOpKind get() = DrawOpKind.REMOVE

        override val touchedIds: List<String> get() = elements.map { it.id }

        override fun inverse(author: String, clock: Long): DrawOp = Add(elements, author, clock)

        override fun restamped(author: String, clock: Long): DrawOp = copy(author = author, clock = clock)
    }

    data class Replace(
        val before: List<DrawElement>,
        val after: List<DrawElement>,
        override val author: String,
        override val clock: Long
    ) : DrawOp() {
        override val kind: DrawOpKind get() = DrawOpKind.REPLACE

        override val touchedIds: List<String> get() = (before + after).map { it.id }.distinct()

        override fun inverse(author: String, clock: Long): DrawOp = Replace(after, before, author, clock)

        override fun restamped(author: String, clock: Long): DrawOp = copy(author = author, clock = clock)
    }

    data class Paper(
        val before: DrawPaper,
        val after: DrawPaper,
        override val author: String,
        override val clock: Long
    ) : DrawOp() {
        override val kind: DrawOpKind get() = DrawOpKind.PAPER

        override fun inverse(author: String, clock: Long): DrawOp = Paper(after, before, author, clock)

        override fun restamped(author: String, clock: Long): DrawOp = copy(author = author, clock = clock)
    }
}
