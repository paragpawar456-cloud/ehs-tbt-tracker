package com.ehs.tbttracker.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.ehs.tbttracker.domain.model.ContractorShare
import com.ehs.tbttracker.domain.model.DayTotal
import com.ehs.tbttracker.domain.model.PhotoRef
import com.ehs.tbttracker.domain.model.RangePreset
import com.ehs.tbttracker.domain.model.SyncState
import com.ehs.tbttracker.domain.model.TbtRecord
import com.ehs.tbttracker.domain.parsing.DriveLinkResolver
import com.ehs.tbttracker.ui.components.Eyebrow
import com.ehs.tbttracker.ui.components.OfflineBanner
import com.ehs.tbttracker.ui.components.Panel
import com.ehs.tbttracker.ui.components.PanelHeader
import com.ehs.tbttracker.ui.components.SyncStateChip
import com.ehs.tbttracker.ui.components.toImageModel
import com.ehs.tbttracker.ui.theme.Ehs
import com.ehs.tbttracker.ui.theme.MonoFamily
import com.ehs.tbttracker.ui.util.longLabel
import com.ehs.tbttracker.ui.util.shortLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

const val SHEET_URL = "https://docs.google.com/spreadsheets/d/1nmAYAH25c2-4TVLvPJaOJLjbFRYbuzK8tteh1xPD-jw/edit#gid=1314221799"
private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun DashboardRoute(snackbar: SnackbarHostState, onNewTbt: () -> Unit, viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.messageShown() }
    }
    DashboardScreen(
        state = state,
        actions = DashboardActions(
            onRefresh = viewModel::refresh,
            onNewTbt = onNewTbt,
            onRange = viewModel::setRange,
            onContractor = viewModel::setContractor,
            onQuery = viewModel::setQuery,
            onMoreDays = viewModel::showMoreDays,
            onRetryFailed = viewModel::retryFailed,
        ),
    )
}

