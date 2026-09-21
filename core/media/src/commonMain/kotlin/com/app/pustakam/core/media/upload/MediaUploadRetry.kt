package com.app.pustakam.core.media.upload

// ⬆️ uploads run inside sync, which :core:media cannot see — the feature that owns sync supplies this
fun interface MediaUploadRetry {
    fun retry()
}
