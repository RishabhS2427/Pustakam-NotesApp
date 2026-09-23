// 🆔 20-Sep-2026 — @JsonNames below needs this opt-in.
@file:OptIn(ExperimentalSerializationApi::class)

package com.app.pustakam.core.model.models.response.notes

import com.app.pustakam.core.model.models.RichTextMetadata
import com.app.pustakam.core.model.models.Version
import com.app.pustakam.core.common.util.ContentType
// 🔧 C6: UniqueIdGenerator import moved out — id generation lives ONLY in NoteContentObjectHelper
// 🔧 getCurrentTimestamp kept: withX() helpers stamp updatedAt on every edit
import com.app.pustakam.core.common.util.getCurrentTimestamp
import com.app.pustakam.core.common.util.isPlayableMedia
import com.app.pustakam.core.common.util.resolveLocalFilePath // 🔧 15-Jul-2026 iOS MEDIA-LOST FIX
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
enum class SyncStatus { LOCAL_ONLY, SYNCED, PENDING_UPDATE, PENDING_DELETE }

@Serializable
data class Note(
    @SerialName("_id")
    val id: String,
    val title: String?,
    val updates: List<String>? = null,
    val updatedAt: String?,
    val createdAt: String?,
    val categoryId: String? ="",
    val isSynced : Boolean? = false,
    val ownerId: String? = null,
    val version: String = "",
    val syncStatus: String = "PENDING",
    val deleted: Boolean = false,
    val deletedAt: String? = null,
    val serverUpdatedAt: Long? = null,
    val contents: List<NoteContentModel> = emptyList(),
    // 🔄 24-Sep-2026 — the master editor's pages and widgets; null means "this copy carries no layout", never "no layout"
    val canvas: List<NoteCanvasNode>? = null,
    ) {
    /** Swift-friendly copy helpers — Kotlin data-class copy() does not export
     *  usable default arguments to Swift, so immutable edits go through these.
     *  🔧 every edit stamps updatedAt = getCurrentTimestamp() → "last updated" is always current */
    fun withNextVersion () : String = Version.nextVersion(ownerId, version)
    // 🔧 20-Aug-2026 sync: withNextVersion() returns a String and BOTH call sites wrapped it in
    //   .apply { }, which threw the result away — the version never actually advanced. Sync compares
    //   versions for idempotency, so it has to. updatedAt is deliberately NOT touched here.
    fun stampedWithNextVersion(): Note = copy(version = withNextVersion())
    fun withTitle(newTitle: String?): Note =
        copy(title = newTitle, updatedAt = "${getCurrentTimestamp()}", syncStatus = "PENDING")

    fun withContents(newContents: List<NoteContentModel>): Note =
        copy(contents = newContents, updatedAt = "${getCurrentTimestamp()}", syncStatus = "PENDING")

    fun withTitleAndContents(newTitle: String?, newContents: List<NoteContentModel>): Note =
        copy(title = newTitle, contents = newContents, updatedAt = "${getCurrentTimestamp()}", syncStatus = "PENDING")
}

