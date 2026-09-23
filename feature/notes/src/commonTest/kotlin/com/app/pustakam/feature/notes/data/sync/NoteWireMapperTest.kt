package com.app.pustakam.feature.notes.data.sync

import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.model.models.response.notes.Note
import com.app.pustakam.core.model.models.response.notes.NoteCanvasNode
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SYNCED = "SYNCED"

class NoteWireMapperTest {

    private fun note(
        id: String = "note-1",
        syncStatus: String = SYNCED,
        contents: List<NoteContentModel> = emptyList(),
    ) = Note(
        id = id, title = "t", updatedAt = "100", createdAt = "1",
        syncStatus = syncStatus, contents = contents,
    )

    private fun media(
        id: String = "media-1",
        localPath: String? = "/data/user/0/com.app.pustakam/files/image/note-1/1.png",
        thumbnailPath: String? = "/data/user/0/com.app.pustakam/files/thumbnails/1_thumb.jpg",
        assetId: String? = null,
    ) = NoteContentModel.MediaContent(
        id = id, noteId = "note-1", url = "", position = 0.0, type = ContentType.IMAGE,
        localPath = localPath, thumbnailPath = thumbnailPath, assetId = assetId,
        updatedAt = "100", createdAt = "1",
    )

    private fun text(id: String = "text-1") = NoteContentModel.TextContent(
        id = id, noteId = "note-1", text = "hello", position = 1.0,
        updatedAt = "100", createdAt = "1",
    )

    @Test
    fun toWire_stripsDevicePathsFromMedia() {
        val wire = NoteWireMapper.toWire(note(contents = listOf(media(), text())))
        val outbound = wire.contents.filterIsInstance<NoteContentModel.MediaContent>().single()

        assertNull(outbound.localPath, "localPath is a path on THIS device and must not be sent")
        assertNull(outbound.thumbnailPath)
    }

    @Test
    fun toWire_keepsEverythingElseIntact() {
        val original = note(contents = listOf(media(assetId = "asset-9"), text()))
        val wire = NoteWireMapper.toWire(original)
        val outbound = wire.contents.filterIsInstance<NoteContentModel.MediaContent>().single()

        assertEquals(original.id, wire.id)
        assertEquals(original.updatedAt, wire.updatedAt)
        assertEquals(2, wire.contents.size)
        assertEquals("asset-9", outbound.assetId)
        assertEquals("hello", wire.contents.filterIsInstance<NoteContentModel.TextContent>().single().text)
    }

    @Test
    fun toWire_dropsLocalOnlyBookkeeping() {
        val wire = NoteWireMapper.toWire(
            note().copy(deletedAt = "999", serverUpdatedAt = 12345L)
        )
        assertNull(wire.deletedAt)
        assertNull(wire.serverUpdatedAt)
    }

    @Test
    fun toWire_leavesTheSourceNoteAlone() {
        val original = note(contents = listOf(media()))
        NoteWireMapper.toWire(original)
        val stillThere = original.contents.filterIsInstance<NoteContentModel.MediaContent>().single()

        assertEquals("/data/user/0/com.app.pustakam/files/image/note-1/1.png", stillThere.localPath)
    }

    @Test
    fun mergeLocalMedia_restoresPathsThePullCouldNotKnow() {
        val local = note(contents = listOf(media()))
        val incoming = note(contents = listOf(media(localPath = null, thumbnailPath = null, assetId = "asset-9")))

        val merged = NoteWireMapper.mergeLocalMedia(incoming, local)
        val content = merged.contents.filterIsInstance<NoteContentModel.MediaContent>().single()

        assertEquals("/data/user/0/com.app.pustakam/files/image/note-1/1.png", content.localPath)
        assertEquals("/data/user/0/com.app.pustakam/files/thumbnails/1_thumb.jpg", content.thumbnailPath)
        assertEquals("asset-9", content.assetId, "the server's asset id must survive the merge")
    }

