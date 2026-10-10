package com.mimee.epubmaker.domain.model

import java.util.UUID

sealed interface CoverSource {
    /** Cover image is taken from a rendered PDF page (page index is zero-based). */
    data class PdfPage(
        val sourceUri: String,
        val pageIndex: Int
    ) : CoverSource {
        init {
            require(sourceUri.isNotBlank()) { "PDF source URI must not be blank" }
            require(pageIndex >= 0) { "PDF page index must be zero or greater" }
        }
    }

    /** Cover image is selected directly from an image file. */
    data class ImageFile(
        val sourceUri: String,
        val fileName: String,
        val mimeType: String
    ) : CoverSource {
        init {
            require(sourceUri.isNotBlank()) { "Cover image URI must not be blank" }
            require(fileName.isNotBlank()) { "Cover file name must not be blank" }
            require(mimeType.startsWith("image/")) { "Cover MIME type must start with image/" }
        }
    }

    /** Reuse an image asset that already exists among the book's content. */
    data class ExistingImage(val imageAssetId: String) : CoverSource {
        init {
            require(imageAssetId.isNotBlank()) { "Image asset ID must not be blank" }
        }
    }
}

data class CoverImage(
    val id: String = UUID.randomUUID().toString(),
    val source: CoverSource,
    val altText: String = "Book cover",
    val mimeType: String,
    val fileName: String = "cover",
    val widthPx: Int? = null,
    val heightPx: Int? = null
) {
    init {
        require(mimeType.startsWith("image/")) { "Cover MIME type must start with image/" }
        require(fileName.isNotBlank()) { "Cover file name must not be blank" }
        require(widthPx == null || widthPx > 0) { "Cover width must be positive" }
        require(heightPx == null || heightPx > 0) { "Cover height must be positive" }
    }
}
