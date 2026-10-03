package com.app.pustakam.feature.notes.domain.bridge

import com.app.pustakam.core.common.bridge.BridgeError
import com.app.pustakam.core.common.bridge.Closeable
import com.app.pustakam.core.data.bridge.subscribeTo
import com.app.pustakam.core.model.models.share.NoteAccessGate
import com.app.pustakam.core.model.models.share.NoteShare
import com.app.pustakam.core.model.models.share.NoteShareInfo
import com.app.pustakam.core.model.models.share.ShareMemberChange
import com.app.pustakam.core.model.models.share.ShareMemberRequest
import com.app.pustakam.core.model.models.share.StopSharingResult
import com.app.pustakam.feature.notes.domain.usecase.ChangeShareMembersUseCase
import com.app.pustakam.feature.notes.domain.usecase.ListNoteSharesUseCase
import com.app.pustakam.feature.notes.domain.usecase.OpenSharedNoteUseCase
import com.app.pustakam.feature.notes.domain.usecase.ReadNoteAccessUseCase
import com.app.pustakam.feature.notes.domain.usecase.ReadNoteShareUseCase
import com.app.pustakam.feature.notes.domain.usecase.ShareNoteUseCase
import com.app.pustakam.feature.notes.domain.usecase.SharedNoteIdsUseCase
import com.app.pustakam.feature.notes.domain.usecase.StopSharingUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ShareBridge : KoinComponent {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private companion object {
        private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    private val shareNote: ShareNoteUseCase by inject()
    private val listShares: ListNoteSharesUseCase by inject()
    private val changeShareMembers: ChangeShareMembersUseCase by inject()
    private val stopSharing: StopSharingUseCase by inject()
    private val readShare: ReadNoteShareUseCase by inject()
    private val sharedIds: SharedNoteIdsUseCase by inject()
    private val readAccess: ReadNoteAccessUseCase by inject()
    private val openShared: OpenSharedNoteUseCase by inject()

    fun share(
        noteId: String,
        members: List<ShareMemberRequest>,
        conversationId: String?,
        role: String?,
        onLoading: () -> Unit,
        onSuccess: (NoteShare?) -> Unit,
        onError: (BridgeError) -> Unit,
    ): Closeable = subscribeTo(writeScope, { shareNote(noteId, members, conversationId, role) }, onLoading, onSuccess, onError)

    fun shares(
        onLoading: () -> Unit,
        onSuccess: (List<NoteShare>?) -> Unit,
        onError: (BridgeError) -> Unit,
    ): Closeable = subscribeTo(scope, { listShares() }, onLoading, onSuccess, onError)

    fun changeMembers(
        shareId: String,
        changes: List<ShareMemberChange>,
        onLoading: () -> Unit,
        onSuccess: (NoteShare?) -> Unit,
        onError: (BridgeError) -> Unit,
    ): Closeable = subscribeTo(writeScope, { changeShareMembers(shareId, changes) }, onLoading, onSuccess, onError)

    fun stop(
        shareId: String,
        onLoading: () -> Unit,
        onSuccess: (StopSharingResult?) -> Unit,
        onError: (BridgeError) -> Unit,
    ): Closeable = subscribeTo(writeScope, { stopSharing(shareId) }, onLoading, onSuccess, onError)

    fun shareOf(noteId: String): NoteShareInfo? = readShare(noteId)

    fun sharedNoteIds(): Set<String> = sharedIds()

    fun accessOf(noteId: String?): NoteAccessGate = readAccess(noteId)

    fun ensureLocal(noteId: String, onResult: (Boolean) -> Unit) {
        scope.launch { onResult(openShared(noteId)) }
    }

    fun dispose() = scope.cancel()
}
