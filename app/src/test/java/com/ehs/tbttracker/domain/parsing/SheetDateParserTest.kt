package com.ehs.tbttracker.domain.parsing

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate
import java.time.LocalDateTime

class SheetDateParserTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "2026-09-12, 2026-09-12",   // ISO (rows up to 13 Sep)
        "9/14/2026, 2026-09-14",    // US locale (rows from 14 Sep)
        "09/05/2026, 2026-09-05",   // zero padded US
        "25/09/2026, 2026-09-25",   // day > 12 forces d/M
        "2026-9-1, 2026-09-01",
        "9/14/26, 2026-09-14",      // two-digit year
        "46289, 2026-09-24",        // Sheets serial number
        "'2026-08-25 12:22:18', 2026-08-25", // timestamp in date column
    )
    fun `normalises mixed date formats to ISO`(raw: String, expected: String) {
        assertThat(SheetDateParser.parseDate(raw)).isEqualTo(LocalDate.parse(expected))
    }

    @Test
    fun `rejects garbage and impossible dates`() {
        assertThat(SheetDateParser.parseDate("")).isNull()
        assertThat(SheetDateParser.parseDate("tomorrow")).isNull()
        assertThat(SheetDateParser.parseDate("2026-02-30")).isNull()
        assertThat(SheetDateParser.parseDate("13/13/2026")).isNull()
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "'2026-08-25 12:22:18', 2026-08-25T12:22:18",
        "'9/14/2026 9:27:34', 2026-09-14T09:27:34",
        "'9/14/2026 16:07:40', 2026-09-14T16:07:40",
        "'9/14/2026 4:07:40 PM', 2026-09-14T16:07:40",
        "'9/14/2026 12:05:00 AM', 2026-09-14T00:05:00",
        "2026-09-14T09:27:34, 2026-09-14T09:27:34",
        "9/14/2026, 2026-09-14T00:00",
    )
    fun `parses timestamps in both sheet locales`(raw: String, expected: String) {
        assertThat(SheetDateParser.parseTimestamp(raw)).isEqualTo(LocalDateTime.parse(expected))
    }

    @Test
    fun `formats as ISO for the backend`() {
        assertThat(SheetDateParser.format(LocalDate.of(2026, 9, 4))).isEqualTo("2026-09-04")
    }
}
