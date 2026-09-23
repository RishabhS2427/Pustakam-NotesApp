package com.app.pustakam.android

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.app.pustakam.android.di.getAndroidSpecifics
import com.app.pustakam.android.sync.SyncWorker
import com.app.pustakam.core.common.util.Result
import com.app.pustakam.core.richtext.master.presentation.PAGE_SCREEN_MARGIN
import com.app.pustakam.feature.notes.domain.usecase.NotifyConnectivityUseCase
import com.app.pustakam.feature.notes.domain.usecase.StartSyncUseCase
import com.app.pustakam.feature.notes.domain.usecase.UpgradeCanvasLayoutsUseCase
import com.app.pustakam.koin.initKoin
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

import org.koin.core.logger.Level

class PustakmApplication : Application(), KoinComponent {

    // 🔄 20-Aug-2026 sync: lazy, so nothing is resolved before initKoin() has run
    private val startSync: StartSyncUseCase by inject()
    private val notifyConnectivity: NotifyConnectivityUseCase by inject()
    private val upgradeCanvasLayouts: UpgradeCanvasLayoutsUseCase by inject()

    override fun onCreate() {
        super.onCreate()
        initKoin {
            FirebaseApp.initializeApp(this@PustakmApplication)
            androidLogger(level = Level.INFO)
            androidContext(this@PustakmApplication)
            modules(getAndroidSpecifics())
        }
        // 📐 24-Sep-2026 — before sync can carry a single canvas, the stored ones move to dp and the compact layout
        upgradeCanvasLayoutsOnce()
        // 🔄 in-app loop (sign-in, debounced saves, timer) + the network-constrained background worker
        startSync()
        watchConnectivity()
        SyncWorker.schedulePeriodic(this)
    }

    // 📐 24-Sep-2026 — Android kept canvases in pixels; once per install they become dp so iOS and Android read one layout
    private fun upgradeCanvasLayoutsOnce() {
        val prefs = getSharedPreferences(CANVAS_PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(CANVAS_LAYOUT_UPGRADED, false)) return
        val metrics = resources.displayMetrics
        val density = metrics.density.takeIf { it > 0f } ?: 1f
        val paperWidth = minOf(metrics.widthPixels, metrics.heightPixels) / density - PAGE_SCREEN_MARGIN * 2f
        val upgraded = runCatching {
            runBlocking {
                upgradeCanvasLayouts(1f / density, paperWidth).first { it !is Result.Loading } is Result.Success
            }
        }.getOrDefault(false)
        // 📐 commit, not apply: a lost flag would scale every canvas down a second time
        if (upgraded) prefs.edit().putBoolean(CANVAS_LAYOUT_UPGRADED, true).commit()
    }

    /** 🔄 28-Aug-2026 — Android's NWPathMonitor. iOS has had this since day one; Android had
     *  nothing in-process, so "I turned airplane mode off" was only noticed by the 15-minute
     *  WorkManager run. Now the engine hears it immediately and flushes what was queued offline. */
    private fun watchConnectivity() {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching {
            manager.registerNetworkCallback(
                request,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) = notifyConnectivity(true)
                    override fun onLost(network: Network) = notifyConnectivity(false)
                }
            )
        }
    }

    private companion object {
        const val CANVAS_PREFS = "pustakam_canvas"
        const val CANVAS_LAYOUT_UPGRADED = "canvas_layout_dp_v1"
    }
}
