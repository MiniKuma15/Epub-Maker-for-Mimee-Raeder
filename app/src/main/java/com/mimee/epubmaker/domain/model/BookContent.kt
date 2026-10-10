package com.mimee.epubmaker.domain.model

import java.util.UUID

/** Every item in a chapter has a stable ID so editor updates do not depend on list position. */
sealed interface BookContent {
    val id: String
    val order: Int
}

/** Text content retains rich inline runs and paragraph-level formatting. */
data class TextBlock(
    override val id: String = UUID.randomUUID().toString(),
    override val order: Int = 0,
    val runs: List<RichTextRun> = emptyList(),
    val paragraphStyle: ParagraphStyle = ParagraphStyle(),
    val role: TextBlockRole = TextBlockRole.BODY
) : BookContent {
    val plainText: String
        get() = runs.joinToString(separator = "") { it.text }

    companion object {
        fun paragraph(
            text: String,
            order: Int = 0,
            style: ParagraphStyle = ParagraphStyle(),
            role: TextBlockRole = TextBlockRole.BODY
        ): TextBlock = TextBlock(
            order = order,
            runs = listOf(RichTextRun(text = text)),
            paragraphStyle = style,
            role = role
        )
    }
}

/** The original image is referenced as a URI/path; bytes are copied into EPUB during export. */
data class ImageAsset(
    val sourceUri: String,
    val fileName: String,
    val mimeType: String,
    val altText: String = "",
    val caption: String = "",
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val embedInEpub: Boolean = true
) {
    init {
        require(sourceUri.isNotBlank()) { "Image source URI must not be blank" }
        require(fileName.isNotBlank()) { "Image file name must not be blank" }
        require(mimeType.startsWith("image/")) { "Image MIME type must start with image/" }
        require(widthPx == null || widthPx > 0) { "Image width must be positive" }
        require(heightPx == null || heightPx > 0) { "Image height must be positive" }
    }
}

enum class ImageHandling {
    /** Keep the page/image in the EPUB without running OCR on it. */
    EMBED_AS_IMAGE,
    /** Run OCR and keep the recognized text; the source image can still be retained. */
    OCR_TO_TEXT,
    /** Keep both the recognized text and the original image. */
    KEEP_IMAGE_AND_OCR
}

data class ImageBlock(
    override val id: String = UUID.randomUUID().toString(),
    override val order: Int = 0,
    val image: ImageAsset,
    val handling: ImageHandling = ImageHandling.EMBED_AS_IMAGE
) : BookContent

data class Chapter(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val order: Int = 0,
    val contents: List<BookContent> = emptyList()
) {
    /** Stable ordering for content assembled from PDF pages or editor actions. */
    fun orderedContents(): List<BookContent> = contents.sortedBy { it.order }
}
