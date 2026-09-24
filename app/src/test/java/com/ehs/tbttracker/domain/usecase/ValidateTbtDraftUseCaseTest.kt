package com.ehs.tbttracker.domain.usecase

import com.ehs.tbttracker.testutil.Fixtures.TODAY
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ValidateTbtDraftUseCaseTest {
    private val validate = ValidateTbtDraftUseCase()

    private fun run(
        date: java.time.LocalDate? = TODAY,
        contractor: String = "Choudhary Construction",
        manpower: String = "12",
        location: String = "Tower D1",
        photo: String? = "/data/photo.jpg",
    ) = validate(date, contractor, manpower, location, photo, TODAY)

    @Test fun `valid draft`() = assertThat(run().isValid).isTrue()

    @ParameterizedTest
    @ValueSource(strings = ["", "0", "abc", "10 labour", "-3", "501", "1.5"])
    fun `manpower must be an integer between 1 and 500`(v: String) {
        assertThat(run(manpower = v).errors).containsKey(FormField.MANPOWER)
    }

    @Test fun `future date rejected`() =
        assertThat(run(date = TODAY.plusDays(1)).errors[FormField.DATE]).isEqualTo("Date cannot be in the future")

    @Test fun `stale date rejected`() =
        assertThat(run(date = TODAY.minusDays(31)).errors).containsKey(FormField.DATE)

    @Test fun `missing fields each reported`() {
        val r = run(date = null, contractor = " ", manpower = "", location = "", photo = null)
        assertThat(r.errors.keys).containsExactly(
            FormField.DATE, FormField.CONTRACTOR, FormField.MANPOWER, FormField.LOCATION, FormField.PHOTO,
        )
    }
}
