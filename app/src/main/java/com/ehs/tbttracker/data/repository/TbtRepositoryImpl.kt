package com.ehs.tbttracker.data.repository

import com.ehs.tbttracker.data.local.TbtDao
import com.ehs.tbttracker.data.local.TbtEntity
import com.ehs.tbttracker.data.local.toDomain
import com.ehs.tbttracker.data.photo.PhotoStorage
import com.ehs.tbttracker.data.remote.BackendConfig
import com.ehs.tbttracker.data.remote.BackendException
import com.ehs.tbttracker.data.remote.CreateRequest
import com.ehs.tbttracker.data.remote.SheetRowMapper
import com.ehs.tbttracker.data.remote.SheetsWebAppApi
import com.ehs.tbttracker.di.IoDispatcher
import com.ehs.tbttracker.domain.model.SyncState
import com.ehs.tbttracker.domain.model.TbtDraft
import com.ehs.tbttracker.domain.model.TbtRecord
import com.ehs.tbttracker.domain.parsing.ContractorNormalizer
import com.ehs.tbttracker.domain.parsing.SheetDateParser
import com.ehs.tbttracker.domain.repository.SyncReport
import com.ehs.tbttracker.domain.repository.SyncScheduler
import com.ehs.tbttracker.domain.repository.TbtRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Clock
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TbtRepositoryImpl @Inject constructor(
    private val dao: TbtDao,
    private val api: SheetsWebAppApi,
    private val config: BackendConfig,
    private val photoStorage: PhotoStorage,
    private val syncScheduler: SyncScheduler,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) : TbtRepository {

    /** Serialises uploads so a manual retry and the worker never double-post the same record. */
    private val syncMutex = Mutex()

    override fun observeRecords(): Flow<List<TbtRecord>> = dao.observeAll()
        .map { entities ->
            val normalizer = ContractorNormalizer(entities.map { it.contractorName })
            entities.map { it.toDomain(normalizer) }
        }
        .flowOn(io)

    override suspend fun refresh(): Result<Int> = withContext(io) {
        runCatchingNonCancel {
            check(config.isConfigured) { "Backend not configured. Set EHS_WEB_APP_URL and EHS_API_TOKEN." }
            val response = api.list(config.webAppUrl, config.token)
            if (!response.ok) throw BackendException(response.code ?: 500, response.error ?: "Sheet read failed")
            val existing = dao.getByIds(response.rows.map(SheetRowMapper::stableId)).associateBy { it.id }
            val entities = response.rows.map { SheetRowMapper.toEntity(it, existing[SheetRowMapper.stableId(it)]) }
            dao.reconcileWithRemote(entities)
            // Anything captured offline gets another chance now that we know the network works.
            syncScheduler.scheduleSync()
            entities.size
        }
    }

    override suspend fun submit(draft: TbtDraft): TbtRecord = withContext(io) {
        val id = UUID.randomUUID().toString()
        val storedPhoto = photoStorage.persist(draft.photoPath, id)
        val now = LocalDateTime.now(clock)
        val entity = TbtEntity(
            id = id,
            timestampIso = now.withNano(0).toString(),
            dateIso = draft.date.toString(),
            contractorName = draft.contractor,
            manpower = draft.manpower,
            manpowerRaw = draft.manpower.toString(),
            location = draft.location,
            photoUrl = null,
            localPhotoPath = storedPhoto,
            notes = draft.notes,
            syncState = SyncState.PENDING,
            rowNumber = null,
            attemptCount = 0,
            lastError = null,
            createdAtEpochMs = clock.millis(),
        )
        dao.upsert(entity)
        syncScheduler.scheduleSync()
        entity.toDomain(ContractorNormalizer(listOf(draft.contractor)))
    }

    override suspend fun syncPending(): SyncReport = withContext(io) {
        syncMutex.withLock {
            if (!config.isConfigured) return@withLock SyncReport(0, dao.getPending().size, 0)
            var uploaded = 0
            var retryable = 0
            var permanent = 0
            val attempted = HashSet<String>()
            // Loop so records saved while this sync runs are included in the same pass.
            while (true) {
                val batch = dao.getPending().filter { attempted.add(it.id) }
                if (batch.isEmpty()) break
                for (entity in batch) {
                    try {
                        upload(entity)
                        uploaded++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: BackendException) {
                        if (e.isPermanent) {
                            dao.markFailed(entity.id, e.message ?: "Rejected by server")
                            permanent++
                        } else {
                            dao.recordRetryableFailure(entity.id, e.message ?: "Server error")
                            retryable++
                        }
                    } catch (e: IOException) {
                        dao.recordRetryableFailure(entity.id, e.message ?: "Network error")
                        retryable++
                    } catch (e: retrofit2.HttpException) {
                        dao.recordRetryableFailure(entity.id, "HTTP ${e.code()}")
                        retryable++
                    } catch (e: kotlinx.serialization.SerializationException) {
                        // Usually an HTML error/login page: wrong deployment URL or access setting.
                        dao.markFailed(entity.id, "Unexpected server response. Check the Web App deployment.")
                        permanent++
                    }
                }
            }
            SyncReport(uploaded, retryable, permanent)
        }
    }

    override suspend fun retryFailed() {
        withContext(io) { if (dao.requeueFailed() > 0) syncScheduler.scheduleSync() }
    }

    private suspend fun upload(entity: TbtEntity) {
        val photo = entity.localPhotoPath?.let(photoStorage::readBytes)
        val request = CreateRequest(
            token = config.token,
            clientRef = entity.id,
            date = entity.dateIso ?: SheetDateParser.format(java.time.LocalDate.now(clock)),
            contractor = entity.contractorName,
            manpower = entity.manpower,
            location = entity.location,
            notes = entity.notes,
            photoBase64 = photo?.let { Base64.getEncoder().encodeToString(it) },
            photoName = "TBT_${entity.dateIso}_${entity.contractorName.take(30)}_${entity.id.take(8)}.jpg",
        )
        val response = api.create(config.webAppUrl, request)
        if (!response.ok) throw BackendException(response.code ?: 500, response.error ?: "Upload failed")
        dao.markSynced(
            id = entity.id,
            photoUrl = response.photoUrl?.ifBlank { null },
            rowNumber = response.row,
            timestampIso = SheetDateParser.parseTimestamp(response.timestamp)?.toString(),
        )
    }

    private inline fun <T> runCatchingNonCancel(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}
