package com.ehs.tbttracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ehs.tbttracker.domain.model.DashboardStats
import com.ehs.tbttracker.domain.model.RangePreset
import com.ehs.tbttracker.domain.model.RecordFilter
import com.ehs.tbttracker.domain.model.SyncState
import com.ehs.tbttracker.domain.model.TbtRecord
import com.ehs.tbttracker.domain.repository.ConnectivityObserver
import com.ehs.tbttracker.domain.repository.TbtRepository
import com.ehs.tbttracker.domain.usecase.ComputeDashboardUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** One day in the inspection log. */
data class LogDay(val date: LocalDate?, val entries: List<TbtRecord>) {
    val manpower: Int get() = entries.sumOf { it.manpower }
}

/** Everything the viewer can change on the dashboard. */
data class DashboardControls(
    val range: RangePreset = RangePreset.WEEK,
    val contractor: String? = null,
    val query: String = "",
    val daysShown: Int = PAGE_DAYS,
) {
    companion object { const val PAGE_DAYS = 7 }
}

data class DashboardUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isOnline: Boolean = true,
    val stats: DashboardStats,
    val controls: DashboardControls = DashboardControls(),
    val contractors: List<String> = emptyList(),
    val log: List<LogDay> = emptyList(),
    val hasMoreDays: Boolean = false,
    val filteredCount: Int = 0,
    val totalRows: Int = 0,
    val failedCount: Int = 0,
    val message: String? = null,
    /** Last refresh error, cleared by the next successful refresh. */
    val syncError: String? = null,
) {
    /** Kept for callers/tests that read the filter directly. */
    val filter: RecordFilter get() = RecordFilter(contractor = controls.contractor, query = controls.query)
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: TbtRepository,
    private val computeDashboard: ComputeDashboardUseCase,
    connectivity: ConnectivityObserver,
    private val clock: Clock,
) : ViewModel() {

    private val controls = MutableStateFlow(DashboardControls())
    private data class SyncUi(val refreshing: Boolean = false, val message: String? = null, val error: String? = null)
    private val sync = MutableStateFlow(SyncUi())

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.observeRecords(),
        controls,
        connectivity.isOnline.onStart { emit(connectivity.isCurrentlyOnline()) },
        sync,
    ) { records, c, online, syncUi ->
        val today = LocalDate.now(clock)
        val filter = c.range.toFilter(today, RecordFilter(contractor = c.contractor, query = c.query))
        val filtered = ComputeDashboardUseCase.applyFilter(records, filter)
        val days = filtered.groupBy { it.date }.map { (d, rows) -> LogDay(d, rows) }
            .sortedWith(compareByDescending(nullsFirst<LocalDate>()) { it.date })
        DashboardUiState(
            isLoading = false,
            isRefreshing = syncUi.refreshing,
            isOnline = online,
            stats = computeDashboard(records, filter, today),
            controls = c,
            contractors = records.map { it.contractor }.distinct().sorted(),
            log = days.take(c.daysShown),
            hasMoreDays = days.size > c.daysShown,
            filteredCount = filtered.size,
            totalRows = records.size,
            failedCount = records.count { it.syncState == SyncState.FAILED },
            message = syncUi.message,
            syncError = syncUi.error,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DashboardUiState(stats = DashboardStats.empty(LocalDate.now(clock))),
    )

    init {
        refresh()
    }

    fun refresh() {
        if (sync.value.refreshing) return
        sync.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            repository.refresh()
                .onSuccess { sync.update { it.copy(refreshing = false, error = null) } }
                .onFailure { e ->
                    val reason = e.message ?: "sync failed"
                    sync.update { it.copy(refreshing = false, message = "Showing saved data - $reason", error = reason) }
                }
        }
    }

    fun setRange(range: RangePreset) = controls.update { it.copy(range = range, daysShown = DashboardControls.PAGE_DAYS) }
    fun setContractor(name: String?) = controls.update { it.copy(contractor = name) }
    fun setQuery(q: String) = controls.update { it.copy(query = q) }
    fun showMoreDays() = controls.update { it.copy(daysShown = it.daysShown + DashboardControls.PAGE_DAYS) }
    fun clearFilters() { controls.value = DashboardControls() }
    fun retryFailed() { viewModelScope.launch { repository.retryFailed() } }
    fun messageShown() = sync.update { it.copy(message = null) }
}
