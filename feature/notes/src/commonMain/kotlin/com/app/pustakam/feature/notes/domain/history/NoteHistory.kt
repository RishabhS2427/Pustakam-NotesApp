package com.app.pustakam.feature.notes.domain.history

import com.app.pustakam.core.model.models.response.notes.Note
import com.app.pustakam.core.model.models.response.notes.NoteContentModel

enum class NoteEditKind {
    TEXT,
    FORMATTING,
    TITLE,
    ADD_TEXT,
    DELETE_CONTENT,
    REORDER,
    ADD_MEDIA,
    ADD_DOCUMENT,
    DRAWING,
    LAYOUT,
    SYNC
}

data class NoteHistoryStep(
    val history: NoteHistory,
    val note: Note,
    val dirtyIds: Set<String> = emptySet(),
    val removed: List<NoteContentModel> = emptyList()
)

data class NoteHistory(
    val past: List<Note> = emptyList(),
    val future: List<Note> = emptyList(),
    val limit: Int = DEFAULT_LIMIT
) {
    val canUndo: Boolean get() = past.isNotEmpty()

    val canRedo: Boolean get() = future.isNotEmpty()

    fun record(previous: Note, kind: NoteEditKind): NoteHistory {
        if (!isUndoable(kind)) return this
        if (past.lastOrNull()?.let { sameContent(it, previous) } == true) return this
        return copy(past = bounded(past + previous, emptyList()), future = emptyList())
    }

    fun track(start: Note?, end: Note?, kind: NoteEditKind): NoteHistory = when {
        start == null || end == null || sameContent(start, end) -> this
        isUndoable(kind) -> record(start, kind)
        else -> adopting(start.contents, end.contents)
    }

    fun adopting(before: List<NoteContentModel>, after: List<NoteContentModel>): NoteHistory {
        val previous = before.associateBy { it.id }
        val updates = after.filter { previous[it.id] != it }.associateBy { it.id }
        val removedIds = previous.keys - after.map { it.id }.toSet()
        if (updates.isEmpty() && removedIds.isEmpty()) return this
        val added = updates.values.filter { it.id !in previous }
        fun carried(snapshot: Note): Note {
            val present = snapshot.contents.map { it.id }.toSet()
            val kept = snapshot.contents
                .filterNot { it.id in removedIds }
                .map { updates[it.id] ?: it }
            return snapshot.copy(contents = kept + added.filterNot { it.id in present })
        }
        return copy(past = past.map(::carried), future = future.map(::carried))
    }

    fun recordText(previous: Note): NoteHistory = record(previous, NoteEditKind.TEXT)

    fun recordFormatting(previous: Note): NoteHistory = record(previous, NoteEditKind.FORMATTING)

    fun recordTitle(previous: Note): NoteHistory = record(previous, NoteEditKind.TITLE)

    fun recordAddText(previous: Note): NoteHistory = record(previous, NoteEditKind.ADD_TEXT)

    fun recordDeleteContent(previous: Note): NoteHistory =
        record(previous, NoteEditKind.DELETE_CONTENT)

    fun recordAddMedia(previous: Note): NoteHistory = record(previous, NoteEditKind.ADD_MEDIA)

    fun recordAddDocument(previous: Note): NoteHistory =
        record(previous, NoteEditKind.ADD_DOCUMENT)

    fun undoStep(current: Note): NoteHistoryStep? {
        val previous = past.lastOrNull() ?: return null
        return stepTo(previous, current, copy(past = past.dropLast(1), future = listOf(current) + future))
    }

    fun redoStep(current: Note): NoteHistoryStep? {
        val next = future.firstOrNull() ?: return null
        val ahead = future.drop(1)
        return stepTo(next, current, copy(past = bounded(past + current, ahead), future = ahead))
    }

    fun cleared(): NoteHistory = NoteHistory(limit = limit)

    private fun bounded(entries: List<Note>, ahead: List<Note>): List<Note> {
        var kept = entries.takeLast(limit)
        while (kept.size > 1 && weightOf(kept + ahead) > MAX_WEIGHT) kept = kept.drop(1)
        return kept
    }

    companion object {
        const val DEFAULT_LIMIT = 50

        const val MAX_WEIGHT = 16_000_000L

        fun isUndoable(kind: NoteEditKind): Boolean = kind != NoteEditKind.SYNC

        fun sameContent(a: Note, b: Note): Boolean =
            a.title == b.title && a.contents == b.contents && a.canvas == b.canvas

        fun weightOf(snapshots: List<Note>): Long {
            var total = 0L
            var newer: Map<String, NoteContentModel> = emptyMap()
            for (snapshot in snapshots.asReversed()) {
                for (content in snapshot.contents) {
                    if (content is NoteContentModel.Drawing && newer[content.id] !== content) {
                        total += content.drawing.length
                    }
                }
                newer = snapshot.contents.associateBy { it.id }
            }
            return total
        }

        private fun stepTo(target: Note, current: Note, history: NoteHistory): NoteHistoryStep {
            val now = current.contents.associateBy { it.id }
            val kept = target.contents.map { it.id }.toSet()
            return NoteHistoryStep(
                history = history,
                note = target,
                dirtyIds = target.contents.filter { now[it.id] != it }.map { it.id }.toSet(),
                removed = current.contents.filterNot { it.id in kept }
            )
        }
    }
}
