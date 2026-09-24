package com.ehs.tbttracker.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.ehs.tbttracker.domain.model.SyncState
import kotlinx.coroutines.flow.Flow

@Dao
interface TbtDao {

    @Query("SELECT * FROM tbt_records ORDER BY date_iso DESC, timestamp_iso DESC, created_at DESC")
    fun observeAll(): Flow<List<TbtEntity>>

    @Query("SELECT * FROM tbt_records WHERE id = :id")
    suspend fun getById(id: String): TbtEntity?

    @Query("SELECT * FROM tbt_records WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<TbtEntity>

    @Query("SELECT * FROM tbt_records WHERE sync_state = 'PENDING' ORDER BY created_at ASC")
    suspend fun getPending(): List<TbtEntity>

    @Query("SELECT COUNT(*) FROM tbt_records WHERE sync_state != 'SYNCED'")
    fun observeUnsyncedCount(): Flow<Int>

    @Upsert
    suspend fun upsert(entity: TbtEntity)

    @Upsert
    suspend fun upsertAll(entities: List<TbtEntity>)

    @Query(
        """UPDATE tbt_records SET sync_state = 'SYNCED', photo_url = :photoUrl, row_number = :rowNumber,
           timestamp_iso = COALESCE(:timestampIso, timestamp_iso), last_error = NULL WHERE id = :id""",
    )
    suspend fun markSynced(id: String, photoUrl: String?, rowNumber: Int?, timestampIso: String?)

    @Query("UPDATE tbt_records SET attempt_count = attempt_count + 1, last_error = :error WHERE id = :id")
    suspend fun recordRetryableFailure(id: String, error: String)

    @Query("UPDATE tbt_records SET sync_state = 'FAILED', attempt_count = attempt_count + 1, last_error = :error WHERE id = :id")
    suspend fun markFailed(id: String, error: String)

    @Query("UPDATE tbt_records SET sync_state = 'PENDING', last_error = NULL WHERE sync_state = 'FAILED'")
    suspend fun requeueFailed(): Int

    /**
     * Makes the cache mirror the sheet while never touching unsynced local work:
     * - rows from the sheet are upserted as SYNCED (a PENDING row whose Client Ref is already
     *   in the sheet was uploaded by a request whose response got lost; it becomes SYNCED);
     * - SYNCED rows no longer in the sheet (deleted there) are removed.
     */
    @Transaction
    suspend fun reconcileWithRemote(remote: List<TbtEntity>) {
        upsertAll(remote)
        val keep = remote.mapTo(HashSet()) { it.id }
        // Chunked to stay under SQLite's bound-argument limit on large sheets.
        allSyncedIds().filterNot { it in keep }.chunked(500).forEach { deleteByIds(it) }
    }

    @Query("SELECT id FROM tbt_records WHERE sync_state = 'SYNCED'")
    suspend fun allSyncedIds(): List<String>

    @Query("DELETE FROM tbt_records WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}
