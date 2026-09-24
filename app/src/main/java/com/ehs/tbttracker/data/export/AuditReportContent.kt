package com.ehs.tbttracker.data.export

import com.ehs.tbttracker.domain.model.PhotoRef
import com.ehs.tbttracker.domain.usecase.ContractorMonthReport
import com.ehs.tbttracker.domain.usecase.DayStatus
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Text and tables of the contractor audit, shared by the Excel and PDF exports.
 * Kept free of Android APIs so it is unit-tested; never pass these strings to String.format
 * (they contain literal "%" signs such as "43%").
 */
object AuditReportContent {
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

    fun avg(r: ContractorMonthReport): String = String.format(Locale.US, "%.1f", r.avgWorkersPerDay)

    fun monthLabel(r: ContractorMonthReport): String = r.month.format(monthFmt)

    fun status(d: DayStatus): String = when { d.done -> "TBT Done"; d.upcoming -> "Upcoming"; else -> "No TBT Done" }

    fun weekday(d: DayStatus): String = d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    /** Summary lines printed under the PDF title. */
    fun pdfSummary(r: ContractorMonthReport): List<String> = listOf(
        "${r.contractor}  |  ${monthLabel(r)}",
        "TBT done: ${r.doneDays.size} days (${r.sessions} sessions)    Not done: ${r.notDoneDays.size} of ${r.daysInMonth} days",
        "Compliance rate: ${r.complianceRate}%    Manpower trained: ${r.manpower} workers (avg ${avg(r)} per conducted day)",
    )

    fun excelSheets(r: ContractorMonthReport): List<XlsxWriter.Sheet> {
        val summary = listOf(
            listOf("TBT Safety Compliance Portal - Contractor Wise Audit", null),
            listOf("Contractor", r.contractor),
            listOf("Month", monthLabel(r)),
            listOf("TBT done (days)", r.doneDays.size),
            listOf("Sessions", r.sessions),
            listOf("Days not done", r.notDoneDays.size),
            listOf("Days in month", r.daysInMonth),
            listOf("Compliance rate (%)", r.complianceRate),
            listOf("Total manpower trained", r.manpower),
            listOf("Avg workers per conducted day", avg(r)),
        )
        val daily = listOf(listOf("Day", "Date", "Weekday", "Status", "Sessions", "Manpower", "Location", "Photo")) +
            r.days.map { d ->
                listOf(
                    d.date.dayOfMonth, d.date.toString(), weekday(d), status(d), d.records.size,
                    if (d.done) d.manpower else null, d.locations,
                    d.records.mapNotNull { (it.photo as? PhotoRef.Drive)?.originalUrl }.joinToString(" "),
                )
            }
        return listOf(XlsxWriter.Sheet("Summary", summary), XlsxWriter.Sheet("Daily audit", daily))
    }
}
