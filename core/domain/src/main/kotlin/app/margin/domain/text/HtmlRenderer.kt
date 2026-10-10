package app.margin.domain.text

/** Renders the canonical Markdown to safe, self-contained HTML (export + share). */
object HtmlRenderer {

    fun escape(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(c)
        }
    }

    /** Only these schemes are emitted as live links; anything else (javascript:, data:, file:) becomes text. */
    fun isSafeUrl(url: String): Boolean {
        val u = url.trim().lowercase()
        return u.startsWith("https://") || u.startsWith("http://") || u.startsWith("mailto:") ||
            u.startsWith(MarkdownParser.NOTE_URI_PREFIX) || (!u.contains(':') && !u.startsWith("//"))
    }

    fun renderBody(markdown: String): String {
        val blocks = MarkdownParser.parse(markdown)
        val sb = StringBuilder()
        var openList: String? = null
        fun closeList() { openList?.let { sb.append("</").append(it).append(">\n") }; openList = null }

        for (b in blocks) {
            val listTag = if (b is Block.ListItem) (if (b.kind == Block.ListKind.ORDERED) "ol" else "ul") else null
            if (listTag != openList) {
                closeList()
                if (listTag != null) { sb.append('<').append(listTag).append(">\n"); openList = listTag }
            }
            when (b) {
                is Block.Heading -> sb.append("<h${b.level}>").append(inline(b.inlines)).append("</h${b.level}>\n")
                is Block.Paragraph -> sb.append("<p>").append(inline(b.inlines).replace("\n", "<br>\n")).append("</p>\n")
                is Block.ListItem -> {
                    val style = if (b.indent > 0) " style=\"margin-left:${b.indent * 1.5}em\"" else ""
                    sb.append("<li$style>")
                    if (b.kind == Block.ListKind.TASK) {
                        sb.append("<input type=\"checkbox\" disabled").append(if (b.checked) " checked" else "").append("> ")
                    }
                    sb.append(inline(b.inlines)).append("</li>\n")
                }
                is Block.Quote -> sb.append("<blockquote>").append(inline(b.inlines).replace("\n", "<br>\n")).append("</blockquote>\n")
                is Block.Code -> {
                    val cls = if (b.language.isNotEmpty()) " class=\"language-${escape(b.language)}\"" else ""
                    sb.append("<pre><code$cls>").append(escape(b.code)).append("</code></pre>\n")
                }
                is Block.Divider -> sb.append("<hr>\n")
                is Block.Table -> {
                    sb.append("<table>\n<thead><tr>")
                    b.header.forEachIndexed { i, h -> sb.append("<th").append(alignAttr(b.alignments.getOrNull(i))).append('>').append(inline(InlineParser.parse(h))).append("</th>") }
                    sb.append("</tr></thead>\n<tbody>\n")
                    b.rows.forEach { row ->
                        sb.append("<tr>")
                        row.forEachIndexed { i, c -> sb.append("<td").append(alignAttr(b.alignments.getOrNull(i))).append('>').append(inline(InlineParser.parse(c))).append("</td>") }
                        sb.append("</tr>\n")
                    }
                    sb.append("</tbody>\n</table>\n")
                }
            }
        }
        closeList()
        return sb.toString()
    }

    private fun alignAttr(a: Block.Align?) = when (a) {
        Block.Align.CENTER -> " style=\"text-align:center\""
        Block.Align.RIGHT -> " style=\"text-align:right\""
        Block.Align.LEFT -> " style=\"text-align:left\""
        else -> ""
    }

    fun inline(nodes: List<Inline>): String = buildString { nodes.forEach { append(node(it)) } }

    private fun node(n: Inline): String = when (n) {
        is Inline.Text -> escape(n.text)
        is Inline.Bold -> "<strong>${inline(n.children)}</strong>"
        is Inline.Italic -> "<em>${inline(n.children)}</em>"
        is Inline.Strike -> "<del>${inline(n.children)}</del>"
        is Inline.Underline -> "<u>${inline(n.children)}</u>"
        is Inline.Code -> "<code>${escape(n.text)}</code>"
        is Inline.Link -> if (isSafeUrl(n.url)) "<a href=\"${escape(n.url)}\">${inline(n.children)}</a>" else inline(n.children)
        is Inline.WikiLink -> "<span class=\"wikilink\">${escape(n.target)}</span>"
        is Inline.AutoLink -> "<a href=\"${escape(n.url)}\">${escape(n.url)}</a>"
        is Inline.Styled -> if (n.attrs.isEmpty) inline(n.children) else "<span style=\"${escape(n.attrs.toCss())}\">${inline(n.children)}</span>"
        is Inline.Image -> if (isSafeUrl(n.url)) "<img alt=\"${escape(n.alt)}\" src=\"${escape(n.url)}\">" else escape(n.alt)
    }

    fun document(title: String, bodyHtml: String, metaHtml: String = ""): String = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escape(title)}</title>
<style>
body{font-family:system-ui,-apple-system,Roboto,sans-serif;max-width:46rem;margin:2rem auto;padding:0 1rem;line-height:1.6;color:#1d1b20;background:#fff}
pre{background:#f4f2f7;padding:.75rem;overflow:auto;border-radius:6px}code{font-family:ui-monospace,monospace}
blockquote{border-left:3px solid #ccc;margin:0;padding-left:1rem;color:#555}
table{border-collapse:collapse}td,th{border:1px solid #ccc;padding:.3rem .6rem}
.meta{color:#666;font-size:.85rem}.wikilink{color:#4a3aa8}
@media (prefers-color-scheme: dark){body{background:#141218;color:#e6e0e9}pre{background:#2b2930}.meta{color:#aaa}}
</style>
</head>
<body>
<h1>${escape(title)}</h1>
$metaHtml
$bodyHtml
</body>
</html>
"""
}
