package com.app.pustakam.core.model.models.share

import kotlinx.serialization.Serializable

@Serializable
data class NoteShareInfo(
    val shareId: String,
    val role: String = NoteRole.READER.name,
    val ownerId: String = "",
    val ownerName: String? = null,
    val authors: Map<String, String> = emptyMap()
) {
    val access: NoteRole get() = NoteAccess.roleOf(role)

    fun canWrite(): Boolean = NoteAccess.canWrite(access)

    fun canShare(): Boolean = NoteAccess.canShare(access)

    fun canDelete(itemId: String, userId: String): Boolean = NoteAccess.canDelete(access, authors[itemId] ?: userId, userId)
}

@Serializable
data class NoteShareMember(
    val userId: String,
    val role: String = NoteRole.READER.name,
    val name: String? = null,
    val username: String? = null,
    val addedAt: String? = null
) {
    val access: NoteRole get() = NoteAccess.roleOf(role)

    fun displayName(): String = name?.takeIf { it.isNotBlank() }
        ?: username?.takeIf { it.isNotBlank() }?.let { "@$it" }
        ?: userId
}

@Serializable
data class NoteShare(
    val shareId: String,
    val noteId: String,
    val sourceNoteId: String = "",
    val ownerId: String = "",
    val ownerName: String? = null,
    val title: String? = null,
    val conversationId: String? = null,
    val role: String? = null,
    val members: List<NoteShareMember> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null
) {
    val access: NoteRole get() = NoteAccess.roleOf(role)
}

@Serializable
data class ShareMemberRequest(val userId: String, val role: String)

@Serializable
data class CreateShareRequest(
    val noteId: String,
    val members: List<ShareMemberRequest> = emptyList(),
    val conversationId: String? = null,
    val role: String? = null
)

@Serializable
data class ShareMemberChange(val userId: String, val role: String? = null)

@Serializable
data class UpdateShareMembersRequest(val changes: List<ShareMemberChange>)

@Serializable
data class StopSharingResult(val shareId: String, val stopped: Boolean = false)

data class NoteAccessGate(val share: NoteShareInfo?, val userId: String) {

    val shared: Boolean get() = share != null

    val readOnly: Boolean get() = share != null && !share.canWrite()

    val canDeleteNote: Boolean get() = share == null || share.access == NoteRole.OWNER

    val canShare: Boolean get() = share == null || share.canShare()

    val sharedBy: String? get() = share?.takeIf { it.ownerId != userId }?.let { it.ownerName ?: "" }

    val label: String? get() = share?.let {
        val by = when {
            it.ownerId == userId -> SHARED_BY_YOU
            it.ownerName.isNullOrBlank() -> SHARED_WITH_YOU
            else -> "$SHARED_BY ${it.ownerName}"
        }
        if (readOnly) "$VIEW_ONLY · $by" else by
    }

    val deleteDenial: String get() = if (readOnly) READ_ONLY_DENIED else DELETE_DENIED

    fun canDelete(itemId: String): Boolean = share == null || share.canDelete(itemId, userId)

    fun canDeleteAll(itemIds: List<String>): Boolean = itemIds.all(::canDelete)

    companion object {
        const val DELETE_DENIED = "Only the person who added this can delete it"
        const val READ_ONLY_DENIED = "You can only view this note"
        const val VIEW_ONLY = "View only"
        const val SHARED_BY = "Shared by"
        const val SHARED_BY_YOU = "Shared by you"
        const val SHARED_WITH_YOU = "Shared with you"

        fun open(userId: String): NoteAccessGate = NoteAccessGate(null, userId)
    }
}
