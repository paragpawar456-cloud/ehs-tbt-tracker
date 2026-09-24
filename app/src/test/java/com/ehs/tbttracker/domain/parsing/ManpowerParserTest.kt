package com.ehs.tbttracker.domain.parsing

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class ManpowerParserTest {

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(
        "5, 5",
        "06, 6",
        "'10 Labour', 10",
        "'10 Labour ', 10",
        "09nos, 9",
        "'09 nos', 9",
        "'Approx 35 workers', 35",
        "'12+3', 12",
    )
    fun `extracts first integer from real sheet variants`(raw: String, expected: Int) {
        assertThat(ManpowerParser.parse(raw)).isEqualTo(expected)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "Labour", "nos", "0", "00", "99999"])
    fun `returns null for missing zero or absurd values`(raw: String) {
        assertThat(ManpowerParser.parse(raw)).isNull()
    }

    @Test
    fun `null input is safe`() {
        assertThat(ManpowerParser.parse(null)).isNull()
        assertThat(ManpowerParser.parseOrZero(null)).isEqualTo(0)
    }
}
