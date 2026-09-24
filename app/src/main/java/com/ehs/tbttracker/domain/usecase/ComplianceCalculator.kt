package com.ehs.tbttracker.domain.usecase

import com.ehs.tbttracker.domain.model.TbtRecord
import com.ehs.tbttracker.domain.parsing.ContractorNormalizer
import java.time.LocalDate
import java.time.YearMonth

/** One calendar day for one contractor in the audit sheet. */
data class DayStatus(
    val date: LocalDate,
    val records: List<TbtRecord>,
    /** True for days after today: shown as "Upcoming", never as a miss. */
    val upcoming: Boolean,
) {
    val done: Boolean get() = records.isNotEmpty()
    val manpower: Int get() = records.sumOf { it.manpower }
    val locations: String get() = records.map { it.location }.filter { it.isNotBlank() }.distinct().joinToString(", ")
}

/** "Contractor Wise TBT Details & Audit Sheet" for one contractor and month. */
data class ContractorMonthReport(
    val contractor: String,
    val month: YearMonth,
    val days: List<DayStatus>,
) {
    val daysInMonth: Int get() = month.lengthOfMonth()
    val doneDays: List<DayStatus> get() = days.filter { it.done }
    val notDoneDays: List<DayStatus> get() = days.filter { !it.done }
    val sessions: Int get() = days.sumOf { it.records.size }
    val manpower: Int get() = days.sumOf { it.manpower }
    /** Done days over all days of the month (same basis as the web portal). */
    val complianceRate: Int get() = if (daysInMonth == 0) 0 else Math.round(doneDays.size * 100f / daysInMonth)
    val avgWorkersPerDay: Double get() = if (doneDays.isEmpty()) 0.0 else manpower.toDouble() / doneDays.size
}

data class DateSummary(val date: LocalDate, val records: List<TbtRecord>) {
    val manpower: Int get() = records.sumOf { it.manpower }
    val contractors: List<String> get() = records.map { it.contractor }.distinct().sorted()
}

/** One day in the "Missing TBT Audit (1st to End)". */
data class MissingDay(val date: LocalDate, val reported: List<String>, val missing: List<String>, val upcoming: Boolean)

/** One row of the contractor x day "Monthly Grid"; cell value = manpower, 0 = done with no headcount, null = not done. */
data class GridRow(val contractor: String, val cells: List<Int?>) {
    val doneDays: Int get() = cells.count { it != null }
}

data class MasterContractorSummary(
    val name: String,
    val totalSessions: Int,
    val totalManpower: Int,
    val lastTbt: LocalDate?,
    val doneThisMonth: Int,
    /** True when the name comes from the Master Contractors tab. */
    val registered: Boolean,
)

/**
 * Pure calculations behind every portal tab. Master contractor names (from an optional
 * "Master Contractors" tab) are merged with names seen in the form responses; a master spelling
 * wins when both refer to the same agency.
 */
class ComplianceCalculator(records: List<TbtRecord>, masters: List<String>, private val today: LocalDate) {

    private val masterByKey: Map<String, String> = masters.map { it.trim().replace(Regex("""\s+"""), " ") }
        .filter { it.isNotEmpty() }
        .associateBy { ContractorNormalizer.key(it) }

    /** Records with contractor names mapped onto the master list where they match. */
    val records: List<TbtRecord> = records.map { r -> r.copy(contractor = displayName(r.contractor)) }

    private val masterNames: Set<String> = masterByKey.values.toSet()

    /** Master contractors plus any contractor that reported without being on the master list. */
    val allContractors: List<String> =
        (masterByKey.values + this.records.map { it.contractor }).distinct().sortedBy { it.lowercase() }

    val registeredCount: Int get() = allContractors.size

    val months: List<YearMonth> =
        (this.records.mapNotNull { r -> r.date?.let { YearMonth.from(it) } } + YearMonth.from(today)).distinct().sortedDescending()

