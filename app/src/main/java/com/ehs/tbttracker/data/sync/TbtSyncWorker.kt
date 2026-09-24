package com.ehs.tbttracker.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ehs.tbttracker.domain.repository.TbtRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Drains the offline queue. Scheduled with a CONNECTED constraint, so it starts the moment the
 * device regains network; transient failures return retry() and WorkManager applies
 * exponential backoff (see [WorkManagerSyncScheduler]).
 */
@HiltWorker
class TbtSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: TbtRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val report = repository.syncPending()
        return when {
            report.hasRetryableFailures && runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            report.hasRetryableFailures -> Result.failure()
            else -> Result.success()
        }
    }

    companion object {
        const val UNIQUE_NAME = "tbt-sync"
        const val MAX_ATTEMPTS = 10
    }
}
