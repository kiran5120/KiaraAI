package com.kiara.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.kiara.ai.viewmodel.ApiStatus
import com.kiara.ai.viewmodel.MainViewModel

@Composable
fun HomeScreen(vm: MainViewModel, onOpenChat: () -> Unit) {
    val ctx = LocalContext.current
    val pal = LocalPalette.current
    val listening by vm.listening.collectAsState()
    val speaking by vm.speaking.collectAsState()
    val thinking by vm.thinking.collectAsState()
    val partial by vm.partial.collectAsState()
    val level by vm.level.collectAsState()
    val messages by vm.messages.collectAsState()
    val status by vm.apiStatus.collectAsState()
    val hasKey by vm.hasKey.collectAsState()
    val settings by vm.settings.collectAsState()

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.toggleMic() else vm.toast("Microphone permission is needed for voice")
    }
    fun onMic() {
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (listening || speaking || granted) vm.toggleMic() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val state = when {
        listening -> AvatarState.Listening
        thinking -> AvatarState.Thinking
        speaking -> AvatarState.Speaking
        else -> AvatarState.Idle
    }
    val label = when {
        listening -> if (partial.isBlank()) "Listening…" else partial
        thinking -> "Thinking…"
        speaking -> "Speaking…"
        else -> "Tap the mic and talk to me, ${settings.userNickname}"
    }
    val chipText = when {
        !settings.geminiEnabled -> "Gemini off · offline commands only"
        !hasKey -> "No API key · offline commands only"
        status is ApiStatus.Connected -> "Gemini connected"
        status is ApiStatus.Failed -> "Gemini error · open Settings"
        else -> "Gemini ready (${settings.model})"
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("KIARA AI", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = pal.primary, letterSpacing = 4.sp)
        Text(chipText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        KiaraAvatar(state, level, Modifier.padding(top = 8.dp))

        Text(
            label, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth(), fontSize = 15.sp
        )
        Waveform(active = listening || speaking, level = if (speaking) 0.6f else level)

        Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            IconButton(onClick = onOpenChat, modifier = Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))) {
                Icon(Icons.Filled.Chat, "Open chat", tint = pal.secondary)
            }
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(Brush.linearGradient(listOf(pal.primary, pal.secondary))),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = { onMic() }, modifier = Modifier.fillMaxSize()) {
                    Icon(if (listening || speaking) Icons.Filled.Stop else Icons.Filled.Mic, "Microphone", tint = Color.White, modifier = Modifier.size(34.dp))
                }
            }
            Box(Modifier.size(52.dp))
        }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { vm.runAction("OPEN_YOUTUBE") }, label = { Text("YouTube") })
            AssistChip(onClick = { vm.runAction("OPEN_WHATSAPP") }, label = { Text("WhatsApp") })
            AssistChip(onClick = { vm.runAction("OPEN_CHROME") }, label = { Text("Chrome") })
            AssistChip(onClick = { vm.runAction("OPEN_SETTINGS") }, label = { Text("Settings") })
            AssistChip(onClick = { vm.runAction("SETTINGS_WIFI") }, label = { Text("Wi-Fi") })
            AssistChip(onClick = { vm.runAction("TORCH_ON") }, label = { Text("Torch on") })
            AssistChip(onClick = { vm.runAction("TORCH_OFF") }, label = { Text("Torch off") })
            AssistChip(onClick = { vm.runAction("READ_NOTIFICATIONS") }, label = { Text("Notifications") })
        }

        GlassCard(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Text("Recent conversation", fontWeight = FontWeight.SemiBold, color = pal.primary, fontSize = 13.sp)
            val recent = messages.takeLast(4)
            if (recent.isEmpty()) {
                Text("Nothing yet. Say hi!", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            } else recent.forEach {
                Text(
                    (if (it.role == "user") "You: " else "Kiara: ") + it.text.replace("\n", " "),
                    maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}
