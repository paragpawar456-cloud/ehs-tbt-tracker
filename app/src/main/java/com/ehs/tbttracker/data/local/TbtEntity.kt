package com.ehs.tbttracker.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ehs.tbttracker.domain.model.SyncState

@Entity(
    tableName = "tbt_records",
    indices = [Index("date_iso"), Index("sync_state")],
)
data class TbtEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "timestamp_iso") val timestampIso: String?,
    @ColumnInfo(name = "date_iso") val dateIso: String?,
    @ColumnInfo(name = "contractor_name") val contractorName: String,
    val manpower: Int,
    @ColumnInfo(name = "manpower_raw") val manpowerRaw: String,
    val location: String,
    @ColumnInfo(name = "photo_url") val photoUrl: String?,
    @ColumnInfo(name = "local_photo_path") val localPhotoPath: String?,
    val notes: String,
    @ColumnInfo(name = "sync_state") val syncState: SyncState,
    @ColumnInfo(name = "row_number") val rowNumber: Int?,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int,
    @ColumnInfo(name = "last_error") val lastError: String?,
    @ColumnInfo(name = "created_at") val createdAtEpochMs: Long,
)
