package com.ehs.tbttracker.testutil

import com.ehs.tbttracker.data.remote.ListResponse
import com.ehs.tbttracker.domain.model.PhotoRef
import com.ehs.tbttracker.domain.model.SyncState
import com.ehs.tbttracker.domain.model.TbtRecord
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object Fixtures {
    val TODAY: LocalDate = LocalDate.of(2026, 9, 24)
    val IST: ZoneId = ZoneId.of("Asia/Kolkata")
    val CLOCK: Clock = Clock.fixed(TODAY.atTime(12, 0).atZone(IST).toInstant(), IST)

    private val json = Json { ignoreUnknownKeys = true }

    /** Snapshot of the real "Contractor Daily Tbt details" sheet (94 rows, 25 Aug - 24 Sep 2026). */
    fun realSheet(): ListResponse = json.decodeFromString(
        ListResponse.serializer(),
        requireNotNull(javaClass.classLoader!!.getResource("sheet_list_response.json")).readText(),
    )

    fun record(
        id: String = "id-${counter++}",
        contractor: String = "Choudhary Construction",
        manpower: Int = 10,
        date: LocalDate = TODAY,
        location: String = "Tower D1 Level 09",
        sync: SyncState = SyncState.SYNCED,
        notes: String = "",
    ) = TbtRecord(
        id = id,
        timestamp = LocalDateTime.of(date, java.time.LocalTime.of(9, 30)),
        date = date,
        contractorRaw = contractor,
        contractor = contractor,
        manpower = manpower,
        location = location,
        photo = PhotoRef.Drive("1abcdefghijk", "https://drive.google.com/open?id=1abcdefghijk"),
        notes = notes,
        syncState = sync,
    )

    private var counter = 0
}
