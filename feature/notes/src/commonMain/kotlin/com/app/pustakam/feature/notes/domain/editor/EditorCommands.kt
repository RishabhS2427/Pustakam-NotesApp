package com.app.pustakam.feature.notes.domain.editor

import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.model.models.response.notes.Note
import com.app.pustakam.core.model.models.response.notes.NoteContentModel

object EditorCommands {

    // ✍️ 23-Sep-2026 — how long an edit may live only in memory; both platforms tick on this one number
    private const val AUTO_SAVE_MILLIS = 5_000L

    fun autoSaveMillis(): Long = AUTO_SAVE_MILLIS

    fun emptyState(): EditorState = EditorState()

    fun stateOf(
        note: Note?,
        contents: List<NoteContentModel>,
        dirtyContentIds: Set<String>,
        isLoading: Boolean,
        error: String?,
        capabilities: EditorCapabilityState
    ): EditorState = EditorState(
        note = note,
        contents = contents,
        dirtyContentIds = dirtyContentIds,
        isLoading = isLoading,
        error = error,
        capabilities = capabilities
    )

    fun load(noteId: String?): EditorIntent = EditorIntent.Load(noteId)

    fun refresh(): EditorIntent = EditorIntent.Refresh

    fun noteLoaded(note: Note): EditorIntent = EditorIntent.NoteLoaded(note)

    fun loadFailed(message: String): EditorIntent = EditorIntent.LoadFailed(message)

    fun addContent(content: NoteContentModel): EditorIntent = EditorIntent.AddContent(content)

    fun addContents(contents: List<NoteContentModel>): EditorIntent =
        EditorIntent.AddContents(contents)

    fun updateContent(content: NoteContentModel): EditorIntent = EditorIntent.UpdateContent(content)

    fun removeContent(contentId: String): EditorIntent = EditorIntent.RemoveContent(contentId)

    fun thumbnailReady(contentId: String, thumbnailPath: String): EditorIntent =
        EditorIntent.ThumbnailReady(contentId, thumbnailPath)

    fun saveRequested(): EditorIntent = EditorIntent.SaveRequested

    fun saved(note: Note, savedContentIds: Set<String>): EditorIntent =
        EditorIntent.Saved(note, savedContentIds)

    fun captureRequested(type: ContentType): EditorIntent = EditorIntent.CaptureRequested(type)

    fun permissionGranted(): EditorIntent = EditorIntent.PermissionGranted

    fun permissionDenied(type: ContentType): EditorIntent = EditorIntent.PermissionDenied(type)

    fun captureFinished(): EditorIntent = EditorIntent.CaptureFinished

    fun showImportSheet(visible: Boolean): EditorIntent = EditorIntent.ShowImportSheet(visible)

    fun showAttachSheet(visible: Boolean): EditorIntent = EditorIntent.ShowAttachSheet(visible)

    fun askDeleteContent(contentId: String): EditorIntent = EditorIntent.AskDeleteContent(contentId)

    fun askDeleteNote(): EditorIntent = EditorIntent.AskDeleteNote

    fun dismissDelete(): EditorIntent = EditorIntent.DismissDelete

    fun externalContentsChanged(contents: List<NoteContentModel>): EditorIntent =
        EditorIntent.ExternalContentsChanged(contents)

    fun clearError(): EditorIntent = EditorIntent.ClearError

    fun reduce(state: EditorState, intent: EditorIntent): EditorState =
        EditorReducer.reduce(state, intent)

    fun effects(
        before: EditorState,
        next: EditorState,
        intent: EditorIntent
    ): List<EditorEffect> = EditorReducer.effects(before, next, intent)

    fun readNoteEffect(effect: EditorEffect): EditorEffect.ReadNote? =
        effect as? EditorEffect.ReadNote

    fun saveNoteEffect(effect: EditorEffect): EditorEffect.SaveNote? =
        effect as? EditorEffect.SaveNote

    fun deleteContentRowEffect(effect: EditorEffect): EditorEffect.DeleteContentRow? =
        effect as? EditorEffect.DeleteContentRow

    fun deleteFilesEffect(effect: EditorEffect): EditorEffect.DeleteFiles? =
        effect as? EditorEffect.DeleteFiles

    fun makeThumbnailEffect(effect: EditorEffect): EditorEffect.MakeThumbnail? =
        effect as? EditorEffect.MakeThumbnail

    fun publishMediaEffect(effect: EditorEffect): EditorEffect.PublishMedia? =
        effect as? EditorEffect.PublishMedia

    fun openCameraEffect(effect: EditorEffect): EditorEffect.OpenCamera? =
        effect as? EditorEffect.OpenCamera

    fun isStartRecorder(effect: EditorEffect): Boolean = effect == EditorEffect.StartRecorder

    fun isOpenImportPicker(effect: EditorEffect): Boolean = effect == EditorEffect.OpenImportPicker
}
