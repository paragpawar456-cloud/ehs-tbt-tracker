package com.ehs.tbttracker.data.sync

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.ehs.tbttracker.domain.model.TbtDraft
import com.ehs.tbttracker.domain.model.TbtRecord
import com.ehs.tbttracker.domain.repository.SyncReport
import com.ehs.tbttracker.domain.repository.TbtRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Scriptable fake: each sync returns the next report in the queue. */
class FakeRepository(vararg reports: SyncReport) : TbtRepository {
    private val queue = ArrayDeque(reports.toList())
    var syncCalls = 0
    override fun observeRecords(): Flow<List<TbtRecord>> = emptyFlow()
    override suspend fun refresh() = Result.success(0)
    override suspend fun submit(draft: TbtDraft): TbtRecord = error("unused")
    override suspend fun retryFailed() = Unit
    override suspend fun syncPending(): SyncReport { syncCalls++; return queue.removeFirstOrNull() ?: SyncReport(0, 0, 0) }
}

private class FakeWorkerFactory(private val repo: TbtRepository) : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, params: WorkerParameters): ListenableWorker =
        TbtSyncWorker(appContext, params, repo)
}

@RunWith(AndroidJUnit4::class)
class TbtSyncWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun worker(repo: TbtRepository, attempt: Int = 0) =
        TestListenableWorkerBuilder<TbtSyncWorker>(context)
            .setWorkerFactory(FakeWorkerFactory(repo))
            .setRunAttemptCount(attempt)
            .build()

    @Test
    fun allUploaded_success() = runBlocking {
        assertThat(worker(FakeRepository(SyncReport(3, 0, 0))).doWork()).isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun transientFailure_retry() = runBlocking {
        assertThat(worker(FakeRepository(SyncReport(1, 2, 0))).doWork()).isEqualTo(ListenableWorker.Result.retry())
    }

    @Test
    fun permanentFailureOnly_doesNotRetryForever() = runBlocking {
        assertThat(worker(FakeRepository(SyncReport(0, 0, 1))).doWork()).isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun givesUpAfterMaxAttempts() = runBlocking {
        val w = worker(FakeRepository(SyncReport(0, 1, 0)), attempt = TbtSyncWorker.MAX_ATTEMPTS)
        assertThat(w.doWork()).isEqualTo(ListenableWorker.Result.failure())
    }
}

/** End-to-end queue behaviour through the real scheduler: offline -> enqueued, online -> runs. */
@RunWith(AndroidJUnit4::class)
class OfflineSyncQueueTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repo: FakeRepository
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        repo = FakeRepository(SyncReport(0, 1, 0), SyncReport(2, 0, 0))
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(SynchronousExecutor())
                .setWorkerFactory(FakeWorkerFactory(repo))
                .build(),
        )
        workManager = WorkManager.getInstance(context)
    }

    private fun info(): WorkInfo = workManager.getWorkInfosForUniqueWork(TbtSyncWorker.UNIQUE_NAME).get().last()

    @Test
    fun waitsForNetwork_thenRuns_thenBacksOffOnTransientFailure() {
        WorkManagerSyncScheduler(workManager).scheduleSync()

        // Offline: constraint unmet, nothing has run.
        assertThat(info().state).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(info().constraints.requiredNetworkType).isEqualTo(androidx.work.NetworkType.CONNECTED)
        assertThat(repo.syncCalls).isEqualTo(0)

        // Network restored.
        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(info().id)

        // First attempt hit a transient error -> re-enqueued with exponential backoff.
        assertThat(repo.syncCalls).isEqualTo(1)
        assertThat(info().state).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(info().runAttemptCount).isEqualTo(1)
    }

    @Test
    fun secondScheduleWhileQueued_appendsInsteadOfDuplicating() {
        val scheduler = WorkManagerSyncScheduler(workManager)
        scheduler.scheduleSync()
        scheduler.scheduleSync()
        val infos = workManager.getWorkInfosForUniqueWork(TbtSyncWorker.UNIQUE_NAME).get()
        assertThat(infos).hasSize(2)
        assertThat(infos.map { it.state }).containsExactly(WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED)
    }
}
