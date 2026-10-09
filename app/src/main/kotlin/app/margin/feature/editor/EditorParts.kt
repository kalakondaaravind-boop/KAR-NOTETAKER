package app.margin.feature.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.margin.R
import app.margin.domain.editor.InlineStyle
import app.margin.domain.editor.LineStyle
import app.margin.domain.model.Attachment

/**
 * Formatting toolbar pinned above the keyboard. Every control works on the current selection or
 * the current line(s); nothing requires entering a "mode" before typing.
 */
@Composable
fun FormattingToolbar(
    vm: EditorViewModel,
    onLink: () -> Unit,
    onAttach: () -> Unit,
    attachMenu: Boolean,
    onDismissAttach: () -> Unit,
    onPickFile: () -> Unit,
    onCamera: () -> Unit,
    onDictate: () -> Unit,
    recording: Boolean,
    onRecord: () -> Unit,
) {
    var headingMenu by remember { mutableStateOf(false) }
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            @Composable fun B(icon: ImageVector, label: Int, onClick: () -> Unit) =
                IconButton(onClick = onClick) { Icon(icon, stringResource(label)) }
            B(Icons.Filled.FormatBold, R.string.fmt_bold) { vm.toggleInline(InlineStyle.BOLD) }
            B(Icons.Filled.FormatItalic, R.string.fmt_italic) { vm.toggleInline(InlineStyle.ITALIC) }
            B(Icons.Filled.FormatUnderlined, R.string.fmt_underline) { vm.toggleInline(InlineStyle.UNDERLINE) }
            B(Icons.Filled.FormatStrikethrough, R.string.fmt_strike) { vm.toggleInline(InlineStyle.STRIKE) }
            IconButton(onClick = { headingMenu = true }) { Icon(Icons.Filled.Title, stringResource(R.string.fmt_heading)) }
            DropdownMenu(expanded = headingMenu, onDismissRequest = { headingMenu = false }) {
                listOf(LineStyle.HEADING1 to R.string.fmt_h1, LineStyle.HEADING2 to R.string.fmt_h2, LineStyle.HEADING3 to R.string.fmt_h3, LineStyle.PLAIN to R.string.fmt_plain).forEach { (s, l) ->
                    DropdownMenuItem(text = { Text(stringResource(l)) }, onClick = { headingMenu = false; vm.setLineStyle(s) })
                }
            }
            B(Icons.Outlined.CheckBox, R.string.fmt_checklist) { vm.setLineStyle(LineStyle.CHECKLIST) }
            B(Icons.AutoMirrored.Filled.FormatListBulleted, R.string.fmt_bullets) { vm.setLineStyle(LineStyle.BULLET) }
            B(Icons.Filled.FormatListNumbered, R.string.fmt_numbered) { vm.setLineStyle(LineStyle.NUMBERED) }
            B(Icons.AutoMirrored.Filled.FormatIndentIncrease, R.string.fmt_indent) { vm.indent(false) }
            B(Icons.AutoMirrored.Filled.FormatIndentDecrease, R.string.fmt_outdent) { vm.indent(true) }
            B(Icons.Filled.FormatQuote, R.string.fmt_quote) { vm.setLineStyle(LineStyle.QUOTE) }
            B(Icons.Filled.Link, R.string.fmt_link, onLink)
            B(Icons.Filled.Code, R.string.fmt_inline_code) { vm.toggleInline(InlineStyle.CODE) }
            B(Icons.Filled.DataObject, R.string.fmt_code_block) { vm.insertCodeBlock() }
            B(Icons.Filled.TableChart, R.string.fmt_table) { vm.insertTable() }
            B(Icons.Filled.HorizontalRule, R.string.fmt_divider) { vm.insertDivider() }
            B(Icons.Outlined.Event, R.string.fmt_datetime) { vm.insertDateTime() }
            B(Icons.Outlined.RecordVoiceOver, R.string.dictate, onDictate)
            IconButton(onClick = onRecord) {
                Icon(if (recording) Icons.Outlined.StopCircle else Icons.Outlined.Mic, stringResource(if (recording) R.string.stop_recording else R.string.record_voice),
                    tint = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onAttach) { Icon(Icons.Outlined.AttachFile, stringResource(R.string.attach)) }
            DropdownMenu(expanded = attachMenu, onDismissRequest = onDismissAttach) {
                DropdownMenuItem(text = { Text(stringResource(R.string.attach_file)) }, leadingIcon = { Icon(Icons.Outlined.Description, null) }, onClick = { onDismissAttach(); onPickFile() })
                DropdownMenuItem(text = { Text(stringResource(R.string.take_photo)) }, leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null) }, onClick = { onDismissAttach(); onCamera() })
            }
        }
    }
}

