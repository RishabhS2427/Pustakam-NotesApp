package com.app.pustakam.core.drawing

import com.app.pustakam.core.drawing.editor.DrawCommands
import com.app.pustakam.core.drawing.editor.DrawReducer
import com.app.pustakam.core.drawing.live.DrawLiveOutbox
import com.app.pustakam.core.drawing.live.DrawLiveProtocol
import com.app.pustakam.core.drawing.live.DrawLiveRequest
import com.app.pustakam.core.drawing.live.DrawLiveRoom
import com.app.pustakam.core.drawing.live.DrawLive
import com.app.pustakam.core.drawing.model.DrawPointerType
import com.app.pustakam.core.drawing.note.DrawNoteContents
import com.app.pustakam.core.drawing.ops.DrawOp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DrawLiveOutboxTest {

    private val points = DrawTestKit.line(DrawTestKit.point(20f, 40f), DrawTestKit.point(220f, 40f), 20)

    @Test
    fun aStrokeStreamsWhileDrawnAndCommitsOnce() {
        val outbox = DrawLiveOutbox("n1")
        val start = DrawTestKit.overlay()
        assertTrue(outbox.next(start, "p1", 0L).isEmpty())
        val down = DrawReducer.reduce(start, DrawCommands.pointerDown(points.first(), DrawPointerType.STYLUS, emptyList()))
        val moving = DrawReducer.reduce(down, DrawCommands.pointerMove(points.drop(1)))

        val streamed = outbox.next(moving, "p1", 100L).single() as DrawLiveRequest.Stroke
        assertEquals(1L, streamed.seq)
        assertTrue(streamed.elements.isNotEmpty())
        assertTrue(outbox.next(moving, "p1", 120L).isEmpty())

        val lifted = DrawReducer.reduce(moving, DrawCommands.pointerUp())
        val finished = outbox.next(lifted, "p1", 130L)
        assertEquals(2, finished.size)
        val op = (finished[0] as DrawLiveRequest.Op).op as DrawOp.Add
        assertEquals(lifted.document.elements.single().id, op.elements.single().id)
        val cleared = finished[1] as DrawLiveRequest.Stroke
        assertEquals(2L, cleared.seq)
        assertTrue(cleared.elements.isEmpty())
        assertTrue(outbox.next(lifted, "p1", 400L).isEmpty())
    }

    @Test
    fun aPageIsAskedForOnceUntilTheServerAnswersOrTimeRunsOut() {
        val outbox = DrawLiveOutbox("n1")
        val frame = """{"t":"live_state","d":{"roomId":"n1","you":{"sessionId":"s-me","userId":"u","role":"EDITOR"},"members":[],"locks":[]}}"""
        val room = DrawLive.reduce(DrawLiveRoom.joining("n1"), DrawLiveProtocol.decode(frame)!!)
        assertEquals(listOf(DrawLiveRequest.Lock("n1", "p1")), outbox.claims(room, "p1", drawing = true, busy = false, now = 0L))
        assertTrue(outbox.claims(room, "p1", drawing = true, busy = false, now = 500L).isEmpty())
        assertEquals(listOf(DrawLiveRequest.Lock("n1", "p2")), outbox.claims(room, "p2", drawing = true, busy = false, now = 600L))
        assertEquals(1, outbox.claims(room, "p2", drawing = true, busy = false, now = 600L + DrawLiveOutbox.CLAIM_RETRY_MILLIS).size)
    }

    @Test
    fun nothingLeavesWithoutAPageAndOldEditsAreNotReplayed() {
        val outbox = DrawLiveOutbox("n1")
        val drawn = DrawTestKit.stroke(DrawTestKit.overlay(), points)
        assertTrue(outbox.next(drawn, null, 0L).isEmpty())
        assertTrue(outbox.next(drawn, "p1", 10L).isEmpty())
        val again = DrawTestKit.stroke(drawn, points)
        assertTrue(outbox.next(again, null, 20L).isEmpty())
        assertTrue(outbox.next(again, "p1", 30L).isEmpty())
    }

    @Test
    fun theOverlayIdIsStableAndPassesTheServersIdRule() {
        val serverRule = Regex("^\\d{10,17}-[0-9a-fA-F-]{8,64}$")
        val noteId = "1790863697502-a29aa9aa-cbdc-4493-8924-191cfb2a73c6"
        assertEquals(DrawNoteContents.overlayId(noteId), DrawNoteContents.overlayId(noteId))
        assertTrue(serverRule.matches(DrawNoteContents.overlayId(noteId)))
        assertTrue(DrawNoteContents.overlayId(noteId) != noteId)
        assertTrue(serverRule.matches(DrawNoteContents.overlayId("legacy-note")))
    }

    @Test
    fun aRemoteOpLandsInTheOverlayRowWithAStableId() {
        val noteId = "1790863697502-a29aa9aa-cbdc-4493-8924-191cfb2a73c6"
        val overlay = DrawNoteContents.createOverlay(noteId, 3.0)
        assertEquals(DrawNoteContents.overlayId(noteId), overlay.id)
        assertEquals(overlay.id, DrawNoteContents.createOverlay(noteId, 9.0).id)
        assertTrue(overlay.isOverlay())
        val element = DrawTestKit.stroke(DrawTestKit.overlay(), points).document.elements.single()
        val applied = DrawNoteContents.withOp(overlay, DrawOp.Add(listOf(element), "u-ravi", 7L))
        assertEquals(listOf(element.id), DrawNoteContents.documentOf(applied).elements.map { it.id })
        assertEquals(overlay.id, applied.id)
    }

    @Test
    fun remoteStrokesRenderInTheLocalViewport() {
        val state = DrawTestKit.overlay()
        assertTrue(DrawCommands.liveFrame(state, null, emptyList()).isEmpty())
        val drawn = DrawTestKit.stroke(DrawTestKit.overlay(), points).document.elements
        val frame = """{"t":"live_state","d":{"roomId":"n1","you":{"sessionId":"s-me","userId":"u","role":"OWNER"},"members":[],"locks":[{"pageId":"p1","sessionId":"s-2","userId":"u","since":1}]}}"""
        val joined = DrawLive.reduce(DrawLiveRoom.joining("n1"), DrawLiveProtocol.decode(frame)!!)
        val stroke = DrawLiveProtocol.encode(DrawLiveRequest.Stroke("n1", "p1", 1L, drawn, emptySet()))
            .replace("\"d\":{", "\"d\":{\"sessionId\":\"s-2\",")
        val streamed = DrawLive.reduce(joined, DrawLiveProtocol.decode(stroke)!!)
        assertTrue(DrawCommands.liveFrame(state, streamed, emptyList()).isNotEmpty())
    }
}
