package com.mimee.epubmaker

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mimee.epubmaker.domain.model.ParagraphStyle
import com.mimee.epubmaker.domain.model.RichTextRun
import com.mimee.epubmaker.domain.model.TextAlignment
import com.mimee.epubmaker.feature.editor.EpubMakerUiState
import com.mimee.epubmaker.feature.editor.EpubMakerViewModel

private val Sky = Color(0xFFE3F2FD)
private val Blue = Color(0xFF1976D2)
private val PaleBlue = Color(0xFFBBDEFB)
private val Ink = Color(0xFF203247)
private val Muted = Color(0xFF63788B)
private val Line = Color(0xFFD2E5F5)
private val Paper = Color.White

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(227, 242, 253)
        window.navigationBarColor = android.graphics.Color.rgb(227, 242, 253)
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
                val vm: EpubMakerViewModel = viewModel()
                EpubMakerScreen(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpubMakerScreen(vm: EpubMakerViewModel) {
    val state by vm.state.collectAsState()
    var editorValue by remember { mutableStateOf(TextFieldValue("")) }
    var alignment by remember { mutableStateOf(TextAlignment.START) }
    var indent by remember { mutableFloatStateOf(1f) }
    var spacing by remember { mutableFloatStateOf(0.8f) }
    var showPreview by remember { mutableStateOf(false) }
    var showEngineMenu by remember { mutableStateOf(false) }
    var altText by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }

    val pickSource = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val mime = runCatching { vm.javaClass }.getOrNull()
            val resolver = androidx.compose.ui.platform.LocalContext.current.contentResolver
            val type = resolver.getType(uri) ?: when (uri.toString().lowercase()) {
                else -> if (uri.toString().lowercase().endsWith(".pdf")) "application/pdf" else "image/jpeg"
            }
            val name = queryDisplayName(resolver, uri)
            vm.selectSource(uri, type, name)
        }
    }
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val type = context.contentResolver.getType(uri) ?: "image/jpeg"
            vm.setCoverImage(uri, type, queryDisplayName(context.contentResolver, uri))
        }
    }
    val saveEpub = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/epub+zip")) { uri ->
        if (uri != null) {
            val runs = richRuns(editorValue.annotatedString)
            vm.exportTo(uri, editorValue.text, runs, ParagraphStyle(alignment, indent, spacing))
        }
    }

    LaunchedEffect(state.hasConverted, state.selectedUri) {
        if (state.hasConverted) {
            val text = state.book.allTextBlocks().joinToString("\n\n") { it.plainText }
            editorValue = TextFieldValue(AnnotatedString(text), selection = androidx.compose.ui.text.TextRange(text.length))
            alignment = state.book.allTextBlocks().firstOrNull()?.paragraphStyle?.alignment ?: TextAlignment.START
            indent = state.book.allTextBlocks().firstOrNull()?.paragraphStyle?.firstLineIndentEm ?: 1f
            spacing = state.book.allTextBlocks().firstOrNull()?.paragraphStyle?.spacingAfterEm ?: 0.8f
        }
    }

    if (state.error != null) {
        AlertDialog(
            onDismissRequest = vm::dismissError,
            title = { Text("เกิดข้อผิดพลาด") },
            text = { Text(state.error.orEmpty()) },
            confirmButton = { TextButton(onClick = vm::dismissError) { Text("ตกลง") } }
        )
    }

    Surface(Modifier.fillMaxSize(), color = Sky) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Line)
            ) {
                Row(
                    Modifier.fillMaxWidth().background(Color.White).padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    BearReadingMascot(Modifier.size(96.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("EPUB Maker", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text("แปลง PDF และรูปภาพเป็นหนังสือ EPUB", color = Muted, fontSize = 13.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Icon(Icons.Default.AutoStories, null, tint = Blue, modifier = Modifier.size(18.dp))
                            Text("  ข้อความ • รูปภาพ • หน้าปก", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            SectionCard("1. นำเข้าไฟล์", Icons.Default.FileOpen) {
                Text("เลือก PDF, JPG หรือ PNG ก่อน ระบบจะยังไม่ OCR หรือแปลงจนกว่าจะกดปุ่มเริ่มแปลง", color = Muted, fontSize = 13.sp)
                Button(
                    onClick = { pickSource.launch(arrayOf("application/pdf", "image/jpeg", "image/png")) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    androidx.compose.material3.Icon(Icons.Default.FileOpen, null)
                    Text("  เลือก PDF / JPG / PNG")
                }
                if (state.selectedUri != null) {
                    Text(state.selectedFileName, fontWeight = FontWeight.SemiBold, color = Ink)
                    Text(state.selectedMimeType, color = Muted, fontSize = 12.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { vm.setPdfFirstPageAsCover() },
                        modifier = Modifier.weight(1f),
                        enabled = state.selectedUri != null && (state.selectedMimeType == "application/pdf" || state.selectedFileName.endsWith(".pdf", true))
                    ) { Text("ใช้หน้าแรก PDF เป็นปก") }
                    OutlinedButton(
                        onClick = { pickCover.launch(arrayOf("image/jpeg", "image/png")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        androidx.compose.material3.Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(17.dp))
                        Text(" เลือกรูปปก")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(
                        checked = state.keepScannedPagesAsImages,
                        onCheckedChange = vm::setKeepScannedPagesAsImages
                    )
                    Column(Modifier.weight(1f)) {
                        Text("เก็บหน้าสแกนเป็นรูปภาพ", fontWeight = FontWeight.Medium, color = Ink)
                        Text("หน้าที่ไม่มีข้อความจะฝังเป็นภาพใน EPUB และไม่ OCR", color = Muted, fontSize = 12.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(
                        checked = state.runOcrForImages,
                        onCheckedChange = vm::setRunOcrForImages
                    )
                    Column(Modifier.weight(1f)) {
                        Text("OCR รูปภาพ JPG/PNG", fontWeight = FontWeight.Medium, color = Ink)
                        Text("ปิดตัวเลือกนี้หากต้องการเก็บรูปภาพอย่างเดียว", color = Muted, fontSize = 12.sp)
                    }
                }
            }

            SectionCard("2. ตั้งค่า OCR", Icons.Default.CenterFocusStrong) {
                OutlinedButton(onClick = { showEngineMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("โมเดล: " + engineLabel(state.ocrEngine))
                }
                DropdownMenu(expanded = showEngineMenu, onDismissRequest = { showEngineMenu = false }) {
                    listOf(
                        "tesseract" to "Tesseract — Offline ไทย + อังกฤษ",
                        "mlkit-latin" to "Google ML Kit — Offline อังกฤษ/Latin",
                        "paddleocr" to "PaddleOCR — Server",
                        "easyocr" to "EasyOCR — Server",
                        "paddleocr-vl" to "PaddleOCR-VL — Server",
                        "google-vision" to "Google Cloud Vision — Online API",
                        "gemini" to "Gemini Vision — Online API",
                        "google-document-ai" to "Google Document AI — Online API"
                    ).forEach { (id, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { vm.setOcrEngine(id); showEngineMenu = false })
                    }
                }
                if (state.ocrEngine == "mlkit-latin") {
                    Text("ข้อจำกัด: ML Kit Text Recognition รุ่น Latin ไม่รองรับภาษาไทยโดยตรง หากต้องการ OCR ภาษาไทยแบบออฟไลน์ให้เลือก Tesseract", color = Muted, fontSize = 12.sp)
                }
                if (state.ocrEngine != "tesseract" && state.ocrEngine != "mlkit-latin") {
                    OutlinedTextField(
                        value = state.serverUrl,
                        onValueChange = vm::setServerUrl,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("OCR Server URL") },
                        placeholder = { Text("https://your-server.example.com") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = state.serverToken,
                        onValueChange = vm::setServerToken,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Server token (ถ้ามี)") },
                        singleLine = true
                    )
                    Text("บริการออนไลน์ต้องมี backend และตั้งค่า API credentials ที่เซิร์ฟเวอร์ ห้ามฝังคีย์ Google ใน APK", color = Muted, fontSize = 12.sp)
                }
                Button(
                    onClick = vm::startConversion,
                    enabled = state.selectedUri != null && !state.isProcessing && !state.isExporting,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (state.isProcessing) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(10.dp))
                        Text("กำลังแปลง…")
                    } else {
                        androidx.compose.material3.Icon(Icons.Default.AutoStories, null)
                        Text("  เริ่มแปลง (Start Conversion)", fontWeight = FontWeight.SemiBold)
                    }
                }
                if (state.isProcessing) LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                Text(state.status, color = Muted, fontSize = 13.sp)
            }

            SectionCard("3. รายละเอียดหนังสือ", Icons.Default.MenuBook) {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = vm::updateTitle,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("ชื่อหนังสือ") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = state.author,
                    onValueChange = vm::updateAuthor,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("ชื่อผู้แต่ง") },
                    singleLine = true
                )
                Text("รูปแบบไฟล์ปลายทาง: EPUB 3 เท่านั้น", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }

            SectionCard("4. แก้ไขข้อความ (Rich Text Editor)", Icons.Default.TextFields) {
                Text("เลือกข้อความในช่องแก้ไขแล้วใช้ปุ่มตัวหนา/ตัวเอียง หรือเลือกการจัดแนวและการเยื้องบรรทัดแรก", color = Muted, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                    EditorTool(Icons.Default.FormatBold, "ตัวหนา", Modifier.weight(1f)) {
                        editorValue = applyStyle(editorValue, SpanStyle(fontWeight = FontWeight.Bold))
                    }
                    EditorTool(Icons.Default.FormatItalic, "ตัวเอียง", Modifier.weight(1f)) {
                        editorValue = applyStyle(editorValue, SpanStyle(fontStyle = FontStyle.Italic))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                    AlignmentTool(Icons.Default.FormatAlignLeft, alignment == TextAlignment.START, Modifier.weight(1f)) { alignment = TextAlignment.START }
                    AlignmentTool(Icons.Default.FormatAlignCenter, alignment == TextAlignment.CENTER, Modifier.weight(1f)) { alignment = TextAlignment.CENTER }
                    AlignmentTool(Icons.Default.FormatAlignRight, alignment == TextAlignment.END, Modifier.weight(1f)) { alignment = TextAlignment.END }
                    AlignmentTool(Icons.Default.FormatAlignJustify, alignment == TextAlignment.JUSTIFY, Modifier.weight(1f)) { alignment = TextAlignment.JUSTIFY }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("เยื้องบรรทัดแรก", color = Ink, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.weight(1f))
                        Text("${"%.2f".format(indent)} em", color = Blue, fontSize = 12.sp)
                    }
                    Slider(value = indent, onValueChange = { indent = it }, valueRange = 0f..2f, steps = 7)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ระยะห่างหลังย่อหน้า", color = Ink, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.weight(1f))
                        Text("${"%.2f".format(spacing)} em", color = Blue, fontSize = 12.sp)
                    }
                    Slider(value = spacing, onValueChange = { spacing = it }, valueRange = 0f..2f, steps = 7)
                }
                Box(
                    Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFFFFEFA)).padding(14.dp)
                ) {
                    if (editorValue.text.isEmpty()) {
                        Text("หลังจากกดเริ่มแปลง ข้อความจะแสดงที่นี่…", color = Muted)
                    }
                    BasicTextField(
                        value = editorValue,
                        onValueChange = { editorValue = it },
                        modifier = Modifier.fillMaxSize(),
                        textStyle = TextStyle(
                            color = Ink,
                            fontSize = 16.sp,
                            lineHeight = 26.sp,
                            textAlign = when (alignment) {
                                TextAlignment.START -> TextAlign.Start
                                TextAlignment.CENTER -> TextAlign.Center
                                TextAlignment.END -> TextAlign.End
                                TextAlignment.JUSTIFY -> TextAlign.Justify
                            }
                        ),
                        decorationBox = { inner -> inner() }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { showPreview = !showPreview }, modifier = Modifier.weight(1f), enabled = state.hasConverted) {
                        androidx.compose.material3.Icon(Icons.Default.Preview, null, modifier = Modifier.size(18.dp))
                        Text(if (showPreview) " ซ่อนพรีวิว" else " พรีวิว")
                    }
                    OutlinedButton(
                        onClick = {
                            val text = editorValue.text
                            editorValue = TextFieldValue(AnnotatedString(text), selection = androidx.compose.ui.text.TextRange(text.length))
                        },
                        modifier = Modifier.weight(1f),
                        enabled = state.hasConverted
                    ) { Text("ล้างรูปแบบ") }
                }
                if (showPreview) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFA)),
                        border = BorderStroke(1.dp, Line),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(state.title.ifBlank { "Untitled" }, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
                            if (state.author.isNotBlank()) Text(state.author, color = Muted, fontSize = 13.sp)
                            Text(editorValue.annotatedString, fontSize = 16.sp, lineHeight = 28.sp, color = Ink)
                            state.book.allImages().forEach { image ->
                                Text("ภาพ: ${image.image.altText}", color = Muted, fontSize = 12.sp)
                                if (image.image.caption.isNotBlank()) Text(image.image.caption, color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            SectionCard("5. รูปภาพและคำอธิบาย", Icons.Default.Image) {
                val images = state.book.allImages()
                if (images.isEmpty()) {
                    Text("ภาพจาก PDF ที่เลือกเก็บ หรือภาพที่นำเข้า จะปรากฏที่นี่หลังเริ่มแปลง", color = Muted, fontSize = 13.sp)
                } else {
                    Text("ฝังภาพใน EPUB: ${images.size} ภาพ", color = Blue, fontWeight = FontWeight.SemiBold)
                    images.forEachIndexed { index, image ->
                        Text("ภาพ ${index + 1}: ${image.image.fileName}", fontWeight = FontWeight.Medium, color = Ink)
                        Text("Alt Text: ${image.image.altText}", color = Muted, fontSize = 12.sp)
                        if (image.image.caption.isNotBlank()) Text("Caption: ${image.image.caption}", color = Muted, fontSize = 12.sp)
                    }
                    Text("รูปภาพถูกเก็บเป็น ImageBlock และจะฝังลง EPUB เมื่อส่งออก", color = Muted, fontSize = 12.sp)
                }
                if (state.book.coverImage != null) {
                    Text("ตั้งค่าหน้าปกแล้ว", color = Blue, fontWeight = FontWeight.Medium)
                } else {
                    Text("ยังไม่ได้เลือกหน้าปก ระบบยังสามารถส่งออก EPUB ได้", color = Muted, fontSize = 12.sp)
                }
            }

            Button(
                onClick = {
                    val filename = state.title.trim().ifBlank { "my-book" }
                        .replace(Regex("[^A-Za-z0-9ก-๙._ -]"), "_").take(80) + ".epub"
                    saveEpub.launch(filename)
                },
                enabled = state.hasConverted && editorValue.text.isNotBlank() && !state.isProcessing && !state.isExporting,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(17.dp)
            ) {
                if (state.isExporting) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else androidx.compose.material3.Icon(Icons.Default.FileUpload, null)
                Text(if (state.isExporting) " กำลังสร้าง EPUB…" else "  ส่งออกเป็นไฟล์ .EPUB", fontWeight = FontWeight.SemiBold)
            }
            Text("สร้างไฟล์ EPUB 3 พร้อมข้อความ รูปภาพ สารบัญ และ Metadata", modifier = Modifier.align(Alignment.CenterHorizontally), color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SectionCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Line)
    ) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Icon(icon, null, tint = Blue)
                Text(title, fontWeight = FontWeight.Bold, color = Ink, fontSize = 16.sp)
            }
            content()
        }
    }
}

@Composable
private fun EditorTool(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        androidx.compose.material3.Icon(icon, null, modifier = Modifier.size(18.dp))
        Text(" $label", fontSize = 12.sp)
    }
}

@Composable
private fun AlignmentTool(icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) PaleBlue else Color.White,
            contentColor = Blue
        )
    ) { androidx.compose.material3.Icon(icon, null, modifier = Modifier.size(19.dp)) }
}

