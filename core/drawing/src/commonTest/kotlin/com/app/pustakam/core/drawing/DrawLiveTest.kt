package com.app.pustakam.core.drawing

import com.app.pustakam.core.drawing.live.DrawLive
import com.app.pustakam.core.drawing.live.DrawLiveEvent
import com.app.pustakam.core.drawing.live.DrawLiveLock
import com.app.pustakam.core.drawing.live.DrawLiveProtocol
import com.app.pustakam.core.drawing.live.DrawLiveRequest
import com.app.pustakam.core.drawing.live.DrawLiveRoom
import com.app.pustakam.core.drawing.live.DrawLiveStatus
import com.app.pustakam.core.drawing.ops.DrawOp
import com.app.pustakam.core.model.models.share.NoteAccess
import com.app.pustakam.core.model.models.share.NoteRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DrawLiveTest {

    private val room = "note-7"

    private fun lockJson(pageId: String, sessionId: String, userId: String) =
        """{"pageId":"$pageId","sessionId":"$sessionId","userId":"$userId","since":5}"""

    private fun state(role: String = "EDITOR", locks: List<String> = emptyList(), you: String = "s-me", userId: String = "u-me") = """
        {"t":"live_state","d":{"roomId":"$room",
          "you":{"sessionId":"$you","userId":"$userId","role":"$role"},
          "members":[
            {"sessionId":"s-me","userId":"u-me","name":"Asha","role":"$role","focusPageId":"p1"},
            {"sessionId":"s-ravi","userId":"u-ravi","name":"Ravi","role":"EDITOR","focusPageId":"p2"},
            {"sessionId":"s-tab","userId":"u-me","name":"Asha","role":"$role","focusPageId":null}
          ],
          "locks":[${locks.joinToString(",")}]}}
    """.trimIndent()

    private fun live(frame: String, start: DrawLiveRoom = DrawLive.joining(room)): DrawLiveRoom =
        DrawLive.reduce(start, DrawLiveProtocol.decode(frame)!!)

    private fun relayed(request: DrawLiveRequest, sessionId: String): String =
        DrawLiveProtocol.encode(request).replace("\"d\":{", "\"d\":{\"sessionId\":\"$sessionId\",")

    private fun ink(y: Float) = DrawTestKit.horizontal(DrawTestKit.overlay(), y).document.elements

    @Test
    fun theFirstStateMakesTheRoomLive() {
        val joined = live(state(locks = listOf(lockJson("p1", "s-me", "u-me"))))
        assertEquals(DrawLiveStatus.LIVE, joined.status)
        assertEquals(NoteRole.EDITOR, joined.role)
        assertEquals(3, joined.members.size)
        assertTrue(DrawLive.holds(joined, "p1"))
        assertTrue(DrawLive.canDrawOn(joined, "p1"))
        assertFalse(DrawLive.canDrawOn(joined, "p2"))
    }

    @Test
    fun aPageSomeoneElseIsDrawingOnShowsWhoAndCannotBeTaken() {
        val joined = live(state(locks = listOf(lockJson("p2", "s-ravi", "u-ravi"))))
        val badge = DrawLive.badgeOn(joined, "p2")!!
        assertEquals("Ravi", badge.name)
        assertFalse(badge.mine)
        assertFalse(badge.otherDevice)
        assertFalse(DrawLive.canClaim(joined, "p2"))
        assertTrue(DrawLive.claims(joined, "p2", drawing = true, busy = false).isEmpty())
        assertNull(DrawLive.badgeOn(joined, "p1"))
    }

    @Test
    fun myOtherDeviceDrawingIsNotMe() {
        val joined = live(state(locks = listOf(lockJson("p1", "s-tab", "u-me"))))
        val badge = DrawLive.badgeOn(joined, "p1")!!
        assertFalse(badge.mine)
        assertTrue(badge.otherDevice)
        assertFalse(DrawLive.canDrawOn(joined, "p1"))
    }

    @Test
    fun focusingAnotherPageReleasesTheOldOneAndClaimsTheNew() {
        val joined = live(state(locks = listOf(lockJson("p1", "s-me", "u-me"))))
        assertEquals(
            listOf(DrawLiveRequest.Unlock(room, "p1"), DrawLiveRequest.Lock(room, "p3")),
            DrawLive.claims(joined, "p3", drawing = true, busy = false)
        )
        assertTrue(DrawLive.claims(joined, "p3", drawing = true, busy = true).isEmpty())
        assertTrue(DrawLive.claims(joined, "p1", drawing = true, busy = false).isEmpty())
        assertEquals(listOf(DrawLiveRequest.Unlock(room, "p1")), DrawLive.claims(joined, "p1", drawing = false, busy = false))
        assertEquals(
            listOf(DrawLiveRequest.Unlock(room, "p1"), DrawLiveRequest.Leave(room)),
            DrawLive.leaving(joined)
        )
    }

    @Test
    fun aReaderOnlyWatches() {
        val joined = live(state(role = "READER"))
        assertFalse(DrawLive.canWrite(joined))
        assertFalse(DrawLive.canClaim(joined, "p1"))
        assertTrue(DrawLive.claims(joined, "p1", drawing = true, busy = false).isEmpty())
        assertEquals(NoteRole.READER, live(state(role = "SOMETHING_NEW")).role)
    }

    @Test
    fun otherPeoplesStrokesStreamInOrderAndMineAreIgnored() {
        val joined = live(state(locks = listOf(lockJson("p2", "s-ravi", "u-ravi"))))
        val drawn = ink(100f)
        val first = relayed(DrawLiveRequest.Stroke(room, "p2", 2L, drawn, setOf("gone")), "s-ravi")
        val streamed = live(first, joined)
        assertEquals(drawn.single().id, DrawLive.previews(streamed).single().id)
        assertEquals(setOf("gone"), DrawLive.hiddenIds(streamed))
        val late = relayed(DrawLiveRequest.Stroke(room, "p2", 1L, emptyList(), emptySet()), "s-ravi")
        assertEquals(streamed, live(late, streamed))
        val mine = relayed(DrawLiveRequest.Stroke(room, "p1", 9L, ink(50f), emptySet()), "s-me")
        assertEquals(streamed, live(mine, streamed))
        val cleared = relayed(DrawLiveRequest.Stroke(room, "p2", 3L, emptyList(), emptySet()), "s-ravi")
        assertTrue(DrawLive.previews(live(cleared, streamed)).isEmpty())
    }

    @Test
    fun aCommittedOpReplacesThePreview() {
        val joined = live(state(locks = listOf(lockJson("p2", "s-ravi", "u-ravi"))))
        val drawn = ink(100f)
        val streamed = live(relayed(DrawLiveRequest.Stroke(room, "p2", 1L, drawn, emptySet()), "s-ravi"), joined)
        val op = DrawOp.Add(drawn, "u-ravi", 4L)
        val frame = relayed(DrawLiveRequest.Op(room, "p2", op), "s-ravi")
        val event = DrawLiveProtocol.decode(frame) as DrawLiveEvent.Op
        assertEquals(op.elements.map { it.id }, (event.op as DrawOp.Add).elements.map { it.id })
        assertEquals(op.elements.single().points.size, (event.op as DrawOp.Add).elements.single().points.size)
        assertTrue(DrawLive.previews(DrawLive.reduce(streamed, event)).isEmpty())
    }

    @Test
    fun aReleasedPageDropsItsPreviewAndADenialIsRemembered() {
        val joined = live(state(locks = listOf(lockJson("p2", "s-ravi", "u-ravi"))))
        val streamed = live(relayed(DrawLiveRequest.Stroke(room, "p2", 1L, ink(100f), emptySet()), "s-ravi"), joined)
        val denied = live("""{"t":"live_denied","d":{"roomId":"$room","lock":${lockJson("p2", "s-ravi", "u-ravi")}}}""", streamed)
        assertEquals(DrawLiveLock("p2", "s-ravi", "u-ravi", 5L), denied.denied)
        val released = live(state(), denied)
        assertTrue(DrawLive.previews(released).isEmpty())
        assertNull(released.denied)
    }

    @Test
    fun syncFromOthersAsksForAPullButMyOwnDoesNot() {
        val joined = live(state())
        val fromRavi = live(relayed(DrawLiveRequest.Sync(room), "s-ravi"), joined)
        assertEquals(1L, fromRavi.syncRevision)
        assertEquals(fromRavi, live(relayed(DrawLiveRequest.Sync(room), "s-me"), fromRavi))
    }

    @Test
    fun closingOrDroppingTheConnectionStopsDrawing() {
        val joined = live(state(locks = listOf(lockJson("p1", "s-me", "u-me"))))
        val dropped = DrawLive.reduce(joined, DrawLiveEvent.Disconnected(room))
        assertEquals(DrawLiveStatus.CONNECTING, dropped.status)
        assertFalse(DrawLive.canDrawOn(dropped, "p1"))
        val closed = live("""{"t":"live_closed","d":{"roomId":"$room","reason":"ACCESS_REVOKED"}}""", joined)
        assertEquals(DrawLiveStatus.CLOSED, closed.status)
        assertEquals("ACCESS_REVOKED", closed.closedReason)
        assertEquals(closed, DrawLive.reduce(closed, DrawLiveEvent.Disconnected(room)))
    }

    @Test
    fun framesForOtherRoomsOrOtherFeaturesAreNotMine() {
        val joined = live(state())
        assertEquals(joined, DrawLive.reduce(joined, DrawLiveEvent.Sync("other-note", "s-ravi")))
        assertNull(DrawLiveProtocol.decode("""{"t":"message","d":{"roomId":"$room"}}"""))
        assertNull(DrawLiveProtocol.decode("not json"))
        assertNull(DrawLiveProtocol.decode("""{"t":"live_op","d":{"roomId":"$room","pageId":"p1"}}"""))
        assertTrue(DrawLiveProtocol.isLiveFrame("live_state"))
        assertFalse(DrawLiveProtocol.isLiveFrame("ack"))
    }

    @Test
    fun outgoingFramesCarryTheRoomAndPage() {
        val frame = DrawLiveProtocol.encode(DrawLiveRequest.Lock(room, "p9"))
        assertTrue(frame.contains("\"t\":\"live_lock\""))
        assertTrue(frame.contains("\"roomId\":\"$room\""))
        assertTrue(frame.contains("\"pageId\":\"p9\""))
        assertTrue(DrawLiveProtocol.encode(DrawLiveRequest.Focus(room, null)).contains("\"pageId\":null"))
    }

    @Test
    fun badgesReadLikeAChat() {
        val ravi = live(state(locks = listOf(lockJson("p2", "s-ravi", "u-ravi"), lockJson("p1", "s-me", "u-me"), lockJson("p3", "s-tab", "u-me"))))
        assertEquals("Ravi is drawing…", DrawLive.labelOn(ravi, "p2"))
        assertEquals("You are drawing", DrawLive.labelOn(ravi, "p1"))
        assertEquals("Drawing on your other device…", DrawLive.labelOn(ravi, "p3"))
        assertNull(DrawLive.labelOn(ravi, "p9"))
        assertNull(DrawLive.labelOn(null, "p1"))
        assertEquals("p1", DrawLive.inputPage(ravi))
        assertTrue(DrawLive.acceptsInput(null))
        assertTrue(DrawLive.acceptsInput(ravi))
        assertFalse(DrawLive.acceptsInput(live(state())))
    }

    @Test
    fun accessFollowsTheSharedRole() {
        assertFalse(NoteAccess.canWrite(NoteRole.READER))
        assertTrue(NoteAccess.canWrite(NoteRole.EDITOR))
        assertTrue(NoteAccess.canDelete(NoteRole.EDITOR, "u-me", "u-me"))
        assertFalse(NoteAccess.canDelete(NoteRole.EDITOR, "u-ravi", "u-me"))
        assertFalse(NoteAccess.canDelete(NoteRole.EDITOR, null, "u-me"))
        assertTrue(NoteAccess.canDelete(NoteRole.OWNER, "u-ravi", "u-me"))
        assertFalse(NoteAccess.canDelete(NoteRole.READER, "u-me", "u-me"))
        assertTrue(NoteAccess.canShare(NoteRole.OWNER))
        assertFalse(NoteAccess.canShare(NoteRole.EDITOR))
    }
}
