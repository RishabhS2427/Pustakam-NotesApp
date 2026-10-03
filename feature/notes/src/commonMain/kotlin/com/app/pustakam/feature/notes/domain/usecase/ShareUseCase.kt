package com.app.pustakam.feature.notes.domain.usecase

import com.app.pustakam.core.data.usecases.BaseUseCase
import com.app.pustakam.core.model.models.share.NoteAccessGate
import com.app.pustakam.core.model.models.share.NoteShareInfo
import com.app.pustakam.core.model.models.share.ShareMemberChange
import com.app.pustakam.core.model.models.share.ShareMemberRequest
import com.app.pustakam.feature.notes.domain.repository.IShareRepository
import org.koin.core.component.inject

abstract class ShareBaseUseCase : BaseUseCase() {
    protected val shares: IShareRepository by inject()
}

class ShareNoteUseCase : ShareBaseUseCase() {
    suspend operator fun invoke(noteId: String, members: List<ShareMemberRequest>, conversationId: String?, role: String?) =
        getBaseApiCall { shares.share(noteId, members, conversationId, role) }
}

class ListNoteSharesUseCase : ShareBaseUseCase() {
    suspend operator fun invoke() = getBaseApiCall { shares.shares() }
}

class ChangeShareMembersUseCase : ShareBaseUseCase() {
    suspend operator fun invoke(shareId: String, changes: List<ShareMemberChange>) =
        getBaseApiCall { shares.changeMembers(shareId, changes) }
}

class StopSharingUseCase : ShareBaseUseCase() {
    suspend operator fun invoke(shareId: String) = getBaseApiCall { shares.stop(shareId) }
}

class ReadNoteShareUseCase : ShareBaseUseCase() {
    operator fun invoke(noteId: String): NoteShareInfo? = shares.shareOf(noteId)
}

class ReadNoteAccessUseCase : ShareBaseUseCase() {
    operator fun invoke(noteId: String?): NoteAccessGate = shares.accessOf(noteId)
}

class OpenSharedNoteUseCase : ShareBaseUseCase() {
    suspend operator fun invoke(noteId: String): Boolean = shares.ensureLocal(noteId)
}

class SharedNoteIdsUseCase : ShareBaseUseCase() {
    operator fun invoke(): Set<String> = shares.sharedNoteIds()
}
