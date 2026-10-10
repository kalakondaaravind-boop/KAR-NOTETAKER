@file:OptIn(ExperimentalMaterial3Api::class)

package app.margin.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.margin.R
import app.margin.domain.text.NoteFont
import app.margin.domain.text.Symbols
import app.margin.feature.drawing.ClipartLibrary
import app.margin.ui.theme.NoteFonts

/** Symbols (arrows, maths, Greek, currency, shapes, ticks, faces, ...) grouped by category, with search. */
@Composable
fun SymbolPickerSheet(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf(Symbols.categories.first().id) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(stringResource(R.string.insert_symbol), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            placeholder = { Text(stringResource(R.string.symbol_search_hint)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
        if (query.isBlank()) LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Symbols.categories, key = { it.id }) { c -> FilterChip(selected = c.id == cat, onClick = { cat = c.id }, label = { Text(c.name) }) }
        }
        val list = if (query.isBlank()) Symbols.categories.first { it.id == cat }.symbols else Symbols.search(query)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(56.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.height(380.dp),
        ) {
            items(list) { s ->
                Box(
                    Modifier.size(56.dp).clickable(role = Role.Button) { onPick(s) }.semantics { contentDescription = s },
                    contentAlignment = Alignment.Center,
                ) { Text(s, fontSize = 26.sp) }
            }
        }
    }
}

/** Clipart: simple vector pictures that stay sharp at any size and take the chosen colour. */
@Composable
fun ClipartPickerSheet(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(stringResource(R.string.insert_clipart), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            placeholder = { Text(stringResource(R.string.clipart_search_hint)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
        val q = query.trim().lowercase()
        LazyVerticalGrid(columns = GridCells.Adaptive(76.dp), contentPadding = PaddingValues(16.dp), modifier = Modifier.height(460.dp)) {
            for (c in ClipartLibrary.categories) {
                val matches = if (q.isEmpty()) c.items else c.items.filter { q in it.label.lowercase() || q in it.key || q in c.name.lowercase() }
                if (matches.isEmpty()) continue
                item(span = { GridItemSpan(maxLineSpan) }, key = "h-" + c.name) {
                    Text(c.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                }
                items(matches, key = { c.name + it.key }) { ci ->
                    Column(
                        Modifier.clickable(role = Role.Button) { onPick(ci.key) }.padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Icon(ci.icon, null, Modifier.padding(10.dp).size(36.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Text(ci.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * Font menu where every font name is drawn in its own typeface, so the list itself shows what you
 * get (no separate preview needed). [current] is shown with a tick.
 */
@Composable
fun FontMenu(expanded: Boolean, current: NoteFont?, onDismiss: () -> Unit, onPick: (NoteFont?) -> Unit, includeDefault: Boolean = true) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (includeDefault) DropdownMenuItem(
            text = { Text(stringResource(R.string.font_default), fontWeight = if (current == null) FontWeight.Bold else null) },
            onClick = { onPick(null) },
        )
        NoteFont.entries.forEach { f ->
            DropdownMenuItem(
                text = { Text(f.displayName, fontFamily = NoteFonts.family(f), fontSize = 18.sp, fontWeight = if (f == current) FontWeight.Bold else null) },
                onClick = { onPick(f) },
            )
        }
    }
}

/** Labels that look like the formatting they apply: **B** is bold, *I* is italic, U is underlined, S is struck. */
@Composable
fun StyleGlyph(kind: String) {
    when (kind) {
        "bold" -> Text("B", fontWeight = FontWeight.Black, fontSize = 18.sp)
        "italic" -> Text("I", fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium, fontSize = 18.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
        "underline" -> Text("U", textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline, fontSize = 18.sp)
        "strike" -> Text("S", textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough, fontSize = 18.sp)
        else -> Text(kind)
    }
}
