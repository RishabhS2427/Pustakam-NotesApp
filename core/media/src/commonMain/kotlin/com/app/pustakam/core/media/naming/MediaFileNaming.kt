package com.app.pustakam.core.media.naming

import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.filesys.mime.MimeCatalog
import com.app.pustakam.core.filesys.path.PathPolicy

// 📥 where a downloaded asset lands — shared with MediaSyncer so both always write the SAME path
object MediaFileNaming {

    // 📥 relative to app storage — "imported/<noteId>/<assetId><ext>"
    fun destinationFor(noteId: String, assetId: String, mimeType: String, type: ContentType): String {
        val destination = PathPolicy.importPath(noteId, fileNameFor(assetId, mimeType, type))
        return PathPolicy.relativePath(destination.folder, destination.fileName)
    }

    // 📥 the asset id IS unique, so no collision handling; the extension keeps type-detection-by-name working
    fun fileNameFor(assetId: String, mimeType: String, type: ContentType): String {
        // 🎧 audio keeps its real extension — extensionFor(AUDIO) is ".mp3", and AAC named .mp3 will not open
        audioExtensionFor(mimeType)?.let { return "$assetId$it" }
        val resolved = MimeCatalog.contentTypeForMime(mimeType) ?: type
        return "$assetId${MimeCatalog.extensionFor(resolved)}"
    }

    // 🎧 declare the file's real audio type — mimeFor(AUDIO) says "audio/mpeg" even for .m4a recordings
    fun declaredMimeFor(type: ContentType, mimeType: String, fileName: String): String {
        val fallback = mimeType.trim().ifBlank { MimeCatalog.mimeFor(type) }
        if (type != ContentType.AUDIO) return fallback
        return audioMimeForExtension(fileName.substringAfterLast('.', "")) ?: fallback
    }

    private fun audioMimeForExtension(extension: String): String? =
        when (extension.lowercase()) {
            // 🎧 .mp4 included on purpose: Android's recorder writes AAC into an .mp4 name
            "m4a", "mp4" -> "audio/mp4"
            "aac" -> "audio/aac"
            "wav" -> "audio/wav"
            "amr" -> "audio/amr"
            "ogg", "oga" -> "audio/ogg"
            "flac" -> "audio/x-flac"
            "mp3" -> "audio/mpeg"
            else -> null
        }

    private fun audioExtensionFor(mimeType: String): String? =
        when (mimeType.trim().lowercase().substringBefore(';')) {
            "audio/mp4", "audio/x-m4a" -> ".m4a"
            "audio/aac" -> ".aac"
            "audio/wav", "audio/x-wav" -> ".wav"
            "audio/amr" -> ".amr"
            "audio/ogg" -> ".ogg"
            "audio/flac", "audio/x-flac" -> ".flac"
            "audio/mpeg" -> ".mp3"
            else -> null
        }
}
