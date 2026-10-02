package com.app.pustakam.core.drawing

import com.app.pustakam.core.drawing.live.DrawLive
import com.app.pustakam.core.drawing.live.DrawLiveEvent
import com.app.pustakam.core.drawing.live.DrawLiveHub
import com.app.pustakam.core.drawing.live.DrawLiveRequest
import com.app.pustakam.core.drawing.live.DrawLiveStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DrawLiveHubTest {

    private val sent = mutableListOf<String>()

    private var online = true

    private val hub = DrawLiveHub { frame -> if (online) sent.add(frame) else false }

    private fun state(roomId: String, locks: String = "") =
        """{"t":"live_state","d":{"roomId":"$roomId","you":{"sessionId":"s-me","userId":"u-me","role":"OWNER"},"members":[],"locks":[$locks]}}"""

    private fun types(): List<String> = sent.map { Regex("\"t\":\"([a-z_]+)\"").find(it)!!.groupValues[1] }

    @Test
    fun joiningSendsOnceAndTheServerStateMakesItLive() {
        hub.join("n1")
        hub.join("n1")
        assertEquals(listOf("live_join"), types())
        assertFalse(hub.request(DrawLiveRequest.Lock("n1", "p1")))
        assertTrue(hub.onFrame(state("n1")) is DrawLiveEvent.State)
        hub.join("n1")
        assertEquals(1, sent.size)
        assertTrue(hub.request(DrawLiveRequest.Lock("n1", "p1")))
        assertEquals(listOf("live_join", "live_lock"), types())
    }

    @Test
    fun framesForRoomsNotJoinedOrNotLiveAreIgnored() {
        assertNull(hub.onFrame(state("n1")))
        assertNull(hub.onFrame("""{"t":"message","d":{}}"""))
        assertFalse(hub.request(DrawLiveRequest.Sync("n1")))
        assertTrue(hub.rooms().isEmpty())
    }

    @Test
    fun aDroppedConnectionRejoinsEveryRoomWhenItComesBack() {
        hub.join("n1")
        hub.join("n2")
        hub.onFrame(state("n1", """{"pageId":"p1","sessionId":"s-me","userId":"u-me","since":1}"""))
        assertTrue(DrawLive.holds(hub.room("n1")!!, "p1"))
        online = false
        hub.onDisconnected()
        assertEquals(DrawLiveStatus.CONNECTING, hub.room("n1")!!.status)
        assertFalse(DrawLive.holds(hub.room("n1")!!, "p1"))
        online = true
        sent.clear()
        hub.onConnected()
        assertEquals(listOf("live_join", "live_join"), types())
    }

    @Test
    fun leavingReleasesHeldPagesFirst() {
        hub.join("n1")
        hub.onFrame(state("n1", """{"pageId":"p1","sessionId":"s-me","userId":"u-me","since":1}"""))
        sent.clear()
        hub.leave("n1")
        assertEquals(listOf("live_unlock", "live_leave"), types())
        assertNull(hub.room("n1"))
    }

    @Test
    fun aClosedRoomCanBeJoinedAgain() {
        hub.join("n1")
        hub.onFrame("""{"t":"live_closed","d":{"roomId":"n1","reason":"NO_ACCESS"}}""")
        assertEquals(DrawLiveStatus.CLOSED, hub.room("n1")!!.status)
        sent.clear()
        hub.join("n1")
        assertEquals(listOf("live_join"), types())
        assertEquals(DrawLiveStatus.CONNECTING, hub.room("n1")!!.status)
    }
}
