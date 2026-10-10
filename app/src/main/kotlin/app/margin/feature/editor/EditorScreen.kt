@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.margin.feature.editor

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.margin.R
import app.margin.domain.editor.InlineStyle
import app.margin.domain.model.NoteState
import app.margin.domain.text.MarkdownParser
import app.margin.feature.ai.AiActionSheet
import app.margin.platform.AudioRecorder
import app.margin.platform.FileAttachmentSource
import app.margin.platform.Files
import app.margin.platform.Ocr
import app.margin.platform.UriAttachmentSource
import app.margin.ui.components.ConfirmDialog
import app.margin.ui.components.EmptyState
import app.margin.ui.components.FolderPickerDialog
import app.margin.ui.components.LoadingState
import app.margin.ui.components.SaveStatusIndicator
import app.margin.ui.components.TagEditorDialog
import app.margin.ui.components.TextInputDialog
import app.margin.ui.navigation.Navigator
import app.margin.ui.navigation.Routes
import app.margin.ui.theme.LocalSemanticColors
import app.margin.ui.theme.LocalSpacing
import app.margin.ui.theme.editorTextStyle
import kotlinx.coroutines.launch

@Composable
fun EditorScreen(n: Navigator, vm: EditorViewModel = hiltViewModel()) {
    val meta by vm.meta.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val title by vm.title.collectAsStateWithLifecycle()
    val body by vm.body.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val status by vm.saveStatus.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val allTags by vm.allTagNames.collectAsStateWithLifecycle()
    val folders by vm.folderList.collectAsStateWithLifecycle()
    val attachments by vm.attachmentList.collectAsStateWithLifecycle()
    val backlinks by vm.backlinks.collectAsStateWithLifecycle()
    val find by vm.find.collectAsStateWithLifecycle()
    val canUndo by vm.canUndo.collectAsStateWithLifecycle()
    val canRedo by vm.canRedo.collectAsStateWithLifecycle()
    val page by vm.page.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sp = LocalSpacing.current
    val semantic = LocalSemanticColors.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var menu by remember { mutableStateOf(false) }
    var tagDialog by rememberSaveable { mutableStateOf(false) }
    var moveDialog by rememberSaveable { mutableStateOf(false) }
    var linkDialog by rememberSaveable { mutableStateOf(false) }
    var infoDialog by rememberSaveable { mutableStateOf(false) }
    var templateDialog by rememberSaveable { mutableStateOf(false) }
    var aiSheet by rememberSaveable { mutableStateOf(false) }
    var attachMenu by remember { mutableStateOf(false) }
    var openUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var focusMode by rememberSaveable { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    val recorder = remember { AudioRecorder(context) }
    var pendingCamera by rememberSaveable { mutableStateOf<String?>(null) }
    var pageSheet by rememberSaveable { mutableStateOf(false) }
    var exportMenu by remember { mutableStateOf(false) }
    var exportFormat by remember { mutableStateOf<NoteExport.Format?>(null) }

    val trashed = live?.state == NoteState.TRASHED
    val archived = live?.state == NoteState.ARCHIVED
    val readOnly = trashed

    // Persist when the app goes to the background (lifecycle), not only on navigation.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.flush() }

    fun leave() { vm.onLeave(); n.back() }
    BackHandler { if (find != null) vm.closeFind() else leave() }

    val msgAttachFailed = stringResource(R.string.attachment_failed)
    val msgOcrDone = stringResource(R.string.ocr_done)
    val msgOcrFailed = stringResource(R.string.ocr_failed)
    val msgNoSpeech = stringResource(R.string.speech_unavailable)
    val msgMicDenied = stringResource(R.string.mic_permission_denied)
    val msgNoCamera = stringResource(R.string.camera_unavailable)

    fun attach(src: app.margin.domain.repository.AttachmentSource, ocr: Uri? = null) = vm.addAttachment(src) { r ->
        r.onFailure { scope.launch { snackbar.showSnackbar(msgAttachFailed) } }
        r.onSuccess { a ->
            if (ocr != null && a.mimeType.startsWith("image/")) scope.launch {
                runCatching { Ocr.recognize(context, ocr) }
                    .onSuccess { text -> if (text.isNotBlank()) { vm.setExtractedText(a.id, text); snackbar.showSnackbar(msgOcrDone) } }
                    .onFailure { snackbar.showSnackbar(msgOcrFailed) }
            }
        }
    }

    var pendingExport by remember { mutableStateOf<ByteArray?>(null) }
    val saveDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val bytes = pendingExport
        pendingExport = null
        if (uri != null && bytes != null) scope.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Files.writeTo(context, uri, bytes) }
            snackbar.showSnackbar(context.getString(if (ok) R.string.export_saved else R.string.export_failed))
        }
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { attach(UriAttachmentSource(context.contentResolver, it), ocr = it) }
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingCamera
        pendingCamera = null
        if (ok && path != null) {
            val f = java.io.File(path)
            attach(FileAttachmentSource(f, "image/jpeg"), ocr = Uri.fromFile(f))
        }
    }
    // Dictation inserts at the cursor, followed by a space so the next dictation continues the sentence.
    val dictate = app.margin.platform.rememberDictation(
        prompt = stringResource(R.string.dictation_prompt),
        onText = { vm.insertText("$it ") },
        onUnavailable = { scope.launch { snackbar.showSnackbar(msgNoSpeech) } },
    )
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) recording = recorder.start() else scope.launch { snackbar.showSnackbar(msgMicDenied) }
    }

    if (meta.loading) { LoadingState(); return }
    if (meta.missing) {
        Scaffold(topBar = { TopAppBar(title = {}, navigationIcon = { IconButton(onClick = n::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }) }) { p ->
            EmptyState(Icons.Outlined.Delete, stringResource(R.string.note_missing_title), stringResource(R.string.note_missing_body), modifier = Modifier.padding(p))
        }
        return
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val baseStyle = editorTextStyle(MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface), settings.editorTextScale, settings.editorMonospace)
        .let { st -> page.font?.let { st.copy(fontFamily = app.margin.ui.theme.NoteFonts.family(it)) } ?: st }
    // On lined paper every text line sits on a ruled line: line height = the paper's spacing.
    val lineSpacingPx = with(density) { maxOf(page.page.spacingDp.dp.toPx(), baseStyle.fontSize.toPx() * 1.45f) }
    val bodyStyle = if (page.page == app.margin.domain.model.PageStyle.PLAIN) baseStyle else baseStyle.copy(
        lineHeight = with(density) { lineSpacingPx.toSp() },
        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
            androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.None),
    )
    val transformation = remember(semantic, settings.editorTextScale, settings.showFormattingMarks) {
        MarkdownVisualTransformation(semantic, androidx.compose.ui.graphics.Color.Gray, showMarks = settings.showFormattingMarks)
    }
    val bodyFocus = remember { FocusRequester() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (!focusMode) TopAppBar(
                navigationIcon = { IconButton(onClick = ::leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                title = { SaveStatusIndicator(status) },
                actions = {
                    if (!readOnly) {
                        IconButton(onClick = vm::undo, enabled = canUndo) { Icon(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.undo)) }
                        IconButton(onClick = vm::redo, enabled = canRedo) { Icon(Icons.AutoMirrored.Outlined.Redo, stringResource(R.string.redo)) }
                        IconButton(onClick = { vm.setPreview(!preview) }) {
                            Icon(if (preview) Icons.Outlined.Edit else Icons.Outlined.Visibility, stringResource(if (preview) R.string.edit_mode else R.string.preview_mode))
                        }
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        val note = live
                        if (!readOnly) {
                            DropdownMenuItem(text = { Text(stringResource(if (note?.pinned == true) R.string.unpin else R.string.pin)) },
                                leadingIcon = { Icon(Icons.Outlined.PushPin, null) }, onClick = { menu = false; vm.togglePinned() })
                            DropdownMenuItem(text = { Text(stringResource(if (note?.favorite == true) R.string.unfavorite else R.string.favorite)) },
                                leadingIcon = { Icon(Icons.Outlined.StarOutline, null) }, onClick = { menu = false; vm.toggleFavorite() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.tags)) }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Label, null) }, onClick = { menu = false; tagDialog = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.move)) }, leadingIcon = { Icon(Icons.Outlined.DriveFileMove, null) }, onClick = { menu = false; moveDialog = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.find_replace)) }, leadingIcon = { Icon(Icons.Outlined.FindReplace, null) }, onClick = { menu = false; vm.setPreview(false); vm.openFind(true) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.ai_assist)) }, leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null) }, onClick = { menu = false; aiSheet = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.focus_mode)) }, leadingIcon = { Icon(Icons.Outlined.VisibilityOff, null) }, onClick = { menu = false; focusMode = true; vm.setPreview(false) })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.history)) }, leadingIcon = { Icon(Icons.Outlined.History, null) }, onClick = { menu = false; vm.flush { n.open(Routes.revisions(vm.noteId)) } })
                        DropdownMenuItem(text = { Text(stringResource(R.string.note_info)) }, leadingIcon = { Icon(Icons.Outlined.Info, null) }, onClick = { menu = false; infoDialog = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.share_export)) }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = {
                            menu = false; exportMenu = true
                        })
                        if (!readOnly) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.duplicate)) }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, onClick = { menu = false; vm.duplicate { n.replace(Routes.editor(it)) } })
                            DropdownMenuItem(text = { Text(stringResource(R.string.save_as_template)) }, leadingIcon = { Icon(Icons.Outlined.Dashboard, null) }, onClick = { menu = false; templateDialog = true })
                            if (archived) DropdownMenuItem(text = { Text(stringResource(R.string.unarchive)) }, leadingIcon = { Icon(Icons.Outlined.Unarchive, null) }, onClick = { menu = false; vm.unarchive() })
                            else DropdownMenuItem(text = { Text(stringResource(R.string.archive_action)) }, leadingIcon = { Icon(Icons.Outlined.Archive, null) }, onClick = { menu = false; vm.archive() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.move_to_trash)) }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; vm.trash { n.back() } })
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!preview && !readOnly) {
                Column(Modifier.imePadding()) {
                    if (focusMode) {
                        TextButton(onClick = { focusMode = false }, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.exit_focus_mode)) }
                    }
                    FormattingToolbar(
                        vm = vm,
                        onLink = { linkDialog = true },
                        onAttach = { attachMenu = true },
                        attachMenu = attachMenu,
                        onDismissAttach = { attachMenu = false },
                        onPickFile = { pickFile.launch(arrayOf("*/*")) },
                        onCamera = {
                            val (f, uri) = Files.newCameraFile(context)
                            pendingCamera = f.absolutePath
                            try { takePicture.launch(uri) } catch (e: android.content.ActivityNotFoundException) { scope.launch { snackbar.showSnackbar(msgNoCamera) } }
                        },
                        onDictate = dictate,
                        recording = recording,
                        onRecord = {
                            if (recording) {
                                recording = false
                                recorder.stop()?.let { attach(FileAttachmentSource(it, "audio/mp4")) }
                            } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onDrawing = { vm.flush { n.open(Routes.drawing(vm.noteId, page = "plain")) } },
                        onClipart = { key -> vm.addClipartDrawing(key, 0xFF1E88E5) { r -> r.onFailure { scope.launch { snackbar.showSnackbar(msgAttachFailed) } } } },
                        onPageStyle = { pageSheet = true },
                        noteFont = page.font,
                        hasSelection = !body.selection.collapsed,
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (trashed) Banner(stringResource(R.string.note_in_trash), stringResource(R.string.restore)) { vm.restore() }
            if (archived) Banner(stringResource(R.string.note_archived), stringResource(R.string.unarchive)) { vm.unarchive() }
            find?.let { FindBar(it, vm) }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = sp.readingWidth).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = sp.l, vertical = sp.s)
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown || !e.isCtrlPressed || readOnly) return@onPreviewKeyEvent false
                            when (e.key) {
                                Key.B -> { vm.toggleInline(InlineStyle.BOLD); true }
                                Key.I -> { vm.toggleInline(InlineStyle.ITALIC); true }
                                Key.U -> { vm.toggleInline(InlineStyle.UNDERLINE); true }
                                Key.Z -> { if (e.isShiftPressed) vm.redo() else vm.undo(); true }
                                Key.Y -> { vm.redo(); true }
                                Key.F -> { vm.openFind(false); true }
                                Key.H -> { vm.openFind(true); true }
                                Key.K -> { linkDialog = true; true }
                                Key.S -> { vm.flush(); true }
                                Key.E -> { vm.setPreview(!preview); true }
                                else -> false
                            }
                        },
                ) {
                    if (!focusMode) MetaRow(live?.folderId?.let { id -> folders.firstOrNull { it.id == id }?.name }, tags.map { it.name }, live?.pinned == true, live?.favorite == true,
                        onFolder = { if (!readOnly) moveDialog = true }, onTags = { if (!readOnly) tagDialog = true })
                    BasicTextField(
                        value = title,
                        onValueChange = vm::onTitleChange,
                        readOnly = readOnly,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        singleLine = false, maxLines = 3,
                        modifier = Modifier.fillMaxWidth().padding(vertical = sp.s).semantics { contentDescription = context.getString(R.string.note_title) },
                        decorationBox = { inner ->
                            if (title.isEmpty()) Text(stringResource(R.string.title_placeholder), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            inner()
                        },
                    )
                    if (preview || readOnly) {
                        MarkdownPreview(
                            body.text,
                            PreviewActions(
                                onToggleTask = { if (!readOnly) vm.toggleTask(it) },
                                onOpenWikiLink = { target -> scope.launch { openWiki(target, vm, n) } },
                                onOpenUrl = { openUrl = it },
                            ),
                            bodyStyle, semantic, Modifier.fillMaxWidth().heightIn(min = 200.dp),
                        )
                        if (body.text.isBlank()) Text(stringResource(R.string.empty_note_preview), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        BasicTextField(
                            value = body,
                            onValueChange = vm::onBodyChange,
                            textStyle = bodyStyle,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            visualTransformation = transformation,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp)
                                .drawBehind { drawEditorPaper(page.page, lineSpacingPx, size.width, size.height) }
                                .focusRequester(bodyFocus)
                                .semantics { contentDescription = context.getString(R.string.note_body) },
                            decorationBox = { inner ->
                                if (body.text.isEmpty()) Text(stringResource(R.string.body_placeholder), style = bodyStyle, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                inner()
                            },
                        )
                    }
                    val drawingAttachments = attachments.filter { it.isDrawing() }
                    if (drawingAttachments.isNotEmpty() && !focusMode) DrawingStrip(drawingAttachments, readOnly,
                        onOpen = { a -> vm.flush { n.open(Routes.drawing(vm.noteId, attachment = a.id)) } },
                        onNew = { vm.flush { n.open(Routes.drawing(vm.noteId, page = "plain")) } },
                        onRemove = vm::removeAttachment)
                    val otherAttachments = attachments.filterNot { it.isDrawing() }
                    if (otherAttachments.isNotEmpty() && !focusMode) AttachmentStrip(otherAttachments, readOnly, onOpen = { a ->
                        if (!Files.openAttachment(context, a.storagePath, a.mimeType)) scope.launch { snackbar.showSnackbar(context.getString(R.string.no_app_to_open)) }
                    }, onRemove = vm::removeAttachment, onInsertText = { t -> vm.insertBlock(t) })
                    if (backlinks.isNotEmpty() && !focusMode) {
                        HorizontalDivider(Modifier.padding(vertical = sp.m))
                        Text(stringResource(R.string.backlinks), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        backlinks.forEach { b -> TextButton(onClick = { n.open(Routes.editor(b.id)) }) { Text(b.title.ifBlank { stringResource(R.string.untitled) }) } }
                    }
                    Spacer(Modifier.size(sp.xxl))
                }
            }
        }
    }

    if (pageSheet) app.margin.feature.drawing.PageStyleSheet(page.page, onDismiss = { pageSheet = false }) { st -> pageSheet = false; vm.setPageStyle(st) }
    if (exportMenu) ExportDialog(onDismiss = { exportMenu = false }) { f, save ->
        exportMenu = false
        val note = live ?: return@ExportDialog
        // Export what is on screen (including edits not yet flushed), not the last stored row.
        val current = note.copy(title = title, body = app.margin.domain.model.NoteMeta.join(page, body.text))
        val name = app.margin.domain.io.NoteExporter.safeFileName(title.ifBlank { current.displayTitle(context.getString(R.string.untitled)) }) + "." + f.ext
        scope.launch {
            val bytes = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { NoteExport.bytes(context, f, current, tags.map { it.name }, attachments) } }
                .getOrElse { snackbar.showSnackbar(context.getString(R.string.export_failed)); return@launch }
            if (save) { pendingExport = bytes; exportFormat = f; saveDoc.launch(name) }
            else {
                val uri = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Files.shareableBytes(context, name, bytes) }
                Files.share(context, uri, f.mime, title.ifBlank { context.getString(R.string.untitled) })
            }
        }
    }
    if (tagDialog) TagEditorDialog(tags.map { it.name }, allTags, onSave = { vm.setTags(it); tagDialog = false }, onDismiss = { tagDialog = false })
    if (moveDialog) FolderPickerDialog(folders, stringResource(R.string.move_to), onPick = { vm.move(it); moveDialog = false }, onDismiss = { moveDialog = false })
    if (linkDialog) LinkDialog(onInsertUrl = { url, label -> vm.insertLink(url, label) }, onInsertNoteLink = { vm.insertWikiLink(it) }, onDismiss = { linkDialog = false })
    if (infoDialog) NoteInfoDialog(vm, onDismiss = { infoDialog = false })
    if (templateDialog) TextInputDialog(stringResource(R.string.save_as_template), stringResource(R.string.template_name), title,
        onConfirm = { name -> vm.saveAsTemplate(name) { scope.launch { snackbar.showSnackbar(context.getString(R.string.template_saved)) } } }, onDismiss = { templateDialog = false })
    openUrl?.let { url ->
        ConfirmDialog(stringResource(R.string.open_link_title), stringResource(R.string.open_link_message, url), stringResource(R.string.open),
            onConfirm = { Files.openUrl(context, url) }, onDismiss = { openUrl = null })
    }
    if (aiSheet) AiActionSheet(
        noteId = vm.noteId,
        input = body.text.substring(body.selection.min, body.selection.max).ifBlank { body.text },
        hasSelection = !body.selection.collapsed,
        onInsert = { vm.insertBlock(it) },
        onReplaceSelection = { text -> vm.insertText(text) },
        onSetTitle = { vm.onTitleChange(it) },
        onAddTags = { t -> vm.setTags((tags.map { it.name } + t).distinct()) },
        onDismiss = { aiSheet = false },
    )
    LaunchedEffect(meta.note?.id) {
        // New, empty notes open straight into typing.
        if (meta.note?.body.isNullOrEmpty() && !preview && !readOnly) runCatching { bodyFocus.requestFocus() }
    }
}

