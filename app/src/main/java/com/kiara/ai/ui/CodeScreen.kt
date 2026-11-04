package com.kiara.ai.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiara.ai.viewmodel.MainViewModel

private val Languages = listOf("Kotlin", "Java", "Python", "JavaScript", "HTML", "CSS")
private val Modes = listOf("Generate", "Explain", "Fix", "Debug", "Project")

private val keywords = setOf(
    "fun", "val", "var", "class", "object", "interface", "if", "else", "when", "for", "while", "return", "import", "package",
    "public", "private", "protected", "static", "void", "int", "String", "new", "try", "catch", "finally", "throw", "null",
    "true", "false", "def", "from", "as", "in", "is", "not", "and", "or", "lambda", "None", "True", "False", "with", "pass",
    "function", "const", "let", "await", "async", "export", "this", "self", "extends", "implements", "override", "suspend",
    "break", "continue", "do", "switch", "case", "default", "enum", "data", "sealed", "super", "typeof", "yield", "elif", "except"
)

class SyntaxTransformation(private val lang: String) : VisualTransformation {
    private val kw = Regex("\\b(" + keywords.joinToString("|") + ")\\b")
    private val num = Regex("\\b\\d+(\\.\\d+)?\\b")
    private val str = Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'")
    private val comment: Regex = when (lang) {
        "Python" -> Regex("#.*")
        "HTML" -> Regex("<!--[\\s\\S]*?-->")
        "CSS" -> Regex("/\\*[\\s\\S]*?\\*/")
        else -> Regex("//.*|/\\*[\\s\\S]*?\\*/")
    }
    private val tag = Regex("</?[a-zA-Z][a-zA-Z0-9]*")

    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        val b = AnnotatedString.Builder(s)
        if (lang == "HTML") tag.findAll(s).forEach { b.addStyle(SpanStyle(color = Color(0xFFFF79C6)), it.range.first, it.range.last + 1) }
        else kw.findAll(s).forEach { b.addStyle(SpanStyle(color = Color(0xFFFF79C6)), it.range.first, it.range.last + 1) }
        num.findAll(s).forEach { b.addStyle(SpanStyle(color = Color(0xFFBD93F9)), it.range.first, it.range.last + 1) }
        str.findAll(s).forEach { b.addStyle(SpanStyle(color = Color(0xFFF1FA8C)), it.range.first, it.range.last + 1) }
        comment.findAll(s).forEach { b.addStyle(SpanStyle(color = Color(0xFF7A88B8)), it.range.first, it.range.last + 1) }
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@Composable
fun CodeScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val pal = LocalPalette.current
    val clipboard = LocalClipboardManager.current
    val busy by vm.codeBusy.collectAsState()
    val result by vm.codeResult.collectAsState()
    var mode by remember { mutableStateOf("Generate") }
    var lang by remember { mutableStateOf("Kotlin") }
    var prompt by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    // When a new result arrives, load its code into the editor.
    LaunchedEffect(result) { result?.let { if (it.code.isNotBlank()) code = it.code } }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(code.toByteArray()) }
                vm.toast("Saved")
            } catch (e: Exception) { vm.toast("Couldn't save: ${e.message}") }
        }
    }
    val ext = when (lang) { "Kotlin" -> "kt"; "Java" -> "java"; "Python" -> "py"; "JavaScript" -> "js"; "HTML" -> "html"; else -> "css" }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Code assistant", fontSize = 20.sp, color = pal.primary)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Modes.forEach { FilterChip(selected = mode == it, onClick = { mode = it }, label = { Text(it) }) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Languages.forEach { FilterChip(selected = lang == it, onClick = { lang = it }, label = { Text(it) }) }
        }
        OutlinedTextField(
            value = prompt, onValueChange = { prompt = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text(if (mode == "Fix" || mode == "Debug") "Error message / what's wrong" else "What do you need?") }, minLines = 2
        )
        Button(onClick = { vm.runCode(mode, lang, prompt, code) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Working…" else "Run with Gemini")
        }

        Text("Editor (paste code here for Explain / Fix / Debug)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BasicTextField(
            value = code, onValueChange = { code = it },
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color(0xFFE8E2FF)),
            cursorBrush = SolidColor(pal.primary),
            visualTransformation = SyntaxTransformation(lang),
            modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF0B0820)).padding(12.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(code)); vm.toast("Copied") }, enabled = code.isNotBlank()) { Text("Copy") }
            OutlinedButton(onClick = { saver.launch("kiara_code.$ext") }, enabled = code.isNotBlank()) { Text("Save file") }
            OutlinedButton(onClick = { code = "" }, enabled = code.isNotBlank()) { Text("Clear") }
        }

        result?.takeIf { it.explanation.isNotBlank() }?.let {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Kiara says", fontSize = 13.sp, color = pal.primary)
                SelectionContainer { Text(it.explanation, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp)) }
            }
        }
    }
}
