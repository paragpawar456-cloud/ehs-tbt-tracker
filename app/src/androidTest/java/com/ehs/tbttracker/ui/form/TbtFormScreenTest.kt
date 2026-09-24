package com.ehs.tbttracker.ui.form

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ehs.tbttracker.data.photo.PhotoProcessor
import com.ehs.tbttracker.domain.model.SyncState
import com.ehs.tbttracker.domain.model.TbtDraft
import com.ehs.tbttracker.domain.model.TbtRecord
import com.ehs.tbttracker.domain.repository.ConnectivityObserver
import com.ehs.tbttracker.domain.repository.TbtRepository
import com.ehs.tbttracker.domain.usecase.FormField
import com.ehs.tbttracker.domain.usecase.SubmitTbtUseCase
import com.ehs.tbttracker.domain.usecase.ValidateTbtDraftUseCase
import com.ehs.tbttracker.ui.theme.EhsTheme
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class TbtFormScreenTest {
    @get:Rule val compose = createComposeRule()

    private val repository = mockk<TbtRepository>(relaxed = true)
    private val connectivity = mockk<ConnectivityObserver>()
    private val photoProcessor = mockk<PhotoProcessor>()
    private lateinit var vm: TbtFormViewModel
    private val gate = CompletableDeferred<Unit>()

    private fun record(d: TbtDraft) = TbtRecord("new", null, d.date, d.contractor, d.contractor, d.manpower, d.location, null, "", SyncState.PENDING)

    @Before
    fun setUp() {
        every { repository.observeRecords() } returns flowOf(emptyList())
        every { connectivity.isCurrentlyOnline() } returns true
        coEvery { photoProcessor.process(any(), any()) } returns File("/data/local/tmp/tbt.jpg")
        coEvery { repository.submit(any()) } coAnswers { gate.await(); record(firstArg()) }
        vm = TbtFormViewModel(repository, ValidateTbtDraftUseCase(), SubmitTbtUseCase(repository, connectivity), photoProcessor, Clock.systemDefaultZone())

        compose.setContent {
            EhsTheme {
                val snackbar = remember { SnackbarHostState() }
                Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { p ->
                    androidx.compose.foundation.layout.Box(Modifier.padding(p)) { TbtFormRoute(snackbar, vm) }
                }
            }
        }
    }

    private fun fillValidForm() {
        // Set via the ViewModel so the auto-complete popup doesn't sit over the submit button.
        compose.runOnIdle { vm.onContractorChange("Credible Construction Company") }
        compose.onNodeWithTag(FormTags.MANPOWER).performTextInput("32")
        compose.onNodeWithTag(FormTags.LOCATION).performTextInput("T-B1, L-P2")
        compose.runOnIdle { vm.onPhotoCaptured(File("/data/local/tmp/raw.jpg")) }
    }

    @Test
    fun dateDefaultsToToday() {
        compose.onNodeWithTag(FormTags.DATE).assertIsDisplayed()
        compose.runOnIdle { assert(vm.state.value.date == LocalDate.now()) }
    }

    @Test
    fun emptySubmit_showsFieldErrors_andErrorSnackbar() {
        compose.onNodeWithTag(FormTags.SUBMIT).performScrollTo().performClick()

        compose.onNodeWithText("Please fix the highlighted fields").assertIsDisplayed()
        compose.onNodeWithTag(FormTags.error(FormField.PHOTO)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(FormTags.error(FormField.LOCATION)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(FormTags.error(FormField.MANPOWER)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(FormTags.error(FormField.CONTRACTOR)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun contractorAutocomplete_offersAddNew() {
        compose.onNodeWithTag(FormTags.CONTRACTOR).performTextInput("Prachi Enterprises")
        compose.onNodeWithText("Add new contractor \"Prachi Enterprises\"").assertIsDisplayed()
    }

    @Test
    fun manpowerFieldRejectsNonDigits() {
        compose.onNodeWithTag(FormTags.MANPOWER).performTextInput("1O labour")
        compose.runOnIdle { assert(vm.state.value.manpower == "1") }
    }

    @Test
    fun validSubmit_showsLoading_thenSuccessSnackbar() {
        fillValidForm()
        compose.onNodeWithTag(FormTags.SUBMIT).performScrollTo().performClick()

        // Loading: spinner in button, inputs disabled while the save is in flight.
        compose.onNodeWithTag(FormTags.SUBMIT_PROGRESS, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(FormTags.MANPOWER).assertIsNotEnabled()

        gate.complete(Unit)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTextCount("TBT saved - uploading to Google Sheet") > 0
        }
        compose.runOnIdle { assert(vm.state.value.contractor.isEmpty()) }
    }

    @Test
    fun offlineSubmit_showsOfflineSnackbar() {
        every { connectivity.isCurrentlyOnline() } returns false
        gate.complete(Unit)
        fillValidForm()
        compose.onNodeWithTag(FormTags.SUBMIT).performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTextCount("Offline - saved. Will sync automatically when online") > 0
        }
    }

    @Test
    fun repositoryError_showsErrorSnackbar_andKeepsInput() {
        coEvery { repository.submit(any()) } throws IllegalStateException("Storage unavailable")
        fillValidForm()
        compose.onNodeWithTag(FormTags.SUBMIT).performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextCount("Storage unavailable") > 0 }
        compose.runOnIdle { assert(vm.state.value.manpower == "32") }
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String): Int =
    onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
