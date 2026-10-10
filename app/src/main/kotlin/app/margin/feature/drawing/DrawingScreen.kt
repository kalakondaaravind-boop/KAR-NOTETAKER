@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.margin.feature.drawing

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AutoFixNormal
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.FlipToFront
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.HighlightAlt
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.margin.R
import app.margin.domain.drawing.Drawing
import app.margin.domain.drawing.DrawingOps
import app.margin.domain.drawing.InkTool
import app.margin.domain.drawing.Pt
import app.margin.domain.drawing.Shape
import app.margin.domain.drawing.ShapeKind
import app.margin.domain.drawing.TextBox
import app.margin.domain.io.NoteExporter
import app.margin.domain.text.NoteFont
import app.margin.domain.text.Palette
import app.margin.platform.Files
import app.margin.ui.components.ClipartPickerSheet
import app.margin.ui.components.FontMenu
import app.margin.ui.components.StyleGlyph
import app.margin.ui.components.SymbolPickerSheet
import app.margin.ui.navigation.Navigator
import app.margin.ui.theme.NoteFonts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot

enum class DrawTool { HAND, SELECT, PEN, PENCIL, MARKER, HIGHLIGHTER, ERASER, SHAPE, TEXT }

private val DrawTool.ink: InkTool? get() = when (this) {
    DrawTool.PEN -> InkTool.PEN; DrawTool.PENCIL -> InkTool.PENCIL; DrawTool.MARKER -> InkTool.MARKER; DrawTool.HIGHLIGHTER -> InkTool.HIGHLIGHTER
    else -> null
}

private enum class Drag { NONE, INK, ERASE, LASSO, MOVE, RESIZE, SHAPE, PAN, TAP }

private data class TextEdit(val box: TextBox)

private enum class ExportFormat(val ext: String, val mime: String) { PNG("png", "image/png"), SVG("svg", "image/svg+xml"), PDF("pdf", "application/pdf") }

