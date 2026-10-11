package com.mimee.epubmaker.data.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.mimee.epubmaker.PdfOcrEngine
import com.mimee.epubmaker.domain.model.Book
import com.mimee.epubmaker.domain.model.BookContent
import com.mimee.epubmaker.domain.model.BookMetadata
import com.mimee.epubmaker.domain.model.BookSourceType
import com.mimee.epubmaker.domain.model.Chapter
import com.mimee.epubmaker.domain.model.CoverImage
import com.mimee.epubmaker.domain.model.CoverSource
import com.mimee.epubmaker.domain.model.ImageAsset
import com.mimee.epubmaker.domain.model.ImageBlock
import com.mimee.epubmaker.domain.model.ImageHandling
import com.mimee.epubmaker.domain.model.RichTextRun
import com.mimee.epubmaker.domain.model.TextBlock
import com.mimee.epubmaker.domain.model.TextBlockRole
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Import pipeline. No work starts until one of these methods is explicitly invoked by the UI. */
class BookImportProcessor(private val context: Context) {
    fun processPdf(
        uri: Uri,
        title: String,
        author: String,
        keepScannedPagesAsImages: Boolean,
        extractEmbeddedTextOnly: Boolean = false,
        ocrEngine: String,
        serverUrl: String,
        serverToken: String,
        onProgress: (Int, Int, String) -> Unit
    ): Book {
        PDFBoxResourceLoader.init(context)
        val pageText = context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { document ->
                val stripper = PDFTextStripper()
                (1..document.numberOfPages).map { pageNumber ->
                    stripper.startPage = pageNumber
                    stripper.endPage = pageNumber
                    stripper.sortByPosition = true
                    PdfOcrEngine.cleanRecognizedText(stripper.getText(document)).trim()
                }
            }
        } ?: error("เปิดไฟล์ PDF ไม่ได้")

        val contents = mutableListOf<BookContent>()
        val pageChapters = mutableListOf<Chapter>()
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("เปิดไฟล์ PDF เพื่อเรนเดอร์ไม่ได้")
        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                require(renderer.pageCount > 0) { "PDF ไม่มีหน้า" }
                for (index in 0 until renderer.pageCount) {
                    val pageStart = contents.size
                    onProgress(index + 1, renderer.pageCount, "กำลังอ่านหน้า ${index + 1}/${renderer.pageCount}")
                    val text = PdfOcrEngine.cleanRecognizedText(pageText.getOrNull(index).orEmpty())
                    if (text.isNotBlank()) {
                        splitParagraphs(text).forEach { paragraph ->
                            contents += TextBlock.paragraph(paragraph, order = contents.size)
                        }
                    } else {
                        renderer.openPage(index).use { page ->
                            val bitmap = renderPage(page)
                            try {
                                if (keepScannedPagesAsImages || extractEmbeddedTextOnly) {
                                    val saved = saveBitmap(bitmap, "pdf-page-${index + 1}")
                                    contents += ImageBlock(
                                        order = contents.size,
                                        image = ImageAsset(
                                            sourceUri = Uri.fromFile(saved).toString(),
                                            fileName = saved.name,
                                            mimeType = "image/jpeg",
                                            altText = "หน้าที่ ${index + 1} จาก PDF",
                                            caption = "หน้า ${index + 1}",
                                            widthPx = bitmap.width,
                                            heightPx = bitmap.height
                                        ),
                                        handling = ImageHandling.EMBED_AS_IMAGE
                                    )
                                } else {
                                    val recognized = PdfOcrEngine.cleanRecognizedText(PdfOcrEngine.recognizeBitmap(
                                        context, bitmap, ocrEngine, serverUrl, serverToken
                                    ))
                                    if (recognized.isNotBlank()) {
                                        contents += TextBlock.paragraph(
                                            recognized,
                                            order = contents.size,
                                            role = TextBlockRole.BODY
                                        )
                                    } else {
                                        contents += ImageBlock(
                                            order = contents.size,
                                            image = ImageAsset(
                                                sourceUri = Uri.fromFile(saveBitmap(bitmap, "unrecognized-page-${index + 1}")).toString(),
                                                fileName = "unrecognized-page-${index + 1}.jpg",
                                                mimeType = "image/jpeg",
                                                altText = "หน้าที่ ${index + 1} ซึ่ง OCR อ่านไม่ได้",
                                                caption = "หน้า ${index + 1}",
                                                widthPx = bitmap.width,
                                                heightPx = bitmap.height
                                            ),
                                            handling = ImageHandling.EMBED_AS_IMAGE
                                        )
                                    }
                                }
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                    val pageItems = contents.drop(pageStart).mapIndexed { itemIndex, item ->
                        when (item) {
                            is TextBlock -> item.copy(order = itemIndex)
                            is ImageBlock -> item.copy(order = itemIndex)
                        }
                    }
                    // Keep an entry for EVERY source PDF page, including blank pages.
                    // Otherwise a blank page is dropped and editor index N points at a different PDF page.
                    pageChapters += Chapter(
                        title = "หน้า ${index + 1}",
                        order = index,
                        contents = pageItems
                    )
                }
            }
        }
        require(contents.isNotEmpty()) { "ไม่พบข้อความหรือรูปภาพใน PDF" }
        val firstImage = contents.filterIsInstance<ImageBlock>().firstOrNull()
        return Book(
            metadata = BookMetadata(
                title = title.ifBlank { "หนังสือที่นำเข้า" },
                authors = author.takeIf { it.isNotBlank() }?.let(::listOf).orEmpty(),
                sourceFileName = "document.pdf",
                sourceMimeType = "application/pdf"
            ),
            chapters = pageChapters.ifEmpty { listOf(Chapter(title = title.ifBlank { "เนื้อหา" }, order = 0, contents = contents)) },
            coverImage = firstImage?.let {
                CoverImage(
                    source = CoverSource.ExistingImage(it.id),
                    mimeType = it.image.mimeType,
                    fileName = it.image.fileName,
                    altText = title.ifBlank { "ปกหนังสือ" },
                    widthPx = it.image.widthPx,
                    heightPx = it.image.heightPx
                )
            },
            sourceType = if (pageText.any { it.isNotBlank() }) {
                if (pageText.all { it.isNotBlank() }) BookSourceType.PDF_TEXT else BookSourceType.MIXED
            } else BookSourceType.PDF_SCANNED,
            sourceUris = listOf(uri.toString())
        )
    }

