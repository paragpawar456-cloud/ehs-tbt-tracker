package com.ehs.tbttracker.data.photo

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class WatermarkLinesTest {
    private val t = LocalDateTime.of(2026, 9, 24, 9, 34, 45)

    @Test
    fun `stamps date time gps and site label`() {
        val lines = PhotoProcessor.watermarkLines(t, GeoPoint(19.0760123, 72.8776559, 8.4f), "Stellar · B1 8 Floor")
        assertThat(lines).containsExactly(
            "EHS TBT  |  24 Sep 2026  09:34:45",
            "GPS 19.076012, 72.877656  (±8 m)",
            "Stellar · B1 8 Floor",
        ).inOrder()
    }

    @Test
    fun `degrades gracefully without location or label`() {
        assertThat(PhotoProcessor.watermarkLines(t, null, " ")).containsExactly(
            "EHS TBT  |  24 Sep 2026  09:34:45", "GPS unavailable",
        ).inOrder()
    }
}
