package com.app.pustakam.core.model.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ⚠️ This table is duplicated in PustakmServer/test/unit/username.test.js, deliberately and
 * character for character. Two repos, no shared schema — these two tables are the only thing
 * keeping client-side validation and the server's in agreement. Change one, change the other.
 */
class UsernameRulesTest {

    private val accepted = listOf("abc", "a-b", "a1-b2", "rishabh", "r2-d2", "Rishabh", "a".repeat(30))

    private val rejected = listOf(
        "ab",                 // shorter than the minimum
        "a".repeat(31),       // longer than the maximum
        "-abc", "abc-",       // hyphen at an edge
        "a--b",               // doubled hyphen
        "a_b", "a.b", "a b", "a@b",
        "abç", "риш", "🙂🙂🙂",  // non-ASCII: no lookalikes to build from
        "", "   ",
    )

    @Test
    fun `case and surrounding space collapse to one key`() {
        assertEquals("rishabh", UsernameRules.canonical("Rishabh"))
        assertEquals("rishabh", UsernameRules.canonical("  RISHABH  "))
        assertEquals("", UsernameRules.canonical(null))
    }

    @Test
    fun `the accepted table`() {
        accepted.forEach { assertTrue(UsernameRules.isValid(it), "\"$it\" should be accepted") }
    }

    @Test
    fun `the rejected table`() {
        rejected.forEach { assertFalse(UsernameRules.isValid(it), "\"$it\" should be rejected") }
    }

    @Test
    fun `the bounds match the server`() {
        assertEquals(3, UsernameRules.MIN_LENGTH)
        assertEquals(30, UsernameRules.MAX_LENGTH)
    }

    @Test
    fun `every route segment is reserved, so a handle cannot shadow an endpoint`() {
        val routeSegments = listOf(
            "login", "register", "auth", "notes", "sync", "users", "u", "profile",
            "images", "media", "chat", "devices", "deviceConfig", "healthz", "readyz",
        )
        routeSegments.forEach {
            assertTrue(UsernameRules.isReserved(it), "add \"${it.lowercase()}\" to RESERVED")
        }
    }

    @Test
    fun `the u sub-segments are reserved or they would be swallowed by the profile route`() {
        listOf("check", "search", "avatar").forEach { assertTrue(UsernameRules.isReserved(it)) }
    }

    @Test
    fun `reserved matching happens after canonicalisation`() {
        assertTrue(UsernameRules.isReserved("AdMiN"))
        assertTrue(UsernameRules.isReserved("  admin  "))
        assertFalse(UsernameRules.isReserved("rishabh"))
    }

    @Test
    fun `rejectionFor classifies so the composer can say why`() {
        assertEquals(UsernameRejection.INVALID, UsernameRules.rejectionFor("a"))
        assertEquals(UsernameRejection.RESERVED, UsernameRules.rejectionFor("admin"))
        assertNull(UsernameRules.rejectionFor("rishabh"))
    }

    @Test
    fun `only plausible input is worth a network round trip`() {
        assertFalse(UsernameRules.isWorthChecking("ab"))
        assertFalse(UsernameRules.isWorthChecking("admin"))
        assertTrue(UsernameRules.isWorthChecking("rishabh"))
    }

    // ── canSubmit — the rule behind the Save button on both platforms ─────────────

    /**
     * 🔧 17-Sep-2026 — THE regression. Save used to require a successful /u/check, so one 429 from
     * usernameCheckLimiter left the button dead forever with no hint on screen. A missing answer
     * must never block submission: the server owns uniqueness and answers 409 if it is taken.
     */
    @Test
    fun `a valid handle can be submitted before any availability answer arrives`() {
        assertTrue(UsernameRules.canSubmit("rishabh", current = null, knownUnavailable = false))
        assertTrue(UsernameRules.canSubmit("rishabh", current = "oldname", knownUnavailable = false))
    }

    @Test
    fun `only a positive no from the server blocks submission`() {
        assertFalse(UsernameRules.canSubmit("rishabh", current = null, knownUnavailable = true))
    }

    @Test
    fun `locally hopeless input is never submittable`() {
        assertFalse(UsernameRules.canSubmit("ab", current = null, knownUnavailable = false))
        assertFalse(UsernameRules.canSubmit("admin", current = null, knownUnavailable = false))
        assertFalse(UsernameRules.canSubmit("", current = null, knownUnavailable = false))
        assertFalse(UsernameRules.canSubmit(null, current = null, knownUnavailable = false))
    }

    /**
     * The stored username keeps its display case, so both sides must be canonicalised — otherwise
     * "Rishabh" looks like a change from "Rishabh" and burns a rename off the 30-day cooldown.
     */
    @Test
    fun `re-submitting the same handle in a different case is not a change`() {
        assertFalse(UsernameRules.canSubmit("Rishabh", current = "Rishabh", knownUnavailable = false))
        assertFalse(UsernameRules.canSubmit("RISHABH", current = "rishabh", knownUnavailable = false))
        assertFalse(UsernameRules.canSubmit("  rishabh  ", current = "Rishabh", knownUnavailable = false))
        assertTrue(UsernameRules.canSubmit("rishabh2", current = "Rishabh", knownUnavailable = false))
    }
}