    fun displayName(name: String): String {
        val k = ContractorNormalizer.key(name)
        masterByKey[k]?.let { return it }
        return masterByKey.entries.firstOrNull { (mk, _) ->
            ContractorNormalizer.isPrefixMatch(mk, k) || ContractorNormalizer.isPrefixMatch(k, mk)
        }?.value ?: name
    }

    fun isRegistered(name: String) = name in masterNames

    private fun inMonth(r: TbtRecord, month: YearMonth) = r.date != null && YearMonth.from(r.date) == month

    fun contractorReport(contractor: String, month: YearMonth): ContractorMonthReport {
        val mine = records.filter { it.contractor == contractor && inMonth(it, month) }.groupBy { it.date!! }
        val days = (1..month.lengthOfMonth()).map { d ->
            val date = month.atDay(d)
            DayStatus(date, mine[date].orEmpty().sortedBy { it.timestamp }, upcoming = date.isAfter(today))
        }
        return ContractorMonthReport(contractor, month, days)
    }

    /** Done-day count per contractor for the chips ("12 Done"). */
    fun doneCounts(month: YearMonth): Map<String, Int> {
        val done = records.filter { inMonth(it, month) }.groupBy { it.contractor }.mapValues { (_, rs) -> rs.mapNotNull { it.date }.distinct().size }
        return allContractors.associateWith { done[it] ?: 0 }
    }

    /** Every date that has at least one TBT, newest first. */
    fun dateWise(month: YearMonth? = null): List<DateSummary> =
        records.filter { it.date != null && (month == null || inMonth(it, month)) }
            .groupBy { it.date!! }
            .map { (d, rs) -> DateSummary(d, rs.sortedBy { it.timestamp }) }
            .sortedByDescending { it.date }

    val distinctDates: Int get() = records.mapNotNull { it.date }.distinct().size

    /**
     * 1st to end of month: which contractors did not report each day. Only contractors active in
     * that month are expected, so dormant master entries do not flood the audit.
     */
    fun missingAudit(month: YearMonth): List<MissingDay> {
        val monthRecords = records.filter { inMonth(it, month) }
        val expected = monthRecords.map { it.contractor }.distinct().sorted()
        val byDate = monthRecords.groupBy { it.date!! }
        return (1..month.lengthOfMonth()).map { d ->
            val date = month.atDay(d)
            val reported = byDate[date].orEmpty().map { it.contractor }.distinct().sorted()
            MissingDay(date, reported, expected - reported.toSet(), upcoming = date.isAfter(today))
        }
    }

    /** Contractor x day matrix for the month; contractors with no TBT that month are listed last. */
    fun monthlyGrid(month: YearMonth): List<GridRow> {
        val byContractor = records.filter { inMonth(it, month) }.groupBy { it.contractor }
        return allContractors.map { c ->
            val byDay = byContractor[c].orEmpty().groupBy { it.date!!.dayOfMonth }
            GridRow(c, (1..month.lengthOfMonth()).map { d -> byDay[d]?.sumOf { it.manpower } })
        }.sortedWith(compareByDescending<GridRow> { it.doneDays }.thenBy { it.contractor.lowercase() })
    }

    fun masterSummaries(month: YearMonth): List<MasterContractorSummary> {
        val counts = doneCounts(month)
        val byContractor = records.groupBy { it.contractor }
        return allContractors.map { c ->
            val rs = byContractor[c].orEmpty()
            MasterContractorSummary(
                name = c,
                totalSessions = rs.size,
                totalManpower = rs.sumOf { it.manpower },
                lastTbt = rs.mapNotNull { it.date }.maxOrNull(),
                doneThisMonth = counts[c] ?: 0,
                registered = isRegistered(c),
            )
        }.sortedWith(compareByDescending<MasterContractorSummary> { it.doneThisMonth }.thenBy { it.name.lowercase() })
    }

    /** The contractor with the most TBT days in the month; the portal opens on it. */
    fun defaultContractor(month: YearMonth): String? =
        doneCounts(month).entries.filter { it.value > 0 }.maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key })?.key
            ?: allContractors.firstOrNull()
}
