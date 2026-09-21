package com.app.pustakam.core.media.model

// 📥 where one attachment's bytes are on THIS device — the whole answer a media card needs
enum class MediaDownloadState {
    // 📥 on the server, not here — the card offers a Download button
    NOT_DOWNLOADED,

    // 📥 the user asked, but another transfer holds the slot
    QUEUED,

    // 📥 bytes are arriving right now
    DOWNLOADING,

    // ⏸️ stopped by the user — what arrived is kept and resumes with a Range request
    PAUSED,

    // 📥 fully on this device and playable
    DOWNLOADED,

    // 📥 stopped on its own — a tap retries, keeping what already landed
    FAILED;

    // 📥 true while the progress bar should be on screen at all
    fun isBusy(): Boolean = this == QUEUED || this == DOWNLOADING || this == PAUSED

    // ⏯️ true when the centre button should offer resume rather than pause
    fun canResume(): Boolean = this == PAUSED || this == FAILED

    // 📥 the ONLY state in which a card may try to open the file
    fun isReady(): Boolean = this == DOWNLOADED
}