@Composable
fun DrawingScreen(n: Navigator, vm: DrawingViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawing by vm.drawing.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val undo by vm.undoState.collectAsStateWithLifecycle()
    val problem by vm.problem.collectAsStateWithLifecycle()
    val clipboard by DrawingClipboard.items.collectAsStateWithLifecycle()
    val prefs = remember { ColorPrefs(context) }

    var tool by rememberSaveable { mutableStateOf(DrawTool.PEN) }
    var inkColor by rememberSaveable { mutableStateOf(0xFF1F2937L) }
    var highlightColor by rememberSaveable { mutableStateOf(Palette.DEFAULT_MARK) }
    val widths = remember { mutableStateMapOf(DrawTool.PEN to 4f, DrawTool.PENCIL to 3f, DrawTool.MARKER to 12f, DrawTool.HIGHLIGHTER to 30f, DrawTool.ERASER to 24f, DrawTool.SHAPE to 5f) }
    var shapeKind by rememberSaveable { mutableStateOf(ShapeKind.RECT) }
    var shapeFill by rememberSaveable { mutableStateOf(false) }
    var partialErase by rememberSaveable { mutableStateOf(true) }
    var stylusOnly by rememberSaveable { mutableStateOf(false) }
    var slots by remember { mutableStateOf(prefs.slots()) }

    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var fitted by remember { mutableStateOf(false) }
    fun fit() {
        if (viewSize.width == 0) return
        val d = vm.drawing.value
        zoom = minOf(viewSize.width * 0.96f / d.width, viewSize.height * 0.96f / d.height)
        pan = Offset((viewSize.width - d.width * zoom) / 2f, (viewSize.height - d.height * zoom) / 2f)
    }
    LaunchedEffect(viewSize) { if (!fitted && viewSize.width > 0) { fit(); fitted = true } }

    // live gesture state (drawn on the overlay only, so the page itself isn't redrawn per touch point)
    val livePoints = remember { mutableStateListOf<Pt>() }
    var liveShape by remember { mutableStateOf<Shape?>(null) }
    var eraserAt by remember { mutableStateOf<Pt?>(null) }

    // dialogs and sheets
    var colorTarget by remember { mutableStateOf<String?>(null) } // "ink", "highlight", "selection", "fill", "background", "slot:N"
    var showClipart by remember { mutableStateOf(false) }
    var showSymbols by remember { mutableStateOf(false) }
    var showPages by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var textEdit by remember { mutableStateOf<TextEdit?>(null) }

    fun currentColor() = if (tool == DrawTool.HIGHLIGHTER) highlightColor else inkColor
    fun visibleCenter(): Pt = Pt((viewSize.width / 2f - pan.x) / zoom, (viewSize.height / 2f - pan.y) / zoom)

    BackHandler { vm.flush(); n.back() }

    val exportPick = remember { mutableStateOf<ExportFormat?>(null) }
    val title = stringResource(R.string.drawing_title)
    suspend fun bytes(f: ExportFormat): ByteArray = withContext(Dispatchers.Default) {
        val d = vm.drawing.value
        when (f) {
            ExportFormat.PNG -> DrawingImages.png(context, d)
            ExportFormat.SVG -> DrawingImages.svg(d).toByteArray()
            ExportFormat.PDF -> DrawingImages.pdf(context, d)
        }
    }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val f = exportPick.value ?: return@rememberLauncherForActivityResult
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { Files.writeTo(context, uri, bytes(f)) }
            android.widget.Toast.makeText(context, if (ok) R.string.export_saved else R.string.export_failed, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    fun share(f: ExportFormat) = scope.launch {
        val b = bytes(f)
        val uri = withContext(Dispatchers.IO) { Files.shareableBytes(context, NoteExporter.safeFileName(title) + "." + f.ext, b) }
        Files.share(context, uri, f.mime, title)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = { vm.flush(); n.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    IconButton(onClick = vm::undo, enabled = undo.canUndo) { Icon(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.undo)) }
                    IconButton(onClick = vm::redo, enabled = undo.canRedo) { Icon(Icons.AutoMirrored.Outlined.Redo, stringResource(R.string.redo)) }
                    IconButton(onClick = { vm.paste() }, enabled = clipboard.isNotEmpty()) { Icon(Icons.Outlined.ContentPaste, stringResource(R.string.paste)) }
                    IconButton(onClick = { showPages = true }) { Icon(Icons.Outlined.GridOn, stringResource(R.string.page_style)) }
                    IconButton(onClick = ::fit) { Icon(Icons.Outlined.FitScreen, stringResource(R.string.fit_page)) }
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.select_all)) }, leadingIcon = { Icon(Icons.Outlined.SelectAll, null) },
                                onClick = { showMenu = false; tool = DrawTool.SELECT; vm.selectAll() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.paper_colour)) }, leadingIcon = { Icon(Icons.Outlined.Wallpaper, null) },
                                onClick = { showMenu = false; colorTarget = "background" })
                            HorizontalDivider()
                            ExportFormat.entries.forEach { f ->
                                DropdownMenuItem(text = { Text(stringResource(R.string.save_as_format, f.name)) },
                                    onClick = { showMenu = false; exportPick.value = f; saveLauncher.launch(NoteExporter.safeFileName(title) + "." + f.ext) })
                            }
                            DropdownMenuItem(text = { Text(stringResource(R.string.share_as_image)) }, leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                onClick = { showMenu = false; share(ExportFormat.PNG) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.share_as_pdf)) }, leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                onClick = { showMenu = false; share(ExportFormat.PDF) })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text(stringResource(R.string.stylus_only)) },
                                trailingIcon = { Switch(checked = stylusOnly, onCheckedChange = null) },
                                onClick = { stylusOnly = !stylusOnly })
                            DropdownMenuItem(text = { Text(stringResource(R.string.clear_page)) }, leadingIcon = { Icon(Icons.Outlined.DeleteSweep, null) },
                                onClick = { showMenu = false; confirmClear = true })
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                    ToolOptions(
                        tool = tool,
                        color = currentColor(),
                        slots = slots,
                        width = widths[tool] ?: 4f,
                        onWidth = { widths[tool] = it },
                        onColor = { c -> if (tool == DrawTool.HIGHLIGHTER) highlightColor = c else inkColor = c; if (selection.isNotEmpty()) vm.recolor(c) },
                        onEditSlot = { i -> colorTarget = "slot:$i" },
                        onMoreColors = { colorTarget = if (tool == DrawTool.HIGHLIGHTER) "highlight" else "ink" },
                        shapeKind = shapeKind, onShapeKind = { shapeKind = it },
                        shapeFill = shapeFill, onShapeFill = { shapeFill = it },
                        partialErase = partialErase, onPartialErase = { partialErase = it },
                    )
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                        ToolButton(Icons.Outlined.PanTool, R.string.tool_hand, tool == DrawTool.HAND) { tool = DrawTool.HAND }
                        ToolButton(Icons.Outlined.HighlightAlt, R.string.tool_select, tool == DrawTool.SELECT) { tool = DrawTool.SELECT }
                        ToolButton(Icons.Outlined.Draw, R.string.tool_pen, tool == DrawTool.PEN) { tool = DrawTool.PEN; vm.clearSelection() }
                        ToolButton(Icons.Outlined.Create, R.string.tool_pencil, tool == DrawTool.PENCIL) { tool = DrawTool.PENCIL; vm.clearSelection() }
                        ToolButton(Icons.Outlined.Brush, R.string.tool_marker, tool == DrawTool.MARKER) { tool = DrawTool.MARKER; vm.clearSelection() }
                        ToolButton(Icons.Outlined.BorderColor, R.string.tool_highlighter, tool == DrawTool.HIGHLIGHTER) { tool = DrawTool.HIGHLIGHTER; vm.clearSelection() }
                        ToolButton(Icons.Outlined.AutoFixNormal, R.string.tool_eraser, tool == DrawTool.ERASER) { tool = DrawTool.ERASER; vm.clearSelection() }
                        ToolButton(Icons.Outlined.Category, R.string.tool_shapes, tool == DrawTool.SHAPE) { tool = DrawTool.SHAPE; vm.clearSelection() }
                        ToolButton(Icons.Outlined.TextFields, R.string.tool_text, tool == DrawTool.TEXT) { tool = DrawTool.TEXT; vm.clearSelection() }
                        ToolButton(Icons.Outlined.EmojiEmotions, R.string.insert_clipart, false) { showClipart = true }
                        Box(Modifier.size(48.dp).padding(4.dp), contentAlignment = Alignment.Center) {
                            TextButton(onClick = { showSymbols = true }) { Text("Ω", fontSize = 20.sp) }
                        }
                    }
                }
            }
        },
    ) { p ->
        val textMeasurer = rememberTextMeasurer()
        val accent = MaterialTheme.colorScheme.primary
        Box(
            Modifier
                .padding(p)
                .fillMaxSize()
                .background(Color(0xFFDDE7F0))
                .clipToBounds()
                .onSizeChanged { viewSize = it }
                .testTag("drawing-canvas")
                .semantics { contentDescription = context.getString(R.string.drawing_canvas_desc) }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        fun page(o: Offset) = Pt((o.x - pan.x) / zoom, (o.y - pan.y) / zoom, 1f)
                        val t = tool
                        val start = page(down.position)
                        val base = vm.drawing.value
                        val sel = vm.selection.value
                        val selBounds = DrawingOps.selectionBounds(base, sel)
                        val fingerOnStylusPage = stylusOnly && down.type == PointerType.Touch
                        var mode = when {
                            t == DrawTool.HAND || fingerOnStylusPage -> Drag.PAN
                            t == DrawTool.SELECT && selBounds != null && run {
                                val pad = 8f / zoom
                                hypot((start.x - (selBounds.right + pad)) * zoom, (start.y - (selBounds.bottom + pad)) * zoom) <= HANDLE_RADIUS * 1.8f
                            } -> Drag.RESIZE
                            t == DrawTool.SELECT && selBounds?.inflate(12f / zoom)?.contains(start.x, start.y) == true -> Drag.MOVE
                            t == DrawTool.SELECT -> Drag.TAP
                            t.ink != null -> Drag.INK
                            t == DrawTool.ERASER -> Drag.ERASE
                            t == DrawTool.SHAPE -> Drag.SHAPE
                            else -> Drag.TAP
                        }
                        when (mode) {
                            Drag.INK -> { livePoints.clear(); livePoints += start.copy(pressure = down.pressure.coerceIn(0.1f, 1f)) }
                            Drag.ERASE -> eraserAt = start
                            else -> Unit
                        }
                        fun tapSelect() {
                            val hit = DrawingOps.hitTest(base, start.x, start.y, 14f / zoom)
                            if (hit != null && hit.id in sel && hit is TextBox) textEdit = TextEdit(hit)
                            else vm.select(hit?.let { setOf(it.id) } ?: emptySet())
                        }
                        var last = start
                        var moved = false
                        var multi = false
                        while (true) {
                            val ev = awaitPointerEvent()
                            val pressed = ev.changes.filter { it.pressed }
                            if (pressed.size >= 2 || multi) {
                                if (!multi) {
                                    multi = true
                                    // a second finger means pan/zoom: drop whatever the first finger started
                                    livePoints.clear(); liveShape = null; eraserAt = null; vm.cancelPreview()
                                }
                                if (pressed.isEmpty()) break
                                val zc = ev.calculateZoom(); val pc = ev.calculatePan(); val c = ev.calculateCentroid(useCurrent = true)
                                val nz = (zoom * zc).coerceIn(0.1f, 10f)
                                if (c != Offset.Unspecified) pan = c - (c - pan) * (nz / zoom) + pc
                                zoom = nz
                                ev.changes.forEach { it.consume() }
                                continue
                            }
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: ev.changes.first()
                            val pt = page(ch.position).copy(pressure = ch.pressure.coerceIn(0.1f, 1f))
                            if (!moved && (ch.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                moved = true
                                if (mode == Drag.TAP && t == DrawTool.SELECT) { mode = Drag.LASSO; livePoints.clear(); livePoints += start }
                            }
                            if (!ch.pressed) {
                                // gesture finished
                                when (mode) {
                                    Drag.INK -> {
                                        val ink = t.ink!!
                                        val c = if (ink == InkTool.HIGHLIGHTER) highlightColor else inkColor
                                        vm.addStroke(livePoints.toList(), c, widths[t] ?: 4f, ink)
                                        livePoints.clear()
                                    }
                                    Drag.ERASE -> { eraserAt = null; vm.commit() }
                                    Drag.MOVE, Drag.RESIZE -> if (moved) vm.commit() else { vm.cancelPreview(); tapSelect() }
                                    Drag.SHAPE -> { liveShape?.let { s -> if (hypot(s.x2 - s.x1, s.y2 - s.y1) > 6f) vm.add(s) }; liveShape = null }
                                    Drag.LASSO -> { vm.select(DrawingOps.lasso(vm.drawing.value, livePoints.toList())); livePoints.clear() }
                                    Drag.TAP -> when (t) {
                                        DrawTool.TEXT -> {
                                            val hit = DrawingOps.hitTest(base, start.x, start.y, 8f / zoom) as? TextBox
                                            textEdit = TextEdit(hit ?: TextBox(vm.newId(), start.x, start.y, "", inkColor, 36f))
                                        }
                                        else -> tapSelect()
                                    }
                                    else -> Unit
                                }
                                ch.consume()
                                break
                            }
                            when (mode) {
                                Drag.PAN -> pan += ch.position - ch.previousPosition
                                Drag.INK -> if (livePoints.size < Drawing.MAX_POINTS_PER_STROKE) livePoints += pt
                                Drag.ERASE -> {
                                    vm.preview(DrawingOps.erase(vm.drawing.value, listOf(last, pt), widths[DrawTool.ERASER] ?: 24f, partialErase, vm::newId))
                                    eraserAt = pt
                                }
                                Drag.MOVE -> vm.preview(DrawingOps.move(base, sel, pt.x - start.x, pt.y - start.y))
                                Drag.RESIZE -> if (selBounds != null) {
                                    val sx = ((pt.x - selBounds.left) / selBounds.width.coerceAtLeast(1f)).coerceIn(0.05f, 20f)
                                    val sy = ((pt.y - selBounds.top) / selBounds.height.coerceAtLeast(1f)).coerceIn(0.05f, 20f)
                                    // text and pictures keep their proportions
                                    val uniform = base.elements.any { it.id in sel && it !is app.margin.domain.drawing.Stroke && it !is Shape }
                                    val (ax, ay) = if (uniform) maxOf(sx, sy).let { it to it } else sx to sy
                                    vm.preview(DrawingOps.scale(base, sel, ax, ay))
                                }
                                Drag.SHAPE -> liveShape = Shape(
                                    liveShape?.id ?: vm.newId(), shapeKind, start.x, start.y, pt.x, pt.y, inkColor, widths[DrawTool.SHAPE] ?: 5f,
                                    if (shapeFill && shapeKind != ShapeKind.LINE && shapeKind != ShapeKind.ARROW) (inkColor and 0x00FFFFFF) or 0x55000000 else null,
                                )
                                Drag.LASSO -> livePoints += pt
                                else -> Unit
                            }
                            last = pt
                            ch.consume()
                        }
                    }
                }
        ) {
            // Layer 1: the page and everything committed (redrawn only when the drawing, zoom or pan change).
            Canvas(Modifier.fillMaxSize()) {
                translate(pan.x, pan.y) {
                    scale(zoom, zoom, pivot = Offset.Zero) {
                        drawRect(Color(0x33000000), Offset(6f / zoom, 8f / zoom), Size(drawing.width, drawing.height))
                        drawDrawingContent(drawing, textMeasurer)
                    }
                }
            }
            // Layer 2: what the finger is doing right now, and the selection.
            Canvas(Modifier.fillMaxSize()) {
                translate(pan.x, pan.y) {
                    scale(zoom, zoom, pivot = Offset.Zero) {
                        val t = tool
                        if (livePoints.isNotEmpty() && t != DrawTool.SELECT) {
                            val ink = t.ink ?: InkTool.PEN
                            drawElement(app.margin.domain.drawing.Stroke("live", livePoints.toList(), if (ink == InkTool.HIGHLIGHTER) highlightColor else inkColor, widths[t] ?: 4f, ink), textMeasurer)
                        }
                        if (livePoints.size > 1 && t == DrawTool.SELECT) {
                            drawPath(strokePath(livePoints.toList()), accent, style = DrawStroke(2f / zoom, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f / zoom, 8f / zoom))))
                        }
                        liveShape?.let { drawElement(it, textMeasurer) }
                        eraserAt?.let { drawCircle(Color(0x5590A4AE), widths[DrawTool.ERASER] ?: 24f, Offset(it.x, it.y)); drawCircle(Color(0xFF607D8B), widths[DrawTool.ERASER] ?: 24f, Offset(it.x, it.y), style = DrawStroke(1.5f / zoom)) }
                        DrawingOps.selectionBounds(drawing, selection)?.let { drawSelection(it, accent, zoom) }
                    }
                }
            }
            if (selection.isNotEmpty()) SelectionBar(
                single = drawing.elements.singleOrNull { it.id in selection },
                onCut = vm::cut, onCopy = vm::copy, onPaste = { vm.paste() }, canPaste = clipboard.isNotEmpty(),
                onDuplicate = vm::duplicate, onDelete = vm::delete, onColor = { colorTarget = "selection" },
                onFront = vm::toFront, onBack = vm::toBack,
                onEditText = { (drawing.elements.firstOrNull { it.id in selection } as? TextBox)?.let { textEdit = TextEdit(it) } },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
            )
            if (problem != null) Surface(
                color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            ) {
                Text(stringResource(if (problem == DrawingViewModel.Problem.LOAD) R.string.drawing_load_failed else R.string.drawing_save_failed),
                    Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }

    colorTarget?.let { target ->
        val initial = when {
            target == "highlight" -> highlightColor
            target == "background" -> drawing.background
            target.startsWith("slot:") -> slots.getOrElse(target.removePrefix("slot:").toIntOrNull() ?: 0) { inkColor }
            target == "selection" -> (drawing.elements.firstOrNull { it.id in selection }?.let { colorOf(it) }) ?: inkColor
            else -> inkColor
        }
        ColorPickerDialog(
            initial = initial,
            title = stringResource(if (target == "background") R.string.paper_colour else R.string.choose_colour),
            allowAlpha = target != "background",
            onDismiss = { colorTarget = null },
        ) { c ->
            when {
                target == "highlight" -> highlightColor = c
                target == "background" -> vm.setBackground(c)
                target == "selection" -> vm.recolor(c)
                target.startsWith("slot:") -> { val i = target.removePrefix("slot:").toIntOrNull() ?: 0; prefs.setSlot(i, c); slots = prefs.slots(); inkColor = c }
                else -> inkColor = c
            }
            colorTarget = null
        }
    }
    if (showClipart) ClipartPickerSheet(onDismiss = { showClipart = false }) { key ->
        showClipart = false
        vm.addClipart(key, visibleCenter(), inkColor)
        tool = DrawTool.SELECT
    }
    if (showSymbols) SymbolPickerSheet(onDismiss = { showSymbols = false }) { s ->
        showSymbols = false
        val c = visibleCenter()
        vm.upsertText(TextBox(vm.newId(), c.x - 30f, c.y - 40f, s, inkColor, 72f))
        tool = DrawTool.SELECT
    }
    if (showPages) PageStyleSheet(drawing.page, onDismiss = { showPages = false }) { st -> showPages = false; vm.setPage(st) }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text(stringResource(R.string.clear_page_q)) },
        text = { Text(stringResource(R.string.clear_page_body)) },
        confirmButton = { TextButton(onClick = { confirmClear = false; vm.clearPage() }) { Text(stringResource(R.string.clear)) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
    )
    textEdit?.let { te ->
        TextBoxDialog(te.box, onDismiss = { textEdit = null }) { box ->
            textEdit = null
            if (box.text.isBlank()) { if (drawing.byId(box.id) != null) { vm.select(setOf(box.id)); vm.delete() } }
            else { vm.upsertText(box); inkColor = box.color }
        }
    }
}