@Composable
fun FindBar(f: FindState, vm: EditorViewModel) {
    Surface(tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(f.query, vm::setFindQuery, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.find)) },
                    supportingText = { Text(if (f.query.isEmpty()) "" else stringResource(R.string.find_count, if (f.matches.isEmpty()) 0 else f.current + 1, f.matches.size)) })
                IconButton(onClick = { vm.nextMatch(-1) }, enabled = f.matches.isNotEmpty()) { Icon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.previous_match)) }
                IconButton(onClick = { vm.nextMatch(1) }, enabled = f.matches.isNotEmpty()) { Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.next_match)) }
                IconButton(onClick = vm::closeFind) { Icon(Icons.Outlined.Close, stringResource(R.string.close)) }
            }
            if (f.showReplace) Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(f.replacement, vm::setReplacement, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.replace_with)) })
                TextButton(onClick = vm::replaceCurrent, enabled = f.matches.isNotEmpty()) { Text(stringResource(R.string.replace)) }
                TextButton(onClick = vm::replaceAll, enabled = f.matches.isNotEmpty()) { Text(stringResource(R.string.replace_all)) }
            } else TextButton(onClick = { vm.openFind(true) }) { Text(stringResource(R.string.show_replace)) }
        }
    }
}

@Composable
fun AttachmentStrip(items: List<Attachment>, readOnly: Boolean, onOpen: (Attachment) -> Unit, onRemove: (String) -> Unit, onInsertText: (String) -> Unit) {
    Column(Modifier.padding(top = 16.dp)) {
        HorizontalDivider()
        Text(stringResource(R.string.attachments), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
        items.forEach { a ->
            var confirm by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text(a.fileName, maxLines = 1) },
                supportingContent = {
                    Column {
                        Text(android.text.format.Formatter.formatShortFileSize(androidx.compose.ui.platform.LocalContext.current, a.sizeBytes) + " · " + a.mimeType)
                        a.extractedText?.takeIf { it.isNotBlank() }?.let { Text(stringResource(R.string.ocr_text_available), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                    }
                },
                leadingContent = { Icon(if (a.mimeType.startsWith("image/")) Icons.Outlined.Image else if (a.mimeType.startsWith("audio/")) Icons.Outlined.Mic else Icons.Outlined.Description, null) },
                trailingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        a.extractedText?.takeIf { it.isNotBlank() && !readOnly }?.let { text ->
                            IconButton(onClick = { onInsertText(text) }) { Icon(Icons.Outlined.TextSnippet, stringResource(R.string.insert_extracted_text)) }
                        }
                        if (!readOnly) IconButton(onClick = { confirm = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.remove_attachment), Modifier.size(20.dp)) }
                    }
                },
                modifier = Modifier.clickable(onClickLabel = stringResource(R.string.open)) { onOpen(a) },
            )
            if (confirm) app.margin.ui.components.ConfirmDialog(stringResource(R.string.remove_attachment), stringResource(R.string.remove_attachment_message, a.fileName),
                stringResource(R.string.remove), { onRemove(a.id) }, { confirm = false }, destructive = true)
        }
    }
}