    @Test
    fun mergeLocalMedia_doesNotInventPathsForContentThisDeviceNeverHad() {
        val local = note(contents = listOf(media(id = "media-1")))
        val incoming = note(contents = listOf(media(id = "media-2", localPath = null, thumbnailPath = null)))

        val content = NoteWireMapper.mergeLocalMedia(incoming, local)
            .contents.filterIsInstance<NoteContentModel.MediaContent>().single()

        assertNull(content.localPath, "media-2 has never been on this device")
    }

    @Test
    fun mergeLocalMedia_survivesAFirstTimeNote() {
        val incoming = note(contents = listOf(media(localPath = null)))
        assertEquals(incoming, NoteWireMapper.mergeLocalMedia(incoming, local = null))
    }

    @Test
    fun canApplyOverLocal_refusesToClobberAnUnpushedEdit() {
        assertTrue(NoteWireMapper.canApplyOverLocal(null, SYNCED), "a note we have never seen always applies")
        assertTrue(NoteWireMapper.canApplyOverLocal(note(syncStatus = SYNCED), SYNCED))
        assertFalse(
            NoteWireMapper.canApplyOverLocal(note(syncStatus = "PENDING"), SYNCED),
            "a local edit that has not reached the server yet must not be overwritten by a pull"
        )
        assertFalse(NoteWireMapper.canApplyOverLocal(note(syncStatus = "PENDING_DELETE"), SYNCED))
    }

    // ---- 24-Sep-2026: the master editor's layout travels inside its note ----

    private val wireJson = Json {
        explicitNulls = false
        ignoreUnknownKeys = true
        isLenient = true
        classDiscriminator = "type"
        coerceInputValues = true
    }

    private fun canvasNode(id: String, role: String, parentId: String? = null) = NoteCanvasNode(
        id = id, kind = "IMAGE", role = role, parentId = parentId,
        x = 8f, y = 8f, width = 240f, height = 160f, slotOrder = 1.5, pageOrder = 0.0,
    )

    @Test
    fun toWire_keepsTheCanvasSoTheLayoutTravels() {
        val canvas = listOf(canvasNode("p1", "PAGE"), canvasNode("w1", "WIDGET", "p1"))
        assertEquals(canvas, NoteWireMapper.toWire(note().copy(canvas = canvas)).canvas)
    }

    @Test
    fun aNoteWithoutACanvasSendsNoCanvasKey() {
        val json = wireJson.encodeToString(Note.serializer(), NoteWireMapper.toWire(note()))
        assertFalse("\"canvas\"" in json, "absent means this copy carries no layout, so the server keeps its own")
    }

    @Test
    fun theCanvasSurvivesTheWireBothWays() {
        val canvas = listOf(canvasNode("p1", "PAGE"), canvasNode("w1", "WIDGET", "p1"))
        val json = wireJson.encodeToString(Note.serializer(), NoteWireMapper.toWire(note().copy(canvas = canvas)))
        assertEquals(canvas, wireJson.decodeFromString(Note.serializer(), json).canvas)
    }

    @Test
    fun aServerCanvasNodeWithFieldsThisBuildDoesNotKnowStillReads() {
        val json = """{"_id":"note-1","title":"t","updatedAt":"100","createdAt":"1","contents":[],
            "canvas":[{"id":"p1","kind":"TEXT","role":"PAGE","x":0,"y":0,"width":360,"height":640,"extra":true}]}"""
        val canvas = wireJson.decodeFromString(Note.serializer(), json).canvas
        assertEquals("p1", canvas?.single()?.id)
        assertEquals(360f, canvas?.single()?.width)
    }

    @Test
    fun mergeLocalMedia_keepsThePulledCanvas() {
        val canvas = listOf(canvasNode("p1", "PAGE"))
        val merged = NoteWireMapper.mergeLocalMedia(note().copy(canvas = canvas), note(contents = listOf(media())))
        assertEquals(canvas, merged.canvas)
    }
}
