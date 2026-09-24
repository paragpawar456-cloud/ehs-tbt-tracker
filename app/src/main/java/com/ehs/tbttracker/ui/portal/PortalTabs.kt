package com.ehs.tbttracker.ui.portal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HighlightOff
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ehs.tbttracker.domain.model.RangePreset
import com.ehs.tbttracker.domain.model.SyncState
import com.ehs.tbttracker.domain.model.TbtRecord
import com.ehs.tbttracker.domain.usecase.DayStatus
import com.ehs.tbttracker.ui.dashboard.AnalyticsContent
import com.ehs.tbttracker.ui.dashboard.DashboardActions
import com.ehs.tbttracker.ui.dashboard.DashboardViewModel
import java.time.LocalDate
import java.util.Locale

private fun LocalDate.isoWithDay() = "$this (${format(Portal.dayFmt)})"

// ================================================================== Contractor Wise TBT

fun LazyListScope.contractorWiseTab(state: PortalUiState, a: PortalActions) {
    item(key = "cw_header") { ContractorHeaderCard(state, a) }
    item(key = "cw_chips") { ContractorChipsCard(state, a) }
    val r = state.report
    if (r == null) {
        item { EmptyNote("No contractors yet. Pull down to load the Google Sheet.") }
        return
    }
    item(key = "cw_kpis") { ContractorKpis(state) }
    item(key = "cw_done_head") {
        PanelHeader(
            title = "Days TBT was done (${r.doneDays.size} days)",
            icon = Icons.Filled.CheckCircle,
            color = Portal.colors.green,
            panel = Portal.colors.greenPanel,
            badge = "${r.manpower} Workers Total",
            badgeBg = Portal.colors.greenSoft,
        )
    }
    if (r.doneDays.isEmpty()) item { EmptyNote("No TBT recorded for ${r.contractor} in ${r.month.label()}.") }
    items(r.doneDays, key = { "done_${it.date}" }) { d -> DoneDayRow(d, a) }
    item(key = "cw_notdone_head") {
        PanelHeader(
            title = "Days TBT was not done (${r.notDoneDays.size} days)",
            icon = Icons.Filled.HighlightOff,
            color = Portal.colors.red,
            panel = Portal.colors.redPanel,
            badge = "No TBT Done",
            badgeBg = Portal.colors.redSoft,
        )
    }
    items(r.notDoneDays, key = { "miss_${it.date}" }) { d -> NotDoneDayRow(d, r.contractor, a) }
}

