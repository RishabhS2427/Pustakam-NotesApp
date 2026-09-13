package com.app.pustakam.core.model.models.profile

import com.app.pustakam.core.model.validation.UsernameRejection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 👤 What anyone else may see. Key-for-key the server's toPublicUser — no email, no phone, ever.
 * `:feature:chat` reads the same shape through ChatParticipant, so profiles and chat agree without
 * either feature module depending on the other.
 */
@Serializable
data class PublicUser(
    @SerialName("_id")
    val id: String,
    val username: String? = null,
    val name: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
) {
    fun displayName(): String = name?.takeIf { it.isNotBlank() }
        ?: username?.takeIf { it.isNotBlank() }?.let { "@$it" }
        ?: "Someone"

    fun handle(): String = username?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: ""

    fun initial(): String = displayName().trim().firstOrNull()?.uppercase() ?: "?"
}

/**
 * 📝 A partial update. Every field is nullable and the app's Json sets explicitEnabled = false, so
 * a null field is OMITTED from the body — which is exactly the partial-update semantics
 * updateUserSchema wants. The old code posted a whole User at a .strict() schema and would 422.
 */
@Serializable
data class UpdateProfileReq(
    val name: String? = null,
    val bio: String? = null,
    val discoverable: Boolean? = null,
)

@Serializable
data class SetUsernameReq(val username: String)

/** The answer from /u/check. `reason` is null exactly when `available` is true. */
@Serializable
data class UsernameAvailability(
    val username: String = "",
    val available: Boolean = false,
    val reason: String? = null,
    val suggestions: List<String> = emptyList(),
) {
    fun rejection(): UsernameRejection? =
        reason?.let { code -> UsernameRejection.entries.firstOrNull { it.name == code } }

    /** What the composer shows under the field. */
    fun message(): String = when {
        available -> "Available"
        rejection() == UsernameRejection.TAKEN -> "Already taken"
        rejection() == UsernameRejection.RESERVED -> "Not available"
        rejection() == UsernameRejection.INVALID -> "3–30 letters, numbers or single hyphens"
        else -> ""
    }
}
