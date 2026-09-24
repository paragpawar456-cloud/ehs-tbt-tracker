package com.ehs.tbttracker.ui.portal

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ehs.tbttracker.data.export.AuditExporter
import com.ehs.tbttracker.domain.model.DayTotal
import com.ehs.tbttracker.domain.model.TbtRecord
import com.ehs.tbttracker.domain.usecase.ShareItem
import com.ehs.tbttracker.domain.repository.ConnectivityObserver
import com.ehs.tbttracker.domain.repository.TbtRepository
import com.ehs.tbttracker.domain.usecase.ComplianceCalculator
import com.ehs.tbttracker.domain.usecase.ContractorMonthReport
import com.ehs.tbttracker.domain.usecase.DateSummary
import com.ehs.tbttracker.domain.usecase.GridRow
import com.ehs.tbttracker.domain.usecase.MasterContractorSummary
import com.ehs.tbttracker.domain.usecase.MissingDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

enum class PortalTab(val title: String) {
    CONTRACTOR("Contractor Wise TBT"),
    DATE("Date Wise TBT"),
    MISSING("Missing TBT Audit"),
    GRID("Monthly Grid"),
    ANALYTICS("Analytics"),
    MASTERS("Master Contractors"),
    ALL("All Records"),
}

data class PortalSelection(
    val tab: PortalTab = PortalTab.CONTRACTOR,
    val month: YearMonth? = null,
    val contractor: String? = null,
    val contractorQuery: String = "",
    val recordQuery: String = "",
)

data class PortalUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val online: Boolean = true,
    val syncError: String? = null,
    val totalLogs: Int = 0,
    val registeredContractors: Int = 0,
    val distinctDates: Int = 0,
    val months: List<YearMonth> = emptyList(),
    val selection: PortalSelection = PortalSelection(),
    val month: YearMonth,
    val contractor: String? = null,
    /** Contractor chips: name to done-days in the selected month, filtered by the chip search. */
    val contractorChips: List<Pair<String, Int>> = emptyList(),
    val allContractors: List<String> = emptyList(),
    val report: ContractorMonthReport? = null,
    val dateWise: List<DateSummary> = emptyList(),
    val missing: List<MissingDay> = emptyList(),
    val grid: List<GridRow> = emptyList(),
    val masters: List<MasterContractorSummary> = emptyList(),
    val records: List<TbtRecord> = emptyList(),
    /** Chart data for the selected month. */
    val monthDaily: List<DayTotal> = emptyList(),
    val contractorDaily: List<DayTotal> = emptyList(),
    val contractorShare: List<ShareItem> = emptyList(),
    val locationShare: List<ShareItem> = emptyList(),
    val topByDays: List<Pair<String, Int>> = emptyList(),
    val today: LocalDate,
)

sealed interface PortalEvent {
    data class Share(val intent: Intent) : PortalEvent
    data class Message(val text: String) : PortalEvent
}

@HiltViewModel
class PortalViewModel @Inject constructor(
    private val repository: TbtRepository,
    connectivity: ConnectivityObserver,
    private val exporter: AuditExporter,
    private val clock: Clock,
) : ViewModel() {

    private data class Sync(val refreshing: Boolean = false, val error: String? = null)

    private val selection = MutableStateFlow(PortalSelection())
    private val sync = MutableStateFlow(Sync())
    private val _events = MutableSharedFlow<PortalEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<PortalEvent> = _events

    val state: StateFlow<PortalUiState> = combine(
        repository.observeRecords(),
        repository.observeMasterContractors(),
        selection,
        connectivity.isOnline.onStart { emit(connectivity.isCurrentlyOnline()) },
        sync,
    ) { records, masters, sel, online, s ->
        buildPortalState(records, masters, sel, online, s.refreshing, s.error, LocalDate.now(clock))
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PortalUiState(month = YearMonth.from(LocalDate.now(clock)), today = LocalDate.now(clock)),
    )

    init {
        refresh()
    }

    fun refresh() {
        if (sync.value.refreshing) return
        sync.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            repository.refresh()
                .onSuccess { sync.value = Sync() }
                .onFailure { e -> sync.value = Sync(error = e.message ?: "Sync failed") }
        }
    }

    fun selectTab(tab: PortalTab) = selection.update { it.copy(tab = tab) }
    fun selectMonth(month: YearMonth) = selection.update { it.copy(month = month) }
    fun selectContractor(name: String) = selection.update { it.copy(contractor = name) }
    fun setContractorQuery(q: String) = selection.update { it.copy(contractorQuery = q) }
    fun setRecordQuery(q: String) = selection.update { it.copy(recordQuery = q) }

    /** Opens the Contractor Wise tab on a contractor (from the grid, masters list, or audit chips). */
    fun openContractor(name: String) = selection.update { it.copy(tab = PortalTab.CONTRACTOR, contractor = name) }

    fun exportExcel() = export { exporter.exportExcel(it) }
    fun exportPdf() = export { exporter.exportPdf(it) }

    private fun export(block: (ContractorMonthReport) -> java.io.File) {
        val report = state.value.report ?: return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { block(report) } }
                .onSuccess { _events.tryEmit(PortalEvent.Share(exporter.shareIntent(it))) }
                .onFailure { _events.tryEmit(PortalEvent.Message("Export failed: ${it.message}")) }
        }
    }
}

/** Pure mapping from data + selection to screen state (used by the ViewModel and by screenshot tests). */
internal fun buildPortalState(
    records: List<TbtRecord>,
    masters: List<String>,
    sel: PortalSelection,
    online: Boolean,
    refreshing: Boolean,
    syncError: String?,
    today: LocalDate,
): PortalUiState {
    val calc = ComplianceCalculator(records, masters, today)
    val month = sel.month ?: YearMonth.from(today)
    val contractor = sel.contractor?.takeIf { it in calc.allContractors } ?: calc.defaultContractor(month)
    val counts = calc.doneCounts(month)
    val q = sel.contractorQuery.trim().lowercase()
    val rq = sel.recordQuery.trim().lowercase()
    return PortalUiState(
        loading = false,
        refreshing = refreshing,
        online = online,
        syncError = syncError,
        totalLogs = records.size,
        registeredContractors = calc.registeredCount,
        distinctDates = calc.distinctDates,
        months = calc.months,
        selection = sel,
        month = month,
        contractor = contractor,
        contractorChips = counts.entries
            .filter { q.isEmpty() || it.key.lowercase().contains(q) }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() })
            .map { it.key to it.value },
        allContractors = calc.allContractors,
        report = contractor?.let { calc.contractorReport(it, month) },
        dateWise = calc.dateWise(),
        missing = calc.missingAudit(month),
        grid = calc.monthlyGrid(month),
        masters = calc.masterSummaries(month),
        records = calc.records.filter { r ->
            rq.isEmpty() || r.contractor.lowercase().contains(rq) || r.location.lowercase().contains(rq) ||
                r.notes.lowercase().contains(rq) || r.date?.toString()?.contains(rq) == true
        },
        monthDaily = calc.dailyTotals(month),
        contractorDaily = contractor?.let { calc.dailyTotals(month, it) }.orEmpty(),
        contractorShare = calc.contractorShare(month),
        locationShare = contractor?.let { calc.locationShare(it, month) }.orEmpty(),
        topByDays = counts.entries.filter { it.value > 0 }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() })
            .take(8).map { it.key to it.value },
        today = today,
    )
}
