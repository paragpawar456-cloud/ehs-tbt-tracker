package com.ehs.tbttracker.domain.parsing

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Normalises the mix of date formats found in the sheet into java.time values.
 *
 * The sheet contains both ISO ("2026-09-12", "2026-08-25 12:22:18") and US locale
 * ("9/14/2026", "9/14/2026 9:27:34") values because the spreadsheet locale was changed
 * mid-stream. Slash dates are read as M/d/yyyy unless the first part is > 12, in which
 * case d/M/yyyy is the only valid reading. Google Sheets serial numbers are also accepted.
 */
object SheetDateParser {
    private val ISO_DATE = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})$""")
    private val SLASH_DATE = Regex("""^(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{2,4})$""")
    private val TIME = Regex("""^(\d{1,2}):(\d{2})(?::(\d{2}))?\s*([AaPp][Mm])?$""")
    private val SERIAL = Regex("""^\d{5}(\.\d+)?$""")
    private val SHEETS_EPOCH: LocalDate = LocalDate.of(1899, 12, 30)

    val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun parseDate(raw: String?): LocalDate? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        // A timestamp in the date column still has a usable date part.
        val datePart = s.substringBefore('T').substringBefore(' ')
        ISO_DATE.matchEntire(datePart)?.let { m ->
            val (y, mo, d) = m.destructured
            return safeDate(y.toInt(), mo.toInt(), d.toInt())
        }
        SLASH_DATE.matchEntire(datePart)?.let { m ->
            val (a, b, yRaw) = m.destructured
            val y = yRaw.toInt().let { if (it < 100) 2000 + it else it }
            val first = a.toInt()
            val second = b.toInt()
            return if (first > 12) safeDate(y, second, first) else safeDate(y, first, second)
                ?: safeDate(y, second, first)
        }
        if (SERIAL.matches(s)) {
            return SHEETS_EPOCH.plusDays(s.substringBefore('.').toLong())
        }
        return try {
            LocalDate.parse(s)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    fun parseTimestamp(raw: String?): LocalDateTime? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        try {
            return LocalDateTime.parse(s) // 2026-09-14T09:27:34
        } catch (_: DateTimeParseException) { /* try sheet formats */ }

        val sep = s.indexOfAny(charArrayOf(' ', 'T'))
        val date = parseDate(if (sep > 0) s.substring(0, sep) else s) ?: return null
        if (sep < 0) return date.atStartOfDay()
        val time = parseTime(s.substring(sep + 1).trim()) ?: LocalTime.MIDNIGHT
        return LocalDateTime.of(date, time)
    }

    fun format(date: LocalDate): String = date.format(ISO)

    private fun parseTime(raw: String): LocalTime? {
        val m = TIME.matchEntire(raw) ?: return null
        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].toInt()
        val second = m.groupValues[3].ifEmpty { "0" }.toInt()
        when (m.groupValues[4].lowercase()) {
            "pm" -> if (hour < 12) hour += 12
            "am" -> if (hour == 12) hour = 0
        }
        return if (hour in 0..23 && minute in 0..59 && second in 0..59) LocalTime.of(hour, minute, second) else null
    }

    private fun safeDate(y: Int, m: Int, d: Int): LocalDate? =
        try {
            LocalDate.of(y, m, d)
        } catch (_: java.time.DateTimeException) {
            null
        }
}
