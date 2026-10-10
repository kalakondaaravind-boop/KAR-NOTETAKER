package app.margin.feature.editor

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.text.Html
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import app.margin.domain.drawing.Drawing
import app.margin.domain.drawing.DrawingCodec
import app.margin.domain.io.DocxExporter
import app.margin.domain.io.NoteExportContext
import app.margin.domain.io.NoteExporter
import app.margin.domain.model.Attachment
import app.margin.domain.model.Note
import app.margin.domain.model.NoteMeta
import app.margin.domain.text.HtmlRenderer
import app.margin.feature.drawing.DrawingImages
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.ZoneId

/** Single-note export in the formats people share: Word, PDF, Markdown, web page and plain text. */
object NoteExport {
    enum class Format(val ext: String, val mime: String) {
        WORD("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        PDF("pdf", "application/pdf"),
        MARKDOWN("md", "text/markdown"),
        HTML("html", "text/html"),
        TEXT("txt", "text/plain"),
    }

    private fun drawings(context: Context, attachments: List<Attachment>): List<Pair<Attachment, Drawing>> = attachments
        .filter { it.mimeType == DrawingCodec.MIME }
        .mapNotNull { a ->
            runCatching { a to DrawingCodec.decode(File(File(context.filesDir, "attachments"), a.storagePath).readText()) }.getOrNull()
        }

    /** [note] carries the stored body (with its hidden page/font line); exports show only the content. */
    fun bytes(context: Context, format: Format, note: Note, tags: List<String>, attachments: List<Attachment>): ByteArray {
        val content = NoteMeta.split(note.body).second
        val clean = note.copy(body = content)
        val ctx = NoteExportContext(tags, emptyList(), attachments.map { it.fileName })
        return when (format) {
            // Markdown keeps the page/font line (an HTML comment other apps ignore) so re-importing restores the paper.
            Format.MARKDOWN -> NoteExporter.markdown(note, ctx).toByteArray()
            Format.HTML -> NoteExporter.html(clean, ctx, ZoneId.systemDefault()).toByteArray()
            Format.TEXT -> NoteExporter.text(clean).toByteArray()
            Format.WORD -> {
                val images = drawings(context, attachments).mapIndexed { i, (a, d) ->
                    val png = DrawingImages.png(context, d, 1400)
                    val s = minOf(1f, 1400f / maxOf(d.width, d.height))
                    DocxExporter.Image("drawing${i + 1}.png", png, (d.width * s).toInt(), (d.height * s).toInt(), a.fileName.substringBeforeLast('.'))
                }
                DocxExporter.export(note.displayTitle(), content, images, note.createdAt, note.updatedAt)
            }
            Format.PDF -> pdf(context, clean, drawings(context, attachments).map { it.second })
        }
    }

    /** A4 pages: the formatted text flowed across pages, then one page per drawing. */
    private fun pdf(context: Context, note: Note, drawings: List<Drawing>): ByteArray {
        val pageW = 595; val pageH = 842; val margin = 54
        val doc = PdfDocument()
        try {
            val html = "<h1>" + HtmlRenderer.escape(note.displayTitle()) + "</h1>" + HtmlRenderer.renderBody(note.body)
            @Suppress("DEPRECATION")
            val spanned = if (android.os.Build.VERSION.SDK_INT >= 24) Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT) else Html.fromHtml(html)
            val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = 0xFF1F2937.toInt() }
            val width = pageW - 2 * margin
            val layout = StaticLayout.Builder.obtain(spanned, 0, spanned.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(2f, 1.15f).setIncludePad(false).build()
            val usable = pageH - 2 * margin
            var top = 0
            var pageNo = 1
            while (top < layout.height || pageNo == 1) {
                // break between lines, never through one
                var bottomLine = layout.getLineForVertical(top + usable)
                if (layout.getLineBottom(bottomLine) > top + usable && bottomLine > layout.getLineForVertical(top)) bottomLine--
                val bottom = layout.getLineBottom(bottomLine).coerceAtLeast(top + 1)
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo++).create())
                val c = page.canvas
                c.save()
                c.translate(margin.toFloat(), (margin - top).toFloat())
                c.clipRect(0, top, width, bottom)
                layout.draw(c)
                c.restore()
                doc.finishPage(page)
                top = bottom
                if (layout.height == 0) break
            }
            for (d in drawings) {
                val h = (pageW * d.height / d.width).toInt().coerceIn(200, 14_000)
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, h, pageNo++).create())
                DrawingImages.drawOn(context, page.canvas, d, pageW.toFloat(), h.toFloat())
                doc.finishPage(page)
            }
            return ByteArrayOutputStream().use { doc.writeTo(it); it.toByteArray() }
        } finally { doc.close() }
    }
}