@Composable
private fun ContractorHeaderCard(state: PortalUiState, a: PortalActions) {
    val c = Portal.colors
    PortalCard {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(44.dp).background(c.indigoSoft, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Engineering, null, tint = c.indigo)
            }
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Contractor Wise TBT Details & Audit Sheet", color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, lineHeight = 21.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    state.contractor?.let { Badge(it, c.indigoSoft, c.indigo) }
                    Badge(state.month.label(), c.bg, c.ink, mono = true, bold = false)
                }
                Text("Monthly breakdown showing exact dates TBT was conducted, missed days, manpower count, and full printable/downloadable audit.",
                    color = c.muted, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Picker("Month:", state.month, state.months, { it.label() }, a.vm::selectMonth, Modifier.weight(1f), tag = "month_picker")
            Picker("Contractor:", state.contractor, state.allContractors, { it }, a.vm::selectContractor, Modifier.weight(1.3f), tag = "contractor_picker")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = a.vm::exportExcel,
                enabled = state.report != null,
                colors = ButtonDefaults.buttonColors(containerColor = c.green, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).testTag("export_excel"),
            ) { Icon(Icons.Filled.Description, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Export Excel", fontWeight = FontWeight.Bold) }
            Button(
                onClick = a.vm::exportPdf,
                enabled = state.report != null,
                colors = ButtonDefaults.buttonColors(containerColor = c.dark, contentColor = c.onDark),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).testTag("export_pdf"),
            ) { Icon(Icons.Filled.PictureAsPdf, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Export PDF", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ContractorChipsCard(state: PortalUiState, a: PortalActions) {
    val c = Portal.colors
    PortalCard {
        SectionLabel("Click dynamic button to select contractor:", Icons.Filled.Engineering, c.indigo)
        SearchBox(state.selection.contractorQuery, "Search contractor…", a.vm::setContractorQuery, "contractor_search")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("contractor_chips")) {
            items(state.contractorChips, key = { it.first }) { (name, done) ->
                val selected = name == state.contractor
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .background(if (selected) c.indigoSoft else c.surface)
                        .border(if (selected) 2.dp else 1.dp, if (selected) c.indigo else c.line, RoundedCornerShape(10.dp))
                        .clickable { a.vm.selectContractor(name) }
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                        .testTag("chip_$name"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, color = c.ink, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    DoneBadge(done)
                }
            }
        }
        if (state.contractorChips.isEmpty()) Text("No contractor matches this search.", color = c.muted, fontSize = 13.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContractorKpis(state: PortalUiState) {
    val r = state.report ?: return
    val c = Portal.colors
    val monthName = r.month.format(Portal.monthShort).uppercase()
    BoxWithConstraints {
        val perRow = if (maxWidth > 700.dp) 4 else 2
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = perRow) {
            val m = Modifier.weight(1f)
            KpiCard("TBT done in $monthName", Icons.Filled.CheckCircle, c.green, c.greenSoft, "${r.doneDays.size}",
                "Days (${r.sessions} Sessions)", c.green, m.testTag("kpi_done")) {
                Text("✓ Conducted by ${r.contractor}", color = c.green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            }
            KpiCard("Days not done TBT", Icons.Filled.HighlightOff, c.red, c.redSoft, "${r.notDoneDays.size}",
                "of ${r.daysInMonth} Days", c.red, m.testTag("kpi_not_done")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ErrorOutline, null, tint = c.red, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(4.dp))
                    Text("Marked as \"No TBT Done\"", color = c.red, fontSize = 13.sp)
                }
            }
            KpiCard("Monthly compliance rate", Icons.Filled.TrendingUp, c.indigo, c.indigoSoft, "${r.complianceRate}%",
                "Active Rate", c.indigo, m.testTag("kpi_rate")) {
                LinearProgressIndicator(
                    progress = { r.complianceRate / 100f },
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                    color = c.indigo, trackColor = c.track, drawStopIndicator = {},
                )
            }
            KpiCard("Total manpower trained", Icons.Filled.Groups, c.amber, c.amberSoft, "${r.manpower}",
                "Workers", c.ink, m.testTag("kpi_manpower")) {
                Text("Avg %.1f workers per conducted day".format(Locale.US, r.avgWorkersPerDay), color = c.muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PanelHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, panel: Color, badge: String, badgeBg: Color) {
    val c = Portal.colors
    Row(
        Modifier.fillMaxWidth().background(panel, RoundedCornerShape(14.dp)).border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(title.uppercase(), color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, letterSpacing = 0.5.sp, modifier = Modifier.weight(1f))
        Badge(badge, badgeBg, color)
    }
}

@Composable
private fun DoneDayRow(d: DayStatus, a: PortalActions) {
    val c = Portal.colors
    Column(
        Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(12.dp))
            .border(1.dp, c.green.copy(alpha = 0.35f), RoundedCornerShape(12.dp)).padding(14.dp)
            .testTag("done_${d.date}"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Day ${d.date.dayOfMonth} — ${d.date.isoWithDay()}", color = c.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.width(8.dp))
            Badge("TBT Done", c.greenSoft, c.green)
        }
        Text(
            buildLabel("Manpower: ", "${d.manpower} Workers", "  •  Location: ", d.locations.ifBlank { "Not recorded" }),
            color = c.ink, fontSize = 13.sp,
        )
        if (d.records.size > 1) Text("${d.records.size} sessions this day", color = c.muted, fontSize = 12.sp)
        if (d.records.any { it.syncState != SyncState.SYNCED }) Badge("Offline - Sync Pending", c.amberSoft, c.amber)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (d.records.any { it.photo != null }) {
                SmallOutlined("Photo", Icons.Filled.PhotoCamera, c.indigo, { a.onPhoto(d.records.first { it.photo != null }) })
            }
            SmallOutlined("Details", null, c.ink, { a.onDetails(d.records) })
        }
    }
}

@Composable
private fun buildLabel(k1: String, v1: String, k2: String, v2: String) = androidx.compose.ui.text.buildAnnotatedString {
    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)); append(k1.trim()); pop(); append(" $v1")
    append("  •  ")
    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)); append(k2.replace("•", "").trim()); pop(); append(" $v2")
}

