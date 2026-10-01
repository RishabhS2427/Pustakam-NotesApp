package com.app.pustakam.feature.notes.domain.history

import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.model.models.response.notes.Note
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import com.app.pustakam.core.richtext.master.model.CanvasDocument
import com.app.pustakam.core.richtext.master.model.CanvasNode
import com.app.pustakam.core.richtext.master.model.CanvasRect
import com.app.pustakam.core.richtext.master.model.CanvasRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoteHistoryTest {

    private fun note(vararg contents: NoteContentModel) = Note(
        id = "note-1", title = "", updatedAt = null, createdAt = null, contents = contents.toList()
    )

    private fun text(id: String, value: String = "") = NoteContentModel.TextContent(
        id = id, noteId = "note-1", text = value, position = 0.0, updatedAt = null, createdAt = null
    )

    private fun drawing(id: String, payload: String = "") = NoteContentModel.Drawing(
        id = id, noteId = "note-1", position = 0.0, drawing = payload, updatedAt = null, createdAt = null
    )

    private fun media(id: String, thumbnail: String? = null) = NoteContentModel.MediaContent(
        id = id, noteId = "note-1", type = ContentType.IMAGE, position = 0.0,
        localPath = "/files/$id.png", thumbnailPath = thumbnail, updatedAt = null, createdAt = null
    )

    private fun page(id: String, x: Float = 0f) = CanvasNode(
        id = id, kind = ContentType.TEXT, rect = CanvasRect(x, 0f, 400f, 600f), role = CanvasRole.PAGE, pageOrder = x.toDouble()
    )

    private fun widget(id: String, contentId: String, y: Float = 0f) = CanvasNode(
        id = id, kind = ContentType.TEXT, rect = CanvasRect(8f, y, 300f, 80f), contentId = contentId, parentId = "p1"
    )

    @Test
    fun everyUserEditIsUndoableButSyncIsNot() {
        NoteEditKind.entries.forEach { kind ->
            assertEquals(kind != NoteEditKind.SYNC, NoteHistory.isUndoable(kind))
        }
    }

    @Test
    fun aDrawingStrokeBecomesOneUndoStep() {
        val before = note(drawing("d1", "a"))
        val after = note(drawing("d1", "ab"))
        val history = NoteHistory().track(before, after, NoteEditKind.DRAWING)
        assertTrue(history.canUndo)
        val step = assertNotNull(history.undoStep(after))
        assertEquals("a", (step.note.contents.single() as NoteContentModel.Drawing).drawing)
        assertEquals(setOf("d1"), step.dirtyIds)
        assertTrue(step.removed.isEmpty())
        assertTrue(step.history.canRedo)
    }

    @Test
    fun anEditThatChangedNothingLeavesNoStep() {
        val same = note(text("t1", "x"))
        assertFalse(NoteHistory().track(same, same.copy(), NoteEditKind.TEXT).canUndo)
        assertFalse(NoteHistory().track(null, same, NoteEditKind.TEXT).canUndo)
    }

    @Test
    fun undoingAnAdditionDeletesItAndRedoBringsItBack() {
        val before = note(text("t1"))
        val after = note(text("t1"), drawing("page-1", "ink"))
        val history = NoteHistory().track(before, after, NoteEditKind.DRAWING)
        val undo = assertNotNull(history.undoStep(after))
        assertEquals(listOf("page-1"), undo.removed.map { it.id })
        assertTrue(undo.dirtyIds.isEmpty())
        val redo = assertNotNull(undo.history.redoStep(undo.note))
        assertEquals(setOf("page-1"), redo.dirtyIds)
        assertEquals(after.contents, redo.note.contents)
        assertTrue(redo.history.canUndo)
        assertFalse(redo.history.canRedo)
    }

    @Test
    fun mediaAdditionsAreUndoable() {
        val before = note(text("t1"))
        val after = note(text("t1"), media("m1"))
        val step = assertNotNull(NoteHistory().track(before, after, NoteEditKind.ADD_MEDIA).undoStep(after))
        assertEquals(listOf("m1"), step.removed.map { it.id })
    }

    @Test
    fun syncChangesAreCarriedIntoEverySnapshotInsteadOfRecorded() {
        val first = note(media("m1"))
        val typed = note(media("m1"), text("t1", "hi"))
        var history = NoteHistory().track(first, typed, NoteEditKind.ADD_TEXT)
        val thumbed = note(media("m1", thumbnail = "/thumbs/m1.png"), text("t1", "hi"), text("remote"))
        history = history.track(typed, thumbed, NoteEditKind.SYNC)
        assertEquals(1, history.past.size)
        val step = assertNotNull(history.undoStep(thumbed))
        val restored = step.note.contents.associateBy { it.id }
        assertEquals("/thumbs/m1.png", (restored.getValue("m1") as NoteContentModel.MediaContent).thumbnailPath)
        assertTrue("remote" in restored)
        assertFalse("t1" in restored)
    }

    @Test
    fun syncRemovalsLeaveEverySnapshot() {
        val first = note(text("a"), text("b"))
        val second = note(text("a", "x"), text("b"))
        val history = NoteHistory()
            .track(first, second, NoteEditKind.TEXT)
            .track(second, note(text("a", "x")), NoteEditKind.SYNC)
        assertEquals(listOf("a"), history.past.single().contents.map { it.id })
    }

    @Test
    fun masterSnapshotsCarryTheirLayoutAndNoteSnapshotsDoNot() {
        val document = CanvasDocument(listOf(page("p1"), widget("w1", "t1", y = 24f)))
        val board = NoteSnapshots.masterEditor(note(text("t1")), listOf(text("t1")), document)
        assertEquals(document, NoteSnapshots.canvasOf(board))
        val plain = NoteSnapshots.noteEditor(board, "Title", listOf(text("t1")))
        assertNull(NoteSnapshots.canvasOf(plain))
        assertEquals("Title", plain.title)
    }

    @Test
    fun aLayoutChangeAloneIsAStep() {
        val contents = listOf(text("t1"))
        val before = NoteSnapshots.masterEditor(note(), contents, CanvasDocument(listOf(page("p1"), widget("w1", "t1"))))
        val after = NoteSnapshots.masterEditor(note(), contents, CanvasDocument(listOf(page("p1"), widget("w1", "t1", y = 200f))))
        val step = assertNotNull(NoteHistory().track(before, after, NoteEditKind.LAYOUT).undoStep(after))
        assertEquals(0f, NoteSnapshots.canvasOf(step.note)!!.nodes.last().rect.y)
        assertTrue(step.dirtyIds.isEmpty())
    }

    @Test
    fun heavyDrawingHistoryIsTrimmedButKeepsTheLatestStep() {
        val stroke = "x".repeat((NoteHistory.MAX_WEIGHT / 4).toInt())
        var history = NoteHistory()
        var current = note(drawing("d1", ""))
        repeat(8) { index ->
            val next = note(drawing("d1", stroke + index))
            history = history.track(current, next, NoteEditKind.DRAWING)
            current = next
        }
        assertTrue(history.past.size in 1..4)
        assertTrue(NoteHistory.weightOf(history.past) <= NoteHistory.MAX_WEIGHT)
        assertTrue(history.canUndo)
    }

    @Test
    fun sharedPayloadsAreWeighedOnce() {
        val shared = drawing("d1", "y".repeat(1000))
        val snapshots = listOf(note(shared, text("t1")), note(shared, text("t1", "a")), note(shared, text("t1", "ab")))
        assertEquals(1000L, NoteHistory.weightOf(snapshots))
    }

    @Test
    fun theCountLimitStillHolds() {
        var history = NoteHistory(limit = 3)
        repeat(6) { index -> history = history.record(note(text("t1", "$index")), NoteEditKind.TEXT) }
        assertEquals(listOf("3", "4", "5"), history.past.map { (it.contents.single() as NoteContentModel.TextContent).text })
    }
}
