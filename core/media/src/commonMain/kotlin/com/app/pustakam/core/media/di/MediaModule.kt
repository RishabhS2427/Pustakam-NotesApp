package com.app.pustakam.core.media.di

import com.app.pustakam.core.media.download.ApiMediaByteSource
import com.app.pustakam.core.media.download.FileMediaPartStore
import com.app.pustakam.core.media.download.MediaByteSource
import com.app.pustakam.core.media.download.MediaDownloadCoordinator
import com.app.pustakam.core.media.download.MediaDownloadManager
import com.app.pustakam.core.media.download.MediaLandingHandler
import com.app.pustakam.core.media.download.MediaPartStore
import com.app.pustakam.core.media.upload.MediaUploadRetry
import com.app.pustakam.core.media.upload.MediaUploadTracker
import com.app.pustakam.core.common.util.getCurrentTimestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

// 📥 one manager for the whole app: two cards showing the same asset must share one transfer
fun mediaModule(): Module = module {
    single<MediaByteSource> { ApiMediaByteSource(get()) }
    single<MediaPartStore> {
        FileMediaPartStore(fileWriter = get(), directories = get(), deleter = get(), storagePaths = get())
    }
    single {
        MediaDownloadManager(
            source = get(),
            store = get(),
            // 📥 SupervisorJob so one failure cannot cancel the rest; Dispatchers.IO because iOS's provideDispatcher() is Unconfined
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            clock = { getCurrentTimestamp() },
            // 📥 every feature that holds media rows registers one; notes today, chat later
            handlers = getAll<MediaLandingHandler>(),
        )
    }
    // ⬆️ one tracker: the upload half writes it, every card reads it
    single { MediaUploadTracker() }
    // 📥 what the UI injects; the retry hook comes from notes' sync (getOrNull keeps this module standalone)
    single {
        MediaDownloadCoordinator(
            manager = get(),
            prefs = get(),
            uploads = get(),
            uploadRetry = getOrNull<MediaUploadRetry>(),
            directories = get(),
            storagePaths = get(),
        )
    }
}