private suspend fun openWiki(target: String, vm: EditorViewModel, n: Navigator) {
    if (target.startsWith(MarkdownParser.NOTE_URI_PREFIX)) { n.open(Routes.editor(target.removePrefix(MarkdownParser.NOTE_URI_PREFIX))); return }
    vm.flush()
    // Resolve by title; unresolved links offer to create the note (explicit user action).
    val id = vm.resolveTitle(target)
    if (id != null) n.open(Routes.editor(id)) else vm.createLinkedNote(target) { n.open(Routes.editor(it)) }
}

@Composable
private fun Banner(text: String, action: String, onAction: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), color = MaterialTheme.colorScheme.onTertiaryContainer)
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun MetaRow(folder: String?, tags: List<String>, pinned: Boolean, favorite: Boolean, onFolder: () -> Unit, onTags: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AssistChip(onClick = onFolder, label = { Text(folder ?: stringResource(R.string.inbox)) },
            leadingIcon = { Icon(if (folder == null) Icons.Outlined.Inbox else Icons.Outlined.Folder, null, Modifier.size(16.dp)) })
        if (tags.isEmpty()) AssistChip(onClick = onTags, label = { Text(stringResource(R.string.add_tags)) }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Label, null, Modifier.size(16.dp)) })
        tags.forEach { t -> AssistChip(onClick = onTags, label = { Text("#$t") }) }
        if (pinned) Icon(Icons.Filled.PushPin, stringResource(R.string.pinned), Modifier.size(18.dp).align(Alignment.CenterVertically), tint = MaterialTheme.colorScheme.tertiary)
        if (favorite) Icon(Icons.Filled.Star, stringResource(R.string.favorite), Modifier.size(18.dp).align(Alignment.CenterVertically), tint = MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
private fun LinkDialog(onInsertUrl: (String, String?) -> Unit, onInsertNoteLink: (String) -> Unit, onDismiss: () -> Unit) {
    var url by rememberSaveable { mutableStateOf("https://") }
    var label by rememberSaveable { mutableStateOf("") }
    var noteTitle by rememberSaveable { mutableStateOf("") }
    val validUrl = Regex("""^(https?://|mailto:)\S+\.\S+|^mailto:\S+@\S+$""").containsMatchIn(url.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.insert_link)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.link_url)) }, singleLine = true, isError = !validUrl && url.length > 8)
                OutlinedTextField(label, { label = it }, label = { Text(stringResource(R.string.link_label_optional)) }, singleLine = true)
                HorizontalDivider()
                OutlinedTextField(noteTitle, { noteTitle = it }, label = { Text(stringResource(R.string.link_to_note)) }, singleLine = true,
                    supportingText = { Text(stringResource(R.string.link_to_note_hint)) })
            }
        },
        confirmButton = {
            TextButton(enabled = validUrl || noteTitle.isNotBlank(), onClick = {
                if (noteTitle.isNotBlank()) onInsertNoteLink(noteTitle.trim()) else onInsertUrl(url, label.ifBlank { null })
                onDismiss()
            }) { Text(stringResource(R.string.insert)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun NoteInfoDialog(vm: EditorViewModel, onDismiss: () -> Unit) {
    val note by vm.live.collectAsStateWithLifecycle()
    val stats = remember { vm.stats() }
    val fmt = java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM).withZone(java.time.ZoneId.systemDefault())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.note_info)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.info_words, stats.words))
                Text(stringResource(R.string.info_characters, stats.characters, stats.charactersNoSpaces))
                Text(stringResource(R.string.info_reading_time, stats.readingMinutes))
                note?.let { nt ->
                    Text(stringResource(R.string.info_created, fmt.format(nt.createdAt)))
                    Text(stringResource(R.string.info_updated, fmt.format(nt.updatedAt)))
                    nt.reviewedAt?.let { Text(stringResource(R.string.info_reviewed, fmt.format(it))) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.exclude_from_ai), Modifier.weight(1f))
                        androidx.compose.material3.Switch(checked = nt.excludedFromAi, onCheckedChange = { vm.setExcludedFromAi(it) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.markReviewed(); onDismiss() }) { Text(stringResource(R.string.mark_reviewed)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
