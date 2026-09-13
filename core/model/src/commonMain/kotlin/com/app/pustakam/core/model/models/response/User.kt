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
)

/** 🆔 true while the username was assigned by the server and never chosen. */
fun User.needsUsername(): Boolean = username.isNullOrBlank() || usernameUpdatedAt == null

fun User.handle(): String = username?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: ""

fun User.displayName(): String = name?.takeIf { it.isNotBlank() }
    ?: username?.takeIf { it.isNotBlank() }
    ?: "You"

fun User.initial(): String = displayName().trim().firstOrNull()?.uppercase() ?: "?"
