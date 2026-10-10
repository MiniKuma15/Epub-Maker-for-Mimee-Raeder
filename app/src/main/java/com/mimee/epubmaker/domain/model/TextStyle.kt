package com.mimee.epubmaker.domain.model

/**
 * Portable rich-text and paragraph formatting stored in the manuscript model.
 * Values are renderer-independent; the EPUB writer maps them to XHTML/CSS.
 */
enum class TextAlignment {
    START,
    CENTER,
    END,
    JUSTIFY
}

data class ParagraphStyle(
    val alignment: TextAlignment = TextAlignment.START,
    /** First-line indentation in em units. Zero disables indentation. */
    val firstLineIndentEm: Float = 1.0f,
    /** Spacing after the paragraph in em units. */
    val spacingAfterEm: Float = 0.8f,
    val keepWithNext: Boolean = false
) {
    init {
        require(firstLineIndentEm >= 0f) { "First-line indentation cannot be negative" }
        require(spacingAfterEm >= 0f) { "Paragraph spacing cannot be negative" }
    }
}

/** A continuous text run with its own inline formatting. */
data class RichTextRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val language: String? = null
)

enum class TextBlockRole {
    BODY,
    TITLE,
    SUBTITLE,
    HEADING_1,
    HEADING_2,
    HEADING_3,
    QUOTE,
    CAPTION,
    FOOTNOTE
}
