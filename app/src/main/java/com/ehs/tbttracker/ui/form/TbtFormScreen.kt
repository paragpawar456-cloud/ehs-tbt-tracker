package com.ehs.tbttracker.ui.form

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ehs.tbttracker.domain.usecase.FormField
import com.ehs.tbttracker.ui.camera.CameraCaptureScreen
import com.ehs.tbttracker.ui.util.longLabel
import com.ehs.tbttracker.ui.util.toEpochMillisUtc
import com.ehs.tbttracker.ui.util.toLocalDateUtc
import java.io.File
import java.time.LocalDate

object FormTags {
    const val DATE = "form_date"
    const val CONTRACTOR = "form_contractor"
    const val MANPOWER = "form_manpower"
    const val LOCATION = "form_location"
    const val NOTES = "form_notes"
    const val PHOTO_BUTTON = "form_photo_button"
    const val PHOTO_PREVIEW = "form_photo_preview"
    const val SUBMIT = "form_submit"
    const val SUBMIT_PROGRESS = "form_submit_progress"
    fun error(field: FormField) = "form_error_${field.name.lowercase()}"
}

/**
 * @param onSubmitted when set, called with the confirmation text after a successful save so the
 *   host can navigate back to the dashboard and show it there; otherwise the form shows it itself.
 */
@Composable
fun TbtFormRoute(
    snackbar: SnackbarHostState,
    viewModel: TbtFormViewModel = hiltViewModel(),
    onSubmitted: ((String) -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    var showCamera by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.submission) {
        when (val s = state.submission) {
            is SubmissionState.Success -> {
                viewModel.submissionShown()
                if (onSubmitted != null) onSubmitted(s.message) else snackbar.showSnackbar(s.message)
            }
            is SubmissionState.Error -> { snackbar.showSnackbar(s.message); viewModel.submissionShown() }
            else -> Unit
        }
    }

    if (showCamera) {
        CameraCaptureScreen(
            onCaptured = { file -> showCamera = false; viewModel.onPhotoCaptured(file) },
            onClose = { showCamera = false },
        )
    } else {
        TbtFormContent(
            state = state,
            suggestions = suggestions,
            today = LocalDate.now(),
            onDateChange = viewModel::onDateChange,
            onContractorChange = viewModel::onContractorChange,
            onManpowerChange = viewModel::onManpowerChange,
            onLocationChange = viewModel::onLocationChange,
            onNotesChange = viewModel::onNotesChange,
            onOpenCamera = { showCamera = true },
            onRemovePhoto = viewModel::onRemovePhoto,
            onSubmit = viewModel::submit,
        )
    }
}

