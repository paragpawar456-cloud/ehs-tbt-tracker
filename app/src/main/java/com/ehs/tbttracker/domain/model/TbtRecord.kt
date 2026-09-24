package com.ehs.tbttracker.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

enum class SyncState { SYNCED, PENDING, FAILED }

/** One Toolbox Talk session, i.e. one row of "Contractor Daily Tbt details". */
data class TbtRecord(
    val id: String,
    val timestamp: LocalDateTime?,
    val date: LocalDate?,
    /** Exactly as typed in the sheet (trimmed). */
    val contractorRaw: String,
    /** Canonical display name after [com.ehs.tbttracker.domain.parsing.ContractorNormalizer]. */
    val contractor: String,
    val manpower: Int,
    val location: String,
    val photo: PhotoRef?,
    val notes: String,
    val syncState: SyncState,
    val rowNumber: Int? = null,
    val lastError: String? = null,
)

/** Where the evidence photo lives. */
sealed interface PhotoRef {
    /** Captured on-device, not uploaded yet. */
    data class Local(val path: String) : PhotoRef
    /** Google Drive file (Forms uploads and our Apps Script uploads). */
    data class Drive(val fileId: String, val originalUrl: String) : PhotoRef
    /** Any other direct image URL. */
    data class Url(val url: String) : PhotoRef
}

/** What the form produces before it becomes a [TbtRecord]. */
data class TbtDraft(
    val date: LocalDate,
    val contractor: String,
    val manpower: Int,
    val location: String,
    val notes: String,
    /** Compressed + watermarked JPEG on local storage. */
    val photoPath: String,
)

data class RecordFilter(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val contractor: String? = null,
    val query: String = "",
) {
    val isActive: Boolean get() = from != null || to != null || contractor != null || query.isNotBlank()
}

data class ContractorShare(val contractor: String, val manpower: Int, val sessions: Int)

/** Workers briefed on one calendar day (for the 14-day trend). */
data class DayTotal(val date: LocalDate, val manpower: Int, val sessions: Int)

/** A contractor that reported recently but has no TBT today. */
data class MissingContractor(val contractor: String, val lastReported: LocalDate)

data class DashboardStats(
    val today: LocalDate,
    val todayTbtCount: Int,
    val todayManpower: Int,
    val activeContractorsToday: Int,
    val totalContractors: Int,
    val yesterdayTbtCount: Int,
    val yesterdayManpower: Int,
    val activeContractorsYesterday: Int,
    /** Last [TREND_DAYS] days, oldest first, today last. */
    val trend: List<DayTotal>,
    /** Active in the previous [LOOKBACK_DAYS] days but no TBT today, most recent first. */
    val notReportedToday: List<MissingContractor>,
    val reportedToday: List<String>,
    /** Manpower per contractor for the filtered range, largest first. */
    val distribution: List<ContractorShare>,
    val filteredTbtCount: Int,
    val filteredManpower: Int,
    val pendingSyncCount: Int,
) {
    companion object {
        const val TREND_DAYS = 14
        const val LOOKBACK_DAYS = 7L

        fun empty(today: LocalDate) = DashboardStats(
            today, 0, 0, 0, 0, 0, 0, 0,
            (TREND_DAYS - 1 downTo 0).map { DayTotal(today.minusDays(it.toLong()), 0, 0) },
            emptyList(), emptyList(), emptyList(), 0, 0, 0,
        )
    }
}

/** Date-range presets used by the dashboard's segmented control. */
enum class RangePreset(val days: Int?, val label: String) {
    TODAY(1, "Today"), WEEK(7, "7 days"), MONTH(30, "30 days"), ALL(null, "All");

    fun toFilter(today: LocalDate, base: RecordFilter): RecordFilter =
        base.copy(from = days?.let { today.minusDays(it - 1L) }, to = days?.let { today })
}
