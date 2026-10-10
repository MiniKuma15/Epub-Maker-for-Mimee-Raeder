package com.mimee.epubmaker

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val Sky = Color(0xFFEAF5FF)
private val Blue = Color(0xFF3977B8)
private val Ink = Color(0xFF243447)
private val Muted = Color(0xFF6D7D8D)
private val Paper = Color(0xFFFFFFFF)
private val Line = Color(0xFFD7E8F7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(234, 245, 255)
        window.navigationBarColor = android.graphics.Color.rgb(234, 245, 255)
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Blue,
                    onPrimary = Color.White,
                    background = Sky,
                    surface = Paper,
                    onSurface = Ink
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Sky) {
                    EpubMakerScreen(
                        context = this@MainActivity,
                        onOcrPdf = { uri, engine, serverUrl, progress, completed, failed ->
                            lifecycleScope.launch {
                                try {
                                    val text = withContext(Dispatchers.IO) {
                                        PdfOcrEngine.recognize(
                                            context = this@MainActivity,
                                            uri = uri,
                                            engine = engine,
                                            serverUrl = serverUrl,
                                            onProgress = { current, total ->
                                                runOnUiThread { progress(current, total) }
                                            }
                                        )
                                    }
                                    completed(text)
                                } catch (e: Exception) {
                                    failed(e.localizedMessage ?: "ไม่สามารถอ่านข้อความจาก PDF ได้")
                                }
                            }
                        },
                        onExport = { title, author, text, uri ->
                            try {
                                contentResolver.openOutputStream(uri)?.use { output ->
                                    EpubWriter.write(output, title, author, text)
                                } ?: error("ไม่สามารถเขียนไฟล์ได้")
                                Toast.makeText(this, "ส่งออก EPUB สำเร็จ", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    this,
                                    "ส่งออกไม่สำเร็จ: ${e.localizedMessage ?: "เกิดข้อผิดพลาด"}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpubMakerScreen(
    context: Context,
    onOcrPdf: (Uri, String, String, (Int, Int) -> Unit, (String) -> Unit, (String) -> Unit) -> Unit,
    onExport: (String, String, String, Uri) -> Unit
) {
    val draftPrefs = remember(context) { context.getSharedPreferences("epub_maker_draft", Context.MODE_PRIVATE) }
    var title by remember { mutableStateOf(draftPrefs.getString("title", "") ?: "") }
    var ocrEngine by remember { mutableStateOf("tesseract") }
    var ocrServerUrl by remember { mutableStateOf("https://your-ocr-server.example.com") }
    var author by remember { mutableStateOf(draftPrefs.getString("author", "") ?: "") }
    var manuscript by remember { mutableStateOf(draftPrefs.getString("manuscript", "") ?: "") }
    var showHelp by remember { mutableStateOf(true) }
    var ocrRunning by remember { mutableStateOf(false) }
    var ocrStatus by remember { mutableStateOf("เลือก PDF เพื่อเริ่มอ่านข้อความ") }
    var ocrProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(title, author, manuscript) {
        delay(400)
        draftPrefs.edit()
            .putString("title", title)
            .putString("author", author)
            .putString("manuscript", manuscript)
            .apply()
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            ocrRunning = true
            ocrProgress = 0f
            ocrStatus = "กำลังเตรียม OCR ภาษาไทยและอังกฤษ…"
            onOcrPdf(
                uri,
                ocrEngine,
                ocrServerUrl,
                { current, total ->
                    ocrProgress = if (total > 0) current.toFloat() / total else 0f
                    ocrStatus = "กำลังอ่านข้อความ หน้า $current จาก $total"
                },
                { extracted ->
                    manuscript = if (manuscript.isBlank()) extracted else manuscript.trimEnd() + "\n\n" + extracted
                    if (title.isBlank()) title = "หนังสือที่นำเข้า"
                    ocrRunning = false
                    ocrProgress = 1f
                    ocrStatus = "อ่านข้อความเสร็จแล้ว ตรวจแก้ข้อความก่อนส่งออกได้"
                },
                { error ->
                    ocrRunning = false
                    ocrStatus = "OCR ไม่สำเร็จ: $error"
                }
            )
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/epub+zip")
    ) { uri ->
        if (uri != null) onExport(title, author, manuscript, uri)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(54.dp).background(Color.White, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    Icons.Default.AutoStories,
                    contentDescription = null,
                    tint = Blue,
                    modifier = Modifier.size(30.dp)
                )
            }
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text("Epub Maker", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("เปลี่ยน PDF ให้เป็นหนังสือ EPUB", color = Muted, fontSize = 13.sp)
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Line)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(Icons.Default.FileOpen, null, tint = Blue)
                    Text("  นำเข้า PDF และอ่านข้อความ", fontWeight = FontWeight.SemiBold, color = Ink)
                }
                Text(
                    "เลือก OCR แบบออฟไลน์ โมเดลบนเซิร์ฟเวอร์ หรือบริการออนไลน์ของ Google",
                    color = Muted,
                    fontSize = 13.sp
                )
                Text("เลือก OCR Engine", color = Ink, fontWeight = FontWeight.Medium)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "tesseract" to "Tesseract (ออฟไลน์)",
                        "paddleocr" to "PaddleOCR",
                        "easyocr" to "EasyOCR",
                        "paddleocr-vl" to "PaddleOCR-VL (เอกสารซับซ้อน)",
                        "google-vision" to "Google Cloud Vision (ออนไลน์)",
                        "gemini" to "Google Gemini Vision (ออนไลน์)",
                        "google-document-ai" to "Google Document AI (ออนไลน์)"
                    ).forEach { (id, label) ->
                        OutlinedButton(
                            onClick = { ocrEngine = id },
                            enabled = !ocrRunning,
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, if (ocrEngine == id) Blue else Line),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (ocrEngine == id) Blue else Ink
                            )
                        ) {
                            Text(if (ocrEngine == id) "✓  $label" else label)
                        }
                    }
                }
                if (ocrEngine != "tesseract") {
                    OutlinedTextField(
                        value = ocrServerUrl,
                        onValueChange = { ocrServerUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("OCR Server URL") },
                        placeholder = { Text("https://your-server.example.com") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    Text(
                        "โมเดลออนไลน์ต้องติดตั้งและเปิด Backend ก่อน ใช้ HTTPS ในระบบจริง",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
                Button(
                    onClick = { pdfLauncher.launch(arrayOf("application/pdf")) },
                    enabled = !ocrRunning,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    androidx.compose.material3.Icon(Icons.Default.FileOpen, null)
                    Text("  เลือก PDF แล้วเริ่ม OCR")
                }
                if (ocrRunning) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(progress = ocrProgress, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                        Text(ocrStatus, color = Blue, fontSize = 13.sp)
                    }
                } else {
                    Text(ocrStatus, color = Muted, fontSize = 13.sp)
                }
                Text(
                    if (ocrEngine == "tesseract") "หมายเหตุ: ครั้งแรกต้องดาวน์โหลดข้อมูลภาษาไทยและอังกฤษ หลังจากนั้น Tesseract ทำงานบนเครื่องได้" else "หมายเหตุ: PDF แต่ละหน้าจะถูกส่งไปยัง OCR Server ที่คุณกำหนด บริการ Google อาจมีค่าใช้จ่าย",
                    color = Muted,
                    fontSize = 12.sp
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Line)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(Icons.Default.Book, null, tint = Blue)
                    Text("  รายละเอียดหนังสือ", fontWeight = FontWeight.SemiBold, color = Ink)
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("ชื่อหนังสือ") },
                    placeholder = { Text("เช่น หนังสือเล่มโปรด") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("ผู้แต่ง") },
                    placeholder = { Text("ชื่อผู้แต่ง (ถ้ามี)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Line)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(Icons.Default.TextFields, null, tint = Blue)
                    Text("  ต้นฉบับข้อความ", fontWeight = FontWeight.SemiBold, color = Ink)
                }
                Text("ตรวจแก้ข้อความ OCR ได้ก่อนส่งออก หากขึ้นต้นบรรทัดด้วย # ชื่อบท ระบบจะแยกเป็นบทและสร้างสารบัญให้", color = Muted, fontSize = 13.sp)
                OutlinedTextField(
                    value = manuscript,
                    onValueChange = { manuscript = it },
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                    label = { Text("ข้อความในหนังสือ") },
                    placeholder = { Text("ข้อความจาก PDF จะปรากฏที่นี่…") },
                    shape = RoundedCornerShape(14.dp)
                )
                Text("${manuscript.trim().length} ตัวอักษร", color = Muted, fontSize = 12.sp)
            }
        }

        if (showHelp) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFDCEEFF)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    androidx.compose.material3.Icon(Icons.Default.CheckCircle, null, tint = Blue)
                    Column(Modifier.padding(start = 10.dp)) {
                        Text("OCR ทำงานบนเครื่อง", fontWeight = FontWeight.SemiBold, color = Ink)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (ocrEngine == "tesseract") "Tesseract ทำงานบนเครื่อง ไม่ส่งหน้า PDF ไปยังเซิร์ฟเวอร์ OCR ภายนอก" else "โหมดออนไลน์ส่งภาพหน้าหนังสือไปยัง OCR Server ที่ตั้งค่าไว้ โดย Google engines จะส่งต่อภาพไปยัง Google API",
                            color = Ink,
                            fontSize = 13.sp
                        )
                        OutlinedButton(
                            onClick = { showHelp = false },
                            modifier = Modifier.padding(top = 8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue)
                        ) { Text("เข้าใจแล้ว") }
                    }
                }
            }
        }

        Button(
            onClick = {
                saveLauncher.launch(
                    (title.trim().ifBlank { "my-book" })
                        .replace(Regex("[^A-Za-z0-9ก-๙._ -]"), "_")
                        .take(80) + ".epub"
                )
            },
            enabled = title.isNotBlank() && manuscript.isNotBlank() && !ocrRunning,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            androidx.compose.material3.Icon(Icons.Default.FileUpload, null)
            Text("  ส่งออกเป็นไฟล์ EPUB", fontWeight = FontWeight.SemiBold)
        }
        Text(
            "ไฟล์ที่สร้างเป็น EPUB3 และบันทึกไปยังตำแหน่งที่คุณเลือก",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            color = Muted,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))
    }
}

