package app.margin.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.margin.domain.text.Block
import app.margin.domain.text.Inline
import app.margin.domain.text.LiveMarkdown
import app.margin.domain.text.MarkdownHighlighter
import app.margin.ui.theme.NoteFonts
import app.margin.domain.text.MarkdownParser
import app.margin.ui.theme.SemanticColors

/**
 * Editor styling. By default the note is shown "what you see": formatting characters are hidden and list
 * markers become • / ☐ / ☑ (see [LiveMarkdown]); the stored text stays Markdown. With [showMarks] the raw
 * Markdown is shown, styled in place, with an identity offset mapping.
 */
class MarkdownVisualTransformation(
    private val colors: SemanticColors,
    private val syntax: Color,
    private val headingScale: Float = 1f,
    private val showMarks: Boolean = false,
) : VisualTransformation {
    private var cacheText: String? = null
    private var cache: TransformedText? = null

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        cache?.let { if (raw == cacheText) return it }
        // Very long notes: skip styling beyond a size where it could cost frames while typing.
        if (raw.length > 200_000) return TransformedText(text, OffsetMapping.Identity)
        val view = LiveMarkdown.build(raw, showMarks)
        val styled = buildAnnotatedString {
            append(view.text)
            for (s in view.styles) addStyle(style(s), s.start, s.end)
        }
        val mapping = if (showMarks) OffsetMapping.Identity else object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = view.toView(offset)
            override fun transformedToOriginal(offset: Int): Int = view.toOrig(offset)
        }
        return TransformedText(styled, mapping).also { cacheText = raw; cache = it }
    }

    private fun style(s: MarkdownHighlighter.Span): SpanStyle = when (s.kind) {
        MarkdownHighlighter.Kind.HEADING1 -> SpanStyle(fontSize = (1.5f * headingScale).em, fontWeight = FontWeight.Bold)
        MarkdownHighlighter.Kind.HEADING2 -> SpanStyle(fontSize = (1.3f * headingScale).em, fontWeight = FontWeight.Bold)
        MarkdownHighlighter.Kind.HEADING3 -> SpanStyle(fontSize = (1.15f * headingScale).em, fontWeight = FontWeight.SemiBold)
        MarkdownHighlighter.Kind.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
        MarkdownHighlighter.Kind.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
        MarkdownHighlighter.Kind.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        MarkdownHighlighter.Kind.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
        MarkdownHighlighter.Kind.CODE, MarkdownHighlighter.Kind.CODE_BLOCK -> SpanStyle(fontFamily = FontFamily.Monospace, background = colors.code)
        MarkdownHighlighter.Kind.LINK, MarkdownHighlighter.Kind.WIKILINK -> SpanStyle(color = colors.link, textDecoration = TextDecoration.Underline)
        MarkdownHighlighter.Kind.QUOTE -> SpanStyle(fontStyle = FontStyle.Italic, color = syntax)
        MarkdownHighlighter.Kind.LIST_MARKER -> SpanStyle(color = syntax, fontWeight = FontWeight.SemiBold)
        MarkdownHighlighter.Kind.CHECKED_ITEM -> SpanStyle(textDecoration = TextDecoration.LineThrough, color = syntax)
        MarkdownHighlighter.Kind.SYNTAX -> SpanStyle(color = syntax.copy(alpha = 0.55f))
        MarkdownHighlighter.Kind.COLOR -> SpanStyle(color = (s.value as? Long)?.let { Color(it) } ?: Color.Unspecified)
        MarkdownHighlighter.Kind.HIGHLIGHT -> SpanStyle(background = (s.value as? Long)?.let { Color(it) } ?: Color.Unspecified)
        MarkdownHighlighter.Kind.FONT -> SpanStyle(fontFamily = NoteFonts.family(s.value as? String))
    }
}

/** Callbacks for interactive elements in the rendered preview. */
data class PreviewActions(
    val onToggleTask: (line: Int) -> Unit,
    val onOpenWikiLink: (title: String) -> Unit,
    val onOpenUrl: (url: String) -> Unit,
)

