package com.mimee.epubmaker.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookModelTest {
    @Test
    fun bookRetainsRichTextAndImageContentTogether() {
        val text = TextBlock(
            order = 0,
            runs = listOf(
                RichTextRun("Bold text", bold = true),
                RichTextRun(" and italic", italic = true)
            ),
            paragraphStyle = ParagraphStyle(
                alignment = TextAlignment.JUSTIFY,
                firstLineIndentEm = 1.25f,
                spacingAfterEm = 0.5f
            )
        )
        val image = ImageBlock(
            order = 1,
            image = ImageAsset(
                sourceUri = "file:///tmp/page.jpg",
                fileName = "page.jpg",
                mimeType = "image/jpeg",
                altText = "A scanned page",
                caption = "Page caption"
            ),
            handling = ImageHandling.EMBED_AS_IMAGE
        )
        val book = Book(
            metadata = BookMetadata(title = "Test book", authors = listOf("Author")),
            chapters = listOf(Chapter(title = "Chapter 1", contents = listOf(text, image)))
        )

        assertEquals("Test book", book.metadata.title)
        assertEquals("Bold text and italic", book.allTextBlocks().single().plainText)
        assertTrue(book.allTextBlocks().single().runs.first().bold)
        assertEquals(TextAlignment.JUSTIFY, book.allTextBlocks().single().paragraphStyle.alignment)
        assertEquals("A scanned page", book.allImages().single().image.altText)
        assertEquals("Page caption", book.allImages().single().image.caption)
        assertEquals(ImageHandling.EMBED_AS_IMAGE, book.allImages().single().handling)
        assertEquals(listOf(0, 1), book.orderedChapters().single().orderedContents().map { it.order })
    }

    @Test
    fun bookCanKeepImageOnlyChapterWithoutPlainTextConversion() {
        val image = ImageBlock(
            order = 0,
            image = ImageAsset(
                sourceUri = "content://provider/book-cover",
                fileName = "cover.png",
                mimeType = "image/png",
                altText = "Cover"
            )
        )
        val book = Book(chapters = listOf(Chapter(title = "Illustrations", contents = listOf(image))))

        assertTrue(book.allTextBlocks().isEmpty())
        assertEquals(1, book.allImages().size)
    }
}
