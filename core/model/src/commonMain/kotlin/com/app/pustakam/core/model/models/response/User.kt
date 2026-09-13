package com.app.pustakam.core.model.models.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class User(
    val _id : String?,
    val username : String? = null,
    val usernameUpdatedAt : Long? = null,
    val bio : String? = null,
    val discoverable : Boolean = true,
    val name : String?,
    val phone : String?,
    val email : String?,
    @SerialName("avatarUrl")
    val avatarUrl : String?,
    @SerialName("createdAt")
    val createdAt : String?,
    @SerialName("updateAt")
    val updatedAt : String?,
    // 🔐 20-Aug-2026 sync: /login, /register and /auth/refresh spread both tokens into `data`
    val accessToken : String? = null,
    val refreshToken : String? = null,
) {
    // 🔧 05-Sep-2026 — MEMBERS, not top-level extensions. Kotlin/Native exports an extension on an
    //   exported class as an ObjC CATEGORY method, so no `UserKt` file class is generated and Swift
    //   fails with "Cannot find 'UserKt' in scope". Members export as plain methods and are what
    //   PublicUser and ChatParticipant already do — one shape for all three.

    /** 🆔 true while the username was assigned by the server and never chosen. */
    fun needsUsername(): Boolean = username.isNullOrBlank() || usernameUpdatedAt == null

    fun handle(): String = username?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: ""

    fun displayName(): String = name?.takeIf { it.isNotBlank() }
        ?: username?.takeIf { it.isNotBlank() }
        ?: "You"

    fun initial(): String = displayName().trim().firstOrNull()?.uppercase() ?: "?"
}
