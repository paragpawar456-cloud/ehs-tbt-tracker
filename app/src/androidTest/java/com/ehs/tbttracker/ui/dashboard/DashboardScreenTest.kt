package com.ehs.tbttracker.ui.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ehs.tbttracker.domain.model.RangePreset
import com.ehs.tbttracker.domain.model.RecordFilter
import com.ehs.tbttracker.domain.model.SyncState
import com.ehs.tbttracker.domain.model.TbtRecord
import com.ehs.tbttracker.domain.usecase.ComputeDashboardUseCase
import com.ehs.tbttracker.ui.theme.EhsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 9, 24)

    private fun rec(id: String, contractor: String, mp: Int, date: LocalDate, location: String = "P2 NTA", sync: SyncState = SyncState.SYNCED) =
        TbtRecord(id, LocalDateTime.of(date, java.time.LocalTime.of(9, 30)), date, contractor, contractor, mp, location, null, "", sync)

    private fun state(): DashboardUiState {
        val records = listOf(
            rec("1", "Credible Construction Company", 32, today),
            rec("2", "Stellar", 4, today, sync = SyncState.PENDING),
            rec("3", "Ami Plumbing", 10, today.minusDays(2), location = ""),
        )
        val filter = RangePreset.WEEK.toFilter(today, RecordFilter())
        return DashboardUiState(
            isLoading = false,
            stats = ComputeDashboardUseCase()(records, filter, today),
            contractors = records.map { it.contractor }.sorted(),
            log = records.groupBy { it.date }.map { (d, r) -> LogDay(d, r) },
            filteredCount = 3,
            totalRows = 3,
        )
    }

    @Test
    fun showsTodayKpisAndNotReportedContractors() {
        compose.setContent { EhsTheme { DashboardScreen(state(), DashboardActions()) } }
        compose.onNodeWithTag("kpi_tbts").assertIsDisplayed()
        compose.onAllNodesWithText("36").onFirst().assertIsDisplayed() // 32 + 4 workers today (KPI and trend both show it)
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasTestTag("not_reported_panel"))
        compose.onAllNodesWithText("Ami Plumbing").onFirst().assertIsDisplayed()
        compose.onNodeWithText("last 22 Sep").assertIsDisplayed()
    }

    @Test
    fun newTbtAndRangeControlsCallBack() {
        var newTbt = false
        var range: RangePreset? = null
        compose.setContent {
            EhsTheme { DashboardScreen(state(), DashboardActions(onNewTbt = { newTbt = true }, onRange = { range = it })) }
        }
        compose.onNodeWithTag("new_tbt").performClick()
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasTestTag("coverage_panel"))
        compose.onNodeWithTag("range_month").performClick()
        compose.runOnIdle {
            assertTrue(newTbt)
            assertEquals(RangePreset.MONTH, range)
        }
    }

    @Test
    fun logFlagsMissingLocationAndPendingSync() {
        compose.setContent { EhsTheme { DashboardScreen(state(), DashboardActions()) } }
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Location not recorded"))
        compose.onNodeWithText("Location not recorded").assertIsDisplayed()
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasTestTag("chip_pending"))
    }
}
