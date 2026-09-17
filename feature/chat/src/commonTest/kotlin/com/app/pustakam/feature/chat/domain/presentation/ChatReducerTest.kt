package com.app.pustakam.feature.chat.domain.presentation

import com.app.pustakam.core.model.models.chat.AiReplyState
import com.app.pustakam.core.model.models.chat.ChatAttachment
import com.app.pustakam.core.model.models.chat.ChatConnectionState
import com.app.pustakam.core.model.models.chat.ChatConversation
import com.app.pustakam.core.model.models.chat.ChatMessage
import com.app.pustakam.core.model.models.chat.ChatMessageKind
import com.app.pustakam.core.model.models.chat.ChatParticipant
import com.app.pustakam.core.model.models.chat.ConversationKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatReducerTest {

    private fun message(id: String, at: Long, sender: String? = "u1") =
        ChatMessage(id = id, conversationId = "c1", senderId = sender, createdAt = at)

    private fun reduce(state: ChatState, vararg intents: ChatIntent): ChatState =
        intents.fold(state) { acc, intent -> ChatReducer.reduce(acc, intent) }

    @Test
    fun `opening a thread clears the previous one but keeps who i am`() {
        val before = ChatState(
            conversationId = "old", currentUserId = "u1",
            messages = listOf(message("m1", 1)), draft = "leftover",
            connection = ChatConnectionState.CONNECTED,
        )

        val after = ChatReducer.reduce(before, ChatIntent.Open("new"))

        assertEquals("new", after.conversationId)
        assertTrue(after.messages.isEmpty(), "the previous thread must not bleed into this one")
        assertEquals("", after.draft)
        assertEquals("u1", after.currentUserId, "identity survives a thread change")
        assertEquals(ChatConnectionState.CONNECTED, after.connection, "so does the live socket")
    }

    @Test
    fun `an empty draft cannot be sent`() {
        val state = ChatState(conversationId = "c1", draft = "   ")
        assertFalse(state.canSend)
        assertEquals(state, ChatReducer.reduce(state, ChatIntent.SendRequested), "a no-op must not clear anything")
    }

    @Test
    fun `an attachment alone is enough to send`() {
        val state = ChatState(conversationId = "c1", pendingAttachments = listOf(ChatAttachment()))
        assertTrue(state.canSend)
    }

    @Test
    fun `sending clears the composer immediately, not when the server answers`() {
        val state = ChatState(conversationId = "c1", draft = "hello", pendingAttachments = listOf(ChatAttachment()))

        val after = ChatReducer.reduce(state, ChatIntent.SendRequested)

        assertEquals("", after.draft, "the bubble is already on screen; leaving the text looks broken")
        assertTrue(after.pendingAttachments.isEmpty())
        assertTrue(after.isSending)
    }

    @Test
    fun `a short page of history ends the paging, and the list is left to the repository flow`() {
        val onScreen = listOf(message("m3", 30))
        val state = ChatState(conversationId = "c1", messages = onScreen, isLoadingOlder = true)

        val after = ChatReducer.reduce(state, ChatIntent.OlderLoaded(listOf(message("m1", 10), message("m2", 20))))

        assertEquals(onScreen, after.messages, "merging here as well as in the flow shows a page twice")
        assertFalse(after.hasMoreHistory, "a page smaller than CHAT_PAGE_SIZE means there is no more")
        assertFalse(after.isLoadingOlder)
    }

    @Test
    fun `a full page keeps the paging open`() {
        val full = (1..50).map { message("m$it", it.toLong()) }
        val after = ChatReducer.reduce(ChatState(), ChatIntent.OlderLoaded(full))
        assertTrue(after.hasMoreHistory)
    }

    @Test
    fun `my own typing never shows as someone typing at me`() {
        val state = ChatState(currentUserId = "u1")

        val after = ChatReducer.reduce(state, ChatIntent.TypingChanged(setOf("u1", "u2")))

        assertEquals(setOf("u2"), after.typingUserIds)
        assertTrue(after.isSomeoneTyping)
    }

    @Test
    fun `the connection banner only appears when something is wrong`() {
        assertFalse(ChatState(connection = ChatConnectionState.CONNECTED).showsConnectionBanner)
        assertFalse(ChatState(connection = ChatConnectionState.CONNECTING).showsConnectionBanner)
        assertTrue(ChatState(connection = ChatConnectionState.RECONNECTING).showsConnectionBanner)
        assertTrue(ChatState(connection = ChatConnectionState.DISCONNECTED).showsConnectionBanner)
    }

    @Test
    fun `a failure stops every spinner, not just the one that failed`() {
        val state = ChatState(isLoading = true, isSending = true, isLoadingOlder = true)

        val after = ChatReducer.reduce(state, ChatIntent.Failed("no internet"))

        assertFalse(after.isLoading)
        assertFalse(after.isSending)
        assertFalse(after.isLoadingOlder)
        assertEquals("no internet", after.error)
        assertNull(ChatReducer.reduce(after, ChatIntent.ErrorCleared).error)
    }

    @Test
    fun `a half-written assistant reply is exposed for the typing dots`() {
        val streaming = ChatMessage(
            id = "m2", conversationId = "c1", senderId = null,
            kind = ChatMessageKind.AI, aiState = AiReplyState.STREAMING, text = "Hel",
        )
        val state = ChatState(messages = listOf(message("m1", 1), streaming))

        assertEquals("m2", state.streamingMessage?.id)
        assertNull(ChatState(messages = listOf(message("m1", 1))).streamingMessage)
    }

    @Test
    fun `the title falls back through the conversation, never to blank`() {
        val ai = ChatConversation(id = "c1", kind = ConversationKind.AI)
        assertEquals("Pustakam AI", ChatState(conversation = ai).title)
    }

    @Test
    fun `refreshing the inbox leaves it readable, unlike a first load`() {
        val loaded = ChatListState(conversations = listOf(ChatConversation(id = "c1")))

        val refreshing = ChatReducer.reduceList(loaded, ChatListIntent.RefreshRequested)
        assertTrue(refreshing.isRefreshing)
        assertFalse(refreshing.isLoading, "a refresh must not blank the list")

        val firstLoad = ChatReducer.reduceList(ChatListState(), ChatListIntent.Load)
        assertTrue(firstLoad.isLoading)
    }

    @Test
    fun `the inbox search filters the conversations already on the device`() {
        val state = ChatListState(
            conversations = listOf(
                ChatConversation(id = "c1", title = "Design review"),
                ChatConversation(id = "c2", title = "Grocery list"),
            ),
        )

        val filtered = ChatReducer.reduceList(state, ChatListIntent.InboxQueryChanged("desi"))

        assertEquals(listOf("c1"), filtered.visibleConversations.map { it.id })
    }

    /**
     * 🔧 17-Sep-2026 — these were ONE field. Once the picker's box started driving a server-side
     * /u/search, typing in the inbox filter fired user lookups at the server, and opening the
     * picker wiped whatever the inbox was filtered by.
     */
    @Test
    fun `the inbox filter and the people search do not touch each other`() {
        val state = ChatListState(
            conversations = listOf(
                ChatConversation(id = "c1", title = "Design review"),
                ChatConversation(id = "c2", title = "Grocery list"),
            ),
            inboxQuery = "desi",
        )

        val searching = ChatReducer.reduceList(state, ChatListIntent.QueryChanged("rish"))
        assertEquals("desi", searching.inboxQuery, "searching for people leaves the inbox filter alone")
        assertEquals(listOf("c1"), searching.visibleConversations.map { it.id })

        val opened = ChatReducer.reduceList(searching, ChatListIntent.PeerPickerToggled(true))
        assertEquals("desi", opened.inboxQuery, "opening the picker must not wipe the inbox filter")
        assertEquals("", opened.query)
    }

    @Test
    fun `opening the peer picker starts from a clean search box`() {
        val state = ChatListState(query = "old search")
        assertEquals("", ChatReducer.reduceList(state, ChatListIntent.PeerPickerToggled(true)).query)
    }

    /**
     * 🔧 31-Aug-2026 — the query used to SURVIVE closing the picker, which left the inbox silently
     * filtered by whatever had been typed there, with no field on screen to clear it.
     */
    @Test
    fun `closing the peer picker also clears the search box and the results`() {
        val state = ChatListState(
            conversations = listOf(ChatConversation(id = "c1", title = "Design review")),
            peers = listOf(ChatParticipant(id = "u2", username = "rishabh")),
            query = "rish",
            isSearchingPeers = true,
        )

        val closed = ChatReducer.reduceList(state, ChatListIntent.PeerPickerToggled(false))

        assertEquals("", closed.query)
        assertEquals(emptyList(), closed.peers)
        assertFalse(closed.isSearchingPeers)
        assertEquals(listOf("c1"), closed.visibleConversations.map { it.id })
    }

    /**
     * 🆔 peers now come from a server-side USERNAME-prefix search. Filtering them again by display
     * name would hide the very rows the search found — @rishabh whose name is "R. S." contains no
     * "rish". This test is the guard against that filter creeping back in.
     */
    @Test
    fun `peer results are shown exactly as the server returned them`() {
        val state = ChatListState(query = "rish")
        val found = listOf(ChatParticipant(id = "u2", username = "rishabh", name = "R. S."))

        val loaded = ChatReducer.reduceList(state, ChatListIntent.PeersLoaded(found))

        assertEquals(listOf("u2"), loaded.visiblePeers.map { it.id })
        assertFalse(loaded.isSearchingPeers, "results arriving stops the spinner")
    }

    @Test
    fun `a failed peer search stops the spinner instead of hanging on it`() {
        val searching = ChatReducer.reduceList(ChatListState(), ChatListIntent.PeersSearching(true))
        assertTrue(searching.isSearchingPeers)

        val failed = ChatReducer.reduceList(searching, ChatListIntent.Failed("no internet"))
        assertFalse(failed.isSearchingPeers)
        assertEquals("no internet", failed.error)
    }

    @Test
    fun `an empty search box prompts instead of claiming nobody was found`() {
        assertTrue(ChatListState().isPeerSearchIdle)
        assertFalse(ChatListState(query = "rish").isPeerSearchIdle)
        assertFalse(ChatListState(isSearchingPeers = true).isPeerSearchIdle)
    }
}
