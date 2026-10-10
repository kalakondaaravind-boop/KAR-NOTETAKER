@file:OptIn(ExperimentalMaterial3Api::class)

package app.margin.feature.drawing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.margin.R
import app.margin.domain.model.PageStyle
import app.margin.domain.model.PageTemplate
import app.margin.domain.model.PageTemplates

/** A miniature sheet of paper showing [style]'s lines, scaled from a full A-size page. */
@Composable
fun PagePreview(style: PageStyle, modifier: Modifier = Modifier, selected: Boolean = false, drawingHint: Boolean = false) {
    val shape = RoundedCornerShape(6.dp)
    Canvas(
        modifier
            .aspectRatio(1200f / 1700f)
            .shadow(3.dp, shape)
            .clip(shape)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color(0x22000000), shape)
    ) {
        drawRect(Color.White)
        // Draw as if the page were 1200 units wide, so spacing looks like the real thing.
        val s = size.width / 1200f
        scale(s, s, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPaper(style, 1200f, 1700f, lineWidth = 1f / s.coerceAtLeast(0.01f))
            if (drawingHint) {
                val p = strokePath(listOf(
                    app.margin.domain.drawing.Pt(260f, 1200f), app.margin.domain.drawing.Pt(420f, 980f), app.margin.domain.drawing.Pt(600f, 1150f),
                    app.margin.domain.drawing.Pt(780f, 900f), app.margin.domain.drawing.Pt(940f, 1080f),
                ))
                drawPath(p, Color(0xFF1E88E5), style = androidx.compose.ui.graphics.drawscope.Stroke(18f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
        }
    }
}

/** Page templates for new pages (ruled, to-do, Cornell, planners, sketch pages, ...). */
@Composable
fun PageTemplateSheet(onDismiss: () -> Unit, onPick: (PageTemplate) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(stringResource(R.string.page_templates), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
        Text(stringResource(R.string.page_templates_sub), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(132.dp),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(PageTemplates.all, key = { it.id }) { t ->
                Column(Modifier.clickable(role = Role.Button) { onPick(t) }.padding(4.dp)) {
                    PagePreview(t.page, Modifier.fillMaxWidth(), drawingHint = t.drawing)
                    Text(t.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Paper style chooser for an existing note or drawing. */
@Composable
fun PageStyleSheet(current: PageStyle, onDismiss: () -> Unit, onPick: (PageStyle) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(stringResource(R.string.page_style), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(PageStyle.entries.toList(), key = { it.id }) { st ->
                Column(Modifier.clickable(role = Role.Button) { onPick(st) }.padding(4.dp)) {
                    PagePreview(st, Modifier.width(96.dp), selected = st == current)
                    Text(st.label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, modifier = Modifier.width(96.dp).padding(top = 6.dp))
                }
            }
        }
    }
}
