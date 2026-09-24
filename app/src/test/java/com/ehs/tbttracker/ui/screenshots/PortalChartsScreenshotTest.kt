package com.ehs.tbttracker.ui.screenshots

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ehs.tbttracker.data.local.toDomain
import com.ehs.tbttracker.data.remote.SheetRowMapper
import com.ehs.tbttracker.domain.parsing.ContractorNormalizer
import com.ehs.tbttracker.testutil.Fixtures
import com.ehs.tbttracker.ui.charts.LocalChartAnimations
import com.ehs.tbttracker.ui.portal.ContractorCharts
import com.ehs.tbttracker.ui.portal.ContractorKpis
import com.ehs.tbttracker.ui.portal.DateWiseCharts
import com.ehs.tbttracker.ui.portal.Portal
import com.ehs.tbttracker.ui.portal.PortalSelection
import com.ehs.tbttracker.ui.portal.buildPortalState
import com.ehs.tbttracker.ui.theme.EhsTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/** Renders the chart sections with the real sheet snapshot so the layout can be reviewed as PNGs. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h2600dp-xhdpi")
class PortalChartsScreenshotTest {

    private fun state(contractor: String) = Fixtures.realSheet().rows.map { SheetRowMapper.toEntity(it) }.let { es ->
        val n = ContractorNormalizer(es.map { it.contractorName })
        buildPortalState(
            records = es.map { it.toDomain(n) },
            masters = emptyList(),
            sel = PortalSelection(month = YearMonth.of(2026, 9), contractor = contractor),
            online = true, refreshing = false, syncError = null, today = Fixtures.TODAY,
        )
    }

    @Composable
    private fun Frame(dark: Boolean, content: @Composable () -> Unit) {
        EhsTheme(darkTheme = dark) {
            CompositionLocalProvider(LocalChartAnimations provides false) {
                Column(
                    Modifier.fillMaxWidth().background(Portal.colors.bg).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) { content() }
            }
        }
    }

    @Test
    fun contractorWiseCharts() {
        val s = state("Ami Plumbing")
        captureRoboImage("build/screenshots/contractor_wise_charts.png") {
            Frame(dark = false) { ContractorKpis(s); ContractorCharts(s) }
        }
    }

    @Test
    fun contractorWiseChartsMultipleLocations() {
        val s = state("Choudhary Construction")
        captureRoboImage("build/screenshots/contractor_wise_choudhary.png") {
            Frame(dark = false) { ContractorCharts(s) }
        }
    }

    @Test
    fun dateWiseCharts() {
        val s = state("Ami Plumbing")
        captureRoboImage("build/screenshots/date_wise_charts.png") {
            Frame(dark = false) { DateWiseCharts(s) {} }
        }
    }

    @Test
    fun contractorWiseChartsDark() {
        val s = state("Credible Construction Company")
        captureRoboImage("build/screenshots/contractor_wise_dark.png") {
            Frame(dark = true) { ContractorKpis(s); ContractorCharts(s) }
        }
    }
}
