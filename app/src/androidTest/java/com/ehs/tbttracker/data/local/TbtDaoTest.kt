package com.ehs.tbttracker.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.ehs.tbttracker.domain.model.SyncState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TbtDaoTest {
    private lateinit var db: TbtDatabase
    private lateinit var dao: TbtDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TbtDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.tbtDao()
    }

    @After
    fun tearDown() = db.close()

    private fun entity(id: String, date: String = "2026-09-24", ts: String = "${date}T09:00", state: SyncState = SyncState.SYNCED) =
        TbtEntity(id, ts, date, "Choudhary construction", 9, "09", "Tower D1", null, null, "", state, null, 0, null, 0)

    @Test
    fun observeAll_ordersNewestFirst() = runTest {
        dao.upsertAll(listOf(entity("old", "2026-09-22"), entity("new", "2026-09-24", "2026-09-24T10:00"), entity("early", "2026-09-24", "2026-09-24T08:00")))
        dao.observeAll().test {
            assertThat(awaitItem().map { it.id }).containsExactly("new", "early", "old").inOrder()
        }
    }

    @Test
    fun pendingLifecycle_markSyncedAndFailures() = runTest {
        dao.upsert(entity("p1", state = SyncState.PENDING))
        dao.upsert(entity("p2", state = SyncState.PENDING))
        assertThat(dao.getPending().map { it.id }).containsExactly("p1", "p2")

        dao.markSynced("p1", "https://drive.google.com/open?id=abc1234567", 97, null)
        dao.recordRetryableFailure("p2", "timeout")

        with(dao.getById("p1")!!) {
            assertThat(syncState).isEqualTo(SyncState.SYNCED)
            assertThat(rowNumber).isEqualTo(97)
            assertThat(timestampIso).isEqualTo("2026-09-24T09:00") // COALESCE keeps original
        }
        with(dao.getById("p2")!!) {
            assertThat(syncState).isEqualTo(SyncState.PENDING)
            assertThat(attemptCount).isEqualTo(1)
            assertThat(lastError).isEqualTo("timeout")
        }
    }

    @Test
    fun requeueFailed_movesFailedBackToPending() = runTest {
        dao.upsert(entity("f", state = SyncState.PENDING))
        dao.markFailed("f", "422")
        assertThat(dao.getPending()).isEmpty()
        assertThat(dao.requeueFailed()).isEqualTo(1)
        assertThat(dao.getPending().single().lastError).isNull()
    }

    @Test
    fun reconcile_upsertsRemote_deletesStaleSynced_keepsLocalWork() = runTest {
        dao.upsertAll(
            listOf(
                entity("kept"),
                entity("deletedInSheet"),
                entity("offline", state = SyncState.PENDING),
                entity("uploadedButResponseLost", state = SyncState.PENDING),
            ),
        )

        dao.reconcileWithRemote(listOf(entity("kept"), entity("uploadedButResponseLost"), entity("newFromForm")))

        val all = dao.getByIds(listOf("kept", "deletedInSheet", "offline", "uploadedButResponseLost", "newFromForm")).associateBy { it.id }
        assertThat(all.keys).containsExactly("kept", "offline", "uploadedButResponseLost", "newFromForm")
        assertThat(all.getValue("offline").syncState).isEqualTo(SyncState.PENDING)
        assertThat(all.getValue("uploadedButResponseLost").syncState).isEqualTo(SyncState.SYNCED)
    }

    @Test
    fun unsyncedCount_tracksPendingAndFailed() = runTest {
        dao.observeUnsyncedCount().test {
            assertThat(awaitItem()).isEqualTo(0)
            dao.upsert(entity("x", state = SyncState.PENDING))
            assertThat(awaitItem()).isEqualTo(1)
            dao.markSynced("x", null, 5, null)
            assertThat(awaitItem()).isEqualTo(0)
        }
    }
}
