package com.app.pustakam.core.media.model

// 📥 one file to fetch — what the manager needs, and nothing about how it is drawn
data class MediaDownloadRequest(
    val assetId: String,
    // 📥 the OWNER of the asset, which is whose /media/{userId}/{assetId} it lives under
    val ownerId: String,
    // 📥 the row holding the block — a note id today — so the landing handler knows what to update
    val holderId: String,
    // 📥 relative to app storage; the manager appends ".part" while bytes are still arriving
    val destinationRelativePath: String,
    // 📥 the note's own sizeBytes, so a card can show a size before the first byte arrives
    val expectedBytes: Long = 0,
)