@Composable
private fun BearReadingMascot(modifier: Modifier = Modifier) {
    Canvas(modifier.background(Color(0xFFEAF5FF), RoundedCornerShape(20.dp)).padding(2.dp)) {
        val sx = size.width / 100f
        val sy = size.height / 100f
        fun circle(x: Float, y: Float, radius: Float, color: Color) {
            drawCircle(color, radius * sx, androidx.compose.ui.geometry.Offset(x * sx, y * sy))
        }
        circle(28f, 23f, 15f, Color(0xFF9A6A48))
        circle(72f, 23f, 15f, Color(0xFF9A6A48))
        circle(28f, 23f, 8f, Color(0xFFE9B99A))
        circle(72f, 23f, 8f, Color(0xFFE9B99A))
        circle(50f, 43f, 31f, Color(0xFFB9825D))
        circle(39f, 40f, 3f, Ink)
        circle(61f, 40f, 3f, Ink)
        circle(50f, 51f, 12f, Color(0xFFF3D7BE))
        circle(50f, 48f, 3.5f, Ink)
        drawLine(Ink, androidx.compose.ui.geometry.Offset(50f * sx, 51f * sy), androidx.compose.ui.geometry.Offset(46f * sx, 55f * sy), strokeWidth = 1.6f * sx)
        drawLine(Ink, androidx.compose.ui.geometry.Offset(50f * sx, 51f * sy), androidx.compose.ui.geometry.Offset(54f * sx, 55f * sy), strokeWidth = 1.6f * sx)
        drawRoundRect(Color(0xFF6BA7D9), topLeft = androidx.compose.ui.geometry.Offset(18f * sx, 68f * sy), size = androidx.compose.ui.geometry.Size(64f * sx, 23f * sy), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f * sx))
        val leftPage = Path().apply {
            moveTo(50f * sx, 71f * sy); lineTo(22f * sx, 67f * sy); lineTo(22f * sx, 84f * sy); lineTo(50f * sx, 88f * sy); close()
        }
        val rightPage = Path().apply {
            moveTo(50f * sx, 71f * sy); lineTo(78f * sx, 67f * sy); lineTo(78f * sx, 84f * sy); lineTo(50f * sx, 88f * sy); close()
        }
        drawPath(leftPage, Color(0xFFFFFDF6))
        drawPath(rightPage, Color(0xFFFFFDF6))
        drawLine(Color(0xFF9BB6C9), androidx.compose.ui.geometry.Offset(29f * sx, 73f * sy), androidx.compose.ui.geometry.Offset(44f * sx, 76f * sy), strokeWidth = 1f * sx)
        drawLine(Color(0xFF9BB6C9), androidx.compose.ui.geometry.Offset(29f * sx, 78f * sy), androidx.compose.ui.geometry.Offset(44f * sx, 81f * sy), strokeWidth = 1f * sx)
        drawLine(Color(0xFF9BB6C9), androidx.compose.ui.geometry.Offset(56f * sx, 76f * sy), androidx.compose.ui.geometry.Offset(71f * sx, 73f * sy), strokeWidth = 1f * sx)
        drawLine(Color(0xFF9BB6C9), androidx.compose.ui.geometry.Offset(56f * sx, 81f * sy), androidx.compose.ui.geometry.Offset(71f * sx, 78f * sy), strokeWidth = 1f * sx)
    }
}

