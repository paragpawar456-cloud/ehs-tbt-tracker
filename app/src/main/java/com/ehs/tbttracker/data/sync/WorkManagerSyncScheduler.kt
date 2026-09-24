package com.ehs.tbttracker.data.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ehs.tbttracker.domain.repository.SyncScheduler
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    private val workManager: WorkManager,
) : SyncScheduler {

    override fun scheduleSync() {
        // APPEND_OR_REPLACE: a record saved while a sync is mid-run still gets a follow-up run;
        // a chain that previously failed permanently is replaced instead of blocking forever.
        workManager.enqueueUniqueWork(TbtSyncWorker.UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, buildRequest())
    }

    companion object {
        fun buildRequest(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<TbtSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TbtSyncWorker.UNIQUE_NAME)
            .build()
    }
}
