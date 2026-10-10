package com.mimee.epubmaker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.googlecode.tesseract.android.TessBaseAPI
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** PDF page renderer with on-device Tesseract and selectable remote OCR engines. */
internal object PdfOcrEngine {
    private const val DATA_DIR = "tesseract"
    private const val LANG_DIR = "tessdata"
    private const val BASE_URL = "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/"

    fun recognize(
        context: Context,
        uri: Uri,
        engine: String,
        serverUrl: String,
        serverToken: String,
        onProgress: (Int, Int) -> Unit
    ): String {
        if (engine !in setOf("tesseract", "paddleocr", "easyocr", "paddleocr-vl", "google-vision", "gemini", "google-document-ai")) {
            error("ไม่รู้จัก OCR Engine: $engine")
        }
        if (engine != "tesseract" &&
            (serverUrl.isBlank() || serverUrl.contains("your-ocr-server.example.com"))
        ) {
            error("กรุณาตั้งค่า OCR Server URL ที่ใช้งานได้ก่อน")
        }

        val api = if (engine == "tesseract") {
            onProgress(0, 0) // Show setup status while language data is checked/downloaded.
            createTesseract(context, onProgress)
        } else null
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
                                val recognized = if (api != null) {
                                    api.setImage(bitmap)
                                    api.getUTF8Text() ?: ""
                                } else {
                                    recognizeRemote(bitmap, engine, serverUrl, serverToken)
                                }
                                if (recognized.isNotBlank()) {
                                    if (output.isNotEmpty()) output.append("\n\n")
                                    output.append("— หน้า ${index + 1} —\n")
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
            api?.end()
        }
    }

    private fun createTesseract(context: Context, onProgress: (Int, Int) -> Unit): TessBaseAPI {
        val dataRoot = File(context.filesDir, DATA_DIR)
        val languageDir = File(dataRoot, LANG_DIR)
        if (!languageDir.exists() && !languageDir.mkdirs()) {
            error("สร้างโฟลเดอร์ข้อมูล OCR ไม่สำเร็จ")
        }
        onProgress(-1, 0)
        ensureLanguageFile(languageDir, "tha")
        onProgress(-2, 0)
        ensureLanguageFile(languageDir, "eng")
        onProgress(0, -1)
        val api = TessBaseAPI()
        if (!api.init(dataRoot.absolutePath, "tha+eng")) {
            api.end()
            error("เริ่มต้น OCR ไม่สำเร็จ ตรวจสอบไฟล์ข้อมูลภาษา")
        }
        return api
    }

    private fun recognizeRemote(bitmap: Bitmap, engine: String, serverUrl: String, serverToken: String): String {
        val boundary = "----EpubMakerOCR${System.currentTimeMillis()}"
        val endpoint = serverUrl.trim().trimEnd('/') + "/ocr?engine=" +
            URLEncoder.encode(engine, "UTF-8")
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 180_000
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
            if (serverToken.isNotBlank()) setRequestProperty("Authorization", "Bearer ${serverToken.trim()}")
        }
        try {
            val imageBytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                out.toByteArray()
            }
            connection.outputStream.use { output ->
                output.write("--$boundary\r\n".toByteArray())
                output.write("Content-Disposition: form-data; name=\"file\"; filename=\"page.jpg\"\r\n".toByteArray())
                output.write("Content-Type: image/jpeg\r\n\r\n".toByteArray())
                output.write(imageBytes)
                output.write("\r\n--$boundary--\r\n".toByteArray())
            }
            val status = connection.responseCode
            val responseStream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = responseStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = try { JSONObject(response).optString("detail", response) } catch (_: Exception) { response }
                error("OCR Server ตอบกลับ HTTP $status: $detail")
            }
            return JSONObject(response).optString("text", "")
        } finally {
            connection.disconnect()
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
                error("ดาวน์โหลดข้อมูลภาษา $language ไม่สำเร็จ (HTTP ${connection.responseCode})")
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
