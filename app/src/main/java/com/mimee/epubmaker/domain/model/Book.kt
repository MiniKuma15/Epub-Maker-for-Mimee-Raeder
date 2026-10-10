package com.mimee.epubmaker.domain.model

import java.time.Instant
import java.util.UUID

enum class BookSourceType {
    PDF_TEXT,
    PDF_SCANNED,
    IMAGE,
    MIXED,
    IMPORTED_MANUSCRIPT
}

/**
 * Canonical in-memory representation used by import, OCR, editor, preview and EPUB export.
 * Text, images, metadata and cover remain structured until the EPUB writer serializes them.
 */
data class Book(
    val id: String = UUID.randomUUID().toString(),
    val metadata: BookMetadata = BookMetadata(),
    val chapters: List<Chapter> = emptyList(),
    val coverImage: CoverImage? = null,
    val sourceType: BookSourceType = BookSourceType.IMPORTED_MANUSCRIPT,
    val sourceUris: List<String> = emptyList(),
    val createdAt: Instant = Instant.now(),
    val modifiedAt: Instant = Instant.now()
) {
    init {
        require(id.isNotBlank()) { "Book ID must not be blank" }
        require(sourceUris.none { it.isBlank() }) { "Source URIs must not contain blank values" }
    }

    fun orderedChapters(): List<Chapter> = chapters.sortedBy { it.order }

    fun allContent(): List<BookContent> =
        orderedChapters().flatMap { it.orderedContents() }

    fun allImages(): List<ImageBlock> = allContent().filterIsInstance<ImageBlock>()

    fun allTextBlocks(): List<TextBlock> = allContent().filterIsInstance<TextBlock>()

    fun withMetadata(newMetadata: BookMetadata): Book =
        copy(metadata = newMetadata, modifiedAt = Instant.now())

    fun withChapters(newChapters: List<Chapter>): Book =
        copy(chapters = newChapters, modifiedAt = Instant.now())

    fun withCover(newCover: CoverImage?): Book =
        copy(coverImage = newCover, modifiedAt = Instant.now())
}
