package com.ehs.tbttracker.ui.portal

import android.content.Intent
import app.cash.turbine.test
import com.ehs.tbttracker.data.export.AuditExporter
import com.ehs.tbttracker.domain.model.TbtRecord
import com.ehs.tbttracker.domain.repository.ConnectivityObserver
import com.ehs.tbttracker.domain.repository.TbtRepository
import com.ehs.tbttracker.testutil.Fixtures
import com.ehs.tbttracker.testutil.Fixtures.TODAY
import com.ehs.tbttracker.testutil.Fixtures.record
import com.ehs.tbttracker.testutil.MainDispatcherExtension
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.File
import java.io.IOException
import java.time.YearMonth

class PortalViewModelTest {
    @JvmField @RegisterExtension val main = MainDispatcherExtension()

    private val records = MutableStateFlow<List<TbtRecord>>(emptyList())
    private val masters = MutableStateFlow<List<String>>(emptyList())
    private val repository = mockk<TbtRepository>(relaxed = true)
    private val connectivity = mockk<ConnectivityObserver>()
    private val exporter = mockk<AuditExporter>()

    @BeforeEach
    fun setUp() {
        every { repository.observeRecords() } returns records
        every { repository.observeMasterContractors() } returns masters
        every { connectivity.isOnline } returns MutableStateFlow(true)
        every { connectivity.isCurrentlyOnline() } returns true
        coEvery { repository.refresh() } returns Result.success(0)
    }

    private fun vm() = PortalViewModel(repository, connectivity, exporter, Fixtures.CLOCK)

    private suspend fun app.cash.turbine.ReceiveTurbine<PortalUiState>.loaded(): PortalUiState {
        var s = awaitItem(); while (s.loading) s = awaitItem(); return s
    }

    @Test
    fun `opens on the busiest contractor with its month report`() = runTest {
        records.value = listOf(record(contractor = "A"), record(contractor = "A", date = TODAY.minusDays(1)), record(contractor = "B"))
        vm().state.test {
            val s = loaded()
            assertThat(s.contractor).isEqualTo("A")
            assertThat(s.report!!.doneDays).hasSize(2)
            assertThat(s.month).isEqualTo(YearMonth.from(TODAY))
            assertThat(s.contractorChips.first()).isEqualTo("A" to 2)
            assertThat(s.totalLogs).isEqualTo(3)
        }
    }

    @Test
    fun `master names count as registered and chips filter by search`() = runTest {
        records.value = listOf(record(contractor = "Ami Plumbing"))
        masters.value = listOf("AMI Plumbing", "Shiv Krupa Construction Pvt Ltd")
        val vm = vm()
        vm.state.test {
            val s = loaded()
            assertThat(s.registeredContractors).isEqualTo(2)
            assertThat(s.contractorChips).containsExactly("AMI Plumbing" to 1, "Shiv Krupa Construction Pvt Ltd" to 0).inOrder()
            vm.setContractorQuery("shiv")
            assertThat(awaitItem().contractorChips.map { it.first }).containsExactly("Shiv Krupa Construction Pvt Ltd")
        }
    }

    @Test
    fun `openContractor switches to the contractor tab`() = runTest {
        records.value = listOf(record(contractor = "A"), record(contractor = "B"))
        val vm = vm()
        vm.selectTab(PortalTab.GRID)
        vm.openContractor("B")
        vm.state.test {
            val s = loaded()
            assertThat(s.selection.tab).isEqualTo(PortalTab.CONTRACTOR)
            assertThat(s.contractor).isEqualTo("B")
        }
    }

    @Test
    fun `sync failure is shown and cleared by the next refresh`() = runTest {
        coEvery { repository.refresh() } returns Result.failure(IOException("The action is not supported for OBJECT sheet."))
        val vm = vm()
        vm.state.test {
            assertThat(loaded().syncError).contains("OBJECT sheet")
            coEvery { repository.refresh() } returns Result.success(94)
            vm.refresh()
            var s = awaitItem(); while (s.refreshing) s = awaitItem()
            assertThat(s.syncError).isNull()
        }
    }

    @Test
    fun `export emits a share event`() = runTest {
        records.value = listOf(record(contractor = "A"))
        val intent = mockk<Intent>()
        every { exporter.exportPdf(any()) } returns File("x.pdf")
        every { exporter.shareIntent(any()) } returns intent
        val vm = vm()
        vm.state.test {
            loaded()
            vm.events.test {
                vm.exportPdf()
                assertThat(awaitItem()).isEqualTo(PortalEvent.Share(intent))
            }
            cancelAndIgnoreRemainingEvents()
        }
    }
}
