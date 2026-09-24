package com.ehs.tbttracker.ui.charts

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class NiceCeilTest {
    @Test
    fun `axis maximum rounds up to clean numbers`() {
        assertThat(listOf(0, 3, 7, 10, 13, 35, 82, 386).map(::niceCeil)).containsExactly(0, 4, 10, 10, 20, 50, 100, 500).inOrder()
    }
}
