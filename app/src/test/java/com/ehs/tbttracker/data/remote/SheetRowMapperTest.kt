package com.ehs.tbttracker.data.remote

import com.ehs.tbttracker.domain.model.SyncState
import com.ehs.tbttracker.testutil.Fixtures
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SheetRowMapperTest {

    @Test
    fun `every real sheet row is sanitised without loss`() {
        val rows = Fixtures.realSheet().rows
        val entities = rows.map { SheetRowMapper.toEntity(it) }

        assertThat(entities).hasSize(94)
        assertThat(entities.map { it.id }.toSet()).hasSize(94) // ids are unique
        assertThat(entities.filter { it.dateIso == null }).isEmpty()
        assertThat(entities.filter { it.timestampIso == null }).isEmpty()
        assertThat(entities.filter { it.manpower <= 0 }).isEmpty()
        assertThat(entities.all { it.syncState == SyncState.SYNCED }).isTrue()
    }

    @Test
    fun `labour suffix and US dates`() {
        val e = SheetRowMapper.toEntity(
            SheetRowDto(row = 50, timestamp = "9/14/2026 9:27:34", date = "9/14/2026", contractor = "Choudhary Construction ",
                manpower = "10 Labour ", location = "Tower B floor 03", photo = "https://drive.google.com/open?id=1pmQOSGewpQlhBVxX9uhzEiZd0VgyMgOm"),
        )
        assertThat(e.manpower).isEqualTo(10)
        assertThat(e.manpowerRaw).isEqualTo("10 Labour")
        assertThat(e.dateIso).isEqualTo("2026-09-14")
        assertThat(e.timestampIso).isEqualTo("2026-09-14T09:27:34")
        assertThat(e.contractorName).isEqualTo("Choudhary Construction")
        assertThat(e.rowNumber).isEqualTo(50)
    }

    @Test
    fun `blank date falls back to timestamp date`() {
        val e = SheetRowMapper.toEntity(SheetRowDto(timestamp = "2026-09-05 11:46:51", date = "", contractor = "X", manpower = "3"))
        assertThat(e.dateIso).isEqualTo("2026-09-05")
    }

    @Test
    fun `client ref becomes the id so uploads and reads match`() {
        val dto = SheetRowDto(timestamp = "t", contractor = "c", clientRef = "7f0c-uuid")
        assertThat(SheetRowMapper.stableId(dto)).isEqualTo("7f0c-uuid")
    }

    @Test
    fun `form rows get a stable content id independent of row number`() {
        val a = SheetRowDto(row = 2, timestamp = "2026-08-25 12:22:18", contractor = "Aseen Power", photo = "p")
        assertThat(SheetRowMapper.stableId(a)).isEqualTo(SheetRowMapper.stableId(a.copy(row = 99)))
        assertThat(SheetRowMapper.stableId(a)).startsWith("sheet-")
    }
}