    fun processImage(
        uri: Uri,
        title: String,
        author: String,
        runOcr: Boolean,
        ocrEngine: String,
        serverUrl: String,
        serverToken: String,
        onProgress: (Int, Int, String) -> Unit
    ): Book {
        onProgress(1, 1, "กำลังอ่านรูปภาพ")
        val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            android.graphics.BitmapFactory.decodeStream(input)
        } ?: error("เปิดไฟล์รูปภาพไม่ได้หรือรูปแบบไม่รองรับ")
        try {
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "image"
            val imageBlock = ImageBlock(
                order = 0,
                image = ImageAsset(
                    sourceUri = uri.toString(),
                    fileName = name,
                    mimeType = if (mime in setOf("image/jpeg", "image/png", "image/webp", "image/gif")) mime else "image/jpeg",
                    altText = "ภาพจากไฟล์นำเข้า",
                    caption = "",
                    widthPx = bitmap.width,
                    heightPx = bitmap.height,
                    embedInEpub = true
                ),
                handling = if (runOcr) ImageHandling.KEEP_IMAGE_AND_OCR else ImageHandling.EMBED_AS_IMAGE
            )
            val contents = mutableListOf<BookContent>()
            if (runOcr) {
                val text = PdfOcrEngine.recognizeBitmap(context, bitmap, ocrEngine, serverUrl, serverToken)
                if (text.isNotBlank()) contents += TextBlock.paragraph(text, order = 0)
                contents += imageBlock.copy(order = contents.size)
            } else {
                contents += imageBlock
            }
            return Book(
                metadata = BookMetadata(
                    title = title.ifBlank { "ภาพที่นำเข้า" },
                    authors = author.takeIf { it.isNotBlank() }?.let(::listOf).orEmpty(),
                    sourceFileName = name,
                    sourceMimeType = mime
                ),
                chapters = listOf(Chapter(title = title.ifBlank { "เนื้อหา" }, contents = contents)),
                coverImage = CoverImage(
                    source = CoverSource.ImageFile(uri.toString(), name, imageBlock.image.mimeType),
                    mimeType = imageBlock.image.mimeType,
                    fileName = name,
                    altText = title.ifBlank { "ปกหนังสือ" },
                    widthPx = bitmap.width,
                    heightPx = bitmap.height
                ),
                sourceType = BookSourceType.IMAGE,
                sourceUris = listOf(uri.toString())
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun splitParagraphs(value: String): List<String> =
        value.replace("\r\n", "\n").split(Regex("""\n\s*\n"""))
            .map { it.trim() }.filter { it.isNotBlank() }

    private fun renderPage(page: PdfRenderer.Page): Bitmap {
        val width = (page.width * 2).coerceAtMost(2200).coerceAtLeast(1)
        val height = (page.height * 2).coerceAtMost(3000).coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        val matrix = Matrix().apply { setScale(width.toFloat() / page.width, height.toFloat() / page.height) }
        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
        return bitmap
    }

    private fun saveBitmap(bitmap: Bitmap, prefix: String): File {
        val directory = File(context.filesDir, "epub-import-images").apply { mkdirs() }
        val file = File(directory, "${prefix}-${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)) { "บันทึกภาพหน้าหนังสือไม่สำเร็จ" }
        }
        return file
    }
}
