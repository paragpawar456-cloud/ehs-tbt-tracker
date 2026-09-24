package com.ehs.tbttracker.domain.usecase

import com.ehs.tbttracker.domain.model.ContractorShare
import com.ehs.tbttracker.domain.model.DashboardStats
import com.ehs.tbttracker.domain.model.DayTotal
import com.ehs.tbttracker.domain.model.MissingContractor
import com.ehs.tbttracker.domain.model.RecordFilter
import com.ehs.tbttracker.domain.model.SyncState
import com.ehs.tbttracker.domain.model.TbtRecord
import java.time.LocalDate
import javax.inject.Inject

/** Pure aggregation for the executive dashboard. */
class ComputeDashboardUseCase @Inject constructor() {

    operator fun invoke(records: List<TbtRecord>, filter: RecordFilter, today: LocalDate): DashboardStats {
        val byDate = records.groupBy { it.date }
        val todays = byDate[today].orEmpty()
        val yesterdays = byDate[today.minusDays(1)].orEmpty()
        val filtered = applyFilter(records, filter)
        val distribution = filtered.groupBy { it.contractor }
            .map { (name, rows) -> ContractorShare(name, rows.sumOf { it.manpower }, rows.size) }
            .sortedWith(compareByDescending<ContractorShare> { it.manpower }.thenBy { it.contractor })

        val trend = (DashboardStats.TREND_DAYS - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val rows = byDate[d].orEmpty()
            DayTotal(d, rows.sumOf { it.manpower }, rows.size)
        }

        val reportedToday = todays.map { it.contractor }.toSortedSet()
        val lookbackStart = today.minusDays(DashboardStats.LOOKBACK_DAYS)
        val notReported = records
            .filter { r -> r.date != null && !r.date.isBefore(lookbackStart) && r.date.isBefore(today) && r.contractor !in reportedToday }
            .groupBy { it.contractor }
            .map { (name, rows) -> MissingContractor(name, rows.maxOf { it.date!! }) }
            .sortedWith(compareByDescending<MissingContractor> { it.lastReported }.thenBy { it.contractor })

        return DashboardStats(
            today = today,
            todayTbtCount = todays.size,
            todayManpower = todays.sumOf { it.manpower },
            activeContractorsToday = reportedToday.size,
            totalContractors = records.map { it.contractor }.distinct().size,
            yesterdayTbtCount = yesterdays.size,
            yesterdayManpower = yesterdays.sumOf { it.manpower },
            activeContractorsYesterday = yesterdays.map { it.contractor }.distinct().size,
            trend = trend,
            notReportedToday = notReported,
            reportedToday = reportedToday.toList(),
            distribution = distribution,
            filteredTbtCount = filtered.size,
            filteredManpower = filtered.sumOf { it.manpower },
            pendingSyncCount = records.count { it.syncState != SyncState.SYNCED },
        )
    }

    companion object {
        fun applyFilter(records: List<TbtRecord>, f: RecordFilter): List<TbtRecord> {
            if (!f.isActive) return records
            val q = f.query.trim().lowercase()
            return records.filter { r ->
                (f.from == null || (r.date != null && !r.date.isBefore(f.from))) &&
                    (f.to == null || (r.date != null && !r.date.isAfter(f.to))) &&
                    (f.contractor == null || r.contractor == f.contractor) &&
                    (q.isEmpty() || r.location.lowercase().contains(q) ||
                        r.contractor.lowercase().contains(q) || r.notes.lowercase().contains(q))
            }
        }
    }
}
