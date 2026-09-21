package com.app.pustakam.feature.notes.domain.editor

import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.model.models.response.notes.NoteContentModel

object EditorReducer {

    fun reduce(state: EditorState, intent: EditorIntent): EditorState = when (intent) {

        is EditorIntent.Load -> state.copy(isLoading = true, error = null)

        EditorIntent.Refresh -> state

        EditorIntent.LoadStarted -> state.copy(isLoading = true)

        is EditorIntent.NoteLoaded -> state.copy(
            note = intent.note,
            contents = if (state.hasUnsavedContent) state.contents else intent.note.contents,
            isLoading = false,
            error = null
        )

        is EditorIntent.LoadFailed -> state.copy(isLoading = false, error = intent.message)

        is EditorIntent.AddContent -> state.withContents(listOf(intent.content))

        is EditorIntent.AddContents -> state.withContents(intent.contents)

        is EditorIntent.UpdateContent -> state.withContents(listOf(intent.content))

        is EditorIntent.RemoveContent -> state.copy(
            contents = state.contents.filterNot { it.id == intent.contentId },
            dirtyContentIds = state.dirtyContentIds - intent.contentId
        )

        is EditorIntent.ThumbnailReady -> {
            val media = state.mediaById(intent.contentId)
            if (media == null) state
            else state.withContents(listOf(media.withThumbnail(intent.thumbnailPath)))
        }

        EditorIntent.SaveRequested -> state

        is EditorIntent.Saved -> state.copy(
            note = intent.note,
            isLoading = false,
            error = null,
            dirtyContentIds = state.dirtyContentIds - intent.savedContentIds
        )

        is EditorIntent.CaptureRequested -> state.copy(
            capabilities = EditorCapabilityReducer.requestCapture(state.capabilities, intent.type)
        )

        EditorIntent.PermissionGranted -> state.copy(
            capabilities = EditorCapabilityReducer.granted(state.capabilities)
        )

        is EditorIntent.PermissionDenied -> state.copy(
            capabilities = EditorCapabilityReducer.denied(state.capabilities),
            error = "Allow ${intent.type.name.lowercase()} access in Settings to capture here."
        )

        EditorIntent.CaptureFinished -> state.copy(
            capabilities = EditorCapabilityReducer.captureFinished(state.capabilities)
        )

        is EditorIntent.ShowImportSheet -> state.copy(
            capabilities = EditorCapabilityReducer.setImportSheet(state.capabilities, intent.visible)
        )

        is EditorIntent.ShowAttachSheet -> state.copy(
            capabilities = EditorCapabilityReducer.setAttachSheet(state.capabilities, intent.visible)
        )

        is EditorIntent.AskDeleteContent -> state.copy(
            capabilities = EditorCapabilityReducer.askDeleteContent(
                state.capabilities,
                intent.contentId
            )
        )

        EditorIntent.AskDeleteNote -> state.copy(
            capabilities = EditorCapabilityReducer.askDeleteNote(state.capabilities)
        )

        EditorIntent.DismissDelete -> state.copy(
            capabilities = EditorCapabilityReducer.dismissDelete(state.capabilities)
        )

        is EditorIntent.ExternalContentsChanged -> state.merging(intent.contents)

        EditorIntent.ClearError -> state.copy(error = null)
    }

