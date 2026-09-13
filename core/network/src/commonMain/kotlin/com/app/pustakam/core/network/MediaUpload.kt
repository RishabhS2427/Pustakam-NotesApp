package com.app.pustakam.core.network

// 🖼️ 20-Aug-2026 sync: one file on its way to POST /images. Not @Serializable — it goes out as
//   multipart, and a ByteArray in a data class would give equals()/hashCode() nobody wants.
class MediaUpload(
    val contentId: String,
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)

// 🖼️ the multipart field name multer is configured for: multerConfig.array(UPLOAD_FIELD.FILES, ...)
const val UPLOAD_FIELD_FILES = "files"
// 🖼️ POST /profile uses multer.single("avatar") — a different field name than /images
const val UPLOAD_FIELD_AVATAR = "avatar"