/** Stateless form so it can be driven directly by Compose UI tests. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TbtFormContent(
    state: TbtFormState,
    suggestions: FormSuggestions,
    today: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    onContractorChange: (String) -> Unit,
    onManpowerChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onOpenCamera: () -> Unit,
    onRemovePhoto: () -> Unit,
    onSubmit: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val enabled = state.submission !is SubmissionState.Submitting

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Daily Toolbox Talk", style = MaterialTheme.typography.headlineSmall)
        Text("Record one TBT session per contractor crew.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        // ---- Date
        Box {
            OutlinedTextField(
                value = state.date.longLabel(),
                onValueChange = {},
                readOnly = true,
                label = { Text("TBT date") },
                leadingIcon = { Icon(Icons.Filled.CalendarMonth, null) },
                isError = FormField.DATE in state.errors,
                supportingText = state.errors[FormField.DATE]?.let { { Text(it, Modifier.testTag(FormTags.error(FormField.DATE))) } },
                modifier = Modifier.fillMaxWidth().testTag(FormTags.DATE),
            )
            // Click-catcher: read-only text fields consume taps themselves.
            Box(
                Modifier.matchParentSize().padding(top = 8.dp)
                    .clickable(enabled = enabled, onClickLabel = "Change date") { showDatePicker = true }
                    .testTag(FormTags.DATE + "_click"),
            )
        }

        // ---- Contractor (auto-complete from history + add new)
        ContractorField(
            value = state.contractor,
            options = suggestions.contractors,
            error = state.errors[FormField.CONTRACTOR],
            enabled = enabled,
            onValueChange = onContractorChange,
        )

        // ---- Manpower
        OutlinedTextField(
            value = state.manpower,
            onValueChange = onManpowerChange,
            label = { Text("Nos of manpower") },
            placeholder = { Text("e.g. 12") },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            isError = FormField.MANPOWER in state.errors,
            supportingText = state.errors[FormField.MANPOWER]?.let { { Text(it, Modifier.testTag(FormTags.error(FormField.MANPOWER))) } },
            modifier = Modifier.fillMaxWidth().testTag(FormTags.MANPOWER),
        )

        // ---- Location + quick tags
        OutlinedTextField(
            value = state.location,
            onValueChange = onLocationChange,
            label = { Text("Location of TBT") },
            placeholder = { Text("Tower / Level / Wing / Area") },
            leadingIcon = { Icon(Icons.Filled.Place, null) },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            isError = FormField.LOCATION in state.errors,
            supportingText = state.errors[FormField.LOCATION]?.let { { Text(it, Modifier.testTag(FormTags.error(FormField.LOCATION))) } },
            modifier = Modifier.fillMaxWidth().testTag(FormTags.LOCATION),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.locations.forEach { tag ->
                SuggestionChip(onClick = { onLocationChange(tag) }, label = { Text(tag) }, enabled = enabled)
            }
        }

        // ---- Photo evidence
        PhotoSection(
            photoPath = state.photoPath,
            processing = state.isProcessingPhoto,
            error = state.errors[FormField.PHOTO],
            enabled = enabled,
            onOpenCamera = onOpenCamera,
            onRemovePhoto = onRemovePhoto,
        )

        // ---- Notes (sheet column "Photo" = Status / Photo notes)
        OutlinedTextField(
            value = state.notes,
            onValueChange = onNotesChange,
            label = { Text("Status / photo notes (optional)") },
            placeholder = { Text("Topics covered, PPE compliance, remarks") },
            minLines = 2,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag(FormTags.NOTES),
        )

        Button(
            onClick = onSubmit,
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag(FormTags.SUBMIT),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
        ) {
            if (state.submission is SubmissionState.Submitting) {
                CircularProgressIndicator(Modifier.size(22.dp).testTag(FormTags.SUBMIT_PROGRESS), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSecondary)
            } else {
                Text("Submit TBT")
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showDatePicker) {
        val todayMillis = today.toEpochMillisUtc()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.toEpochMillisUtc(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
                override fun isSelectableYear(year: Int) = year <= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onDateChange(it.toLocalDateUtc()) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContractorField(
    value: String,
    options: List<String>,
    error: String?,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val query = value.trim()
    val filtered = remember(query, options) {
        if (query.isEmpty()) options else options.filter { it.contains(query, ignoreCase = true) }
    }
    val isNew = query.length >= 2 && options.none { it.equals(query, ignoreCase = true) }

    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it); expanded = true },
            label = { Text("Contractor / agency") },
            singleLine = true,
            enabled = enabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            isError = error != null,
            supportingText = error?.let { { Text(it, Modifier.testTag(FormTags.error(FormField.CONTRACTOR))) } },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable).testTag(FormTags.CONTRACTOR),
        )
        ExposedDropdownMenu(expanded = expanded && enabled && (filtered.isNotEmpty() || isNew), onDismissRequest = { expanded = false }) {
            filtered.take(12).forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onValueChange(name); expanded = false })
            }
            if (isNew) {
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Add new contractor \"$query\"") },
                    onClick = { onValueChange(query); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun PhotoSection(
    photoPath: String?,
    processing: Boolean,
    error: String?,
    enabled: Boolean,
    onOpenCamera: () -> Unit,
    onRemovePhoto: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Toolbox talk photo", style = MaterialTheme.typography.titleMedium)
            when {
                processing -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text("Adding date / time / GPS watermark…")
                }
                photoPath != null -> Box {
                    AsyncImage(
                        model = File(photoPath),
                        contentDescription = "Captured TBT photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).testTag(FormTags.PHOTO_PREVIEW),
                    )
                    FilledTonalIconButton(onClick = onRemovePhoto, enabled = enabled, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                        Icon(Icons.Filled.Close, "Remove photo")
                    }
                }
                else -> Text("Photo is stamped with date, time and GPS before upload.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onOpenCamera, enabled = enabled && !processing, modifier = Modifier.fillMaxWidth().testTag(FormTags.PHOTO_BUTTON)) {
                Icon(Icons.Filled.PhotoCamera, null)
                Spacer(Modifier.size(8.dp))
                Text(if (photoPath == null) "Capture photo" else "Retake photo")
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.testTag(FormTags.error(FormField.PHOTO)))
            }
        }
    }
}
