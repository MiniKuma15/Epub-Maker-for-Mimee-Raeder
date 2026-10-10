package com.mimee.epubmaker

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
    onExport: (String, String, String, Uri) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var manuscript by remember { mutableStateOf("") }
    var showHelp by remember { mutableStateOf(true) }

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
                Text("เปลี่ยนต้นฉบับให้เป็นหนังสือของคุณ", color = Muted, fontSize = 13.sp)
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line)
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
            border = androidx.compose.foundation.BorderStroke(1.dp, Line)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(Icons.Default.TextFields, null, tint = Blue)
                    Text("  ต้นฉบับข้อความ", fontWeight = FontWeight.SemiBold, color = Ink)
                }
                Text(
                    "วางข้อความที่ได้จาก OCR ลงในช่องนี้ แล้วตรวจแก้ก่อนส่งออก",
                    color = Muted,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = manuscript,
                    onValueChange = { manuscript = it },
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    label = { Text("ข้อความในหนังสือ") },
                    placeholder = { Text("วางหรือพิมพ์ข้อความที่นี่…\n\nเว้นบรรทัดระหว่างย่อหน้า") },
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
                        Text("ขั้นแรกพร้อมใช้งาน", fontWeight = FontWeight.SemiBold, color = Ink)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "ตอนนี้ส่งออก EPUB3 จากข้อความที่แก้ไขแล้วได้ ส่วนการนำเข้า PDF และ OCR ภาษาไทย/อังกฤษจะพัฒนาเป็นขั้นถัดไป",
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
                        .replace(Regex("[\\/:*?\"<>|]"), "_")
                        .take(80) + ".epub"
                )
            },
            enabled = title.isNotBlank() && manuscript.isNotBlank(),
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
    fun write(output: OutputStream, title: String, author: String, manuscript: String) {
        val safeTitle = xml(title.trim())
        val safeAuthor = xml(author.trim().ifBlank { "ไม่ระบุผู้แต่ง" })
        val paragraphs = manuscript.trim()
            .split(Regex("\\r?\\n\\s*\\r?\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n") { "<p>${xml(it).replace("\n", "<br/>")}</p>" }

        val bookId = "urn:uuid:${UUID.randomUUID()}"
        val chapter = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="th" lang="th">
<head><title>$safeTitle</title><meta charset="UTF-8"/><link rel="stylesheet" type="text/css" href="../styles/book.css"/></head>
<body><h1>$safeTitle</h1>$paragraphs</body>
</html>"""

        val opf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id" xml:lang="th" prefix="dcterms: http://purl.org/dc/terms/">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:identifier id="book-id">$bookId</dc:identifier>
<dc:title>$safeTitle</dc:title><dc:creator>$safeAuthor</dc:creator>
<dc:language>th</dc:language><meta property="dcterms:modified">${java.time.Instant.now().toString().substring(0, 19)}Z</meta>
</metadata>
<manifest>
<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
<item id="chapter" href="text/chapter.xhtml" media-type="application/xhtml+xml"/>
<item id="css" href="styles/book.css" media-type="text/css"/>
</manifest>
<spine><itemref idref="chapter"/></spine>
</package>"""

        val nav = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="th" lang="th">
<head><title>สารบัญ</title><meta charset="UTF-8"/></head>
<body><nav epub:type="toc" id="toc"><h1>สารบัญ</h1><ol><li><a href="text/chapter.xhtml">$safeTitle</a></li></ol></nav></body>
</html>"""

        val css = """body { font-family: serif; line-height: 1.7; margin: 5%; } h1 { text-align: center; margin: 1em 0 2em; font-size: 1.5em; } p { text-indent: 1em; margin: 0 0 0.8em; white-space: normal; }"""

        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype").apply { method = ZipEntry.STORED; size = 20; compressedSize = 20; crc = 0x2cab616fL })
            zip.write("application/epub+zip".toByteArray(Charsets.US_ASCII))
            zip.closeEntry()
            add(zip, "META-INF/container.xml", """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            add(zip, "OEBPS/content.opf", opf)
            add(zip, "OEBPS/nav.xhtml", nav)
            add(zip, "OEBPS/text/chapter.xhtml", chapter)
            add(zip, "OEBPS/styles/book.css", css)
        }
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
