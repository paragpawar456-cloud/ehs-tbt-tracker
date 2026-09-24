package com.ehs.tbttracker.data.repository

import app.cash.turbine.test
import com.ehs.tbttracker.data.local.TbtDao
import com.ehs.tbttracker.data.local.TbtEntity
import com.ehs.tbttracker.data.photo.PhotoStorage
import com.ehs.tbttracker.data.remote.BackendConfig
import com.ehs.tbttracker.data.remote.CreateRequest
import com.ehs.tbttracker.data.remote.CreateResponse
import com.ehs.tbttracker.data.remote.ListResponse
import com.ehs.tbttracker.data.remote.SheetRowDto
import com.ehs.tbttracker.data.remote.SheetsWebAppApi
import com.ehs.tbttracker.domain.model.SyncState
import com.ehs.tbttracker.domain.model.TbtDraft
import com.ehs.tbttracker.domain.repository.SyncScheduler
import com.ehs.tbttracker.testutil.Fixtures
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.IOException

class TbtRepositoryImplTest {
    private val dispatcher = StandardTestDispatcher()
    private val dao = mockk<TbtDao>(relaxed = true)
    private val api = mockk<SheetsWebAppApi>()
    private val photos = mockk<PhotoStorage>()
    private val scheduler = mockk<SyncScheduler>(relaxed = true)
    private val masters = mockk<com.ehs.tbttracker.data.local.MasterContractorStore>(relaxed = true)
    private val config = BackendConfig("https://script.google.com/macros/s/abc/exec", "secret")

    private val repo = TbtRepositoryImpl(dao, api, config, photos, masters, scheduler, Fixtures.CLOCK, dispatcher)

    private fun pending(id: String) = TbtEntity(
        id = id, timestampIso = "2026-09-24T09:00", dateIso = "2026-09-24", contractorName = "Ami plumbing",
        manpower = 6, manpowerRaw = "6", location = "Tower C1 P2", photoUrl = null, localPhotoPath = "/p/$id.jpg",
        notes = "", syncState = SyncState.PENDING, rowNumber = null, attemptCount = 0, lastError = null, createdAtEpochMs = 1,
    )

    @Test
    fun `observeRecords canonicalises contractor names across the whole set`() = runTest(dispatcher) {
        // The most frequent spelling wins (ties go to the shorter one), as in the real sheet.
        every { dao.observeAll() } returns flowOf(listOf(pending("1").copy(contractorName = "Alu-wind"), pending("2").copy(contractorName = "Alu-wind infratech"), pending("3").copy(contractorName = "Alu-wind infratech ")))
        repo.observeRecords().test {
            val list = awaitItem()
            assertThat(list.map { it.contractor }.distinct()).containsExactly("Alu-wind Infratech")
            assertThat(list.first().contractorRaw).isEqualTo("Alu-wind")
            awaitComplete()
        }
    }

    @Test
    fun `refresh reconciles sheet rows and re-triggers sync`() = runTest(dispatcher) {
        coEvery { api.list(any(), "secret", any()) } returns Fixtures.realSheet()
        coEvery { dao.getByIds(any()) } returns emptyList()
        val captured = slot<List<TbtEntity>>()
        coEvery { dao.reconcileWithRemote(capture(captured)) } returns Unit

        val result = repo.refresh()

        assertThat(result.getOrNull()).isEqualTo(94)
        assertThat(captured.captured).hasSize(94)
        verify { scheduler.scheduleSync() }
        verify { masters.save(emptyList()) }
    }

    @Test
    fun `refresh surfaces backend errors as failure`() = runTest(dispatcher) {
        coEvery { api.list(any(), any(), any()) } returns ListResponse(ok = false, code = 401, error = "Unauthorized")
        val result = repo.refresh()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("Unauthorized")
        coVerify(exactly = 0) { dao.reconcileWithRemote(any()) }
    }

    @Test
    fun `refresh keeps local photo of rows we uploaded`() = runTest(dispatcher) {
        val row = SheetRowDto(row = 96, timestamp = "9/24/2026 12:00:00", date = "9/24/2026", contractor = "Ami plumbing",
            manpower = "6", location = "Tower C1", photo = "https://drive.google.com/open?id=1abcdefghijklmnop", clientRef = "uuid-1")
        coEvery { api.list(any(), any(), any()) } returns ListResponse(ok = true, rows = listOf(row))
        coEvery { dao.getByIds(listOf("uuid-1")) } returns listOf(pending("uuid-1"))
        val captured = slot<List<TbtEntity>>()
        coEvery { dao.reconcileWithRemote(capture(captured)) } returns Unit

        repo.refresh()

        with(captured.captured.single()) {
            assertThat(syncState).isEqualTo(SyncState.SYNCED)
            assertThat(localPhotoPath).isEqualTo("/p/uuid-1.jpg")
        }
    }

