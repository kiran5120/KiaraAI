package com.kiara.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.kiara.ai.domain.ChatMessage
import com.kiara.ai.viewmodel.MainViewModel

@Composable
fun ChatScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val pal = LocalPalette.current
    val messages by vm.messages.collectAsState()
    val thinking by vm.thinking.collectAsState()
    val listening by vm.listening.collectAsState()
    val attachment by vm.pendingAttachment.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, thinking) {
        val count = messages.size + if (thinking) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { vm.attachImage(it) } }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.attachTextFile(it) } }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.toggleMic() else vm.toast("Microphone permission is needed for voice")
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Chat with Kiara", fontSize = 20.sp, color = pal.primary, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.clearHistory() }) { Icon(Icons.Filled.DeleteSweep, "Clear conversation") }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (messages.isEmpty()) item {
                Text(
                    "Say hi! You can type or use the mic. Try: \"open YouTube\", \"calculate 15*8\", \"remind me in 10 minutes to drink water\", or just chat.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)
                )
            }
            items(messages, key = { it.id }) { Bubble(it) }
            if (thinking) item { Text("Kiara is typing…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(8.dp)) }
        }

        attachment?.let {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("📎 ${it.label}", color = pal.secondary, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.clearAttachment() }) { Icon(Icons.Filled.Close, "Remove attachment") }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Icon(Icons.Filled.Image, "Attach image") }
            IconButton(onClick = { filePicker.launch(arrayOf("text/*", "application/json", "application/xml")) }) { Icon(Icons.Filled.AttachFile, "Attach text file") }
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f), placeholder = { Text("Message Kiara…") }, maxLines = 4,
                shape = RoundedCornerShape(24.dp)
            )
            IconButton(onClick = {
                val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted) vm.toggleMic() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }) { Icon(Icons.Filled.Mic, "Voice input", tint = if (listening) Color.Red else pal.secondary) }
            IconButton(onClick = { vm.send(input); input = "" }) { Icon(Icons.Filled.Send, "Send", tint = pal.primary) }
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage) {
    val pal = LocalPalette.current
    val mine = m.role == "user"
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier.widthIn(max = 320.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (mine) Brush.linearGradient(listOf(pal.primary.copy(alpha = 0.85f), pal.secondary.copy(alpha = 0.85f))) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.09f))))
                .padding(12.dp)
        ) {
            SelectionContainer { MessageText(m.text, mine) }
        }
    }
}

/** Renders ``` fenced code blocks in monospace, everything else as normal text. */
@Composable
private fun MessageText(text: String, mine: Boolean) {
    val color = if (mine) Color.Black else MaterialTheme.colorScheme.onSurface
    val parts = text.split("```")
    Column {
        parts.forEachIndexed { i, part ->
            if (part.isBlank()) return@forEachIndexed
            if (i % 2 == 1) {
                val body = part.substringAfter('\n', part).trimEnd()
                Text(
                    body, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFFD7C8FF),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0B0820)).padding(8.dp)
                )
            } else Text(part.trim(), color = color, fontSize = 15.sp)
        }
    }
}