private fun colorOf(e: app.margin.domain.drawing.Element): Long = when (e) {
    is app.margin.domain.drawing.Stroke -> e.color
    is Shape -> e.color
    is TextBox -> e.color
    is app.margin.domain.drawing.Clipart -> e.color
}

@Composable
private fun ToolButton(icon: ImageVector, label: Int, selected: Boolean, onClick: () -> Unit) {
    IconToggleButton(checked = selected, onCheckedChange = { onClick() }, modifier = Modifier.size(52.dp)) {
        Box(
            Modifier.size(44.dp).background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, stringResource(label), tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun ToolOptions(
    tool: DrawTool, color: Long, slots: List<Long>, width: Float, onWidth: (Float) -> Unit,
    onColor: (Long) -> Unit, onEditSlot: (Int) -> Unit, onMoreColors: () -> Unit,
    shapeKind: ShapeKind, onShapeKind: (ShapeKind) -> Unit, shapeFill: Boolean, onShapeFill: (Boolean) -> Unit,
    partialErase: Boolean, onPartialErase: (Boolean) -> Unit,
) {
    FlowRow(
        Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (tool) {
            DrawTool.ERASER -> {
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(selected = partialErase, onClick = { onPartialErase(true) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.erase_partial)) }
                    SegmentedButton(selected = !partialErase, onClick = { onPartialErase(false) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.erase_whole)) }
                }
                WidthSlider(width, 6f..120f, onWidth)
            }
            DrawTool.HAND -> Text(stringResource(R.string.hand_hint), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(8.dp))
            DrawTool.SELECT -> Text(stringResource(R.string.select_hint), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(8.dp))
            else -> {
                if (tool == DrawTool.HIGHLIGHTER) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Palette.highlight.take(6).forEach { c -> Swatch(c, (c or 0xFF000000) == (color or 0xFF000000), 34.dp) { onColor(c) } }
                } else PenSlots(slots, color, onColor, onEditSlot)
                IconButton(onClick = onMoreColors) { Icon(Icons.Outlined.Palette, stringResource(R.string.more_colours)) }
                if (tool != DrawTool.TEXT) WidthSlider(width, if (tool == DrawTool.HIGHLIGHTER) 8f..80f else 1f..60f, onWidth)
                if (tool == DrawTool.SHAPE) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ShapeKind.entries.forEach { k -> FilterChip(selected = k == shapeKind, onClick = { onShapeKind(k) }, label = { Text(stringResource(shapeLabel(k))) }) }
                    }
                    FilterChip(selected = shapeFill, onClick = { onShapeFill(!shapeFill) }, label = { Text(stringResource(R.string.shape_fill)) },
                        leadingIcon = { Icon(Icons.Outlined.FormatColorFill, null, Modifier.size(18.dp)) })
                }
                if (tool == DrawTool.TEXT) Text(stringResource(R.string.text_hint), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun shapeLabel(k: ShapeKind) = when (k) {
    ShapeKind.LINE -> R.string.shape_line; ShapeKind.ARROW -> R.string.shape_arrow; ShapeKind.RECT -> R.string.shape_rect
    ShapeKind.ROUNDED_RECT -> R.string.shape_rounded; ShapeKind.ELLIPSE -> R.string.shape_ellipse
    ShapeKind.TRIANGLE -> R.string.shape_triangle; ShapeKind.STAR -> R.string.shape_star
}

@Composable
private fun WidthSlider(width: Float, range: ClosedFloatingPointRange<Float>, onWidth: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size((width / range.endInclusive * 24f).coerceIn(3f, 24f).dp).background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(50)))
        }
        Slider(value = width.coerceIn(range), onValueChange = onWidth, valueRange = range, modifier = Modifier.width(200.dp))
    }
}