    fun effects(
        before: EditorState,
        next: EditorState,
        intent: EditorIntent
    ): List<EditorEffect> = when (intent) {

        is EditorIntent.Load -> listOf(EditorEffect.ReadNote(intent.noteId))

        EditorIntent.Refresh ->
            if (before.hasUnsavedContent) emptyList()
            else before.noteId
                ?.takeIf { it.isNotEmpty() }
                ?.let { listOf(EditorEffect.ReadNote(it)) }
                .orEmpty()

        is EditorIntent.AddContent -> saveWithMedia(next, listOf(intent.content))

        is EditorIntent.AddContents -> saveWithMedia(next, intent.contents)

        is EditorIntent.UpdateContent -> saveWithMedia(next, listOf(intent.content))

        is EditorIntent.ThumbnailReady -> saveOnly(next)

        is EditorIntent.RemoveContent -> {
            val removed = before.contentById(intent.contentId)
            val media = removed as? NoteContentModel.MediaContent
            val paths = listOfNotNull(media?.localPath, media?.thumbnailPath)
                .filter { it.isNotEmpty() }
            listOf(EditorEffect.DeleteContentRow(intent.contentId)) +
                (if (paths.isEmpty()) emptyList() else listOf(EditorEffect.DeleteFiles(paths))) +
                saveOnly(next)
        }

        EditorIntent.SaveRequested -> saveOnly(next)

        EditorIntent.PermissionGranted -> {
            val pending = before.capabilities.pendingCapture
            val noteId = next.noteId
            when {
                pending == null || noteId == null -> emptyList()
                EditorCapabilityReducer.opensCamera(pending) ->
                    listOf(EditorEffect.OpenCamera(noteId, pending))

                pending == ContentType.AUDIO -> listOf(EditorEffect.StartRecorder)
                else -> emptyList()
            }
        }

        is EditorIntent.ShowImportSheet ->
            if (intent.visible) listOf(EditorEffect.OpenImportPicker) else emptyList()

        // 🎧 21-Sep-2026 — a synced file landed while the note is open: hand the player its new path
        is EditorIntent.ExternalContentsChanged -> landedPlayableMedia(before, next)

        else -> emptyList()
    }

    // 🎧 audio/video whose local file just appeared — the player still holds the old empty path, so play did nothing
    private fun landedPlayableMedia(before: EditorState, next: EditorState): List<EditorEffect> =
        next.mediaContents
            .filter { it.isPlayableMedia() && !it.localPath.isNullOrEmpty() }
            .filter { before.mediaById(it.id)?.localPath != it.localPath }
            .map { EditorEffect.PublishMedia(it) }

    private fun saveOnly(state: EditorState): List<EditorEffect> =
        state.noteWithContents()
            ?.let { listOf(EditorEffect.SaveNote(it, state.dirtyContentIds)) }
            .orEmpty()

    private fun saveWithMedia(
        state: EditorState,
        touched: List<NoteContentModel>
    ): List<EditorEffect> {
        val media = touched.filterIsInstance<NoteContentModel.MediaContent>()
        val thumbnails = media
            .filter { needsThumbnail(it) }
            .map { EditorEffect.MakeThumbnail(it) }
        val published = media
            .filter { it.isPlayableMedia() }
            .map { EditorEffect.PublishMedia(it) }
        return saveOnly(state) + published + thumbnails
    }

    fun needsThumbnail(media: NoteContentModel.MediaContent): Boolean =
        media.thumbnailPath.isNullOrEmpty() &&
            !media.localPath.isNullOrEmpty() &&
            (media.type == ContentType.IMAGE ||
                media.type == ContentType.VIDEO ||
                media.type == ContentType.GIF)
}

private fun EditorState.merging(incoming: List<NoteContentModel>): EditorState {
    val known = contents.associateBy { it.id }
    val accepted = incoming.filterNot { it.id in dirtyContentIds }
    val changed = accepted.filter { known[it.id] != it }
    val removedIds = known.keys - incoming.map { it.id }.toSet() - dirtyContentIds
    if (changed.isEmpty() && removedIds.isEmpty()) return this

    val merged = contents.filterNot { it.id in removedIds }.toMutableList()
    changed.forEach { content ->
        val index = merged.indexOfFirst { it.id == content.id }
        if (index == -1) merged.add(content) else merged[index] = content
    }
    return copy(contents = merged)
}

private fun EditorState.withContents(touched: List<NoteContentModel>): EditorState {
    if (touched.isEmpty()) return this
    val merged = contents.toMutableList()
    touched.forEach { content ->
        val index = merged.indexOfFirst { it.id == content.id }
        if (index == -1) merged.add(content) else merged[index] = content
    }
    return copy(
        contents = merged,
        dirtyContentIds = dirtyContentIds + touched.map { it.id }
    )
}
