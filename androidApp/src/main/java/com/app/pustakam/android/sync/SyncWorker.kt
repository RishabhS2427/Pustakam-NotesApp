package com.app.pustakam.android.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.app.pustakam.core.common.util.log_d
import com.app.pustakam.feature.notes.domain.usecase.NotifyConnectivityUseCase
import com.app.pustakam.feature.notes.domain.usecase.SyncNowUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit
import com.app.pustakam.core.common.util.Result as AppResult

private const val TAG = "SyncWorker"
private const val PERIODIC_WORK = "pustakam.sync.periodic"
private const val ONE_SHOT_WORK = "pustakam.sync.oneshot"
private const val PERIOD_MINUTES = 15L
private const val MAX_ATTEMPTS = 5

/**
 * 🔄 20-Aug-2026 — the Android half of "came back online, sync in the background".
 *
 * The NetworkType.CONNECTED constraint is the whole trick: WorkManager itself parks the work while
 * the device is offline and starts it the moment there is a connection. Nothing here polls.
 */
class SyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params), KoinComponent {

    private val syncNowUseCase: SyncNowUseCase by inject()
    private val notifyConnectivity: NotifyConnectivityUseCase by inject()

    override suspend fun doWork(): Result {
        // 🔄 we only ever run with the CONNECTED constraint satisfied, so this is a fact, not a guess
        notifyConnectivity(true)

        var failed = false
        // 🔒 22-Sep-2026 — offline mode no longer short-circuits a cycle: the pull still runs and
        //   only the push is held back, so a run under offline mode is an ordinary success.
        syncNowUseCase().collect { result -> if (result is AppResult.Error) failed = true }

        return when {
            !failed -> Result.success()
            // 🔄 give up eventually rather than retrying a genuinely broken sync forever
            runAttemptCount >= MAX_ATTEMPTS -> Result.failure()
            else -> Result.retry()
        }.also { log_d(TAG, "sync work finished: $it (attempt $runAttemptCount)") }
    }

    companion object {

        private fun networkConstraints(): Constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Enqueued once from Application.onCreate; KEEP so a relaunch does not reset the schedule. */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
                .setConstraints(networkConstraints())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        /** 🔄 "flush when there is a line": queued after work is made offline, runs on reconnect. */
        fun syncWhenConnected(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(networkConstraints())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT_WORK, ExistingWorkPolicy.REPLACE, request
            )
        }
    }
}
