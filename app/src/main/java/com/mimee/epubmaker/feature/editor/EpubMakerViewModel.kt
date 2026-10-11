package com.mimee.epubmaker.feature.editor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mimee.epubmaker.data.epub.EpubBookWriter
import com.mimee.epubmaker.data.importer.BookImportProcessor
import com.mimee.epubmaker.domain.model.Book
import com.mimee.epubmaker.domain.model.BookMetadata
import com.mimee.epubmaker.domain.model.BookSourceType
import com.mimee.epubmaker.domain.model.Chapter
import com.mimee.epubmaker.domain.model.CoverImage
import com.mimee.epubmaker.domain.model.CoverSource
import com.mimee.epubmaker.domain.model.ImageBlock
import com.mimee.epubmaker.domain.model.RichTextRun
import com.mimee.epubmaker.domain.model.TextBlock
import com.mimee.epubmaker.domain.model.ParagraphStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

data class EpubMakerUiState(
    val selectedUri: Uri? = null,
    val selectedMimeType: String = "",
    val selectedFileName: String = "",
    val title: String = "",
    val author: String = "",
    val book: Book = Book(),
    val keepScannedPagesAsImages: Boolean = false,
    val extractEmbeddedTextOnly: Boolean = false,
    val runOcrForImages: Boolean = true,
    val ocrEngine: String = "tesseract",
    val serverUrl: String = "",
    val serverToken: String = "",
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val status: String = "เลือกไฟล์ PDF, JPG หรือ PNG จากนั้นกดเริ่มแปลง",
    val error: String? = null,
    val hasConverted: Boolean = false,
    val isExporting: Boolean = false
)

class EpubMakerViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(EpubMakerUiState())
    val state: StateFlow<EpubMakerUiState> = _state.asStateFlow()
    private val processor = BookImportProcessor(application)

    fun selectSource(uri: Uri, mimeType: String, displayName: String) {
        _state.update {
            it.copy(
                selectedUri = uri,
                selectedMimeType = mimeType,
                selectedFileName = displayName,
                book = Book(metadata = BookMetadata(title = it.title, authors = it.author.takeIf { author -> author.isNotBlank() }?.let(::listOf).orEmpty())),
                status = "เลือกไฟล์แล้ว: $displayName — ยังไม่ได้เริ่มประมวลผล",
                error = null,
                hasConverted = false
            )
        }
    }

    fun updateTitle(value: String) {
        _state.update { current ->
            val metadata = current.book.metadata.copy(title = value, modifiedAt = Instant.now())
            current.copy(title = value, book = current.book.copy(metadata = metadata, modifiedAt = Instant.now()))
        }
    }

    fun updateAuthor(value: String) {
        _state.update { current ->
            val authors = value.takeIf { it.isNotBlank() }?.let(::listOf).orEmpty()
            current.copy(author = value, book = current.book.copy(
                metadata = current.book.metadata.copy(authors = authors, modifiedAt = Instant.now()),
                modifiedAt = Instant.now()
            ))
        }
    }

    fun setKeepScannedPagesAsImages(value: Boolean) = _state.update { it.copy(keepScannedPagesAsImages = value) }
    fun setExtractEmbeddedTextOnly(value: Boolean) = _state.update { it.copy(extractEmbeddedTextOnly = value) }
    fun setRunOcrForImages(value: Boolean) = _state.update { it.copy(runOcrForImages = value) }
    fun setOcrEngine(value: String) = _state.update { it.copy(ocrEngine = value) }
    fun setServerUrl(value: String) = _state.update { it.copy(serverUrl = value) }
    fun setServerToken(value: String) = _state.update { it.copy(serverToken = value) }

    fun startConversion() {
        val initial = _state.value
        val uri = initial.selectedUri
        if (uri == null) {
            _state.update { it.copy(error = "กรุณาเลือกไฟล์ก่อนเริ่มแปลง") }
            return
        }
        if (initial.isProcessing) return
        _state.update { it.copy(isProcessing = true, progress = 0f, error = null, status = "กำลังเริ่มประมวลผล…") }
        viewModelScope.launch {
            try {
                val book = withContext(Dispatchers.IO) {
                    val title = _state.value.title.ifBlank { initial.selectedFileName.substringBeforeLast('.') }
                    val author = _state.value.author
                    if (initial.selectedMimeType == "application/pdf" || initial.selectedFileName.endsWith(".pdf", true)) {
                        processor.processPdf(
                            uri = uri,
                            title = title,
                            author = author,
                            keepScannedPagesAsImages = _state.value.keepScannedPagesAsImages,
                            extractEmbeddedTextOnly = _state.value.extractEmbeddedTextOnly,
                            ocrEngine = _state.value.ocrEngine,
                            serverUrl = _state.value.serverUrl,
                            serverToken = _state.value.serverToken,
                            onProgress = { current, total, message ->
                                _state.update { state ->
                                    state.copy(
                                        progress = if (total > 0) current.toFloat() / total else 0f,
                                        status = message
                                    )
                                }
                            }
                        )
                    } else {
                        processor.processImage(
                            uri = uri,
                            title = title,
                            author = author,
                            runOcr = _state.value.runOcrForImages,
                            ocrEngine = _state.value.ocrEngine,
                            serverUrl = _state.value.serverUrl,
                            serverToken = _state.value.serverToken,
                            onProgress = { current, total, message ->
                                _state.update { state ->
                                    state.copy(progress = if (total > 0) current.toFloat() / total else 0f, status = message)
                                }
                            }
                        )
                    }
                }
                _state.update { current ->
                    current.copy(
                        book = book.copy(
                            coverImage = current.book.coverImage ?: book.coverImage,
                            metadata = book.metadata.copy(
                                title = current.title.ifBlank { book.metadata.title },
                                authors = current.author.takeIf { it.isNotBlank() }?.let(::listOf).orEmpty()
                            )
                        ),
                        title = current.title.ifBlank { book.metadata.title },
                        author = current.author,
                        isProcessing = false,
                        progress = 1f,
                        status = "แปลงข้อมูลเสร็จแล้ว ตรวจแก้ข้อความและกดส่งออก EPUB",
                        hasConverted = true
                    )
                }
            } catch (exception: Exception) {
                _state.update {
                    it.copy(
                        isProcessing = false,
                        status = "ประมวลผลไม่สำเร็จ",
                        error = exception.localizedMessage ?: "เกิดข้อผิดพลาดระหว่างประมวลผล"
                    )
                }
            }
        }
    }

    fun setPdfFirstPageAsCover() {
        val current = _state.value
        val uri = current.selectedUri ?: return
        if (current.selectedMimeType != "application/pdf" && !current.selectedFileName.endsWith(".pdf", true)) {
            _state.update { it.copy(error = "เลือกไฟล์ PDF ก่อนตั้งหน้าปกจากหน้าแรก") }
            return
        }
        _state.update { state ->
            val cover = CoverImage(
                source = CoverSource.PdfPage(uri.toString(), 0),
                altText = state.title.ifBlank { "ปกหนังสือ" },
                mimeType = "image/jpeg",
                fileName = "cover.jpg"
            )
            state.copy(book = state.book.copy(coverImage = cover), status = "ตั้งหน้าแรกของ PDF เป็นปกแล้ว")
        }
    }

    fun setCoverImage(uri: Uri, mimeType: String, fileName: String) {
        _state.update { state ->
            val cover = CoverImage(
                source = CoverSource.ImageFile(uri.toString(), fileName, mimeType),
                altText = state.title.ifBlank { "ปกหนังสือ" },
                mimeType = mimeType,
                fileName = fileName
            )
            state.copy(book = state.book.copy(coverImage = cover), status = "ตั้งภาพที่เลือกเป็นปกแล้ว")
        }
    }

    fun updateManuscript(text: String, runs: List<RichTextRun>, paragraphStyle: ParagraphStyle) {
        _state.update { current ->
            val oldChapter = current.book.orderedChapters().firstOrNull()
                ?: Chapter(title = current.title.ifBlank { "เนื้อหา" })
            val normalizedRuns = if (runs.isEmpty() && text.isNotEmpty()) listOf(RichTextRun(text)) else runs
            val paragraphRuns = splitRichRunsIntoParagraphs(text, normalizedRuns)
            val replacements = paragraphRuns.mapIndexed { index, paragraph ->
                TextBlock(
                    order = index,
                    runs = paragraph,
                    paragraphStyle = paragraphStyle
                )
            }
            val oldTextCount = oldChapter.contents.count { it is TextBlock }
            val output = mutableListOf<com.mimee.epubmaker.domain.model.BookContent>()
            var nextReplacement = 0
            var insertedExtraParagraphs = false

            oldChapter.orderedContents().forEach { item ->
                if (item is TextBlock) {
                    if (nextReplacement < replacements.size) {
                        output += replacements[nextReplacement++].copy(order = output.size)
                    }
                } else {
                    if (!insertedExtraParagraphs && nextReplacement >= oldTextCount && replacements.size > nextReplacement) {
                        while (nextReplacement < replacements.size) {
                            output += replacements[nextReplacement++].copy(order = output.size)
                        }
                        insertedExtraParagraphs = true
                    }
                    output += item
                }
            }
            while (nextReplacement < replacements.size) {
                output += replacements[nextReplacement++].copy(order = output.size)
            }

            val updatedChapter = oldChapter.copy(
                title = current.title.ifBlank { oldChapter.title.ifBlank { "เนื้อหา" } },
                contents = output.mapIndexed { index, item ->
                    when (item) {
                        is TextBlock -> item.copy(order = index)
                        is ImageBlock -> item.copy(order = index)
                    }
                }
            )
            current.copy(book = current.book.copy(
                metadata = current.book.metadata.copy(
                    title = current.title,
                    authors = current.author.takeIf { it.isNotBlank() }?.let(::listOf).orEmpty()
                ),
                chapters = listOf(updatedChapter) + current.book.orderedChapters().drop(1),
                modifiedAt = Instant.now()
            ))
        }
    }

    private fun splitRichRunsIntoParagraphs(text: String, runs: List<RichTextRun>): List<List<RichTextRun>> {
        if (text.isBlank()) return emptyList()
        val ranges = mutableListOf<Pair<Int, Int>>()
        val separator = Regex("\\n[ \\t]*\\n+")
        var paragraphStart = 0
        separator.findAll(text).forEach { match ->
            if (match.range.first > paragraphStart) ranges += paragraphStart to match.range.first
            paragraphStart = match.range.last + 1
        }
        if (paragraphStart < text.length) ranges += paragraphStart to text.length
        if (ranges.isEmpty()) ranges += 0 to text.length

        return ranges.map { (start, end) ->
            var runOffset = 0
            val pieces = mutableListOf<RichTextRun>()
            runs.forEach { run ->
                val runStart = runOffset
                val runEnd = runOffset + run.text.length
                runOffset = runEnd
                val overlapStart = maxOf(start, runStart)
                val overlapEnd = minOf(end, runEnd)
                if (overlapStart < overlapEnd) {
                    pieces += run.copy(text = run.text.substring(overlapStart - runStart, overlapEnd - runStart))
                }
            }
            if (pieces.isEmpty()) listOf(RichTextRun(text.substring(start, end))) else pieces
        }
    }

    fun exportTo(uri: Uri, text: String, runs: List<RichTextRun>, paragraphStyle: ParagraphStyle) {
        val current = _state.value
        if (!current.hasConverted) {
            _state.update { it.copy(error = "กรุณากดเริ่มแปลงก่อนส่งออก EPUB") }
            return
        }
        val title = current.title.trim()
        if (title.isBlank()) {
            _state.update { it.copy(error = "กรุณาระบุชื่อหนังสือ") }
            return
        }
        updateManuscript(text, runs, paragraphStyle)
        val book = _state.value.book
        _state.update { it.copy(isExporting = true, error = null, status = "กำลังสร้างไฟล์ EPUB…") }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val output = getApplication<Application>().contentResolver.openOutputStream(uri)
                        ?: error("เปิดไฟล์ปลายทางเพื่อเขียน EPUB ไม่ได้")
                    output.use { EpubBookWriter(getApplication()).write(book, it) }
                }
                _state.update { it.copy(isExporting = false, status = "ส่งออก EPUB สำเร็จ") }
            } catch (exception: Exception) {
                _state.update { it.copy(isExporting = false, status = "ส่งออก EPUB ไม่สำเร็จ", error = exception.localizedMessage ?: "เกิดข้อผิดพลาดขณะส่งออก") }
            }
        }
    }

    fun updateImageDescription(imageBlockId: String, altText: String? = null, caption: String? = null) {
        _state.update { current ->
            val chapters = current.book.chapters.map { chapter ->
                chapter.copy(contents = chapter.contents.map { item ->
                    if (item is ImageBlock && item.id == imageBlockId) {
                        item.copy(image = item.image.copy(
                            altText = altText ?: item.image.altText,
                            caption = caption ?: item.image.caption
                        ))
                    } else item
                })
            }
            current.copy(book = current.book.copy(chapters = chapters, modifiedAt = Instant.now()))
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}