@Composable
private fun SelectionBar(
    single: app.margin.domain.drawing.Element?,
    onCut: () -> Unit, onCopy: () -> Unit, onPaste: () -> Unit, canPaste: Boolean, onDuplicate: () -> Unit, onDelete: () -> Unit,
    onColor: () -> Unit, onFront: () -> Unit, onBack: () -> Unit, onEditText: () -> Unit, modifier: Modifier = Modifier,
) {
    Surface(shape = RoundedCornerShape(16.dp), shadowElevation = 8.dp, tonalElevation = 4.dp, modifier = modifier) {
        Row(Modifier.padding(horizontal = 4.dp).horizontalScroll(rememberScrollState())) {
            if (single is TextBox) IconButton(onClick = onEditText) { Icon(Icons.Outlined.Edit, stringResource(R.string.edit_text)) }
            IconButton(onClick = onCut) { Icon(Icons.Outlined.ContentCut, stringResource(R.string.cut)) }
            IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.copy)) }
            IconButton(onClick = onPaste, enabled = canPaste) { Icon(Icons.Outlined.ContentPaste, stringResource(R.string.paste)) }
            IconButton(onClick = onDuplicate) { Icon(Icons.Outlined.LibraryAdd, stringResource(R.string.duplicate)) }
            IconButton(onClick = onColor) { Icon(Icons.Outlined.Palette, stringResource(R.string.change_colour)) }
            IconButton(onClick = onFront) { Icon(Icons.Outlined.FlipToFront, stringResource(R.string.bring_to_front)) }
            IconButton(onClick = onBack) { Icon(Icons.Outlined.FlipToBack, stringResource(R.string.send_to_back)) }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error) }
        }
    }
}

