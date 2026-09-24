package com.ehs.tbttracker.domain.usecase

import java.time.LocalDate
import javax.inject.Inject

enum class FormField { DATE, CONTRACTOR, MANPOWER, LOCATION, PHOTO }

data class ValidationResult(val errors: Map<FormField, String>) {
    val isValid: Boolean get() = errors.isEmpty()
}

class ValidateTbtDraftUseCase @Inject constructor() {
    operator fun invoke(
        date: LocalDate?,
        contractor: String,
        manpowerText: String,
        location: String,
        photoPath: String?,
        today: LocalDate,
    ): ValidationResult {
        val e = LinkedHashMap<FormField, String>()
        when {
            date == null -> e[FormField.DATE] = "Select the TBT date"
            date.isAfter(today) -> e[FormField.DATE] = "Date cannot be in the future"
            date.isBefore(today.minusDays(MAX_BACKDATE_DAYS)) -> e[FormField.DATE] = "Date is more than $MAX_BACKDATE_DAYS days old"
        }
        if (contractor.isBlank()) e[FormField.CONTRACTOR] = "Contractor is required"
        else if (contractor.trim().length < 2) e[FormField.CONTRACTOR] = "Contractor name is too short"
        val mp = parseManpower(manpowerText)
        when {
            manpowerText.isBlank() -> e[FormField.MANPOWER] = "Manpower is required"
            mp == null -> e[FormField.MANPOWER] = "Enter a whole number"
            mp <= 0 -> e[FormField.MANPOWER] = "Must be greater than 0"
            mp > MAX_MANPOWER -> e[FormField.MANPOWER] = "Must be $MAX_MANPOWER or less"
        }
        if (location.isBlank()) e[FormField.LOCATION] = "Location is required"
        if (photoPath.isNullOrBlank()) e[FormField.PHOTO] = "Toolbox talk photo is required"
        return ValidationResult(e)
    }

    companion object {
        const val MAX_MANPOWER = 500
        const val MAX_BACKDATE_DAYS = 30L

        /** Strict: the form only accepts digits (unlike the lenient sheet parser). */
        fun parseManpower(text: String): Int? = text.trim().takeIf { it.matches(Regex("""\d{1,4}""")) }?.toInt()
    }
}
