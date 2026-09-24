package com.ehs.tbttracker.domain.repository

import com.ehs.tbttracker.domain.model.TbtDraft
import com.ehs.tbttracker.domain.model.TbtRecord
import kotlinx.coroutines.flow.Flow

interface TbtRepository {
    /** Local cache (Room) is the single source of truth; contractor names already canonicalised. */
    fun observeRecords(): Flow<List<TbtRecord>>

    /** Pulls the whole sheet and reconciles it with the cache. Returns number of rows fetched. */
    suspend fun refresh(): Result<Int>

    /** Persists locally as PENDING and schedules a background upload. Never throws for offline. */
    suspend fun submit(draft: TbtDraft): TbtRecord

    /** Uploads every PENDING/FAILED record. Used by the WorkManager worker. */
    suspend fun syncPending(): SyncReport

    /** Moves FAILED records back to PENDING (after the user fixed config / data) and reschedules. */
    suspend fun retryFailed()
}

data class SyncReport(val uploaded: Int, val failedRetryable: Int, val failedPermanent: Int) {
    val hasRetryableFailures: Boolean get() = failedRetryable > 0
}

/** Schedules background sync; implemented with WorkManager. */
interface SyncScheduler {
    fun scheduleSync()
}

interface ConnectivityObserver {
    val isOnline: Flow<Boolean>
    fun isCurrentlyOnline(): Boolean
}
