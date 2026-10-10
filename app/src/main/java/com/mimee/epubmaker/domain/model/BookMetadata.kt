package com.mimee.epubmaker.domain.model

import java.time.Instant
import java.util.UUID

/** Metadata that will be mapped to the EPUB 3 package document (content.opf). */
data class BookMetadata(
    val title: String = "",
    val authors: List<String> = emptyList(),
    val language: String = "th",
    val description: String = "",
    val publisher: String = "",
    val subject: List<String> = emptyList(),
    val rights: String = "",
    val identifier: String = "urn:uuid:${UUID.randomUUID()}",
    val sourceFileName: String? = null,
    val sourceMimeType: String? = null,
    val createdAt: Instant = Instant.now(),
    val modifiedAt: Instant = Instant.now()
) {
    init {
        require(language.isNotBlank()) { "Book language must not be blank" }
        require(identifier.isNotBlank()) { "Book identifier must not be blank" }
    }

    val primaryAuthor: String
        get() = authors.firstOrNull().orEmpty()
}