/** Read-mode renderer for the supported Markdown subset. Unsupported syntax is shown as literal text, never dropped. */
@Composable
fun MarkdownPreview(markdown: String, actions: PreviewActions, textStyle: TextStyle, colors: SemanticColors, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (b in blocks) {
            when (b) {
                is Block.Heading -> Text(
                    inline(b.inlines, colors, actions),
                    style = when (b.level) { 1 -> MaterialTheme.typography.headlineSmall; 2 -> MaterialTheme.typography.titleLarge; else -> MaterialTheme.typography.titleMedium },
                    modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                )
                is Block.Paragraph -> Text(inline(b.inlines, colors, actions), style = textStyle)
                is Block.ListItem -> Row(Modifier.padding(start = (b.indent * 20).dp), verticalAlignment = Alignment.Top) {
                    when (b.kind) {
                        Block.ListKind.TASK -> Checkbox(checked = b.checked, onCheckedChange = { actions.onToggleTask(b.firstLine) },
                            modifier = Modifier.semantics { contentDescription = InlineText.plain(b.inlines) })
                        Block.ListKind.BULLET -> Text("•", style = textStyle, modifier = Modifier.width(20.dp).padding(top = 2.dp))
                        Block.ListKind.ORDERED -> Text("${b.number ?: 1}${b.marker}", style = textStyle, modifier = Modifier.widthIn(min = 24.dp).padding(end = 4.dp))
                    }
                    val t = inline(b.inlines, colors, actions)
                    Text(
                        if (b.checked) AnnotatedString(t.text, listOf(AnnotatedString.Range(SpanStyle(textDecoration = TextDecoration.LineThrough), 0, t.length))) else t,
                        style = textStyle,
                        modifier = Modifier.padding(top = if (b.kind == Block.ListKind.TASK) 12.dp else 0.dp).weight(1f),
                    )
                }
                is Block.Quote -> Row(Modifier.padding(start = 4.dp).height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                    Text(inline(b.inlines, colors, actions), style = textStyle.copy(fontStyle = FontStyle.Italic), modifier = Modifier.padding(start = 12.dp))
                }
                is Block.Code -> Box(Modifier.fillMaxWidth().background(colors.code, MaterialTheme.shapes.small).horizontalScroll(rememberScrollState()).padding(12.dp)) {
                    Text(b.code, style = textStyle.copy(fontFamily = FontFamily.Monospace))
                }
                is Block.Divider -> HorizontalDivider(Modifier.padding(vertical = 8.dp))
                is Block.Table -> MarkdownTable(b, textStyle, colors, actions)
            }
        }
    }
}

@Composable
private fun MarkdownTable(t: Block.Table, textStyle: TextStyle, colors: SemanticColors, actions: PreviewActions) {
    Column(Modifier.horizontalScroll(rememberScrollState()).border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall)) {
        val rows = listOf(t.header) + t.rows
        rows.forEachIndexed { ri, row ->
            Row {
                row.forEach { cell ->
                    Text(
                        inline(app.margin.domain.text.InlineParser.parse(cell), colors, actions),
                        style = if (ri == 0) textStyle.copy(fontWeight = FontWeight.SemiBold) else textStyle,
                        modifier = Modifier.widthIn(min = 96.dp, max = 240.dp).border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(8.dp),
                    )
                }
            }
        }
    }
}

object InlineText {
    fun plain(nodes: List<Inline>) = app.margin.domain.text.InlineParser.plainText(nodes)
}

/** Inline nodes → AnnotatedString with link annotations (wiki links open notes; web links ask before opening). */
fun inline(nodes: List<Inline>, colors: SemanticColors, actions: PreviewActions): AnnotatedString = buildAnnotatedString {
    fun walk(n: Inline, base: SpanStyle?) {
        fun styled(style: SpanStyle, children: List<Inline>) {
            val merged = base?.merge(style) ?: style
            children.forEach { walk(it, merged) }
        }
        when (n) {
            is Inline.Text -> if (base != null) { pushStyle(base); append(n.text); pop() } else append(n.text)
            is Inline.Bold -> styled(SpanStyle(fontWeight = FontWeight.Bold), n.children)
            is Inline.Italic -> styled(SpanStyle(fontStyle = FontStyle.Italic), n.children)
            is Inline.Strike -> styled(SpanStyle(textDecoration = TextDecoration.LineThrough), n.children)
            is Inline.Underline -> styled(SpanStyle(textDecoration = TextDecoration.Underline), n.children)
            is Inline.Code -> { pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = colors.code)); append(n.text); pop() }
            is Inline.WikiLink -> withLink(LinkAnnotation.Clickable("wiki:" + n.target, TextLinkStyles(SpanStyle(color = colors.link, textDecoration = TextDecoration.Underline))) {
                actions.onOpenWikiLink(n.target)
            }) { append(n.target) }
            is Inline.Link -> withLink(LinkAnnotation.Clickable("url:" + n.url, TextLinkStyles(SpanStyle(color = colors.link, textDecoration = TextDecoration.Underline))) {
                if (n.url.startsWith(MarkdownParser.NOTE_URI_PREFIX)) actions.onOpenWikiLink(n.url) else actions.onOpenUrl(n.url)
            }) { n.children.forEach { walk(it, base) } }
            is Inline.AutoLink -> withLink(LinkAnnotation.Clickable("url:" + n.url, TextLinkStyles(SpanStyle(color = colors.link, textDecoration = TextDecoration.Underline))) {
                actions.onOpenUrl(n.url)
            }) { append(n.url) }
            is Inline.Image -> { pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = colors.link)); append("🖼 " + n.alt.ifBlank { n.url }); pop() }
            is Inline.Styled -> styled(SpanStyle(
                color = n.attrs.color?.let { Color(it) } ?: Color.Unspecified,
                background = n.attrs.background?.let { Color(it) } ?: Color.Unspecified,
                fontFamily = n.attrs.font?.let { NoteFonts.family(it) },
            ), n.children)
        }
    }
    nodes.forEach { walk(it, null) }
}