private object EpubWriter {
    private data class Chapter(val title: String, val body: String)

    fun write(output: OutputStream, title: String, author: String, manuscript: String) {
        require(manuscript.isNotBlank()) { "ไม่มีข้อความสำหรับส่งออก" }
        val safeTitle = xml(title.trim().ifBlank { "Untitled" })
        val safeAuthor = xml(author.trim().ifBlank { "ไม่ระบุผู้แต่ง" })
        val chapters = parseChapters(manuscript)
        val bookId = "urn:uuid:${UUID.randomUUID()}"
        val modified = java.time.Instant.now().toString()

        val manifest = StringBuilder()
        val spine = StringBuilder()
        val toc = StringBuilder()
        chapters.forEachIndexed { index, chapter ->
            val id = "chapter${index + 1}"
            val file = "text/$id.xhtml"
            manifest.append("<item id=\"$id\" href=\"$file\" media-type=\"application/xhtml+xml\"/>\n")
            spine.append("<itemref idref=\"$id\"/>")
            toc.append("<li><a href=\"$file\">${xml(chapter.title)}</a></li>")
        }
        val opf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id" xml:lang="th" prefix="dcterms: http://purl.org/dc/terms/">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:identifier id="book-id">$bookId</dc:identifier><dc:title>$safeTitle</dc:title><dc:creator>$safeAuthor</dc:creator>
<dc:language>th</dc:language><meta property="dcterms:modified">$modified</meta>
</metadata>
<manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
$manifest<item id="css" href="styles/book.css" media-type="text/css"/></manifest>
<spine>$spine</spine></package>"""

        val nav = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="th" lang="th">
<head><title>สารบัญ</title><meta charset="UTF-8"/></head>
<body><nav epub:type="toc" id="toc"><h1>สารบัญ</h1><ol>$toc</ol></nav></body></html>"""
        val css = """body { font-family: serif; line-height: 1.7; margin: 5%; } h1 { text-align: center; margin: 1em 0 2em; font-size: 1.5em; } p { text-indent: 1em; margin: 0 0 0.8em; }"""

        ZipOutputStream(output).use { zip ->
            val mime = "application/epub+zip".toByteArray(Charsets.US_ASCII)
            val mimeEntry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mime.size.toLong()
                compressedSize = mime.size.toLong()
                crc = java.util.zip.CRC32().apply { update(mime) }.value
            }
            zip.putNextEntry(mimeEntry)
            zip.write(mime)
            zip.closeEntry()
            add(zip, "META-INF/container.xml", """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            add(zip, "OEBPS/content.opf", opf)
            add(zip, "OEBPS/nav.xhtml", nav)
            chapters.forEachIndexed { index, chapter ->
                val paragraphs = chapter.body.split(Regex("\\r?\\n\\s*\\r?\\n"))
                    .map { it.trim() }.filter { it.isNotEmpty() }
                    .joinToString("\n") { "<p>" + xml(it).replace("\n", "<br/>") + "</p>" }
                val xhtml = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="th" lang="th">
<head><title>${xml(chapter.title)}</title><meta charset="UTF-8"/><link rel="stylesheet" type="text/css" href="../styles/book.css"/></head>
<body><h1>${xml(chapter.title)}</h1>$paragraphs</body></html>"""
                add(zip, "OEBPS/text/chapter${index + 1}.xhtml", xhtml)
            }
            add(zip, "OEBPS/styles/book.css", css)
        }
    }

    private fun parseChapters(text: String): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        var currentTitle = "เนื้อหา"
        val body = StringBuilder()
        fun flush() {
            val content = body.toString().trim()
            if (content.isNotEmpty()) chapters.add(Chapter(currentTitle, content))
            body.clear()
        }
        text.replace("\r\n", "\n").lines().forEach { line ->
            val heading = line.trim().takeIf { it.startsWith("# ") }?.removePrefix("# ")?.trim()
            if (!heading.isNullOrBlank()) {
                flush()
                currentTitle = heading
            } else {
                body.append(line).append('\n')
            }
        }
        flush()
        return chapters.ifEmpty { listOf(Chapter("เนื้อหา", text.trim())) }
    }

    private fun add(zip: ZipOutputStream, path: String, text: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun xml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
