package com.app.pustakam.core.model.models.share

import kotlinx.serialization.Serializable

@Serializable
enum class NoteRole { READER, EDITOR, OWNER }

object NoteAccess {

    fun canRead(role: NoteRole): Boolean = true

    fun canWrite(role: NoteRole): Boolean = role != NoteRole.READER

    fun canDelete(role: NoteRole, createdBy: String?, userId: String): Boolean = when (role) {
        NoteRole.OWNER -> true
        NoteRole.EDITOR -> !createdBy.isNullOrEmpty() && createdBy == userId
        NoteRole.READER -> false
    }

    fun canShare(role: NoteRole): Boolean = role == NoteRole.OWNER

    fun roleOf(name: String?): NoteRole = NoteRole.entries.firstOrNull { it.name == name } ?: NoteRole.READER
}
