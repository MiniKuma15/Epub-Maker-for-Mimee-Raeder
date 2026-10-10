package com.mimee.epubmaker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Page-by-page PDF OCR; language data is cached in app-private storage. */
internal object PdfOcrEngine {
    private const val DATA_DIR = "tesseract"
    private const val LANG_DIR = "tessdata"
    private const val BASE_URL = "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/"

    fun recognize(context: Context, uri: Uri, onProgress: (Int, Int) -> Unit): String {
        val dataRoot = File(context.filesDir, DATA_DIR)
        val languageDir = File(dataRoot, LANG_DIR)
        if (!languageDir.exists() && !languageDir.mkdirs()) {
            error("สร้างโฟลเดอร์ข้อมูล OCR ไม่สำเร็จ")
        }
        ensureLanguageFile(languageDir, "tha")
        ensureLanguageFile(languageDir, "eng")

        val api = TessBaseAPI()
        if (!api.init(dataRoot.absolutePath, "tha+eng")) {
            api.end()
            error("เริ่มต้น OCR ไม่สำเร็จ ตรวจสอบไฟล์ข้อมูลภาษา")
        }

        try {
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: error("เปิดไฟล์ PDF ไม่ได้")
            descriptor.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount == 0) error("PDF ไม่มีหน้า")
                    val output = StringBuilder()
                    for (index in 0 until renderer.pageCount) {
                        onProgress(index + 1, renderer.pageCount)
                        renderer.openPage(index).use { page ->
                            val width = (page.width * 2).coerceAtMost(2400)
                            val height = (page.height * 2).coerceAtMost(3200)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(Color.WHITE)
                            val matrix = Matrix().apply {
                                setScale(width.toFloat() / page.width, height.toFloat() / page.height)
                            }
                            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            try {
                                api.setImage(bitmap)
                                val recognized = api.utF8Text ?: ""
                                if (recognized.isNotBlank()) {
                                    if (output.isNotEmpty()) output.append("\n\n")
                                    output.append("— หน้า \${index + 1} —\n")
                                    output.append(recognized.trim())
                                }
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                    if (output.isBlank()) error("ไม่พบข้อความใน PDF นี้ อาจเป็นไฟล์ว่างหรือภาพที่อ่านไม่ชัด")
                    return output.toString()
                }
            }
        } finally {
            api.end()
        }
    }

    private fun ensureLanguageFile(languageDir: File, language: String) {
        val target = File(languageDir, "$language.traineddata")
        if (target.exists() && target.length() > 100_000L) return
        val temporary = File(languageDir, "$language.traineddata.part")
        val connection = (URL(BASE_URL + "$language.traineddata").openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            requestMethod = "GET"
        }
        try {
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("ดาวน์โหลดข้อมูลภาษา $language ไม่สำเร็จ (HTTP \${connection.responseCode})")
            }
            connection.inputStream.use { input ->
                FileOutputStream(temporary).use { output -> input.copyTo(output) }
            }
            if (temporary.length() < 100_000L) error("ไฟล์ข้อมูลภาษา $language ไม่สมบูรณ์")
            if (target.exists()) target.delete()
            if (!temporary.renameTo(target)) error("บันทึกข้อมูลภาษา $language ไม่สำเร็จ")
        } finally {
            connection.disconnect()
            if (temporary.exists()) temporary.delete()
        }
    }
}
