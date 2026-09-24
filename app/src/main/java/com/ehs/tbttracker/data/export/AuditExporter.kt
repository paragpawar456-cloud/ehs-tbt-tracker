package com.ehs.tbttracker.data.export

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.ehs.tbttracker.domain.usecase.ContractorMonthReport
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Builds the contractor audit sheet as .xlsx or .pdf and hands it to Android's share sheet. */
@Singleton
class AuditExporter @Inject constructor(@ApplicationContext private val context: Context) {

    private val dir get() = File(context.cacheDir, "exports").apply { mkdirs() }
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

    private fun baseName(r: ContractorMonthReport) =
        "TBT_Audit_${r.contractor.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')}_${r.month}"

    fun exportExcel(r: ContractorMonthReport): File {
        val file = File(dir, baseName(r) + ".xlsx")
        val summary = listOf(
            listOf("TBT Safety Compliance Portal - Contractor Wise Audit", null),
            listOf("Contractor", r.contractor),
            listOf("Month", r.month.format(monthFmt)),
            listOf("TBT done (days)", r.doneDays.size),
            listOf("Sessions", r.sessions),
            listOf("Days not done", r.notDoneDays.size),
            listOf("Days in month", r.daysInMonth),
            listOf("Compliance rate (%)", r.complianceRate),
            listOf("Total manpower trained", r.manpower),
            listOf("Avg workers per conducted day", "%.1f".format(Locale.US, r.avgWorkersPerDay)),
        )
        val daily = listOf(listOf("Day", "Date", "Weekday", "Status", "Sessions", "Manpower", "Location", "Photo")) +
            r.days.map { d ->
                listOf(
                    d.date.dayOfMonth, d.date.toString(), d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    when { d.done -> "TBT Done"; d.upcoming -> "Upcoming"; else -> "No TBT Done" },
                    d.records.size, if (d.done) d.manpower else null, d.locations,
                    d.records.mapNotNull { rec -> (rec.photo as? com.ehs.tbttracker.domain.model.PhotoRef.Drive)?.originalUrl }.joinToString(" "),
                )
            }
        file.outputStream().use { XlsxWriter.write(it, listOf(XlsxWriter.Sheet("Summary", summary), XlsxWriter.Sheet("Daily audit", daily))) }
        return file
    }

    fun exportPdf(r: ContractorMonthReport): File {
        val file = File(dir, baseName(r) + ".pdf")
        val doc = PdfDocument()
        val pageW = 595; val pageH = 842; val margin = 36f
        val title = Paint().apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val bold = Paint().apply { textSize = 10f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val body = Paint().apply { textSize = 10f; isAntiAlias = true }
        val green = Paint(bold).apply { color = 0xFF15803D.toInt() }
        val red = Paint(bold).apply { color = 0xFFDC2626.toInt() }
        val grey = Paint(body).apply { color = 0xFF6B7280.toInt() }
        var pageNo = 0
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, ++pageNo).create())
        var y = margin + 10
        fun newPageIfNeeded(h: Float) {
            if (y + h > pageH - margin) { doc.finishPage(page); page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, ++pageNo).create()); y = margin + 10 }
        }
        fun line(text: String, paint: Paint, x: Float = margin, dy: Float = 15f) { newPageIfNeeded(dy); page.canvas.drawText(text, x, y, paint); y += dy }

        line("TBT Safety Compliance Portal", title, dy = 20f)
        line("Contractor Wise TBT Details & Audit Sheet", bold)
        line("${r.contractor}  |  ${r.month.format(monthFmt)}", body, dy = 22f)
        line("TBT done: ${r.doneDays.size} days (${r.sessions} sessions)    Not done: ${r.notDoneDays.size} of ${r.daysInMonth} days", body)
        line("Compliance rate: ${r.complianceRate}%    Manpower trained: ${r.manpower} workers (avg %.1f per conducted day)".format(Locale.US, r.avgWorkersPerDay), body, dy = 24f)

        val cols = floatArrayOf(margin, margin + 34, margin + 120, margin + 165, margin + 245, margin + 305)
        fun header() {
            newPageIfNeeded(18f)
            listOf("Day", "Date", "Wkday", "Status", "Workers", "Location").forEachIndexed { i, h -> page.canvas.drawText(h, cols[i], y, bold) }
            y += 4; page.canvas.drawLine(margin, y, pageW - margin, y, grey); y += 12
        }
        header()
        r.days.forEach { d ->
            if (y + 14 > pageH - margin) { newPageIfNeeded(1000f); header() }
            val c = page.canvas
            c.drawText(d.date.dayOfMonth.toString(), cols[0], y, body)
            c.drawText(d.date.toString(), cols[1], y, body)
            c.drawText(d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), cols[2], y, body)
            when {
                d.done -> c.drawText("TBT Done", cols[3], y, green)
                d.upcoming -> c.drawText("Upcoming", cols[3], y, grey)
                else -> c.drawText("No TBT Done", cols[3], y, red)
            }
            if (d.done) c.drawText(d.manpower.toString(), cols[4], y, body)
            c.drawText(d.locations.take(40), cols[5], y, body)
            y += 14
        }
        y += 10
        line("Generated by EHS Daily TBT Tracker from the Contractor Daily Tbt details sheet.", grey)
        doc.finishPage(page)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun shareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
        val mime = if (file.extension == "pdf") "application/pdf" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension.replace('_', ' '))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share ${file.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
