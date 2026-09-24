package com.ehs.tbttracker.ui.dashboard

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.ehs.tbttracker.domain.model.RangePreset
import com.ehs.tbttracker.domain.model.TbtRecord
import com.ehs.tbttracker.domain.repository.ConnectivityObserver
import com.ehs.tbttracker.domain.repository.TbtRepository
import com.ehs.tbttracker.domain.usecase.ComputeDashboardUseCase
import com.ehs.tbttracker.testutil.Fixtures
import com.ehs.tbttracker.testutil.Fixtures.TODAY
import com.ehs.tbttracker.testutil.Fixtures.record
import com.ehs.tbttracker.testutil.MainDispatcherExtension
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException

class DashboardViewModelTest {
    @JvmField @RegisterExtension val main = MainDispatcherExtension()

    private val records = MutableStateFlow<List<TbtRecord>>(emptyList())
    private val online = MutableStateFlow(true)
    private val repository = mockk<TbtRepository>(relaxed = true)
    private val connectivity = mockk<ConnectivityObserver>()

    @BeforeEach
    fun setUp() {
        every { repository.observeRecords() } returns records
        every { connectivity.isOnline } returns online
        every { connectivity.isCurrentlyOnline() } returns true
        coEvery { repository.refresh() } returns Result.success(0)
    }

    /** Skips the stateIn placeholder so assertions only see combined states. */
    private suspend fun ReceiveTurbine<DashboardUiState>.awaitLoaded(): DashboardUiState {
        var s = awaitItem()
        while (s.isLoading) s = awaitItem()
        return s
    }

    private fun vm() = DashboardViewModel(repository, ComputeDashboardUseCase(), connectivity, Fixtures.CLOCK)

    @Test
    fun `refreshes from sheet on start`() = runTest {
        vm()
        coVerify(exactly = 1) { repository.refresh() }
    }

    @Test
    fun `emits KPIs as records arrive`() = runTest {
        val vm = vm()
        vm.uiState.test {
            assertThat(awaitLoaded().stats.todayTbtCount).isEqualTo(0)
            records.value = listOf(record(contractor = "A", manpower = 8), record(contractor = "B", manpower = 12))
            val s = awaitItem()
            assertThat(s.stats.todayTbtCount).isEqualTo(2)
            assertThat(s.stats.todayManpower).isEqualTo(20)
            assertThat(s.stats.activeContractorsToday).isEqualTo(2)
            assertThat(s.contractors).containsExactly("A", "B").inOrder()
            assertThat(s.stats.today).isEqualTo(TODAY)
        }
    }

    @Test
    fun `contractor filter narrows distribution`() = runTest {
        records.value = listOf(record(contractor = "A", manpower = 8), record(contractor = "B", manpower = 12))
        val vm = vm()
        vm.uiState.test {
            awaitLoaded()
            vm.setContractor("A")
            val s = awaitItem()
            assertThat(s.controls.contractor).isEqualTo("A")
            assertThat(s.stats.distribution.map { it.contractor }).containsExactly("A")
            vm.clearFilters()
            assertThat(awaitItem().stats.distribution).hasSize(2)
        }
    }

    @Test
    fun `range preset drives coverage and the day-grouped log`() = runTest {
        records.value = listOf(
            record(contractor = "A", manpower = 8),
            record(contractor = "A", manpower = 2),
            record(contractor = "B", manpower = 5, date = TODAY.minusDays(20)),
        )
        val vm = vm()
        vm.uiState.test {
            val week = awaitLoaded()
            assertThat(week.controls.range).isEqualTo(RangePreset.WEEK)
            assertThat(week.log.map { it.date }).containsExactly(TODAY)
            assertThat(week.log.single().manpower).isEqualTo(10)
            assertThat(week.stats.distribution.map { it.contractor }).containsExactly("A")

            vm.setRange(RangePreset.MONTH)
            val month = awaitItem()
            assertThat(month.log.map { it.date }).containsExactly(TODAY, TODAY.minusDays(20)).inOrder()
            assertThat(month.filteredCount).isEqualTo(3)
        }
    }

    @Test
    fun `log pages seven days at a time`() = runTest {
        records.value = (0L until 10L).map { record(date = TODAY.minusDays(it)) }
        val vm = vm()
        vm.setRange(RangePreset.ALL)
        vm.uiState.test {
            val first = awaitLoaded()
            assertThat(first.log).hasSize(7)
            assertThat(first.hasMoreDays).isTrue()
            vm.showMoreDays()
            val more = awaitItem()
            assertThat(more.log).hasSize(10)
            assertThat(more.hasMoreDays).isFalse()
        }
    }

    @Test
    fun `retry failed delegates to repository`() = runTest {
        vm().retryFailed()
        coVerify { repository.retryFailed() }
    }

    @Test
    fun `refresh toggles isRefreshing and surfaces failures as message`() = runTest {
        val gate = CompletableDeferred<Result<Int>>()
        coEvery { repository.refresh() } coAnswers { gate.await() }
        val vm = vm()
        vm.uiState.test {
            assertThat(awaitLoaded().isRefreshing).isTrue()
            gate.complete(Result.failure(IOException("Unable to resolve host")))
            val msgState = expectMostRecentItem()
            assertThat(msgState.isRefreshing).isFalse()
            assertThat(msgState.message).contains("Unable to resolve host")
            vm.messageShown()
            assertThat(awaitItem().message).isNull()
        }
    }

    @Test
    fun `offline state is reflected`() = runTest {
        val vm = vm()
        vm.uiState.test {
            assertThat(awaitLoaded().isOnline).isTrue()
            online.value = false
            assertThat(awaitItem().isOnline).isFalse()
        }
    }
}
