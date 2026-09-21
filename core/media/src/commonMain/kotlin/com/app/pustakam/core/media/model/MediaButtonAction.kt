package com.app.pustakam.core.media.model

// ⏯️ what the centre button does right now — shared so both platforms pick an icon for the same word
enum class MediaButtonAction {
    DOWNLOAD,
    PAUSE,
    RESUME,
    RETRY,
    NONE;

    // ⏯️ the accessible label, also shared
    fun label(): String = when (this) {
        DOWNLOAD -> "Download"
        PAUSE -> "Pause download"
        RESUME -> "Resume download"
        RETRY -> "Retry download"
        NONE -> ""
    }
}
