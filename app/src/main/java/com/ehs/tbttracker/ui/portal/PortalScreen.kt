package com.ehs.tbttracker.ui.portal

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ehs.tbttracker.R
import com.ehs.tbttracker.domain.model.TbtRecord
import java.time.LocalDate

/** Callbacks the portal needs from the app shell. */
data class PortalNav(
    val onRecordNew: () -> Unit = {},
    val onLogTbt: (date: LocalDate, contractor: String) -> Unit = { _, _ -> },
)

@Composable
fun PortalRoute(snackbar: SnackbarHostState, nav: PortalNav, viewModel: PortalViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                is PortalEvent.Share -> runCatching { context.startActivity(e.intent) }
                    .onFailure { snackbar.showSnackbar("No app available to open the file") }
                is PortalEvent.Message -> snackbar.showSnackbar(e.text)
            }
        }
    }
    PortalScreen(state, viewModel, nav)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortalScreen(state: PortalUiState, vm: PortalViewModel, nav: PortalNav) {
    val c = Portal.colors
    var photoOf by remember { mutableStateOf<TbtRecord?>(null) }
    var detailsOf by remember { mutableStateOf<List<TbtRecord>?>(null) }
    val actions = PortalActions(
        vm = vm,
        nav = nav,
        onPhoto = { photoOf = it },
        onDetails = { detailsOf = it },
    )
    val listState = rememberLazyListState()

    Column(Modifier.fillMaxSize().background(c.bg).navigationBarsPadding()) {
        PortalHeader(state, onRecordNew = nav.onRecordNew, onRetrySync = vm::refresh)
        PortalTabBar(state, vm::selectTab)
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("portal_list"),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (state.selection.tab) {
                    PortalTab.CONTRACTOR -> contractorWiseTab(state, actions)
                    PortalTab.DATE -> dateWiseTab(state, actions)
                    PortalTab.MISSING -> missingAuditTab(state, actions)
                    PortalTab.GRID -> monthlyGridTab(state, actions)
                    PortalTab.ANALYTICS -> item(key = "analytics") { AnalyticsTab() }
                    PortalTab.MASTERS -> mastersTab(state, actions)
                    PortalTab.ALL -> allRecordsTab(state, actions)
                }
            }
        }
    }
    LaunchedEffect(state.selection.tab) { listState.scrollToItem(0) }
    photoOf?.let { PhotoDialog(it) { photoOf = null } }
    detailsOf?.let { DetailsDialog(it) { detailsOf = null } }
}

class PortalActions(
    val vm: PortalViewModel,
    val nav: PortalNav,
    val onPhoto: (TbtRecord) -> Unit,
    val onDetails: (List<TbtRecord>) -> Unit,
)

// ------------------------------------------------------------------ header

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PortalHeader(state: PortalUiState, onRecordNew: () -> Unit, onRetrySync: () -> Unit) {
    val c = Portal.colors
    BoxWithConstraints(Modifier.fillMaxWidth().background(c.surface).statusBarsPadding()) {
        val wide = maxWidth > 640.dp
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(R.drawable.ic_tbt_logo),
                    contentDescription = "TBT logo",
                    modifier = Modifier.size(52.dp).background(Color.White, CircleShape).testTag("portal_logo"),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("TBT Safety Compliance Portal", color = c.ink, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 22.sp)
                    Text("Live EHS Conduction, Date-Wise Details, Manpower Attendance & Missing TBT Audit",
                        color = c.muted, fontSize = 12.sp, lineHeight = 16.sp)
                }
                if (wide) {
                    Spacer(Modifier.width(12.dp))
                    RecordButton(onRecordNew, Modifier)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SyncPill(state, onRetrySync)
                Text(
                    "${state.registeredContractors} Master Contractors Registered",
                    Modifier.background(c.bg, RoundedCornerShape(50)).border(1.dp, c.line, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    color = c.ink, fontSize = 12.sp,
                )
            }
            if (!wide) RecordButton(onRecordNew, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun RecordButton(onClick: () -> Unit, modifier: Modifier) {
    val c = Portal.colors
    Button(
        onClick = onClick,
        modifier = modifier.testTag("record_new_tbt"),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = c.green, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Record New TBT", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SyncPill(state: PortalUiState, onRetry: () -> Unit) {
    val c = Portal.colors
    val (dot, bg, fg, text) = when {
        state.refreshing || state.loading -> Quad(c.amber, c.amberSoft, c.amber, "Syncing with Form Responses 1…")
        !state.online -> Quad(c.amber, c.amberSoft, c.amber, "Offline · ${state.totalLogs} Logs saved")
        state.syncError != null -> Quad(c.red, c.redSoft, c.red, "Sync failed · tap to retry")
        else -> Quad(c.green, c.greenPanel, c.green, "Synced with Form Responses 1 (${state.totalLogs} Logs Live)")
    }
    Row(
        Modifier.background(bg, RoundedCornerShape(50)).border(1.dp, fg.copy(alpha = 0.35f), RoundedCornerShape(50))
            .clickable(enabled = state.syncError != null, onClick = onRetry)
            .padding(horizontal = 10.dp, vertical = 5.dp).testTag("sync_pill"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(dot, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
    if (state.syncError != null && !state.refreshing) {
        Text(state.syncError, color = c.red, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
    }
}

private data class Quad(val a: Color, val b: Color, val c: Color, val d: String)

// ------------------------------------------------------------------ tabs

private fun PortalTab.icon(): ImageVector = when (this) {
    PortalTab.CONTRACTOR -> Icons.Filled.Engineering
    PortalTab.DATE -> Icons.Filled.EventAvailable
    PortalTab.MISSING -> Icons.Filled.PersonOff
    PortalTab.GRID -> Icons.Filled.CalendarMonth
    PortalTab.ANALYTICS -> Icons.Filled.BarChart
    PortalTab.MASTERS -> Icons.Filled.Layers
    PortalTab.ALL -> Icons.AutoMirrored.Filled.List
}

@Composable
private fun PortalTabBar(state: PortalUiState, onSelect: (PortalTab) -> Unit) {
    val c = Portal.colors
    LazyRow(
        Modifier.fillMaxWidth().background(c.surface).padding(bottom = 10.dp).testTag("portal_tabs"),
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(PortalTab.entries) { tab ->
            val selected = tab == state.selection.tab
            val badge: Pair<String, Pair<Color, Color>>? = when (tab) {
                PortalTab.CONTRACTOR -> state.contractor?.let { it to (Color.White.copy(alpha = 0.18f) to Color.White) }
                PortalTab.DATE -> "${state.distinctDates} Dates" to (c.greenSoft to c.green)
                PortalTab.MISSING -> "1st to End" to (c.redSoft to c.red)
                PortalTab.GRID -> state.month.format(Portal.monthShort) to (c.indigoSoft to c.indigo)
                PortalTab.MASTERS -> "${state.registeredContractors}" to (c.amberSoft to c.amber)
                else -> null
            }
            Row(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (selected) c.indigo else Color.Transparent)
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 12.dp, vertical = 9.dp)
                    .testTag("tab_${tab.name.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(tab.icon(), null, tint = if (selected) Color.White else when (tab) {
                    PortalTab.MISSING -> c.red; PortalTab.DATE -> c.green; PortalTab.MASTERS -> c.amber; else -> c.indigo
                }, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(tab.title, color = if (selected) Color.White else c.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (badge != null) {
                    Spacer(Modifier.width(6.dp))
                    val (bg, fg) = if (selected) (Color.White.copy(alpha = 0.18f) to Color.White) else badge.second
                    Badge(badge.first.take(22), bg, fg)
                }
            }
        }
    }
}