@Composable
private fun NotDoneDayRow(d: DayStatus, contractor: String, a: PortalActions) {
    val c = Portal.colors
    Row(
        Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(12.dp))
            .border(1.dp, c.line, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("missed_${d.date}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Day ${d.date.dayOfMonth}: ${d.date.isoWithDay()}", color = c.ink, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            if (d.upcoming) Badge("Upcoming", c.bg, c.muted) else Badge("No TBT Done", c.redSoft, c.red)
        }
        if (!d.upcoming) {
            OutlinedButton(
                onClick = { a.nav.onLogTbt(d.date, contractor) },
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, c.green.copy(alpha = 0.5f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.testTag("log_${d.date}"),
            ) {
                Icon(Icons.Filled.Add, null, tint = c.green, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                Text("Log TBT", color = c.green, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ================================================================== Date Wise TBT

fun LazyListScope.dateWiseTab(state: PortalUiState, a: PortalActions) {
    item(key = "dw_head") {
        PortalCard {
            SectionLabel("Date wise TBT (${state.dateWise.size} dates)", Icons.Filled.CheckCircle, Portal.colors.green)
            Text("Every date with at least one toolbox talk, newest first.", color = Portal.colors.muted, fontSize = 13.sp)
        }
    }
    if (state.dateWise.isEmpty()) item { EmptyNote("No TBT dates yet.") }
    items(state.dateWise, key = { "dw_${it.date}" }) { day ->
        val c = Portal.colors
        PortalCard(padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(day.date.isoWithDay(), color = c.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Badge("${day.records.size} TBT · ${day.manpower} Workers", c.greenSoft, c.green)
            }
            day.records.forEach { r -> RecordLine(r, a) }
        }
    }
}

@Composable
private fun RecordLine(r: TbtRecord, a: PortalActions) {
    val c = Portal.colors
    Row(
        Modifier.fillMaxWidth().background(c.bg, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(r.contractor, color = c.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${r.manpower} Workers · ${r.location.ifBlank { "Location not recorded" }}" + (r.timestamp?.let { " · " + it.toLocalTime().toString().take(5) } ?: ""),
                color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (r.syncState != SyncState.SYNCED) Text("Offline - Sync Pending", color = c.amber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        if (r.photo != null) IconButton(onClick = { a.onPhoto(r) }) { Icon(Icons.Filled.PhotoCamera, "Photo", tint = c.indigo) }
        TextButton2("Details") { a.onDetails(listOf(r)) }
    }
}

@Composable
private fun TextButton2(text: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) { Text(text, color = Portal.colors.ink, fontWeight = FontWeight.SemiBold) }
}

// ================================================================== Missing TBT Audit

@OptIn(ExperimentalLayoutApi::class)
fun LazyListScope.missingAuditTab(state: PortalUiState, a: PortalActions) {
    item(key = "ma_head") {
        val c = Portal.colors
        PortalCard {
            SectionLabel("Missing TBT audit · 1st to end", Icons.Filled.HighlightOff, c.red)
            Text("For each day of ${state.month.label()}, contractors active this month that did not record a TBT. Tap a name to open its audit sheet.",
                color = c.muted, fontSize = 13.sp)
            Picker("Month:", state.month, state.months, { it.label() }, a.vm::selectMonth, Modifier.fillMaxWidth())
        }
    }
    items(state.missing, key = { "ma_${it.date}" }) { day ->
        val c = Portal.colors
        PortalCard(padding = 12.dp, background = if (day.upcoming) c.bg else c.surface) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Day ${day.date.dayOfMonth}: ${day.date.isoWithDay()}", color = c.ink, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.weight(1f))
                when {
                    day.upcoming -> Badge("Upcoming", c.bg, c.muted)
                    day.missing.isEmpty() -> Badge("All reported", c.greenSoft, c.green)
                    else -> Badge("${day.missing.size} missing", c.redSoft, c.red)
                }
            }
            if (!day.upcoming) {
                Text("${day.reported.size} of ${day.reported.size + day.missing.size} contractors reported", color = c.muted, fontSize = 12.sp)
                if (day.missing.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        day.missing.forEach { name ->
                            Text(name, Modifier.clip(RoundedCornerShape(50)).background(c.redPanel).border(1.dp, c.red.copy(alpha = 0.3f), RoundedCornerShape(50))
                                .clickable { a.vm.openContractor(name) }.padding(horizontal = 10.dp, vertical = 4.dp),
                                color = c.red, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

// ================================================================== Monthly Grid

fun LazyListScope.monthlyGridTab(state: PortalUiState, a: PortalActions) {
    item(key = "mg") {
        val c = Portal.colors
        PortalCard {
            SectionLabel("Monthly grid · ${state.month.label()}", null, c.indigo)
            Picker("Month:", state.month, state.months, { it.label() }, a.vm::selectMonth, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Legend(c.greenSoft, c.green, "7", "workers briefed"); Legend(c.redSoft, c.red, "✗", "missed"); Legend(c.bg, c.muted, "", "upcoming")
            }
            val days = state.month.lengthOfMonth()
            val cell = 30.dp
            Row {
                Column {
                    Box(Modifier.height(cell).width(132.dp))
                    state.grid.forEach { row ->
                        Row(Modifier.height(cell).width(132.dp).clickable { a.vm.openContractor(row.contractor) }, verticalAlignment = Alignment.CenterVertically) {
                            Text(row.contractor, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f))
                            Text("${row.doneDays}", color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(end = 6.dp))
                        }
                    }
                }
                Column(Modifier.horizontalScroll(rememberScrollState()).testTag("grid")) {
                    Row {
                        (1..days).forEach { d ->
                            val date = state.month.atDay(d)
                            Column(Modifier.size(cell), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text("$d", color = c.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 12.sp)
                                Text(date.format(Portal.dayFmt).take(2), color = c.muted, fontSize = 9.sp, lineHeight = 10.sp)
                            }
                        }
                    }
                    state.grid.forEach { row ->
                        Row {
                            row.cells.forEachIndexed { i, v ->
                                val date = state.month.atDay(i + 1)
                                val (bg, fg, t) = when {
                                    v != null -> Triple(c.greenSoft, c.green, if (v > 0) "$v" else "✓")
                                    date.isAfter(state.today) -> Triple(c.bg, c.muted, "")
                                    else -> Triple(c.redPanel, c.red, "✗")
                                }
                                Box(Modifier.size(cell).padding(1.5.dp).background(bg, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                                    Text(t, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend(bg: Color, fg: Color, sample: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(18.dp).background(bg, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
            Text(sample, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(4.dp)); Text(label, color = Portal.colors.muted, fontSize = 11.sp)
    }
}

// ================================================================== Analytics

@Composable
fun AnalyticsTab(viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AnalyticsContent(
        state,
        DashboardActions(
            onRefresh = viewModel::refresh,
            onRange = viewModel::setRange,
            onContractor = viewModel::setContractor,
            onQuery = viewModel::setQuery,
        ),
    )
}

// ================================================================== Master Contractors

fun LazyListScope.mastersTab(state: PortalUiState, a: PortalActions) {
    item(key = "mc_head") {
        val c = Portal.colors
        PortalCard {
            SectionLabel("Master contractors (${state.masters.size})", Icons.Filled.Engineering, c.amber)
            val registered = state.masters.count { it.registered }
            Text(
                if (registered > 0) "$registered registered on the \"Master Contractors\" tab, plus contractors found in form responses. ${state.month.label()} done-days shown."
                else "Contractors found in the form responses. To register all agencies (including ones that have not reported yet), add a tab named \"Master Contractors\" to the sheet with one name per row.",
                color = c.muted, fontSize = 13.sp,
            )
        }
    }
    items(state.masters, key = { "mc_${it.name}" }) { m ->
        val c = Portal.colors
        Row(
            Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(12.dp)).border(1.dp, c.line, RoundedCornerShape(12.dp))
                .clickable { a.vm.openContractor(m.name) }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.name, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (m.registered) { Spacer(Modifier.width(6.dp)); Badge("Registered", c.indigoSoft, c.indigo) }
                }
                Text("${m.totalSessions} TBT · ${m.totalManpower} worker-briefings" + (m.lastTbt?.let { " · last $it" } ?: " · no TBT yet"),
                    color = c.muted, fontSize = 12.sp)
            }
            DoneBadge(m.doneThisMonth)
        }
    }
}

// ================================================================== All Records

fun LazyListScope.allRecordsTab(state: PortalUiState, a: PortalActions) {
    item(key = "ar_head") {
        PortalCard {
            SectionLabel("All records (${state.records.size})", null, Portal.colors.indigo)
            SearchBox(state.selection.recordQuery, "Search contractor, location, date…", a.vm::setRecordQuery, "record_search")
        }
    }
    if (state.records.isEmpty()) item { EmptyNote("No records match.") }
    items(state.records, key = { "ar_${it.id}" }) { r ->
        val c = Portal.colors
        Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(12.dp)).border(1.dp, c.line, RoundedCornerShape(12.dp)).padding(start = 12.dp, top = 10.dp, bottom = 4.dp)) {
            Text(r.date?.isoWithDay() ?: "Undated", color = c.muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            RecordLine(r, a)
        }
    }
}

// ================================================================== shared bits

@Composable
private fun SearchBox(value: String, placeholder: String, onChange: (String) -> Unit, tag: String) {
    val c = Portal.colors
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(placeholder, fontSize = 14.sp) },
        leadingIcon = { Icon(Icons.Filled.Search, null, tint = c.muted) },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, "Clear") } },
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = c.line, focusedBorderColor = c.indigo),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, color = Portal.colors.muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp))
}
