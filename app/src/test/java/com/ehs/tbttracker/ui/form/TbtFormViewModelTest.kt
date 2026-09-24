package com.ehs.tbttracker.ui.form

import app.cash.turbine.test
import com.ehs.tbttracker.data.photo.PhotoProcessor
import com.ehs.tbttracker.domain.model.TbtDraft
import com.ehs.tbttracker.domain.repository.ConnectivityObserver
import com.ehs.tbttracker.domain.repository.TbtRepository
import com.ehs.tbttracker.domain.usecase.FormField
import com.ehs.tbttracker.domain.usecase.SubmitTbtUseCase
import com.ehs.tbttracker.domain.usecase.ValidateTbtDraftUseCase
import com.ehs.tbttracker.testutil.Fixtures
import com.ehs.tbttracker.testutil.Fixtures.TODAY
import com.ehs.tbttracker.testutil.Fixtures.record
import com.ehs.tbttracker.testutil.MainDispatcherExtension
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.File

class TbtFormViewModelTest {
    @JvmField @RegisterExtension val main = MainDispatcherExtension()

    private val repository = mockk<TbtRepository>(relaxed = true)
    private val connectivity = mockk<ConnectivityObserver>()
    private val photoProcessor = mockk<PhotoProcessor>()
    private val records = MutableStateFlow(
        listOf(
            record(contractor = "Choudhary Construction", location = "Tower D1 Laval 09"),
            record(contractor = "Choudhary Construction", location = "Tower D1 Laval 09"),
            record(contractor = "Stellar", location = "P2"),
        ),
    )

    @BeforeEach
    fun setUp() {
        every { repository.observeRecords() } returns records
        every { connectivity.isCurrentlyOnline() } returns true
        coEvery { photoProcessor.process(any(), any()) } returns File("/cache/processed.jpg")
        coEvery { repository.submit(any()) } answers { record(id = "new", contractor = firstArg<TbtDraft>().contractor) }
    }

    private fun vm() = TbtFormViewModel(
        repository, ValidateTbtDraftUseCase(), SubmitTbtUseCase(repository, connectivity), photoProcessor, Fixtures.CLOCK,
    )

    private fun TbtFormViewModel.fillValid() {
        onContractorChange("Stellar")
        onManpowerChange("4")
        onLocationChange("B1 8 Floor")
        onPhotoCaptured(File("/cache/raw.jpg"))
    }

    @Test
    fun `date defaults to today`() {
        assertThat(vm().state.value.date).isEqualTo(TODAY)
    }

    @Test
    fun `suggestions rank contractors by frequency and merge default locations`() = runTest {
        val vm = vm()
        vm.suggestions.test {
            val s = expectMostRecentItem()
            assertThat(s.contractors).containsExactly("Choudhary Construction", "Stellar").inOrder()
            assertThat(s.locations.first()).isEqualTo("Tower D1 Laval 09")
            assertThat(s.locations).contains("NTA Parking")
        }
    }

    @Test
    fun `manpower input keeps digits only`() {
        val vm = vm()
        vm.onManpowerChange("12a-3")
        assertThat(vm.state.value.manpower).isEqualTo("123")
    }

    @Test
    fun `invalid submit shows field errors and error state without calling repository`() = runTest {
        val vm = vm()
        vm.submit()
        val s = vm.state.value
        assertThat(s.errors.keys).containsAtLeast(FormField.CONTRACTOR, FormField.MANPOWER, FormField.LOCATION, FormField.PHOTO)
        assertThat(s.submission).isInstanceOf(SubmissionState.Error::class.java)
        coVerify(exactly = 0) { repository.submit(any()) }
    }

    @Test
    fun `errors clear live once fixed after first attempt`() {
        val vm = vm()
        vm.submit()
        vm.onManpowerChange("7")
        assertThat(vm.state.value.errors).doesNotContainKey(FormField.MANPOWER)
        assertThat(vm.state.value.errors).containsKey(FormField.LOCATION)
    }

    @Test
    fun `photo capture is processed with contractor and location label`() {
        val vm = vm()
        vm.onContractorChange("Stellar")
        vm.onLocationChange("P2")
        vm.onPhotoCaptured(File("/cache/raw.jpg"))
        assertThat(vm.state.value.photoPath).isEqualTo(File("/cache/processed.jpg").absolutePath)
        coVerify { photoProcessor.process(File("/cache/raw.jpg"), "Stellar · P2") }
    }

    @Test
    fun `successful submit goes Submitting then Success and resets the form`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val draft = slot<TbtDraft>()
        coEvery { repository.submit(capture(draft)) } coAnswers { gate.await(); record(id = "new") }
        val vm = vm()
        vm.fillValid()
        vm.state.test {
            skipItems(1)
            vm.submit()
            assertThat(awaitItem().submission).isEqualTo(SubmissionState.Submitting)
            gate.complete(Unit)
            val done = awaitItem()
            assertThat(done.submission).isInstanceOf(SubmissionState.Success::class.java)
            assertThat((done.submission as SubmissionState.Success).offline).isFalse()
            assertThat(done.contractor).isEmpty()
            assertThat(done.photoPath).isNull()
        }
        assertThat(draft.captured.manpower).isEqualTo(4)
        assertThat(draft.captured.contractor).isEqualTo("Stellar")
    }

    @Test
    fun `offline submit reports queued for sync`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns false
        val vm = vm()
        vm.fillValid()
        vm.submit()
        val s = vm.state.value.submission as SubmissionState.Success
        assertThat(s.offline).isTrue()
        assertThat(s.message).contains("Offline")
    }

    @Test
    fun `repository failure surfaces as Error and keeps input`() = runTest {
        coEvery { repository.submit(any()) } throws IllegalStateException("Disk full")
        val vm = vm()
        vm.fillValid()
        vm.submit()
        val s = vm.state.value
        assertThat((s.submission as SubmissionState.Error).message).isEqualTo("Disk full")
        assertThat(s.contractor).isEqualTo("Stellar")
    }
}
