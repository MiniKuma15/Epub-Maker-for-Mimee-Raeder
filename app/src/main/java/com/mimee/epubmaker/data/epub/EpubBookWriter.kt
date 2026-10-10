package com.mimee.epubmaker.data.epub

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.mimee.epubmaker.domain.model.Book
import com.mimee.epubmaker.domain.model.BookContent
import com.mimee.epubmaker.domain.model.CoverImage
import com.mimee.epubmaker.domain.model.CoverSource
import com.mimee.epubmaker.domain.model.ImageBlock
import com.mimee.epubmaker.domain.model.TextAlignment
import com.mimee.epubmaker.domain.model.TextBlock
import com.mimee.epubmaker.domain.model.TextBlockRole
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.Instant
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Serializes the structured Book model into a reflowable EPUB 3 archive.
 * Image bytes are read only during an explicit export action.
 */
class EpubBookWriter(private val context: Context) {
    fun write(book: Book, output: OutputStream) {
        require(book.chapters.isNotEmpty()) { "หนังสือยังไม่มีเนื้อหา" }
        val chapters = book.orderedChapters()
        val images = linkedMapOf<String, ByteArray>()
        val imagePaths = linkedMapOf<String, String>()
        var imageCounter = 0

        book.allImages().forEach { block ->
            if (block.image.embedInEpub) {
                val bytes = readUri(block.image.sourceUri)
                val ext = extensionFor(block.image.mimeType, block.image.fileName)
                val path = "OEBPS/images/image${++imageCounter}.$ext"
                images[path] = bytes
                imagePaths[block.id] = path.removePrefix("OEBPS/")
            }
        }

        var coverPath: String? = null
        val coverBytes = book.coverImage?.let { readCover(it, book) }
        if (coverBytes != null) {
            val cover = book.coverImage!!
            val ext = extensionFor(cover.mimeType, cover.fileName)
            coverPath = "OEBPS/images/cover.$ext"
            images[coverPath] = coverBytes
        }

        val language = xml(book.metadata.language.ifBlank { "th" })
        val title = xml(book.metadata.title.ifBlank { "Untitled" })
        val identifier = xml(book.metadata.identifier)
        val modified = Instant.now().toString().substringBefore('.').plus("Z")
        val manifest = StringBuilder()
        val spine = StringBuilder()
        val navItems = StringBuilder()

        if (coverPath != null) {
            val cover = book.coverImage!!
            val coverHref = "images/cover.${coverPath.substringAfterLast('.')}"
            manifest.append("<item id=\"cover-image\" href=\"${xml(coverHref)}\" media-type=\"${xml(cover.mimeType)}\" properties=\"cover-image\"/>")
        }
        images.forEach { (path, _) ->
            if (path == coverPath) return@forEach
            val relative = path.removePrefix("OEBPS/")
            val id = "img" + relative.hashCode().toUInt().toString(16)
            val mime = mimeForPath(path)
            manifest.append("<item id=\"$id\" href=\"${xml(relative)}\" media-type=\"$mime\"/>")
        }
        chapters.forEachIndexed { index, chapter ->
            val id = "chapter${index + 1}"
            val href = "text/$id.xhtml"
            manifest.append("<item id=\"$id\" href=\"$href\" media-type=\"application/xhtml+xml\"/>")
            spine.append("<itemref idref=\"$id\"/>")
            navItems.append("<li><a href=\"$href\">${xml(chapter.title.ifBlank { "บทที่ ${index + 1}" })}</a></li>")
        }

        val opf = buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id" xml:lang="$language">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:identifier id="book-id">$identifier</dc:identifier>
<dc:title>$title</dc:title>
<dc:language>$language</dc:language>
""")
            book.metadata.authors.filter { it.isNotBlank() }.forEach { append("<dc:creator>${xml(it)}</dc:creator>") }
            if (book.metadata.description.isNotBlank()) append("<dc:description>${xml(book.metadata.description)}</dc:description>")
            if (book.metadata.publisher.isNotBlank()) append("<dc:publisher>${xml(book.metadata.publisher)}</dc:publisher>")
            book.metadata.subject.filter { it.isNotBlank() }.forEach { append("<dc:subject>${xml(it)}</dc:subject>") }
            if (book.metadata.rights.isNotBlank()) append("<dc:rights>${xml(book.metadata.rights)}</dc:rights>")
            append("<meta property=\"dcterms:modified\">$modified</meta></metadata>")
            append("<manifest><item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/><item id=\"css\" href=\"styles/book.css\" media-type=\"text/css\"/>")
            append(manifest)
            append("</manifest><spine>")
            append(spine)
            append("</spine></package>")
        }

        val nav = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="$language" lang="$language">
<head><title>สารบัญ</title><meta charset="UTF-8"/></head>
<body><nav epub:type="toc" id="toc"><h1>สารบัญ</h1><ol>$navItems</ol></nav></body></html>"""

        val css = """
            body { font-family: serif; line-height: 1.7; margin: 5%; }
            h1 { text-align: center; margin: 1em 0 2em; font-size: 1.5em; }
            h2, h3 { line-height: 1.3; }
            p { margin: 0 0 .8em; orphans: 2; widows: 2; }
            figure { margin: 1em 0; text-align: center; }
            img { max-width: 100%; height: auto; }
            figcaption { font-size: .9em; font-style: italic; }
            blockquote { margin-left: 1.5em; margin-right: 1em; }
        """.trimIndent()

        ZipOutputStream(output).use { zip ->
            val mimeBytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
            val mimeEntry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimeBytes.size.toLong()
                compressedSize = mimeBytes.size.toLong()
                crc = CRC32().apply { update(mimeBytes) }.value
            }
            zip.putNextEntry(mimeEntry)
            zip.write(mimeBytes)
            zip.closeEntry()
            add(zip, "META-INF/container.xml", """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            add(zip, "OEBPS/content.opf", opf)
            add(zip, "OEBPS/nav.xhtml", nav)
            add(zip, "OEBPS/styles/book.css", css)
            chapters.forEachIndexed { index, chapter ->
                val body = chapter.orderedContents().joinToString("\n") { item ->
                    when (item) {
                        is TextBlock -> renderText(item)
                        is ImageBlock -> renderImage(item, imagePaths[item.id])
                    }
                }
                val chapterTitle = xml(chapter.title.ifBlank { "บทที่ ${index + 1}" })
                val xhtml = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="$language" lang="$language">
<head><title>$chapterTitle</title><meta charset="UTF-8"/><link rel="stylesheet" type="text/css" href="../styles/book.css"/></head>
<body><h1>$chapterTitle</h1>$body</body></html>"""
                add(zip, "OEBPS/text/chapter${index + 1}.xhtml", xhtml)
            }
            images.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    private fun renderText(block: TextBlock): String {
        val style = block.paragraphStyle
        val alignment = when (style.alignment) {
            TextAlignment.START -> "left"
            TextAlignment.CENTER -> "center"
            TextAlignment.END -> "right"
            TextAlignment.JUSTIFY -> "justify"
        }
        val roleTag = when (block.role) {
            TextBlockRole.TITLE, TextBlockRole.HEADING_1 -> "h2"
            TextBlockRole.HEADING_2 -> "h3"
            TextBlockRole.HEADING_3 -> "h4"
            TextBlockRole.QUOTE -> "blockquote"
            else -> "p"
        }
        val textStyle = if (roleTag == "p" || roleTag == "blockquote") {
            "text-align:$alignment;text-indent:${style.firstLineIndentEm}em;margin-bottom:${style.spacingAfterEm}em;"
        } else {
            "text-align:$alignment;"
        }
        val content = block.runs.joinToString("") { run ->
            var value = xml(run.text).replace("\n", "<br/>")
            if (run.bold) value = "<strong>$value</strong>"
            if (run.italic) value = "<em>$value</em>"
            if (run.underline) value = "<span style=\"text-decoration:underline\">$value</span>"
            if (run.strikethrough) value = "<span style=\"text-decoration:line-through\">$value</span>"
            value
        }
        return "<$roleTag style=\"$textStyle\">$content</$roleTag>"
    }

    private fun renderImage(block: ImageBlock, path: String?): String {
        if (path == null) return ""
        val alt = xml(block.image.altText)
        val caption = block.image.caption.takeIf { it.isNotBlank() }?.let { "<figcaption>${xml(it)}</figcaption>" }.orEmpty()
        return "<figure><img src=\"${xml(path)}\" alt=\"$alt\"/>$caption</figure>"
    }

    private fun readCover(cover: CoverImage, book: Book): ByteArray? {
        return when (val source = cover.source) {
            is CoverSource.ImageFile -> readUri(source.sourceUri)
            is CoverSource.PdfPage -> {
                val pfd = context.contentResolver.openFileDescriptor(Uri.parse(source.sourceUri), "r")
                    ?: error("เปิด PDF เพื่อสร้างปกไม่ได้")
                pfd.use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        require(source.pageIndex < renderer.pageCount) { "หน้าปกอยู่นอกช่วงหน้า PDF" }
                        renderer.openPage(source.pageIndex).use { page ->
                            val width = page.width.coerceAtMost(1800)
                            val height = (page.height.toFloat() * width / page.width).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(android.graphics.Color.WHITE)
                                val matrix = Matrix().apply { setScale(width.toFloat() / page.width, height.toFloat() / page.height) }
                            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                ByteArrayOutputStream().use { out ->
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                    out.toByteArray()
                                }
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
            }
            is CoverSource.ExistingImage -> {
                val block = book.allImages().firstOrNull { it.id == source.imageAssetId } ?: return null
                readUri(block.image.sourceUri)
            }
        }
    }

    private fun readUri(value: String): ByteArray {
        return context.contentResolver.openInputStream(Uri.parse(value))?.use { it.readBytes() }
            ?: error("อ่านไฟล์รูปภาพไม่ได้: $value")
    }

    private fun extensionFor(mime: String, fileName: String): String {
        val byMime = when (mime.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/svg+xml" -> "svg"
            "image/webp" -> "webp"
            else -> ""
        }
        if (byMime.isNotBlank()) return byMime
        return fileName.substringAfterLast('.', "jpg").lowercase().filter { it.isLetterOrDigit() }.ifBlank { "jpg" }
    }

    private fun mimeForPath(path: String): String = when (path.substringAfterLast('.').lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "svg" -> "image/svg+xml"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }

    private fun add(zip: ZipOutputStream, path: String, value: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(value.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun xml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
