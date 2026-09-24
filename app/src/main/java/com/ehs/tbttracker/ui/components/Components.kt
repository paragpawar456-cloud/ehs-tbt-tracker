package com.ehs.tbttracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ehs.tbttracker.data.photo.DrivePhoto
import com.ehs.tbttracker.domain.model.PhotoRef
import com.ehs.tbttracker.domain.model.SyncState
import com.ehs.tbttracker.ui.theme.Ehs
import java.io.File

/** Uppercase section label ("TOOLBOX TALKS TODAY"). */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, style = MaterialTheme.typography.labelSmall, color = Ehs.colors.muted)
}

/** White panel with a hairline border; the one container style used on the dashboard. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Ehs.colors
    Column(
        modifier.fillMaxWidth()
            .background(c.surface, RoundedCornerShape(12.dp))
            .border(1.dp, c.line, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun PanelHeader(title: String, sub: String?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Ehs.colors.ink, modifier = Modifier.weight(1f))
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = Ehs.colors.muted)
    }
}

@Composable
fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.background(container, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icon?.let { Icon(it, null, tint = content, modifier = Modifier.size(14.dp)) }
        Text(text, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
    }
}

@Composable
fun SyncStateChip(state: SyncState, modifier: Modifier = Modifier) {
    val c = Ehs.colors
    when (state) {
        SyncState.SYNCED -> Pill("Synced", c.accentSoft, c.ink, modifier.testTag("chip_synced"), Icons.Filled.CloudDone)
        SyncState.PENDING -> Pill("Offline - Sync Pending", c.warnSoft, c.ink, modifier.testTag("chip_pending"), Icons.Filled.CloudOff)
        SyncState.FAILED -> Pill("Sync failed", c.critSoft, c.crit, modifier.testTag("chip_failed"), Icons.Filled.ErrorOutline)
    }
}

@Composable
fun OfflineBanner(pending: Int, modifier: Modifier = Modifier) {
    val c = Ehs.colors
    Row(
        modifier.fillMaxWidth().background(c.warnSoft, RoundedCornerShape(10.dp))
            .border(1.dp, c.line, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp).testTag("offline_banner"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.CloudOff, null, tint = c.warn, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            if (pending > 0) "Offline - $pending record(s) will sync when network returns" else "Offline - showing saved data",
            style = MaterialTheme.typography.bodySmall,
            color = c.ink,
        )
    }
}

/** Maps a domain photo reference to a Coil model. */
fun PhotoRef.toImageModel(widthPx: Int = 800): Any = when (this) {
    is PhotoRef.Local -> File(path)
    is PhotoRef.Drive -> DrivePhoto(fileId, widthPx)
    is PhotoRef.Url -> url
}
