package com.app.pustakam.core.media.download

// 📥 the feature owning a media block hears when its bytes land, so it can point its row at the file
interface MediaLandingHandler {
    suspend fun onLanded(assetId: String, holderId: String, absolutePath: String)
}
