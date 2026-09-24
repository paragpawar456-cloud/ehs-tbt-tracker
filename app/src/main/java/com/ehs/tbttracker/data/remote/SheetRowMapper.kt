package com.ehs.tbttracker.data.remote

import com.ehs.tbttracker.data.local.TbtEntity
import com.ehs.tbttracker.domain.model.SyncState
import com.ehs.tbttracker.domain.parsing.ManpowerParser
import com.ehs.tbttracker.domain.parsing.SheetDateParser

/** Sheet row -> cache entity. All sanitisation happens here, once, on the way in. */
object SheetRowMapper {

    fun toEntity(dto: SheetRowDto, existing: TbtEntity? = null): TbtEntity {
        val timestamp = SheetDateParser.parseTimestamp(dto.timestamp)
        // Fall back to the submission timestamp when the Date cell is blank or unparseable.
        val date = SheetDateParser.parseDate(dto.date) ?: timestamp?.toLocalDate()
        return TbtEntity(
            id = stableId(dto),
            timestampIso = timestamp?.toString(),
            dateIso = date?.toString(),
            contractorName = dto.contractor.trim().replace(Regex("""\s+"""), " "),
            manpower = ManpowerParser.parseOrZero(dto.manpower),
            manpowerRaw = dto.manpower.trim(),
            location = dto.location.trim().replace(Regex("""\s+"""), " "),
            photoUrl = dto.photo.trim().ifEmpty { null },
            // Keep the on-device copy of photos we uploaded ourselves so they render offline.
            localPhotoPath = existing?.localPhotoPath,
            notes = dto.notes.trim(),
            syncState = SyncState.SYNCED,
            rowNumber = dto.row.takeIf { it > 0 },
            attemptCount = 0,
            lastError = null,
            createdAtEpochMs = existing?.createdAtEpochMs ?: 0L,
        )
    }

    /**
     * Rows created by this app carry a Client Ref (UUID) so the uploaded record and the
     * row read back later share an id. Google Form rows get a content-derived id that
     * survives row insertions/deletions (unlike the row number).
     */
    fun stableId(dto: SheetRowDto): String {
        if (dto.clientRef.isNotBlank()) return dto.clientRef.trim()
        val basis = listOf(dto.timestamp, dto.contractor, dto.photo).joinToString("|") { it.trim().lowercase() }
        return "sheet-" + sha1(basis).take(20)
    }

    private fun sha1(s: String): String =
        java.security.MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