    @Test
    fun `submit stores PENDING record with persisted photo and schedules sync`() = runTest(dispatcher) {
        every { photos.persist("/cache/cap.jpg", any()) } answers { "/files/${secondArg<String>()}.jpg" }
        val saved = slot<TbtEntity>()
        coEvery { dao.upsert(capture(saved)) } returns Unit

        val record = repo.submit(TbtDraft(Fixtures.TODAY, "Stellar", 4, "B1 8 Floor", "ok", "/cache/cap.jpg"))

        assertThat(saved.captured.syncState).isEqualTo(SyncState.PENDING)
        assertThat(saved.captured.localPhotoPath).isEqualTo("/files/${record.id}.jpg")
        assertThat(saved.captured.dateIso).isEqualTo("2026-09-24")
        assertThat(record.syncState).isEqualTo(SyncState.PENDING)
        verify { scheduler.scheduleSync() }
    }

    @Test
    fun `syncPending uploads base64 photo and marks synced`() = runTest(dispatcher) {
        coEvery { dao.getPending() } returnsMany listOf(listOf(pending("a")), emptyList())
        every { photos.readBytes("/p/a.jpg") } returns byteArrayOf(1, 2, 3)
        val req = slot<CreateRequest>()
        coEvery { api.create(any(), capture(req)) } returns CreateResponse(ok = true, row = 97, photoUrl = "https://drive.google.com/open?id=NEW123456789", timestamp = "9/24/2026 12:01:02")

        val report = repo.syncPending()

        assertThat(report.uploaded).isEqualTo(1)
        assertThat(req.captured.clientRef).isEqualTo("a")
        assertThat(req.captured.photoBase64).isEqualTo("AQID")
        assertThat(req.captured.token).isEqualTo("secret")
        coVerify { dao.markSynced("a", "https://drive.google.com/open?id=NEW123456789", 97, "2026-09-24T12:01:02") }
    }

    @Test
    fun `network errors are retryable, validation errors are permanent`() = runTest(dispatcher) {
        coEvery { dao.getPending() } returnsMany listOf(listOf(pending("net"), pending("bad")), emptyList())
        every { photos.readBytes(any()) } returns null
        coEvery { api.create(any(), match { it.clientRef == "net" }) } throws IOException("timeout")
        coEvery { api.create(any(), match { it.clientRef == "bad" }) } returns CreateResponse(ok = false, code = 422, error = "manpower must be > 0")

        val report = repo.syncPending()

        assertThat(report.failedRetryable).isEqualTo(1)
        assertThat(report.failedPermanent).isEqualTo(1)
        assertThat(report.hasRetryableFailures).isTrue()
        coVerify { dao.recordRetryableFailure("net", "timeout") }
        coVerify { dao.markFailed("bad", "manpower must be > 0") }
    }

    @Test
    fun `records added mid-sync are uploaded in the same run`() = runTest(dispatcher) {
        coEvery { dao.getPending() } returnsMany listOf(listOf(pending("1")), listOf(pending("1"), pending("2")), emptyList())
        every { photos.readBytes(any()) } returns null
        coEvery { api.create(any(), any()) } returns CreateResponse(ok = true, row = 1)

        assertThat(repo.syncPending().uploaded).isEqualTo(2)
        coVerify(exactly = 2) { api.create(any(), any()) }
    }

    @Test
    fun `unconfigured backend never calls the network`() = runTest(dispatcher) {
        val unconfigured = TbtRepositoryImpl(dao, api, BackendConfig("https://script.google.com/macros/s/REPLACE_WITH_DEPLOYMENT_ID/exec", "x"), photos, masters, scheduler, Fixtures.CLOCK, dispatcher)
        coEvery { dao.getPending() } returns listOf(pending("1"))
        assertThat(unconfigured.syncPending().failedRetryable).isEqualTo(1)
        assertThat(unconfigured.refresh().isFailure).isTrue()
        coVerify(exactly = 0) { api.create(any(), any()) }
    }
}
