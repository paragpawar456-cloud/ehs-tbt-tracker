package com.ehs.tbttracker.domain.usecase

import com.ehs.tbttracker.data.local.toDomain
import com.ehs.tbttracker.data.remote.SheetRowMapper
import com.ehs.tbttracker.domain.parsing.ContractorNormalizer
import com.ehs.tbttracker.testutil.Fixtures
import com.ehs.tbttracker.testutil.Fixtures.TODAY
import com.ehs.tbttracker.testutil.Fixtures.record
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

class ComplianceCalculatorTest {
    private val sep = YearMonth.of(2026, 9)

    private fun realRecords() = Fixtures.realSheet().rows.map { SheetRowMapper.toEntity(it) }.let { es ->
        val n = ContractorNormalizer(es.map { it.contractorName }); es.map { it.toDomain(n) }
    }

    @Test
    fun `AMI Plumbing September matches the web portal`() {
        val calc = ComplianceCalculator(realRecords(), listOf("AMI Plumbing"), TODAY)
        val r = calc.contractorReport("AMI Plumbing", sep)
        assertThat(r.doneDays).hasSize(13)
        assertThat(r.sessions).isEqualTo(13)
        assertThat(r.notDoneDays).hasSize(17)
        assertThat(r.daysInMonth).isEqualTo(30)
        assertThat(r.complianceRate).isEqualTo(43)
        assertThat(r.manpower).isEqualTo(77)
        assertThat("%.1f".format(java.util.Locale.US, r.avgWorkersPerDay)).isEqualTo("5.9")
        assertThat(r.doneDays.first().date).isEqualTo(LocalDate.of(2026, 9, 5))
        assertThat(r.doneDays.first().manpower).isEqualTo(7)
        assertThat(r.notDoneDays.first().date).isEqualTo(LocalDate.of(2026, 9, 1))
        assertThat(r.notDoneDays.last().upcoming).isTrue()
    }

    @Test
    fun `27 distinct TBT dates and master names win over form spellings`() {
        val calc = ComplianceCalculator(realRecords(), listOf("AMI Plumbing", "Vruddhi Steel Limited"), TODAY)
        assertThat(calc.distinctDates).isEqualTo(27)
        assertThat(calc.allContractors).contains("AMI Plumbing")
        assertThat(calc.allContractors).doesNotContain("Ami Plumbing")
        assertThat(calc.doneCounts(sep)["Vruddhi Steel Limited"]).isEqualTo(0)
        assertThat(calc.registeredCount).isEqualTo(16) // 15 from responses + Vruddhi (AMI merges with Ami plumbing)
    }

    @Test
    fun `missing audit expects only contractors active in the month`() {
        val calc = ComplianceCalculator(
            listOf(record(contractor = "A", date = TODAY), record(contractor = "B", date = TODAY.minusDays(1))),
            listOf("Dormant Co"), TODAY,
        )
        val day = calc.missingAudit(YearMonth.from(TODAY)).first { it.date == TODAY }
        assertThat(day.reported).containsExactly("A")
        assertThat(day.missing).containsExactly("B")
        assertThat(calc.missingAudit(YearMonth.from(TODAY)).last().upcoming).isTrue()
    }

    @Test
    fun `monthly grid holds manpower per day and sorts by done days`() {
        val calc = ComplianceCalculator(
            listOf(record(contractor = "A", manpower = 4, date = TODAY), record(contractor = "A", manpower = 3, date = TODAY),
                record(contractor = "B", manpower = 9, date = TODAY.minusDays(2)), record(contractor = "B", manpower = 1, date = TODAY.minusDays(1))),
            emptyList(), TODAY,
        )
        val grid = calc.monthlyGrid(YearMonth.from(TODAY))
        assertThat(grid.map { it.contractor }).containsExactly("B", "A").inOrder()
        assertThat(grid[1].cells[TODAY.dayOfMonth - 1]).isEqualTo(7)
        assertThat(grid[1].cells[0]).isNull()
    }

    @Test
    fun `default contractor is the one with most TBT days`() {
        val calc = ComplianceCalculator(realRecords(), emptyList(), TODAY)
        assertThat(calc.defaultContractor(sep)).isEqualTo("Choudhary Construction")
        assertThat(calc.months).containsExactly(sep, YearMonth.of(2026, 8)).inOrder()
    }

    @Test
    fun `daily totals and contractor share for September`() {
        val calc = ComplianceCalculator(realRecords(), emptyList(), TODAY)
        val daily = calc.dailyTotals(sep)
        assertThat(daily).hasSize(30)
        assertThat(daily.sumOf { it.manpower }).isEqualTo(1038)
        assertThat(daily[21].manpower).isEqualTo(82)
        assertThat(daily[21].sessions).isEqualTo(7)
        assertThat(calc.dailyTotals(sep, "Ami Plumbing").sumOf { it.manpower }).isEqualTo(77)

        val share = calc.contractorShare(sep)
        assertThat(share).hasSize(6)
        assertThat(share.first().label).isEqualTo("Credible Construction Company")
        assertThat(share.first().value).isEqualTo(386)
        assertThat(share.last().isOther).isTrue()
        assertThat(share.last().label).isEqualTo("Other (8)")
        assertThat(share.sumOf { it.value }).isEqualTo(1038)
    }

    @Test
    fun `location share merges spelling variants`() {
        val calc = ComplianceCalculator(realRecords(), emptyList(), TODAY)
        val ami = calc.locationShare("Ami Plumbing", sep)
        assertThat(ami).hasSize(1)
        assertThat(ami.single().value).isEqualTo(13)
        val choudhary = calc.locationShare("Choudhary Construction", sep)
        assertThat(choudhary.first().label).isEqualTo("Tower D1 Laval 09")
        assertThat(choudhary.first().value).isEqualTo(8)
        assertThat(choudhary.sumOf { it.value }).isEqualTo(15)
    }
}
