package com.app.pustakam.koin

import com.app.pustakam.core.data.di.coreDataModule
import com.app.pustakam.core.database.di.databaseModule
import com.app.pustakam.core.filesys.di.getFileSystemModule
import com.app.pustakam.core.database.di.preferencesModule
import com.app.pustakam.core.database.localdb.database.getDatabaseModule
import com.app.pustakam.core.database.localdb.preferences.getDataSourceFromPlatForm
import com.app.pustakam.core.network.di.networkModule
import com.app.pustakam.feature.auth.di.authModule
import com.app.pustakam.feature.chat.di.chatModule
import com.app.pustakam.feature.notes.di.notesModule
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

// 🔧 30-Jul-2026 02:10 — :shared is now the COMPOSITION ROOT only: each module declares its own Koin
//   module and this file just assembles them, in the SAME ORDER as the old inline version.
//   Name and signature are unchanged on purpose — Swift calls KoinKt.doInitKoin(appDeclaration:)
//   and Android's PustakmApplication calls initKoin { androidContext(...) }.
fun initKoin(appDeclaration: KoinAppDeclaration = {}) = startKoin {

    appDeclaration()

    modules(
        preferencesModule(),          // was sharedPrefModule
        getDataSourceFromPlatForm(),
        coreDataModule(),
        notesModule(),                // was repositoriesModules + the notes half of useCases
        authModule(),                 // was the auth half of useCases
        chatModule(),                 // 💬 31-Aug-2026 chat
        networkModule(),
        databaseModule(),
        getDatabaseModule(),
        // 🔧 30-Jul-2026 02:10 Phase 4 — platform filesys seams (FileReader/Writer/Deleter/...).
        //   All `single`, all lazy — nothing is constructed until something injects it.
        getFileSystemModule(),
    )
}
