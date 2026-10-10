package app.margin.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.margin.R
import app.margin.domain.model.PageStyle
import app.margin.feature.drawing.PaperAccent
import app.margin.feature.drawing.PaperDot
import app.margin.feature.drawing.PaperLine

/**
 * Paper behind the note text. Lines follow the text's line height ([line] px), so writing sits on
 * the rules; styles that only make sense on a drawing page (music, storyboard) fall back to ruled.
 */
fun DrawScope.drawEditorPaper(style: PageStyle, line: Float, width: Float, height: Float) {
    if (style == PageStyle.PLAIN || line <= 4f) return
    val stroke = 1.dp.toPx()
    when (style) {
        PageStyle.GRID, PageStyle.GRID_SMALL -> {
            val step = if (style == PageStyle.GRID_SMALL) line / 2f else line
            var x = 0f; while (x <= width) { drawLine(PaperLine.copy(alpha = 0.7f), Offset(x, 0f), Offset(x, height), stroke); x += step }
            var y = 0f; while (y <= height) { drawLine(PaperLine.copy(alpha = 0.7f), Offset(0f, y), Offset(width, y), stroke); y += step }
        }
        PageStyle.DOTTED -> {
            var y = line; while (y <= height) { var x = line / 2f; while (x <= width) { drawCircle(PaperDot, 1.6f * stroke, Offset(x, y)); x += line }; y += line }
        }
        else -> {
            var y = line
            while (y <= height + 0.5f) { drawLine(PaperLine, Offset(0f, y - stroke), Offset(width, y - stroke), stroke); y += line }
            if (style == PageStyle.CORNELL) drawLine(PaperAccent, Offset(width * PageStyle.CORNELL_CUE_FRACTION, 0f), Offset(width * PageStyle.CORNELL_CUE_FRACTION, height), stroke)
        }
    }
}

/** Export choices: each format can be saved to the tablet (Files) or shared to another app. */
@Composable
fun ExportDialog(onDismiss: () -> Unit, onPick: (NoteExport.Format, save: Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_note)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NoteExport.Format.entries.forEach { f ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                            Text(stringResource(exportLabel(f)), style = MaterialTheme.typography.titleSmall)
                            Text("." + f.ext, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onPick(f, true) }) { Icon(Icons.Outlined.Download, stringResource(R.string.save_to_device)) }
                        IconButton(onClick = { onPick(f, false) }) { Icon(Icons.Outlined.Share, stringResource(R.string.share)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

private fun exportLabel(f: NoteExport.Format) = when (f) {
    NoteExport.Format.WORD -> R.string.format_word
    NoteExport.Format.PDF -> R.string.format_pdf
    NoteExport.Format.MARKDOWN -> R.string.format_markdown
    NoteExport.Format.HTML -> R.string.format_html
    NoteExport.Format.TEXT -> R.string.format_text
}
