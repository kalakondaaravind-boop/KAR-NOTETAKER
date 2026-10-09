@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.margin.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Attachment
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.margin.R
import app.margin.domain.editor.SaveError
import app.margin.domain.editor.SaveStatus
import app.margin.domain.model.NoteSummary
import app.margin.domain.search.Highlight
import app.margin.ui.theme.LocalSemanticColors
import app.margin.ui.theme.LocalSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

/** Every empty state explains what the user can do next. */
@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, actionLabel: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val sp = LocalSpacing.current
    Column(
        modifier.fillMaxSize().padding(sp.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(sp.l))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.size(sp.s))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp))
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.size(sp.l))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.loading)
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.semantics { contentDescription = label })
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val sp = LocalSpacing.current
    Row(modifier.fillMaxWidth().padding(start = sp.l, end = sp.s, top = sp.l, bottom = sp.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.invoke()
    }
}

/** Localised relative time ("Just now", "5 min ago", "14:05", "Tue", "3 Mar 2025"). */
@Composable
fun relativeTime(i: Instant, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    val res = LocalContext.current.resources
    val mins = ChronoUnit.MINUTES.between(i, now)
    val local = i.atZone(zone)
    return when {
        mins < 1 -> res.getString(R.string.time_just_now)
        mins < 60 -> res.getQuantityString(R.plurals.time_minutes_ago, mins.toInt(), mins.toInt())
        local.toLocalDate() == now.atZone(zone).toLocalDate() -> local.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        ChronoUnit.DAYS.between(local.toLocalDate(), now.atZone(zone).toLocalDate()) < 7 -> local.format(DateTimeFormatter.ofPattern("EEE"))
        else -> local.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}

/** A single note in a list. Long-press selects (multi-select), tap opens. */
@Composable
fun NoteRow(
    note: NoteSummary,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleHighlight: Highlight? = null,
    snippet: Highlight? = null,
    folderName: String? = null,
) {
    val sp = LocalSpacing.current
    val untitled = stringResource(R.string.untitled)
    val pinnedLabel = stringResource(R.string.pinned)
    val favoriteLabel = stringResource(R.string.favorite)
    val selectedLabel = stringResource(R.string.selected)
    val notSelectedLabel = stringResource(R.string.not_selected)
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, role = Role.Button,
                onLongClickLabel = stringResource(R.string.select))
            .semantics {
                if (selectionMode) stateDescription = if (selected) selectedLabel else notSelectedLabel
            },
    ) {
        Row(Modifier.padding(horizontal = sp.l, vertical = sp.m).heightIn(min = 48.dp), verticalAlignment = Alignment.Top) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onClick() }, modifier = Modifier.padding(end = sp.s))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val title = note.title.ifBlank { untitled }
                    Text(
                        text = titleHighlight?.let { highlighted(it) } ?: AnnotatedString(title),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (note.pinned) Icon(Icons.Filled.PushPin, pinnedLabel, Modifier.padding(start = sp.xs).size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                    if (note.favorite) Icon(Icons.Filled.Star, favoriteLabel, Modifier.padding(start = sp.xs).size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                    if (note.hasAttachments) Icon(Icons.Outlined.Attachment, stringResource(R.string.has_attachments), Modifier.padding(start = sp.xs).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val body = snippet?.let { highlighted(it) } ?: AnnotatedString(note.preview)
                if (body.isNotBlank()) {
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = sp.xxs))
                }
                Row(Modifier.padding(top = sp.xs), horizontalArrangement = Arrangement.spacedBy(sp.s), verticalAlignment = Alignment.CenterVertically) {
                    Text(relativeTime(note.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    folderName?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                    note.tagNames.take(3).forEach { TagLabel(it) }
                    if (note.tagNames.size > 3) Text("+${note.tagNames.size - 3}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun TagLabel(name: String) {
    Text("#$name", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
}

@Composable
fun highlighted(h: Highlight): AnnotatedString {
    val c = LocalSemanticColors.current
    return buildAnnotatedString {
        append(h.text)
        h.ranges.forEach { r ->
            if (r.first >= 0 && r.last < h.text.length) addStyle(SpanStyle(background = c.highlight, color = c.onHighlight, fontWeight = FontWeight.SemiBold), r.first, r.last + 1)
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Honest save indicator: "Saved" appears only after the database write succeeded. */
@Composable
fun SaveStatusIndicator(status: SaveStatus, modifier: Modifier = Modifier) {
    val (icon, text) = when (status) {
        SaveStatus.Idle -> return
        SaveStatus.Pending -> Icons.Outlined.Sync to stringResource(R.string.save_pending)
        SaveStatus.Saving -> Icons.Outlined.Sync to stringResource(R.string.save_saving)
        is SaveStatus.Saved -> Icons.Outlined.CheckCircle to stringResource(R.string.save_saved)
        is SaveStatus.Failed -> Icons.Outlined.ErrorOutline to when (status.reason) {
            SaveError.STORAGE_FULL -> stringResource(R.string.save_failed_storage)
            SaveError.NOTE_MISSING -> stringResource(R.string.save_failed_missing)
            else -> if (status.willRetry) stringResource(R.string.save_retrying) else stringResource(R.string.save_failed)
        }
    }
    val color = if (status is SaveStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = color)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
fun OfflineBadge(modifier: Modifier = Modifier) {
    AssistChip(onClick = {}, label = { Text(stringResource(R.string.on_device_only)) }, modifier = modifier,
        leadingIcon = { Icon(Icons.Outlined.CloudOff, null, Modifier.size(AssistChipDefaults.IconSize)) })
}
