package com.ehs.tbttracker.data.local

import com.ehs.tbttracker.domain.model.PhotoRef
import com.ehs.tbttracker.domain.model.TbtRecord
import com.ehs.tbttracker.domain.parsing.ContractorNormalizer
import com.ehs.tbttracker.domain.parsing.DriveLinkResolver
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

fun TbtEntity.toDomain(normalizer: ContractorNormalizer): TbtRecord = TbtRecord(
    id = id,
    timestamp = timestampIso?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
    date = dateIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    contractorRaw = contractorName,
    contractor = normalizer.canonical(contractorName),
    manpower = manpower,
    location = location,
    // Prefer the local file (instant, offline) and fall back to Drive.
    photo = localPhotoPath?.takeIf { File(it).exists() }?.let { PhotoRef.Local(it) }
        ?: DriveLinkResolver.toPhotoRef(photoUrl),
    notes = notes,
    syncState = syncState,
    rowNumber = rowNumber,
    lastError = lastError,
)
