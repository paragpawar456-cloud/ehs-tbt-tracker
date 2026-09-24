package com.ehs.tbttracker.data.export

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class XlsxWriterTest {
    @Test
    fun `writes a valid workbook with escaped strings and numbers`() {
        val out = ByteArrayOutputStream()
        XlsxWriter.write(out, listOf(XlsxWriter.Sheet("Daily audit", listOf(listOf("Day", "Location"), listOf(5, "Tower <C1> & P2")))))
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { z ->
            generateSequence { z.nextEntry }.forEach { e -> entries[e.name] = z.readBytes().toString(Charsets.UTF_8) }
        }
        assertThat(entries.keys).containsAtLeast("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/styles.xml", "xl/worksheets/sheet1.xml")
        val sheet = entries.getValue("xl/worksheets/sheet1.xml")
        assertThat(sheet).contains("""<c r="A2"><v>5</v></c>""")
        assertThat(sheet).contains("Tower &lt;C1&gt; &amp; P2")
        assertThat(entries.getValue("xl/workbook.xml")).contains("""name="Daily audit"""")
    }

    @Test
    fun `column names`() {
        assertThat(listOf(0, 25, 26, 27, 701).map(XlsxWriter::colName)).containsExactly("A", "Z", "AA", "AB", "ZZ").inOrder()
    }
}
