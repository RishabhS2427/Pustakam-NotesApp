package com.app.pustakam.core.database.localdb.database

import app.cash.sqldelight.db.SqlDriver
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun getDatabaseModule(): Module = module {
    single <SqlDriver>{ SqlDelightDriverFactory(context = androidContext()).createDriver() }
    single<NotesDao> { NotesDao() }
    single<ChatDao> { ChatDao() }
}