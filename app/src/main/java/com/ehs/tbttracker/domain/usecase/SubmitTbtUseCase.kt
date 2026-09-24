package com.ehs.tbttracker.domain.usecase

import com.ehs.tbttracker.domain.model.TbtDraft
import com.ehs.tbttracker.domain.model.TbtRecord
import com.ehs.tbttracker.domain.repository.ConnectivityObserver
import com.ehs.tbttracker.domain.repository.TbtRepository
import javax.inject.Inject

sealed interface SubmitOutcome {
    /** Saved and upload scheduled; [online] tells the UI whether to say "syncing" or "offline". */
    data class Queued(val record: TbtRecord, val online: Boolean) : SubmitOutcome
    data class Failed(val message: String) : SubmitOutcome
}

class SubmitTbtUseCase @Inject constructor(
    private val repository: TbtRepository,
    private val connectivity: ConnectivityObserver,
) {
    suspend operator fun invoke(draft: TbtDraft): SubmitOutcome = try {
        val record = repository.submit(draft.copy(contractor = draft.contractor.trim(), location = draft.location.trim(), notes = draft.notes.trim()))
        SubmitOutcome.Queued(record, connectivity.isCurrentlyOnline())
    } catch (t: Throwable) {
        if (t is kotlinx.coroutines.CancellationException) throw t
        SubmitOutcome.Failed(t.message ?: "Could not save TBT record")
    }
}
