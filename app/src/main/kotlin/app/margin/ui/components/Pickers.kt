@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.margin.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.margin.R
import app.margin.domain.model.Folder
import app.margin.domain.organization.Folders
import app.margin.domain.organization.Tags

/** Pick a destination folder (or the Inbox). Shows the hierarchy with indentation. */
@Composable
fun FolderPickerDialog(folders: List<Folder>, title: String, onPick: (String?) -> Unit, onDismiss: () -> Unit, excludeId: String? = null) {
    val flat = remember(folders, excludeId) {
        val excluded = excludeId?.let { setOf(it) + Folders.descendants(it, folders) } ?: emptySet()
        Folders.flatten(folders).filter { it.first.id !in excluded }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { onPick(null) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Inbox, null)
                        Text(stringResource(R.string.inbox), Modifier.padding(start = 12.dp))
                    }
                }
                items(flat, key = { it.first.id }) { (f, depth) ->
                    Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { onPick(f.id) }.padding(start = (depth * 20).dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Folder, null)
                        Text(f.name, Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Tag editor with autocomplete. Case/whitespace variants of existing tags resolve to the existing
 * tag (no duplicates); commas or Enter commit a tag.
 */
@Composable
fun TagEditorDialog(initial: List<String>, allTags: List<String>, onSave: (List<String>) -> Unit, onDismiss: () -> Unit, title: String = stringResource(R.string.tags)) {
    val chosen = remember { mutableStateListOf<String>().apply { addAll(initial) } }
    var input by rememberSaveable { mutableStateOf("") }
    val invalid = input.isNotBlank() && Tags.validate(input) != null
    fun commit() {
        Tags.parseList(input).forEach { name ->
            val existing = allTags.firstOrNull { Tags.key(it) == Tags.key(name) } ?: name
            if (chosen.none { Tags.key(it) == Tags.key(existing) }) chosen += existing
        }
        input = ""
    }
    val suggestions = remember(input, chosen.size, allTags) { Tags.suggest(input, allTags, chosen) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chosen.forEach { t ->
                        InputChip(selected = true, onClick = { chosen.remove(t) }, label = { Text(t) },
                            trailingIcon = { Icon(Icons.Outlined.Close, stringResource(R.string.remove_tag, t), Modifier.size(16.dp)) })
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { v -> if (v.endsWith(",")) { input = v.dropLast(1); commit() } else input = v },
                    label = { Text(stringResource(R.string.add_tag)) },
                    isError = invalid,
                    supportingText = { if (invalid) Text(stringResource(R.string.tag_invalid)) else Text(stringResource(R.string.tag_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (suggestions.isNotEmpty()) {
                    Text(stringResource(R.string.suggestions), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        suggestions.forEach { s -> SuggestionChip(onClick = { chosen += s; input = "" }, label = { Text(s) }) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { commit(); onSave(chosen.toList()) }, enabled = !invalid) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Single text input dialog (rename, new folder, save search…). */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String = "",
    confirmLabel: String = stringResource(R.string.save),
    validate: (String) -> String? = { null },
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    val error = validate(value)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value, { value = it }, label = { Text(label) }, isError = error != null && value.isNotEmpty(),
                supportingText = { if (error != null && value.isNotEmpty()) Text(error) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.trim()); onDismiss() }, enabled = error == null && value.isNotBlank()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
