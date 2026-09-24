package com.ehs.tbttracker.ui.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ehs.tbttracker.data.photo.PhotoProcessor
import com.ehs.tbttracker.domain.model.TbtDraft
import com.ehs.tbttracker.domain.repository.TbtRepository
import com.ehs.tbttracker.domain.usecase.FormField
import com.ehs.tbttracker.domain.usecase.SubmitOutcome
import com.ehs.tbttracker.domain.usecase.SubmitTbtUseCase
import com.ehs.tbttracker.domain.usecase.ValidateTbtDraftUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

sealed interface SubmissionState {
    data object Idle : SubmissionState
    data object Submitting : SubmissionState
    data class Success(val message: String, val offline: Boolean) : SubmissionState
    data class Error(val message: String) : SubmissionState
}

data class TbtFormState(
    val date: LocalDate,
    val contractor: String = "",
    val manpower: String = "",
    val location: String = "",
    val notes: String = "",
    val photoPath: String? = null,
    val isProcessingPhoto: Boolean = false,
    val errors: Map<FormField, String> = emptyMap(),
    val submission: SubmissionState = SubmissionState.Idle,
) {
    val canSubmit: Boolean get() = submission !is SubmissionState.Submitting && !isProcessingPhoto
}

data class FormSuggestions(
    val contractors: List<String> = emptyList(),
    val locations: List<String> = DEFAULT_LOCATIONS,
) {
    companion object {
        val DEFAULT_LOCATIONS = listOf("Tower B1", "Level P2", "NTA Parking", "Tower C1", "Tower D1")
    }
}

@HiltViewModel
class TbtFormViewModel @Inject constructor(
    repository: TbtRepository,
    private val validate: ValidateTbtDraftUseCase,
    private val submitTbt: SubmitTbtUseCase,
    private val photoProcessor: PhotoProcessor,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    /** "+ Log TBT" on a missed day opens the form with that date and contractor filled in. */
    private val _state = MutableStateFlow(
        TbtFormState(
            date = savedStateHandle.get<String>(ARG_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now(clock),
            contractor = savedStateHandle.get<String>(ARG_CONTRACTOR).orEmpty(),
        ),
    )
    val state: StateFlow<TbtFormState> = _state.asStateFlow()

    /** Contractors ranked by how often they report, locations by frequency, both from history. */
    val suggestions: StateFlow<FormSuggestions> = repository.observeRecords()
        .map { records ->
            FormSuggestions(
                contractors = records.groupingBy { it.contractor }.eachCount()
                    .entries.sortedByDescending { it.value }.map { it.key },
                locations = (records.groupingBy { it.location.trim() }.eachCount()
                    .filterKeys { it.isNotBlank() }.entries.sortedByDescending { it.value }.take(8).map { it.key } +
                    FormSuggestions.DEFAULT_LOCATIONS)
                    .distinctBy { it.lowercase() }.take(10),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FormSuggestions())

    private var submittedOnce = false

    fun onDateChange(date: LocalDate) = edit { copy(date = date) }
    fun onContractorChange(v: String) = edit { copy(contractor = v) }
    fun onManpowerChange(v: String) = edit { copy(manpower = v.filter(Char::isDigit).take(4)) }
    fun onLocationChange(v: String) = edit { copy(location = v) }
    fun onNotesChange(v: String) = edit { copy(notes = v.take(500)) }
    fun onRemovePhoto() = edit { copy(photoPath = null) }

    fun onPhotoCaptured(raw: File) {
        _state.update { it.copy(isProcessingPhoto = true) }
        viewModelScope.launch {
            val label = with(_state.value) { listOf(contractor, location).filter { it.isNotBlank() }.joinToString(" · ") }
            runCatching { photoProcessor.process(raw, label) }
                .onSuccess { f -> edit { copy(photoPath = f.absolutePath, isProcessingPhoto = false) } }
                .onFailure { e ->
                    _state.update { it.copy(isProcessingPhoto = false, submission = SubmissionState.Error("Photo failed: ${e.message}")) }
                }
        }
    }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        submittedOnce = true
        val result = validate(s.date, s.contractor, s.manpower, s.location, s.photoPath, LocalDate.now(clock))
        if (!result.isValid) {
            _state.update { it.copy(errors = result.errors, submission = SubmissionState.Error("Please fix the highlighted fields")) }
            return
        }
        _state.update { it.copy(errors = emptyMap(), submission = SubmissionState.Submitting) }
        viewModelScope.launch {
            val draft = TbtDraft(
                date = s.date,
                contractor = s.contractor,
                manpower = ValidateTbtDraftUseCase.parseManpower(s.manpower)!!,
                location = s.location,
                notes = s.notes,
                photoPath = s.photoPath!!,
            )
            when (val outcome = submitTbt(draft)) {
                is SubmitOutcome.Queued -> {
                    submittedOnce = false
                    _state.value = TbtFormState(
                        date = s.date,
                        submission = SubmissionState.Success(
                            if (outcome.online) "TBT saved - uploading to Google Sheet" else "Offline - saved. Will sync automatically when online",
                            offline = !outcome.online,
                        ),
                    )
                }
                is SubmitOutcome.Failed -> _state.update { it.copy(submission = SubmissionState.Error(outcome.message)) }
            }
        }
    }

    fun submissionShown() = _state.update {
        if (it.submission is SubmissionState.Success || it.submission is SubmissionState.Error) it.copy(submission = SubmissionState.Idle) else it
    }

    /** Re-validates live after the first submit attempt so errors clear as the user fixes them. */
    private inline fun edit(crossinline change: TbtFormState.() -> TbtFormState) = _state.update { current ->
        val next = current.change()
        if (!submittedOnce) next
        else next.copy(errors = validate(next.date, next.contractor, next.manpower, next.location, next.photoPath, LocalDate.now(clock)).errors)
    }

    companion object {
        const val ARG_DATE = "date"
        const val ARG_CONTRACTOR = "contractor"
    }
}
