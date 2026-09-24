package com.ehs.tbttracker.data.export

import com.ehs.tbttracker.data.local.toDomain
import com.ehs.tbttracker.data.remote.SheetRowMapper
import com.ehs.tbttracker.domain.parsing.ContractorNormalizer
import com.ehs.tbttracker.domain.usecase.ComplianceCalculator
import com.ehs.tbttracker.testutil.Fixtures
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.time.YearMonth

/** Regression: "43%    Manpower…" used to be passed to String.format and crashed Export PDF. */
class AuditReportContentTest {
    private val report = Fixtures.realSheet().rows.map { SheetRowMapper.toEntity(it) }.let { es ->
        val n = ContractorNormalizer(es.map { it.contractorName })
        ComplianceCalculator(es.map { it.toDomain(n) }, emptyList(), Fixtures.TODAY)
    }.let { it.contractorReport("Ami Plumbing", YearMonth.of(2026, 9)) }

    @Test
    fun `pdf summary keeps literal percent signs`() {
        val lines = AuditReportContent.pdfSummary(report)
        assertThat(lines[0]).isEqualTo("Ami Plumbing  |  September 2026")
        assertThat(lines[1]).isEqualTo("TBT done: 13 days (13 sessions)    Not done: 17 of 30 days")
        assertThat(lines[2]).isEqualTo("Compliance rate: 43%    Manpower trained: 77 workers (avg 5.9 per conducted day)")
    }

    @Test
    fun `excel sheets hold summary and every day of the month`() {
        val sheets = AuditReportContent.excelSheets(report)
        assertThat(sheets.map { it.name }).containsExactly("Summary", "Daily audit").inOrder()
        assertThat(sheets[0].rows).contains(listOf("Compliance rate (%)", 43))
        val daily = sheets[1].rows
        assertThat(daily).hasSize(31) // header + 30 days
        assertThat(daily[5]).containsAtLeast(5, "2026-09-05", "Sat", "TBT Done", 7).inOrder()
        assertThat(daily[1][3]).isEqualTo("No TBT Done")
        assertThat(daily[30][3]).isEqualTo("Upcoming")
        val out = ByteArrayOutputStream()
        XlsxWriter.write(out, sheets)
        assertThat(out.size()).isGreaterThan(1000)
    }
}