/** Text on the canvas: the preview shows the real font, size, weight and slant as you type. */
@Composable
private fun TextBoxDialog(initial: TextBox, onDismiss: () -> Unit, onDone: (TextBox) -> Unit) {
    var text by remember { mutableStateOf(initial.text) }
    var font by remember { mutableStateOf(NoteFont.from(initial.font)) }
    var size by remember { mutableFloatStateOf(initial.size) }
    var bold by remember { mutableStateOf(initial.bold) }
    var italic by remember { mutableStateOf(initial.italic) }
    var color by remember { mutableStateOf(initial.color) }
    var fontMenu by remember { mutableStateOf(false) }
    var pickColor by remember { mutableStateOf(false) }
    val style = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = NoteFonts.family(font), fontSize = (size / 2f).coerceIn(12f, 48f).sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        color = Color(color),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tool_text)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it.take(2000) }, textStyle = style, modifier = Modifier.fillMaxWidth().height(140.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box {
                        OutlinedButton(onClick = { fontMenu = true }) { Text(font?.displayName ?: stringResource(R.string.font_default), fontFamily = NoteFonts.family(font)) }
                        FontMenu(fontMenu, font, onDismiss = { fontMenu = false }, onPick = { font = it; fontMenu = false })
                    }
                    IconToggleButton(checked = bold, onCheckedChange = { bold = it }) { StyleGlyph("bold") }
                    IconToggleButton(checked = italic, onCheckedChange = { italic = it }) { StyleGlyph("italic") }
                    Swatch(color, false, 32.dp, stringResource(R.string.choose_colour)) { pickColor = true }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.text_size), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(8.dp))
                    Slider(value = size, onValueChange = { size = it }, valueRange = 12f..200f)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(initial.copy(text = text, font = font?.id, size = size, bold = bold, italic = italic, color = color)) }) { Text(stringResource(R.string.done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (pickColor) ColorPickerDialog(color, stringResource(R.string.choose_colour), onDismiss = { pickColor = false }) { color = it; pickColor = false }
}

/** Small preview bitmap of a drawing (used by the editor strip). */
fun drawingThumbnail(context: Context, d: Drawing) = DrawingImages.bitmap(context, d, 480)
