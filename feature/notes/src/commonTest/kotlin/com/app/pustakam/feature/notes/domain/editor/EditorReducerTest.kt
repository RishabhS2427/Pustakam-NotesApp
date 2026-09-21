package com.app.pustakam.feature.notes.domain.editor

import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.model.models.response.notes.Note
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorReducerTest {

    private fun note(id: String = "note-1") = Note(
        id = id, title = "", updatedAt = null, createdAt = null, contents = emptyList()
    )

    private fun text(id: String, position: Double = 0.0) = NoteContentModel.TextContent(
        id = id, noteId = "note-1", text = "", position = position,
        updatedAt = null, createdAt = null
    )

    private fun media(
        id: String,
        type: ContentType = ContentType.IMAGE,
        localPath: String? = "/tmp/$id.png",
        thumbnailPath: String? = null
    ) = NoteContentModel.MediaContent(
        id = id, noteId = "note-1", url = "", position = 0.0, type = type,
        localPath = localPath, thumbnailPath = thumbnailPath,
        updatedAt = null, createdAt = null
    )

    private fun loaded(vararg contents: NoteContentModel): EditorState =
        EditorReducer.reduce(EditorState(), EditorIntent.NoteLoaded(note()))
            .let { base ->
                contents.fold(base) { acc, c ->
                    EditorReducer.reduce(acc, EditorIntent.AddContent(c))
                }
            }

    @Test
    fun noteLoaded_clearsLoadingAndError() {
        val failed = EditorReducer.reduce(EditorState(), EditorIntent.LoadFailed("boom"))
        assertEquals("boom", failed.error)

        val next = EditorReducer.reduce(failed, EditorIntent.NoteLoaded(note()))
        assertNull(next.error)
        assertEquals(false, next.isLoading)
        assertEquals("note-1", next.noteId)
    }

    @Test
    fun noteLoaded_keepsUnsavedContents() {
        val dirty = loaded(text("t1"))
        val reloaded = EditorReducer.reduce(dirty, EditorIntent.NoteLoaded(note()))
        assertEquals(1, reloaded.contents.size)
    }

    @Test
    fun addContent_marksDirty_andUpdateReplacesInPlace() {
        val state = loaded(text("t1"))
        assertTrue(state.dirtyContentIds.contains("t1"))

        val edited = EditorReducer.reduce(
            state,
            EditorIntent.UpdateContent(text("t1").withText("hello"))
        )
        assertEquals(1, edited.contents.size)
        assertEquals("hello", (edited.contents.first() as NoteContentModel.TextContent).text)
    }

    @Test
    fun removeContent_dropsContentAndDirtyFlag() {
        val state = loaded(text("t1"), text("t2"))
        val next = EditorReducer.reduce(state, EditorIntent.RemoveContent("t1"))
        assertEquals(1, next.contents.size)
        assertEquals(false, next.dirtyContentIds.contains("t1"))
    }

    @Test
    fun removeContent_emitsRowDeleteAndFileCleanup() {
        val state = loaded(media("m1", thumbnailPath = "/tmp/m1-thumb.png"))
        val intent = EditorIntent.RemoveContent("m1")
        val next = EditorReducer.reduce(state, intent)
        val effects = EditorReducer.effects(state, next, intent)

        assertTrue(effects.any { it is EditorEffect.DeleteContentRow })
        val files = effects.filterIsInstance<EditorEffect.DeleteFiles>().single()
        assertEquals(listOf("/tmp/m1.png", "/tmp/m1-thumb.png"), files.paths)
    }

    @Test
    fun saved_clearsOnlyTheSavedIds() {
        val state = loaded(text("t1"), text("t2"))
        val next = EditorReducer.reduce(
            state,
            EditorIntent.Saved(note(), setOf("t1"))
        )
        assertEquals(setOf("t2"), next.dirtyContentIds)
    }

    @Test
    fun addingMedia_requestsThumbnailOnlyWhenMissing() {
        val state = EditorReducer.reduce(EditorState(), EditorIntent.NoteLoaded(note()))

        val fresh = EditorIntent.AddContent(media("m1"))
        val afterFresh = EditorReducer.reduce(state, fresh)
        assertEquals(1, EditorReducer.effects(state, afterFresh, fresh)
            .filterIsInstance<EditorEffect.MakeThumbnail>().size)

        val already = EditorIntent.AddContent(media("m2", thumbnailPath = "/tmp/m2-thumb.png"))
        val afterAlready = EditorReducer.reduce(state, already)
        assertTrue(EditorReducer.effects(state, afterAlready, already)
            .filterIsInstance<EditorEffect.MakeThumbnail>().isEmpty())
    }

    @Test
    fun capture_permissionGranted_opensCameraForImage() {
        val state = EditorReducer.reduce(
            EditorReducer.reduce(EditorState(), EditorIntent.NoteLoaded(note())),
            EditorIntent.CaptureRequested(ContentType.IMAGE)
        )
        assertTrue(state.capabilities.isPermissionPromptVisible)

        val intent = EditorIntent.PermissionGranted
        val next = EditorReducer.reduce(state, intent)
        val effects = EditorReducer.effects(state, next, intent)
        assertTrue(effects.any { it is EditorEffect.OpenCamera })
    }

    @Test
    fun capture_permissionGranted_startsRecorderForAudio() {
        val state = EditorReducer.reduce(
            EditorReducer.reduce(EditorState(), EditorIntent.NoteLoaded(note())),
            EditorIntent.CaptureRequested(ContentType.AUDIO)
        )
        val intent = EditorIntent.PermissionGranted
        val next = EditorReducer.reduce(state, intent)

        assertTrue(next.capabilities.isRecordingAudio)
        assertTrue(EditorReducer.effects(state, next, intent).contains(EditorEffect.StartRecorder))
    }

    @Test
    fun permissionDenied_surfacesAnError() {
        val state = EditorReducer.reduce(EditorState(), EditorIntent.CaptureRequested(ContentType.AUDIO))
        val next = EditorReducer.reduce(state, EditorIntent.PermissionDenied(ContentType.AUDIO))
        assertTrue(next.error?.isNotEmpty() == true)
        assertNull(next.capabilities.pendingCapture)
    }

    @Test
    fun externalChange_appliesWhenNothingIsDirty() {
        val clean = EditorReducer.reduce(
            loaded(text("t1")),
            EditorIntent.Saved(note(), setOf("t1"))
        )
        val incoming = listOf(text("t1").withText("from the other editor"))
        val merged = EditorReducer.reduce(clean, EditorIntent.ExternalContentsChanged(incoming))

        assertEquals(
            "from the other editor",
            (merged.contents.first() as NoteContentModel.TextContent).text
        )
    }

    @Test
    fun externalChange_neverOverwritesAContentBeingEdited() {
        val dirty = loaded(text("t1"))
        val incoming = listOf(text("t1").withText("stale remote copy"))
        val merged = EditorReducer.reduce(dirty, EditorIntent.ExternalContentsChanged(incoming))

        assertEquals("", (merged.contents.first() as NoteContentModel.TextContent).text)
    }

    @Test
    fun externalChange_addsBlocksCreatedInTheOtherEditor() {
        val clean = EditorReducer.reduce(EditorState(), EditorIntent.NoteLoaded(note()))
        val incoming = listOf(text("t1"), media("m1"))
        val merged = EditorReducer.reduce(clean, EditorIntent.ExternalContentsChanged(incoming))

        assertEquals(2, merged.contents.size)
        assertTrue(merged.dirtyContentIds.isEmpty())
    }

    @Test
    fun externalChange_removesBlocksDeletedElsewhere_butKeepsUnsavedOnes() {
        val state = EditorReducer.reduce(
            loaded(text("t1"), text("t2")),
            EditorIntent.Saved(note(), setOf("t1"))
        )
        val merged = EditorReducer.reduce(
            state,
            EditorIntent.ExternalContentsChanged(emptyList())
        )

        assertEquals(listOf("t2"), merged.contents.map { it.id })
    }

    @Test
    fun externalChange_thatMatchesLocalStateIsANoOp() {
        val clean = EditorReducer.reduce(
            loaded(text("t1")),
            EditorIntent.Saved(note(), setOf("t1"))
        )
        val merged = EditorReducer.reduce(
            clean,
            EditorIntent.ExternalContentsChanged(clean.contents)
        )

        assertTrue(merged === clean)
    }

    // 🎧 a download landing on an open note must reach the player, or its play button opens nothing
    @Test
    fun externalChange_publishesAPlayableFileThatJustLanded() {
        val waiting = media("a1", ContentType.AUDIO, localPath = null)
        val clean = EditorReducer.reduce(loaded(waiting), EditorIntent.Saved(note(), setOf("a1")))
        val intent = EditorIntent.ExternalContentsChanged(listOf(waiting.copy(localPath = "/files/a1.m4a")))
        val next = EditorReducer.reduce(clean, intent)

        val published = EditorReducer.effects(clean, next, intent).filterIsInstance<EditorEffect.PublishMedia>()
        assertEquals(listOf("/files/a1.m4a"), published.map { it.content.localPath })
    }

    @Test
    fun externalChange_withoutANewFileDoesNotRepublish() {
        val clean = EditorReducer.reduce(loaded(media("a1", ContentType.AUDIO)), EditorIntent.Saved(note(), setOf("a1")))
        val intent = EditorIntent.ExternalContentsChanged(clean.contents)
        assertTrue(EditorReducer.effects(clean, clean, intent).isEmpty())
    }

    @Test
    fun refresh_isIgnoredWhileEditsAreUnsaved() {
        val dirty = loaded(text("t1"))
        assertTrue(EditorReducer.effects(dirty, dirty, EditorIntent.Refresh).isEmpty())

        val clean = EditorReducer.reduce(dirty, EditorIntent.Saved(note(), setOf("t1")))
        assertTrue(
            EditorReducer.effects(clean, clean, EditorIntent.Refresh)
                .any { it is EditorEffect.ReadNote }
        )
    }
}