private fun applyStyle(value: TextFieldValue, style: SpanStyle): TextFieldValue {
    val length = value.text.length
    if (length == 0) return value
    val start = if (value.selection.collapsed) 0 else minOf(value.selection.start, value.selection.end)
    val end = if (value.selection.collapsed) length else maxOf(value.selection.start, value.selection.end)
    if (start == end) return value
    val builder = AnnotatedString.Builder(value.annotatedString)
    builder.addStyle(style, start, end)
    return value.copy(annotatedString = builder.toAnnotatedString())
}

private fun richRuns(value: AnnotatedString): List<RichTextRun> {
    if (value.isEmpty()) return emptyList()
    val boundaries = sortedSetOf(0, value.length)
    value.spanStyles.forEach { span ->
        boundaries.add(span.start.coerceIn(0, value.length))
        boundaries.add(span.end.coerceIn(0, value.length))
    }
    val points = boundaries.toList()
    val result = mutableListOf<RichTextRun>()
    for (index in 0 until points.lastIndex) {
        val start = points[index]
        val end = points[index + 1]
        if (start >= end) continue
        val active = value.spanStyles.filter { it.start <= start && it.end >= end }.map { it.item }
        val segment = value.text.substring(start, end)
        val run = RichTextRun(
            text = segment,
            bold = active.any { it.fontWeight == FontWeight.Bold },
            italic = active.any { it.fontStyle == FontStyle.Italic },
            underline = active.any { it.textDecoration?.contains(TextDecoration.Underline) == true },
            strikethrough = active.any { it.textDecoration?.contains(TextDecoration.LineThrough) == true }
        )
        val previous = result.lastOrNull()
        if (previous != null && previous.bold == run.bold && previous.italic == run.italic &&
            previous.underline == run.underline && previous.strikethrough == run.strikethrough
        ) result[result.lastIndex] = previous.copy(text = previous.text + run.text) else result += run
    }
    return result
}

private fun engineLabel(engine: String): String = when (engine) {
    "tesseract" -> "Tesseract — Offline ไทย + อังกฤษ"
    "mlkit-latin" -> "Google ML Kit — Offline อังกฤษ/Latin"
    "paddleocr" -> "PaddleOCR — Server"
    "easyocr" -> "EasyOCR — Server"
    "paddleocr-vl" -> "PaddleOCR-VL — Server"
    "google-vision" -> "Google Cloud Vision — Online API"
    "gemini" -> "Gemini Vision — Online API"
    "google-document-ai" -> "Google Document AI — Online API"
    else -> engine
}

private fun queryDisplayName(context: Context, uri: Uri): String {
    var name = uri.lastPathSegment ?: "document"
    val cursor: Cursor? = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) name = it.getString(index) ?: name
        }
    }
    return name
}
