package com.app.pustakam.android.fileUtils

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.graphics.scale
import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.filesys.mime.MimeCatalog
import com.app.pustakam.core.filesys.naming.FileNameGenerator.suggestedFileNameFromMedia
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


fun createFileWithFolders(context: Activity, folderPath : String, fileName : String) : File {
    var file = File("")
    try{
        val baseDir = File(context.filesDir,folderPath)
        if (!baseDir.exists()) {
            if (!baseDir.mkdirs()) {
            throw IOException("Failed to create directories: ${baseDir.absolutePath}")
            }
        }
        file = File(baseDir.absolutePath, fileName)
        if (!file.exists()) {
            try {
                file.createNewFile()
            } catch (e: IOException) {
                throw IOException("Failed to create file: ${file.absolutePath}", e)
            }
        }
    } catch (e: Exception){
        e.printStackTrace()
    }
    return file
}

fun deleteFile(filePath : String){
    val file = File(filePath)
    if (file.exists() && file.isFile) {
        file.delete()
    }
}

fun deleteFilesLater(paths: List<String>) {
    if (paths.isEmpty()) return
    CoroutineScope(Dispatchers.IO).launch { paths.forEach { runCatching { deleteFile(it) } } }
}
private const val THUMBNAIL_MAX_DIMENSION = 512

fun generateThumbnail(context: Context, sourcePath: String, contentType: ContentType): String? {
    return try {
        val source = File(sourcePath)
        if (!source.exists()) return null
        val bitmap: Bitmap = when (contentType) {
            ContentType.IMAGE, ContentType.GIF -> decodeDownsampled(sourcePath)
            ContentType.VIDEO -> {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(sourcePath)
                    val frame = retriever.getFrameAtTime(1_000_000) // ~1s in — skips black lead-ins
                        ?: retriever.getFrameAtTime(0)
                    frame?.let { scaleDown(it) }
                } finally {
                    retriever.release()
                }
            }
            else -> null
        } ?: return null
// 🔧 30-Jul-2026 02:10 Phase 1 — folder + name now come from PathPolicy, not string literals
        val dest = com.app.pustakam.core.filesys.path.PathPolicy.thumbnailPath(sourcePath)
        val thumbDir = File(context.filesDir, dest.folder).apply { mkdirs() }
        val thumbFile = File(thumbDir, dest.fileName)
        FileOutputStream(thumbFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
        }
        thumbFile.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// 🔧 15-Jul-2026 Phase 2.3: decode at reduced resolution straight from disk.
private fun decodeDownsampled(path: String): Bitmap? {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (bounds.outWidth / (sampleSize * 2) >= THUMBNAIL_MAX_DIMENSION ||
        bounds.outHeight / (sampleSize * 2) >= THUMBNAIL_MAX_DIMENSION
    ) sampleSize *= 2
    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return android.graphics.BitmapFactory.decodeFile(path, opts)
}

// 🔧 15-Jul-2026 Phase 2.3: proportional scale so the longest side is THUMBNAIL_MAX_DIMENSION.
private fun scaleDown(bitmap: Bitmap): Bitmap {
    val longest = maxOf(bitmap.width, bitmap.height)
    if (longest <= THUMBNAIL_MAX_DIMENSION) return bitmap
    val scale = THUMBNAIL_MAX_DIMENSION.toFloat() / longest
    return bitmap.scale(
        (bitmap.width * scale).toInt().coerceAtLeast(1),
        (bitmap.height * scale).toInt().coerceAtLeast(1)
    )
}
//fun saveBitmapToFile(bitmap: Bitmap, filePath: String): Boolean {
//    val file = File(filePath)
//   return saveBitmapToFile(bitmap,file)
//}
fun saveBitmapToFile(bitmap: Bitmap,file: File): Boolean{
    return try {
        file.parentFile?.mkdirs() // Ensure the directory exists
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG,100 , out) // Save as PNG with 100% quality
        }
        true // Success
    } catch (e: IOException) {
        e.printStackTrace()
        false // Failure
    }
}
fun saveMediaToGallery(context: Context, media: NoteContentModel.MediaContent): Boolean {
    val sourcePath = media.localPath?.takeIf { it.isNotEmpty() } ?: media.url.takeIf { it.isNotEmpty() }
    if (sourcePath.isNullOrEmpty()) return false
    val source = File(sourcePath)
    if (!source.exists()) return false

    val resolver = context.contentResolver
    val fileName = suggestedFileNameFromMedia(media)
    val mime = MimeCatalog.mimeFor(media.type)
    val isVideo = media.type == ContentType.VIDEO

    // Collection + relative sub-folder differ for images vs videos.
    val collection = if (isVideo)
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val relativeDir = if (isVideo)
        "${Environment.DIRECTORY_MOVIES}/Pustakam" else "${Environment.DIRECTORY_PICTURES}/Pustakam"

    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
            put(MediaStore.MediaColumns.IS_PENDING, 1) // hide until fully written
        }
    }

    return try {
        val uri: Uri = resolver.insert(collection, values) ?: return false
        resolver.openOutputStream(uri)?.use { out ->
            source.inputStream().use { input -> input.copyTo(out) }
        } ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0) // publish
            resolver.update(uri, values, null, null)
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

// 🔧 14-Jul-2026: Copy a media file's bytes into a SAF-picked destination Uri.
//   Used for AUDIO/PDF/DOCX/GIF once the user picks a location via the
//   ACTION_CREATE_DOCUMENT picker. Returns true on success.
//   Usage: writeMediaToUri(context, media, pickedUri)
fun writeMediaToUri(context: Context, media: NoteContentModel.MediaContent, destination: Uri): Boolean {
    val sourcePath = media.localPath?.takeIf { it.isNotEmpty() } ?: media.url.takeIf { it.isNotEmpty() }
    if (sourcePath.isNullOrEmpty()) return false
    val source = File(sourcePath)
    if (!source.exists()) return false
    return try {
        context.contentResolver.openOutputStream(destination)?.use { out ->
            source.inputStream().use { input -> input.copyTo(out) }
        } ?: return false
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}