data class DashboardActions(
    val onRefresh: () -> Unit = {},
    val onNewTbt: () -> Unit = {},
    val onRange: (RangePreset) -> Unit = {},
    val onContractor: (String?) -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onMoreDays: () -> Unit = {},
    val onRetryFailed: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(state: DashboardUiState, actions: DashboardActions) {
    val c = Ehs.colors
    Column(Modifier.fillMaxSize().background(c.bg).navigationBarsPadding()) {
        SiteTopBar(state, actions)
        PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = actions.onRefresh, modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                Modifier.fillMaxSize().testTag("dashboard_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!state.isOnline) item { OfflineBanner(state.stats.pendingSyncCount) }
                if (state.failedCount > 0) item { FailedBanner(state.failedCount, actions.onRetryFailed) }
                item { TodayHeader(state.stats.today) }
                item { KpiRow(state) }
                item { TrendPanel(state.stats.trend) }
                item { NotReportedPanel(state) }
                item { CoveragePanel(state, actions) }
                item { LogPanel(state, actions) }
                item {
                    Text(
                        "New entries are saved on this phone first and upload to the Google Sheet automatically.",
                        style = MaterialTheme.typography.labelMedium, color = c.muted,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- top bar

@Composable
private fun SiteTopBar(state: DashboardUiState, actions: DashboardActions) {
    val c = Ehs.colors
    val (dotColor, status) = when {
        state.isRefreshing || state.isLoading -> c.warn to "Syncing with Google Sheet…"
        !state.isOnline -> c.crit to "Offline"
        else -> c.accent to "${state.totalRows} rows · live"
    }
    Row(
        Modifier.fillMaxWidth().background(c.navy).statusBarsPadding().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)
            .testTag("top_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("TBT Site Tracker", style = MaterialTheme.typography.titleLarge, color = c.onNavy, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(dotColor, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(status, style = MaterialTheme.typography.labelMedium, color = c.onNavy.copy(alpha = 0.8f), maxLines = 1,
                    modifier = Modifier.testTag("status_text"))
            }
        }
        IconButton(onClick = actions.onRefresh) { Icon(Icons.Filled.Refresh, "Refresh", tint = c.onNavy) }
        Button(
            onClick = actions.onNewTbt,
            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier.testTag("new_tbt"),
        ) {
            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("New TBT", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FailedBanner(count: Int, onRetry: () -> Unit) {
    val c = Ehs.colors
    Row(
        Modifier.fillMaxWidth().background(c.critSoft, RoundedCornerShape(10.dp)).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$count record(s) could not be uploaded", style = MaterialTheme.typography.bodySmall, color = c.ink, modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry) { Text("Retry", color = c.crit) }
    }
}

// ---------------------------------------------------------------- today

@Composable
private fun TodayHeader(today: LocalDate) {
    val uri = LocalUriHandler.current
    Row(verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Eyebrow("Toolbox talks today")
            Text(today.longLabel(), style = MaterialTheme.typography.headlineSmall, color = Ehs.colors.ink)
        }
        TextButton(onClick = { uri.openUri(SHEET_URL) }) { Text("Open sheet ↗", color = Ehs.colors.muted) }
    }
}

@Composable
private fun KpiRow(state: DashboardUiState) {
    val s = state.stats
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        KpiTile("TBTs held", s.todayTbtCount.toString(), null, "sessions today",
            delta(s.todayTbtCount, s.yesterdayTbtCount), Modifier.weight(1f).testTag("kpi_tbts"))
        KpiTile("Manpower", s.todayManpower.toString(), null, "workers briefed",
            delta(s.todayManpower, s.yesterdayManpower), Modifier.weight(1f).testTag("kpi_manpower"))
        KpiTile("Contractors", s.activeContractorsToday.toString(), "/${s.totalContractors}", "reporting today",
            delta(s.activeContractorsToday, s.activeContractorsYesterday), Modifier.weight(1f).testTag("kpi_contractors"))
    }
}

private fun delta(now: Int, before: Int): String {
    val d = now - before
    return "${if (d > 0) "+" else ""}$d vs yday"
}

@Composable
private fun KpiTile(label: String, value: String, suffix: String?, caption: String, delta: String, modifier: Modifier) {
    val c = Ehs.colors
    Column(
        modifier.background(c.surface, RoundedCornerShape(12.dp))
            .padding(12.dp)
            .semantics { contentDescription = "$label $value${suffix.orEmpty()}, $caption" },
    ) {
        Eyebrow(label)
        Spacer(Modifier.height(8.dp))
        Text(
            buildAnnotatedString {
                append(value)
                if (suffix != null) withStyle(SpanStyle(fontSize = 17.sp, color = c.muted)) { append(suffix) }
            },
            style = MaterialTheme.typography.displaySmall, color = c.ink, maxLines = 1,
        )
        Text(caption, style = MaterialTheme.typography.labelMedium, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Text(delta, style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFamily, fontSize = 11.sp), color = c.muted, maxLines = 1)
    }
}

// ---------------------------------------------------------------- trend

@Composable
private fun TrendPanel(trend: List<DayTotal>) {
    val c = Ehs.colors
    val max = (trend.maxOfOrNull { it.manpower } ?: 0).coerceAtLeast(1)
    Panel(Modifier.testTag("trend_panel")) {
        PanelHeader("Workers briefed, last 14 days", "peak $max")
        Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            trend.forEachIndexed { i, day ->
                val isToday = i == trend.lastIndex
                val frac by animateFloatAsState(day.manpower / max.toFloat(), label = "trend")
                Column(
                    Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = "${day.date.longLabel()}: ${day.manpower} workers" },
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (day.manpower > 0) {
                        Text("${day.manpower}", style = TextStyleMono, color = c.muted, maxLines = 1)
                        Spacer(Modifier.height(3.dp))
                    }
                    Box(
                        Modifier.fillMaxWidth()
                            .fillMaxHeight(if (day.manpower == 0) 0.02f else (frac * 0.82f).coerceAtLeast(0.03f))
                            .background(if (isToday) c.todayBar else c.accent.copy(alpha = 0.85f), RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            trend.forEachIndexed { i, day ->
                Text(
                    if (i % 2 == 1 || i == trend.lastIndex) day.date.dayOfMonth.toString() else "",
                    style = TextStyleMono, color = c.muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private val TextStyleMono = androidx.compose.ui.text.TextStyle(fontFamily = MonoFamily, fontSize = 10.sp, lineHeight = 12.sp)

// ---------------------------------------------------------------- not reported

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotReportedPanel(state: DashboardUiState) {
    val c = Ehs.colors
    val s = state.stats
    Panel(Modifier.testTag("not_reported_panel")) {
        PanelHeader("Not reported today", "active in last 7 days")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (s.notReportedToday.isEmpty()) {
                Chip("Every recently active contractor has reported today", c.accentSoft, null)
            } else {
                s.notReportedToday.forEach { Chip(it.contractor, c.warnSoft, "last ${it.lastReported.shortLabel()}") }
            }
        }
        Text(
            if (s.reportedToday.isEmpty()) "No TBT recorded yet today." else "Reported: ${s.reportedToday.joinToString(", ")}",
            style = MaterialTheme.typography.bodySmall, color = c.muted,
        )
    }
}

@Composable
private fun Chip(text: String, bg: androidx.compose.ui.graphics.Color, meta: String?) {
    val c = Ehs.colors
    Row(
        Modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = c.ink)
        if (meta != null) Text(meta, style = TextStyleMono.copy(fontSize = 11.sp), color = c.muted)
    }
}

// ---------------------------------------------------------------- coverage

@Composable
private fun CoveragePanel(state: DashboardUiState, actions: DashboardActions) {
    val s = state.stats
    Panel(Modifier.testTag("coverage_panel")) {
        PanelHeader("Manpower coverage by contractor", "${s.filteredTbtCount} TBTs · ${s.filteredManpower} worker-briefings")
        RangeSegments(state.controls.range, actions.onRange)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ContractorPicker(state.controls.contractor, state.contractors, actions.onContractor)
        }
        SearchField(state.controls.query, actions.onQuery)
        val max = (s.distribution.maxOfOrNull { it.manpower } ?: 0).coerceAtLeast(1)
        if (s.distribution.isEmpty()) {
            Text("No TBTs match these filters.", style = MaterialTheme.typography.bodySmall, color = Ehs.colors.muted)
        }
        s.distribution.forEach { ContractorBar(it, it.manpower / max.toFloat()) }
    }
}

@Composable
private fun RangeSegments(selected: RangePreset, onSelect: (RangePreset) -> Unit) {
    val c = Ehs.colors
    Row(
        Modifier.background(c.surface2, RoundedCornerShape(8.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        RangePreset.entries.forEach { r ->
            val on = r == selected
            Text(
                r.label,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
                color = if (on) c.ink else c.muted,
                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                    .background(if (on) c.surface else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(r) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("range_${r.name.lowercase()}"),
            )
        }
    }
}

@Composable
private fun ContractorPicker(selected: String?, options: List<String>, onSelect: (String?) -> Unit) {
    val c = Ehs.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).background(c.surface2).clickable { open = true }
                .padding(start = 12.dp, end = 6.dp, top = 7.dp, bottom = 7.dp)
                .testTag("contractor_picker"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(selected ?: "All contractors (${options.size})", style = MaterialTheme.typography.bodySmall, color = c.ink, maxLines = 1)
            Icon(Icons.Filled.ArrowDropDown, null, tint = c.muted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("All contractors") }, onClick = { onSelect(null); open = false })
            options.forEach { name -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(name); open = false }) }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val c = Ehs.colors
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        placeholder = { Text("Search location, e.g. Tower C1, P2", style = MaterialTheme.typography.bodySmall) },
        leadingIcon = { Icon(Icons.Filled.Search, null, tint = c.muted) },
        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, "Clear search") } },
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = c.line, focusedBorderColor = c.accent),
        modifier = Modifier.fillMaxWidth().testTag("filter_search"),
    )
}

@Composable
private fun ContractorBar(share: ContractorShare, fraction: Float) {
    val c = Ehs.colors
    val animated by animateFloatAsState(fraction, label = "bar")
    Column(Modifier.fillMaxWidth().testTag("bar_${share.contractor}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(share.contractor, style = MaterialTheme.typography.bodyMedium, color = c.ink, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("${share.manpower}", style = MaterialTheme.typography.titleMedium, color = c.ink)
            Spacer(Modifier.width(6.dp))
            Text("${share.sessions} TBT", style = TextStyleMono.copy(fontSize = 11.sp), color = c.muted)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).background(c.barTrack, RoundedCornerShape(4.dp))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(animated.coerceIn(0.02f, 1f)).background(c.accent, RoundedCornerShape(4.dp)))
        }
    }
}

// ---------------------------------------------------------------- log

@Composable
private fun LogPanel(state: DashboardUiState, actions: DashboardActions) {
    val c = Ehs.colors
    Panel(Modifier.testTag("log_panel")) {
        PanelHeader("Inspection log", "${state.filteredCount} entries")
        if (state.log.isEmpty()) {
            Text("No entries in this range.", style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        state.log.forEach { day ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row {
                    Text(day.date?.longLabel() ?: "Undated", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = c.muted, modifier = Modifier.weight(1f))
                    Text("${day.entries.size} TBT · ${day.manpower} workers", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                day.entries.forEach { LogEntry(it) }
            }
        }
        if (state.hasMoreDays) {
            TextButton(onClick = actions.onMoreDays, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("more_days")) {
                Text("Show more days", color = c.ink)
            }
        }
    }
}

@Composable
private fun LogEntry(r: TbtRecord) {
    val c = Ehs.colors
    val uri = LocalUriHandler.current
    var showPhoto by rememberSaveable(r.id) { mutableStateOf(false) }
    val submittedEarly = r.timestamp != null && r.date != null && r.date.isAfter(r.timestamp.toLocalDate())
    Column(
        Modifier.fillMaxWidth().background(c.surface2, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("log_entry_${r.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(r.timestamp?.format(HHMM) ?: "—", style = TextStyleMono.copy(fontSize = 12.sp), color = c.muted, modifier = Modifier.width(42.dp))
            Column(Modifier.weight(1f)) {
                Text(r.contractor, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = c.ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        append(r.location.ifBlank { "Location not recorded" })
                        if (r.notes.isNotBlank()) append(" · ").append(r.notes)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (r.location.isBlank()) c.warn else c.muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (submittedEarly) {
                    Text("Dated after it was submitted (${r.timestamp!!.toLocalDate().shortLabel()})",
                        style = MaterialTheme.typography.labelMedium, color = c.warn)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.background(c.accentSoft, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text("${r.manpower}", style = MaterialTheme.typography.titleMedium, color = c.ink)
                    Text(" workers", style = TextStyleMono, color = c.muted)
                }
                if (r.photo != null) {
                    Text(
                        if (showPhoto) "Hide photo" else "Photo",
                        style = MaterialTheme.typography.labelMedium, color = c.muted,
                        modifier = Modifier.clickable { showPhoto = !showPhoto }.testTag("photo_toggle"),
                    )
                }
            }
        }
        if (r.syncState != SyncState.SYNCED) SyncStateChip(r.syncState)
        val photo = r.photo
        AnimatedVisibility(showPhoto && photo != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SubcomposeAsyncImage(
                    model = photo!!.toImageModel(),
                    contentDescription = "TBT photo for ${r.contractor}",
                    contentScale = ContentScale.Crop,
                    loading = { Text("Loading photo…", Modifier.padding(12.dp), color = c.muted) },
                    error = { Text("Photo unavailable offline", Modifier.padding(12.dp), color = c.muted) },
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(8.dp)),
                )
                if (photo is PhotoRef.Drive) {
                    Text("Open in Drive ↗", style = MaterialTheme.typography.labelMedium, color = c.muted,
                        modifier = Modifier.clickable { uri.openUri(DriveLinkResolver.viewUrl(photo.fileId)) })
                }
            }
        }
    }
}
