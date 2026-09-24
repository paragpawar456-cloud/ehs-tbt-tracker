package com.ehs.tbttracker.ui.portal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.ehs.tbttracker.domain.model.PhotoRef
import com.ehs.tbttracker.domain.model.TbtRecord
import com.ehs.tbttracker.domain.parsing.DriveLinkResolver
import com.ehs.tbttracker.ui.components.toImageModel
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Palette of the "TBT Safety Compliance Portal" design (light) with matching dark values. */
@Immutable
data class PortalPalette(
    val bg: Color, val surface: Color, val line: Color, val ink: Color, val muted: Color,
    val indigo: Color, val indigoSoft: Color,
    val green: Color, val greenSoft: Color, val greenPanel: Color,
    val red: Color, val redSoft: Color, val redPanel: Color,
    val amber: Color, val amberSoft: Color,
    val dark: Color, val onDark: Color, val track: Color,
)

private val LightPortal = PortalPalette(
    bg = Color(0xFFF8FAFC), surface = Color.White, line = Color(0xFFE5E7EB), ink = Color(0xFF0F172A), muted = Color(0xFF64748B),
    indigo = Color(0xFF4F46E5), indigoSoft = Color(0xFFEEF2FF),
    green = Color(0xFF15803D), greenSoft = Color(0xFFDCFCE7), greenPanel = Color(0xFFF0FDF4),
    red = Color(0xFFDC2626), redSoft = Color(0xFFFEE2E2), redPanel = Color(0xFFFEF2F2),
    amber = Color(0xFFD97706), amberSoft = Color(0xFFFEF3C7),
    dark = Color(0xFF111827), onDark = Color.White, track = Color(0xFFE5E7EB),
)

private val DarkPortal = PortalPalette(
    bg = Color(0xFF0B1220), surface = Color(0xFF111A2E), line = Color(0xFF243049), ink = Color(0xFFE5E7EB), muted = Color(0xFF94A3B8),
    indigo = Color(0xFF818CF8), indigoSoft = Color(0xFF1E1B4B),
    green = Color(0xFF4ADE80), greenSoft = Color(0xFF14391F), greenPanel = Color(0xFF0F2418),
    red = Color(0xFFF87171), redSoft = Color(0xFF3F1515), redPanel = Color(0xFF2A1214),
    amber = Color(0xFFFBBF24), amberSoft = Color(0xFF3A2A0A),
    dark = Color(0xFFE5E7EB), onDark = Color(0xFF111827), track = Color(0xFF243049),
)

object Portal {
    val colors: PortalPalette @Composable get() = if (isSystemInDarkTheme()) DarkPortal else LightPortal
    val monthFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    val monthShort: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)
    val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
}

fun YearMonth.label(): String = format(Portal.monthFmt)

@Composable
fun PortalCard(
    modifier: Modifier = Modifier,
    background: Color = Portal.colors.surface,
    border: Color = Portal.colors.line,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth()
            .background(background, RoundedCornerShape(14.dp))
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun Badge(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier, bold: Boolean = true, mono: Boolean = false) {
    Text(
        text,
        modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 3.dp),
        color = fg,
        fontSize = 12.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        fontFamily = if (mono) androidx.compose.ui.text.font.FontFamily.Monospace else null,
        maxLines = 1,
    )
}

@Composable
fun DoneBadge(done: Int) {
    val c = Portal.colors
    if (done > 0) Badge("✓ $done Done", c.greenSoft, c.green) else Badge("0 Done", c.redSoft, c.red)
}

@Composable
fun SectionLabel(text: String, icon: ImageVector? = null, color: Color = Portal.colors.ink) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text.uppercase(), color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.6.sp)
    }
}

/** KPI card: uppercase title, tinted icon box, big value + unit, footer line. */
@Composable
fun KpiCard(
    title: String,
    icon: ImageVector,
    tint: Color,
    tintBg: Color,
    value: String,
    unit: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
) {
    val c = Portal.colors
    PortalCard(modifier, padding = 14.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Text(title.uppercase(), color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
                modifier = Modifier.weight(1f), maxLines = 2)
            Box(Modifier.size(32.dp).background(tintBg, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = valueColor, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 34.sp)
            Spacer(Modifier.width(6.dp))
            Text(unit, color = if (valueColor == c.ink) c.muted else valueColor, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        footer()
    }
}

/** Compact dropdown ("Month: September 2026 ▾"). */
@Composable
fun <T> Picker(label: String, selected: T?, options: List<T>, display: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier, tag: String = "") {
    val c = Portal.colors
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, color = c.muted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Box {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.surface)
                    .border(1.dp, c.line, RoundedCornerShape(10.dp)).clickable { open = true }
                    .padding(start = 12.dp, end = 4.dp, top = 9.dp, bottom = 9.dp).testTag(tag),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(selected?.let(display) ?: "Select", color = c.ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Filled.ArrowDropDown, null, tint = c.ink)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 420.dp)) {
                options.forEach { o -> DropdownMenuItem(text = { Text(display(o)) }, onClick = { onSelect(o); open = false }) }
            }
        }
    }
}

@Composable
fun SmallOutlined(text: String, icon: ImageVector?, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 36.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Portal.colors.line),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    ) {
        if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, color = color, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
fun PhotoDialog(record: TbtRecord, onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    val c = Portal.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${record.contractor} · ${record.date ?: ""}", fontSize = 16.sp) },
        text = {
            val photo = record.photo
            if (photo == null) Text("No photo attached to this TBT.")
            else SubcomposeAsyncImage(
                model = photo.toImageModel(1200),
                contentDescription = "TBT photo",
                contentScale = ContentScale.Fit,
                loading = { Text("Loading photo…", color = c.muted) },
                error = { Text("Photo could not be loaded. Open it in Drive instead.", color = c.muted) },
                modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(10.dp)),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            val p = record.photo
            if (p is PhotoRef.Drive) {
                TextButton(onClick = { uri.openUri(DriveLinkResolver.viewUrl(p.fileId)) }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Open in Drive")
                }
            }
        },
    )
}

@Composable
fun DetailsDialog(records: List<TbtRecord>, onDismiss: () -> Unit) {
    val c = Portal.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TBT details", fontSize = 18.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close") }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                records.forEach { r ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Detail("Contractor", r.contractor + if (r.contractorRaw.trim() != r.contractor) "  (entered as \"${r.contractorRaw.trim()}\")" else "")
                        Detail("TBT date", r.date?.let { "$it (${it.format(Portal.dayFmt)})" } ?: "—")
                        Detail("Submitted", r.timestamp?.toString()?.replace('T', ' ') ?: "—")
                        Detail("Manpower", "${r.manpower} workers")
                        Detail("Location", r.location.ifBlank { "Not recorded" })
                        if (r.notes.isNotBlank()) Detail("Status / notes", r.notes)
                        Detail("Photo", if (r.photo != null) "Attached" else "None")
                        r.rowNumber?.let { Detail("Sheet row", it.toString()) }
                        Detail("Sync", r.syncState.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                    if (records.size > 1) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun Detail(label: String, value: String) {
    Row {
        Text(label, color = Portal.colors.muted, fontSize = 13.sp, modifier = Modifier.width(104.dp))
        Text(value, color = Portal.colors.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
