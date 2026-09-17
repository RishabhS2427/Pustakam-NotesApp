package com.app.pustakam.core.model.validation

/**
 * 🆔 Username rules, client side.
 *
 * ⚠️ PustakmServer/db/usernames.js and constants/reservedUsernames.js are the other half of this
 * contract — two repos, no shared schema, exactly like ChatSocketProtocol.kt and wsProtocol.js.
 * Both test suites carry the SAME accept/reject table, so a drift shows up as a failing test
 * rather than as a signup the server rejects for no reason the user can see.
 *
 * Shape: 3–30 characters, ASCII alphanumerics with single internal hyphens.
 *   - no leading or trailing hyphen, no doubled hyphen
 *   - no underscore and no dot — a dot makes `pustakam://u/<name>` ambiguous with a file extension
 *   - ASCII only: after canonicalisation there is no Unicode left to build a lookalike from
 */
object UsernameRules {

    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 30

    private val PATTERN = Regex("^[a-zA-Z0-9](?:[a-zA-Z0-9]|-(?=[a-zA-Z0-9])){2,29}$")

    /** Handles nobody may claim. Kept in step with the server's RESERVED_USERNAMES. */
    private val RESERVED = setOf(
        "login", "register", "auth", "notes", "sync", "chat", "users", "u", "media", "images",
        "profile", "devices", "deviceconfig", "healthz", "readyz", "ws",
        "check", "search", "avatar",
        "ai", "ai-assistant", "assistant", "pustakam", "bot",
        "admin", "administrator", "root", "support", "help", "security", "abuse", "moderator",
        "staff", "system", "official", "team",
        "me", "you", "settings", "about", "api", "www", "static", "assets", "public",
        "null", "undefined", "none", "anonymous", "deleted", "unknown", "everyone", "all",
    )

    /** The canonical form — what the server stores as usernameLower and matches on. */
    fun canonical(input: String?): String = input?.trim()?.lowercase() ?: ""

    fun isValid(input: String?): Boolean = PATTERN.matches(input?.trim() ?: "")

    fun isReserved(input: String?): Boolean = RESERVED.contains(canonical(input))

    /** null when the handle is usable locally. Taken-ness is a question only the server answers. */
    fun rejectionFor(input: String?): UsernameRejection? = when {
        !isValid(input) -> UsernameRejection.INVALID
        isReserved(input) -> UsernameRejection.RESERVED
        else -> null
    }

    /** Cheap enough to run on every keystroke, so the network check only fires on plausible input. */
    fun isWorthChecking(input: String?): Boolean = rejectionFor(input) == null

    /**
     * 🔧 17-Sep-2026 — may this handle be submitted?
     *
     * Shared because both platforms must answer it identically, and because getting it wrong is
     * invisible: the first version required a SUCCESSFUL /u/check before enabling Save, so one 429
     * from usernameCheckLimiter or one dropped request left the button dead forever with no hint on
     * screen. The server owns uniqueness — a unique index plus a 409 on claim — which means a check
     * answer can only ever REMOVE permission, never grant it.
     *
     * @param knownUnavailable true only when the server answered about THIS exact handle and said no.
     */
    fun canSubmit(draft: String?, current: String?, knownUnavailable: Boolean): Boolean =
        isWorthChecking(draft) &&
            canonical(draft) != canonical(current) &&
            !knownUnavailable
}

/** Mirrors the server's UsernameRejection so a reason code means the same thing on both sides. */
enum class UsernameRejection { INVALID, RESERVED, TAKEN }
