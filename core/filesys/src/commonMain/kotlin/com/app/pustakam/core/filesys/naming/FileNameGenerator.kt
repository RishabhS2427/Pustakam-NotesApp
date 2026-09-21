package com.app.pustakam.core.filesys.naming

import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.common.util.getCurrentTimestamp
import com.app.pustakam.core.filesys.export.ExportFormat
import com.app.pustakam.core.filesys.mime.MimeCatalog
import com.app.pustakam.core.model.models.response.notes.NoteContentModel

// 🔧 30-Jul-2026 02:10 Phase 1 — every filename the app invents, decided in ONE place.
//   Pure: takes the clock as a parameter, never reads it, so every rule below is unit-testable.
//   Rules are lifted verbatim from the existing Android call sites (the ones that own the files
//   already on user devices) — see FileNameGeneratorTest for the pinned expectations.
object FileNameGenerator {

    /** Longest base name we will emit. 255 is the hard limit on both platforms; the headroom
     *  leaves space for the "<millis>_" prefix generateUnique may add. */
    const val MAX_BASE_LENGTH = 200

    /** Capture name: "<timestamp><ext>" — matches NoteContentProvider.addContent. */
    fun generate(type: ContentType, timestamp: Long): String =
        "$timestamp${captureExtension(type)}"

    // 🎧 21-Sep-2026 — both recorders write AAC inside MP4 (Android MPEG_4, iOS kAudioFormatMPEG4AAC): an .m4a, never an .mp3
    private const val RECORDED_AUDIO_EXT = ".m4a"

    private fun captureExtension(type: ContentType): String =
        if (type == ContentType.AUDIO) RECORDED_AUDIO_EXT else MimeCatalog.extensionFor(type)

    /**
     * Path-safe name. Replaces separators, then caps the BASE while preserving the extension.
     * Mirrors the existing `fileName.replace('/', '_')` on both platforms; the length cap is new
     * and only engages where a write would previously have failed outright.
     */
    fun sanitize(name: String, maxBaseLength: Int = MAX_BASE_LENGTH): String {
        val cleaned = name
            .map { if (it == '/' || it == '\\') '_' else it }
            .joinToString("")
            .trim()
            .ifEmpty { "file" }
        val dot = cleaned.lastIndexOf('.')
        val base = if (dot > 0) cleaned.substring(0, dot) else cleaned
        val ext = if (dot > 0) cleaned.substring(dot) else ""
        return if (base.length <= maxBaseLength) cleaned else base.take(maxBaseLength) + ext
    }

    /**
     * First free name for [desired] in a folder. [exists] is supplied by the caller so this stays
     * pure. Collision strategy is the existing one: prefix with the timestamp.
     */
    fun generateUnique(desired: String, timestamp: Long, exists: (String) -> Boolean): String {
        val safe = sanitize(desired)
        if (!exists(safe)) return safe
        val prefixed = sanitize("${timestamp}_$safe")
        if (!exists(prefixed)) return prefixed
        var n = 1
        while (true) {
            val candidate = sanitize("${timestamp}_${n}_$safe")
            if (!exists(candidate)) return candidate
            n++
        }
    }

    /**
     * Export name: "<safeTitle>-<timestamp><ext>" — matches Android NoteExporter.export.
     * The allow-list is deliberately ASCII-only, reproducing the previous Regex("[^A-Za-z0-9-_]")
     * exactly. Kotlin's Char.isLetterOrDigit() would ALSO keep accented and non-Latin characters,
     * which would silently rename exports for anyone with a non-ASCII note title.
     */
    fun suggestExportName(title: String?, format: ExportFormat, timestamp: Long): String {
        val safeTitle = (title?.takeIf { it.isNotBlank() } ?: "note")
            .map { if (it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_') it else '_' }
            .joinToString("")
            .take(MAX_BASE_LENGTH)
        return "$safeTitle-$timestamp${format.ext}"
    }

    /** Save-to-device name — matches Android FileOps.suggestedFileName. */
    fun suggestSaveName(title: String?, sourcePath: String?, type: ContentType, timestamp: Long): String {
        val fromSource = sourcePath?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        // A real file name that already carries an extension wins outright.
        if (fromSource != null && fromSource.contains('.')) return sanitize(fromSource)
        val base = title?.takeIf { it.isNotBlank() }
            ?: fromSource?.substringBeforeLast('.')
            ?: "Pustakam-$timestamp"
        val ext = MimeCatalog.extensionFor(type)
        return sanitize(if (ext.isNotEmpty()) "$base$ext" else base)
    }

    fun suggestedFileNameFromMedia(media: NoteContentModel.MediaContent): String {
// 🔧 30-Jul-2026 02:10 Phase 1 — delegates to FileNameGenerator (same rule, now shared + unit-tested)
        return FileNameGenerator.suggestSaveName(
            title = media.title,
            sourcePath = media.localPath,
            type = media.type,
            timestamp = getCurrentTimestamp(),
        )
    }

    /** Thumbnail name: "<sourceBase>_thumb.jpg" — matches both platforms. */
    fun thumbnailName(sourceFileName: String): String =
        sanitize("${sourceFileName.substringAfterLast('/').substringBeforeLast('.')}_thumb.jpg")

    /**
     * Download name. Prefers the URL's last segment when it carries an extension, else falls back
     * to "download-<timestamp>" AND appends the mime's extension when one is known.
     * The mime-derived extension is new: without it, extensionless download URLs were rejected on
     * Android while succeeding on iOS (see 30jul2026-FIA doc §3.3).
     */
    fun fromUrl(url: String, mime: String? = null, timestamp: Long): String {
        val cleaned = url.substringBefore('?').substringBefore('#').trimEnd('/')
        val segment = cleaned.substringAfterLast('/')
        if (segment.isNotBlank() && segment.contains('.')) return sanitize(segment)
        val ext = MimeCatalog.contentTypeForMime(mime)?.let { MimeCatalog.extensionFor(it) }.orEmpty()
        return "download-$timestamp$ext"
    }



}
