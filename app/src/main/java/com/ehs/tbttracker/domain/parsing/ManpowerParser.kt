package com.ehs.tbttracker.domain.parsing

/**
 * Turns free-text headcount cells into an integer.
 * Real sheet values seen: "5", "06", "10 Labour", "09nos", "09 nos".
 */
object ManpowerParser {
    private val FIRST_INT = Regex("""\d+""")
    private const val MAX_REASONABLE = 5_000

    /** @return the headcount, or null when the cell has no usable number. */
    fun parse(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val digits = FIRST_INT.find(raw)?.value ?: return null
        val value = digits.trimStart('0').ifEmpty { "0" }.toIntOrNull() ?: return null
        return value.takeIf { it in 1..MAX_REASONABLE }
    }

    fun parseOrZero(raw: String?): Int = parse(raw) ?: 0
}