@Serializable
sealed class NoteContentModel {
    // 🔧 C4: position Long → Double — fractional ordering: insert between a and b = (a+b)/2, no row rewrites
    abstract val position: Double  // Position in the layout
    // 🔧 timestamps stay String — platform formats may differ (C3 reverted per review)
    abstract val updatedAt: String?
    abstract val createdAt: String?
    // 🔧 C8: property serializes as "contentType" — "type" is reserved for the class discriminator
    abstract val type: ContentType
    // 🆔 declarative only — kotlinx does NOT honour @SerialName on an abstract property. Every
    //   concrete subclass repeats it; see the comment on TextContent.id.
    @SerialName("_id")
    abstract val id: String
    abstract val noteId : String
    val pages : Int = 0
    val noteProgress : Int = 0
    @Serializable @SerialName("TEXT")
    data class TextContent(
        // 🔧 F2 (C2): val — text edits go through withText(), no in-place mutation
        val text: String = "",
        // 🔧 C6: id/timestamps are REQUIRED — generated only by NoteContentObjectHelper at creation
        //       (id logic untouched: timestamp+UUID via UniqueIdGenerator; deserialization can never regenerate)
        override val updatedAt: String?,
        override val createdAt: String?,
        @SerialName("contentType")
        override val type: ContentType = ContentType.TEXT,
        // 🆔 20-Sep-2026 — MUST be repeated here. @SerialName on the ABSTRACT property in the
        //   sealed base does not reach the generated serializer: kotlinx builds each subclass
        //   serializer from that subclass's own constructor. Without this every content block went
        //   on the wire as "id", the server requires "_id", and it rejected every note the device
        //   owned — "contents.0._id: Required" — so nothing ever synced, in either direction.
        //   Same trap as @SerialName("contentType") on `type` just above; that one was already fixed.
        @SerialName("_id")
        // 🆔 …and ACCEPT the old spelling on the way in. A wire-format change lands on two apps and
        //   a server that are never updated at the same moment; without this, a device on the old
        //   build and a server on the new one simply cannot talk, in either direction.
        @JsonNames("id")
        override val id: String,
        override val noteId: String ,
        override val position: Double,
        val metadata: RichTextMetadata? = null,
    ) : NoteContentModel() {
        // 🔧 F2 (C2): Swift-friendly immutable edit — stamps the CONTENT's updatedAt
        //            so per-content timestamps are trustworthy for versioning/sync
        fun withText(newText: String): TextContent =
            copy(text = newText, updatedAt = "${getCurrentTimestamp()}")
    }

    @Serializable @SerialName("MEDIA")
    data class MediaContent(
        override val position: Double,
        override val noteId: String,
        // 🔧 C1: for MEDIA the contentType is real data — IMAGE/VIDEO/AUDIO/PDF/DOCX/GIF…
        @SerialName("contentType")
        override val type: ContentType ,
        override val updatedAt: String?,
        override val createdAt: String?,
        // 🆔 20-Sep-2026 — MUST be repeated here. @SerialName on the ABSTRACT property in the
        //   sealed base does not reach the generated serializer: kotlinx builds each subclass
        //   serializer from that subclass's own constructor. Without this every content block went
        //   on the wire as "id", the server requires "_id", and it rejected every note the device
        //   owned — "contents.0._id: Required" — so nothing ever synced, in either direction.
        //   Same trap as @SerialName("contentType") on `type` just above; that one was already fixed.
        @SerialName("_id")
        // 🆔 …and ACCEPT the old spelling on the way in. A wire-format change lands on two apps and
        //   a server that are never updated at the same moment; without this, a device on the old
        //   build and a server on the new one simply cannot talk, in either direction.
        @JsonNames("id")
        override val id: String,
        val duration: Long = 0,
        val localPath: String? = null,
        val url: String = "",
        // 🔧 C1: title default "" (was "Audio" leaking onto every image/video); persisted via new DB column
        val title: String = "",
        // 🔧 C1: proper media metadata for all formats (upload/render/sync need these)
        val mimeType: String = "",
        val sizeBytes: Long = 0,
        val width: Int = 0,
        val height: Int = 0,
        val thumbnailPath: String? = null,
        val totalPages: Int = 0,
        val progressPage: Int = 0,
        // 🖼️ 20-Aug-2026 sync: the server's name for these bytes. localPath is this device's
        //   copy and never travels; assetId is what the other device downloads with.
        val assetId: String? = null,
        val checksum: String? = null,
    ) : NoteContentModel() {
        fun withThumbnail(path: String?): MediaContent =
            copy(thumbnailPath = path, updatedAt = "${getCurrentTimestamp()}")
        // 🖼️ 20-Aug-2026 sync: stamped after an upload; updatedAt is NOT touched, because
        //   gaining a server identity is not a user edit and must not re-dirty the note.
        fun withAsset(assetId: String?, url: String, checksum: String?): MediaContent =
            copy(assetId = assetId, url = url, checksum = checksum)
        // 🖼️ 20-Aug-2026 sync: stamped after a download landed the bytes on THIS device
        fun withLocalPath(path: String?): MediaContent = copy(localPath = path)
        fun needsUpload(): Boolean = assetId.isNullOrBlank() && !localPath.isNullOrBlank()
        fun needsDownload(): Boolean = !assetId.isNullOrBlank() && localPath.isNullOrBlank()
        fun withReadingProgress(page: Int, total: Int): MediaContent =
            copy(progressPage = page, totalPages = total, updatedAt = "${getCurrentTimestamp()}")
        fun withProgressPage(page: Int): MediaContent = copy(progressPage = page)

        /** 📖 true once the document has been opened and has a resumable position. */
        fun hasReadingProgress(): Boolean = totalPages > 0 && progressPage > 0
    }

