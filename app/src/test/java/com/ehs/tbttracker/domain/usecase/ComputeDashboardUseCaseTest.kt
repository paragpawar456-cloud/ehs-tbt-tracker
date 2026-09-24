package com.ehs.tbttracker.domain.usecase

import com.ehs.tbttracker.data.local.toDomain
import com.ehs.tbttracker.data.remote.SheetRowMapper
import com.ehs.tbttracker.domain.model.RangePreset
import com.ehs.tbttracker.domain.model.RecordFilter
import com.ehs.tbttracker.domain.model.SyncState
import com.ehs.tbttracker.domain.parsing.ContractorNormalizer
import com.ehs.tbttracker.testutil.Fixtures
import com.ehs.tbttracker.testutil.Fixtures.TODAY
import com.ehs.tbttracker.testutil.Fixtures.record
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ComputeDashboardUseCaseTest {
    private val compute = ComputeDashboardUseCase()

    @Test
    fun `today KPIs`() {
        val records = listOf(
            record(contractor = "A Co", manpower = 10),
            record(contractor = "A Co", manpower = 5),
            record(contractor = "B Co", manpower = 7),
            record(contractor = "C Co", manpower = 3, date = TODAY.minusDays(1)),
        )
        val s = compute(records, RecordFilter(), TODAY)
        assertThat(s.todayTbtCount).isEqualTo(3)
        assertThat(s.todayManpower).isEqualTo(22)
        assertThat(s.activeContractorsToday).isEqualTo(2)
        assertThat(s.totalContractors).isEqualTo(3)
    }

    @Test
    fun `distribution is sorted by manpower and counts sessions`() {
        val records = listOf(record(contractor = "A", manpower = 4), record(contractor = "B", manpower = 9), record(contractor = "A", manpower = 4))
        val d = compute(records, RecordFilter(), TODAY).distribution
        assertThat(d.map { it.contractor }).containsExactly("B", "A").inOrder()
        assertThat(d[1].manpower).isEqualTo(8)
        assertThat(d[1].sessions).isEqualTo(2)
    }

    @Test
    fun `pending and failed rows are counted for the sync badge`() {
        val s = compute(listOf(record(sync = SyncState.PENDING), record(sync = SyncState.FAILED), record()), RecordFilter(), TODAY)
        assertThat(s.pendingSyncCount).isEqualTo(2)
    }

    @Nested
    inner class Filters {
        private val rows = listOf(
            record(contractor = "A", date = TODAY, location = "Tower B1 P2"),
            record(contractor = "B", date = TODAY.minusDays(3), location = "NTA Parking"),
            record(contractor = "A", date = TODAY.minusDays(10), location = "Level 09", notes = "Harness check"),
        )

        @Test fun `date range is inclusive`() {
            val out = ComputeDashboardUseCase.applyFilter(rows, RecordFilter(from = TODAY.minusDays(3), to = TODAY))
            assertThat(out).hasSize(2)
        }

        @Test fun `contractor filter`() {
            assertThat(ComputeDashboardUseCase.applyFilter(rows, RecordFilter(contractor = "A"))).hasSize(2)
        }

        @Test fun `query matches location contractor and notes case-insensitively`() {
            assertThat(ComputeDashboardUseCase.applyFilter(rows, RecordFilter(query = "nta"))).hasSize(1)
            assertThat(ComputeDashboardUseCase.applyFilter(rows, RecordFilter(query = "HARNESS"))).hasSize(1)
        }

        @Test fun `filter affects distribution but not today KPIs`() {
            val s = compute(rows, RecordFilter(contractor = "B"), TODAY)
            assertThat(s.todayTbtCount).isEqualTo(1)
            assertThat(s.distribution.map { it.contractor }).containsExactly("B")
        }
    }

    @Test
    fun `yesterday figures feed the vs-yesterday deltas`() {
        val s = compute(
            listOf(record(contractor = "A", manpower = 4), record(contractor = "B", manpower = 6, date = TODAY.minusDays(1)),
                record(contractor = "C", manpower = 2, date = TODAY.minusDays(1))),
            RecordFilter(), TODAY,
        )
        assertThat(listOf(s.yesterdayTbtCount, s.yesterdayManpower, s.activeContractorsYesterday)).containsExactly(2, 8, 2).inOrder()
    }

    @Test
    fun `trend covers 14 days ending today, zero-filled`() {
        val s = compute(listOf(record(manpower = 9), record(manpower = 3, date = TODAY.minusDays(13)), record(manpower = 50, date = TODAY.minusDays(14))), RecordFilter(), TODAY)
        assertThat(s.trend).hasSize(14)
        assertThat(s.trend.first().date).isEqualTo(TODAY.minusDays(13))
        assertThat(s.trend.first().manpower).isEqualTo(3)
        assertThat(s.trend.last().manpower).isEqualTo(9)
        assertThat(s.trend.subList(1, 13).all { it.manpower == 0 }).isTrue()
    }

    @Test
    fun `not reported lists contractors active in the last 7 days without a TBT today`() {
        val s = compute(
            listOf(
                record(contractor = "Reported", date = TODAY),
                record(contractor = "Reported", date = TODAY.minusDays(2)),
                record(contractor = "Missing", date = TODAY.minusDays(3)),
                record(contractor = "Missing", date = TODAY.minusDays(1)),
                record(contractor = "Dormant", date = TODAY.minusDays(8)),
            ),
            RecordFilter(), TODAY,
        )
        assertThat(s.notReportedToday.map { it.contractor }).containsExactly("Missing")
        assertThat(s.notReportedToday.single().lastReported).isEqualTo(TODAY.minusDays(1))
        assertThat(s.reportedToday).containsExactly("Reported")
    }

    @Test
    fun `range presets translate to inclusive date windows`() {
        assertThat(RangePreset.TODAY.toFilter(TODAY, RecordFilter()).from).isEqualTo(TODAY)
        assertThat(RangePreset.WEEK.toFilter(TODAY, RecordFilter()).from).isEqualTo(TODAY.minusDays(6))
        assertThat(RangePreset.ALL.toFilter(TODAY, RecordFilter(contractor = "A")).let { it.from == null && it.contractor == "A" }).isTrue()
    }

    @Test
    fun `real sheet snapshot - 24 Sep 2026`() {
        val entities = Fixtures.realSheet().rows.map { SheetRowMapper.toEntity(it) }
        val normalizer = ContractorNormalizer(entities.map { it.contractorName })
        val records = entities.map { it.toDomain(normalizer) }

        val s = compute(records, RecordFilter(), TODAY)

        assertThat(s.todayTbtCount).isEqualTo(7)
        assertThat(s.todayManpower).isEqualTo(71)
        assertThat(s.activeContractorsToday).isEqualTo(7)
        assertThat(s.totalContractors).isEqualTo(15)
        assertThat(s.distribution.first().contractor).isEqualTo("Credible Construction Company")
        assertThat(s.distribution.first().manpower).isEqualTo(386)
        assertThat(s.distribution.first { it.contractor == "Alu-wind Infratech" }.sessions).isEqualTo(16)
        assertThat(listOf(s.yesterdayTbtCount, s.yesterdayManpower)).containsExactly(5, 68).inOrder()
        assertThat(s.trend.map { it.manpower }.takeLast(3)).containsExactly(82, 68, 71).inOrder()
        assertThat(s.notReportedToday.map { it.contractor }).containsExactly("Prachi Enterprises", "Ami Plumbing").inOrder()

        val week = compute(records, RangePreset.WEEK.toFilter(TODAY, RecordFilter()), TODAY)
        assertThat(week.filteredTbtCount).isEqualTo(31)
        assertThat(week.filteredManpower).isEqualTo(380)
    }
}