    @Serializable @SerialName("LINK")
    data class Link(
        val url: String ="",
        override val updatedAt: String?,
        override val createdAt: String?,
        @SerialName("contentType")
        override val type: ContentType = ContentType.LINK,
        // 🆔 20-Sep-2026 — MUST be repeated here. @SerialName on the ABSTRACT property in the
        //   sealed base does not reach the generated serializer: kotlinx builds each subclass
        //   serializer from that subclass's own constructor. Without this every content block went
        //   on the wire as "id", the server requires "_id", and it rejected every note the device
        //   owned — "contents.0._id: Required" — so nothing ever synced, in either direction.
        //   Same trap as @SerialName("contentType") on `type` just above; that one was already fixed.
        @SerialName("_id")
        // 🆔 …and ACCEPT the old spelling on the way in. A wire-format change lands on two apps and
        //   a server that are never updated at the same moment; without this, a device on the old
        //   build and a server on the new one simply cannot talk, in either direction.
        @JsonNames("id")
        override val id: String,
        override val position: Double,
        override val noteId: String,
    ) : NoteContentModel()

    // 🔧 C8: was @SerialName("LINK") — duplicate discriminator with Link; kotlinx rejects it at runtime
    @Serializable @SerialName("LOCATION")
    data class Location(
        val latitude: Double =0.0,
        val longitude: Double= 0.0,
        val address: String? = null,
        override val updatedAt: String?,
        override val createdAt: String?,
        @SerialName("contentType")
        override val type: ContentType = ContentType.LOCATION,
        // 🆔 20-Sep-2026 — MUST be repeated here. @SerialName on the ABSTRACT property in the
        //   sealed base does not reach the generated serializer: kotlinx builds each subclass
        //   serializer from that subclass's own constructor. Without this every content block went
        //   on the wire as "id", the server requires "_id", and it rejected every note the device
        //   owned — "contents.0._id: Required" — so nothing ever synced, in either direction.
        //   Same trap as @SerialName("contentType") on `type` just above; that one was already fixed.
        @SerialName("_id")
        // 🆔 …and ACCEPT the old spelling on the way in. A wire-format change lands on two apps and
        //   a server that are never updated at the same moment; without this, a device on the old
        //   build and a server on the new one simply cannot talk, in either direction.
        @JsonNames("id")
        override val id: String,
        override val noteId: String,
        override val position: Double,
    ) : NoteContentModel()

    fun isMediaFile() : Boolean = this is MediaContent
    fun isPlayableMedia(): Boolean = this.type.isPlayableMedia()
}
// 🔧 15-Jul-2026 iOS MEDIA-LOST FIX: localPath goes through resolveLocalFilePath — on iOS the app
//   container UUID changes on every update, so stored absolute paths are re-anchored onto the
//   current container at read time (Android actual is a pass-through). Fixes playback + image cards.
fun NoteContentModel.MediaContent.getMediaUrl(): String = resolveLocalFilePath(localPath)
        ?: url.takeIf { it.isNotEmpty() }
        ?: ""
