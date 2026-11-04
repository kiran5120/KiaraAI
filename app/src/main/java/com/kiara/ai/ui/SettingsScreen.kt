package com.kiara.ai.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kiara.ai.device.KiaraAccessibilityService
import com.kiara.ai.viewmodel.ApiStatus
import com.kiara.ai.viewmodel.MainViewModel
import kotlin.math.roundToInt

private val DefaultModels = listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash")

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val pal = LocalPalette.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Settings", fontSize = 20.sp, color = pal.primary)
        GeminiSection(vm)
        VoiceSection(vm)
        LanguageSection(vm)
        PersonalitySection(vm)
        PermissionsSection(vm)
        AccessibilitySection(vm)
        ThemeSection(vm)
        MemorySection(vm)
        PrivacySection(vm)
        AboutSection()
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(if (open) "−" else "+", fontSize = 20.sp, color = LocalPalette.current.primary)
        }
        if (open) Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, desc: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            if (desc != null) Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onChange: (Float) -> Unit) {
    Column {
        Text("$label: $display", fontSize = 13.sp)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { FilterChip(selected = selected == it, onClick = { onSelect(it) }, label = { Text(it) }) }
    }
}

@Composable
private fun Hint(text: String) = Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun GeminiSection(vm: MainViewModel) {
    val s by vm.settings.collectAsState()
    val status by vm.apiStatus.collectAsState()
    val hasKey by vm.hasKey.collectAsState()
    val keyHint by vm.keyHint.collectAsState()
    val loaded by vm.availableModels.collectAsState()
    var keyInput by remember { mutableStateOf("") }
    var customModel by remember(s.model) { mutableStateOf(s.model) }
    Section("1. Gemini AI") {
        SwitchRow("Enable Gemini AI", s.geminiEnabled) { v -> vm.update { it.copy(geminiEnabled = v) } }
        OutlinedTextField(
            keyInput, { keyInput = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(if (hasKey) "Replace API key (current $keyHint)" else "Gemini API key") },
            visualTransformation = PasswordVisualTransformation()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.saveApiKey(keyInput); keyInput = "" }, enabled = keyInput.isNotBlank()) { Text("Save key") }
            OutlinedButton(onClick = { vm.removeApiKey() }, enabled = hasKey) { Text("Remove") }
        }
        Hint("Stored encrypted with a key in the Android Keystore. Get a free key at aistudio.google.com/apikey")
        Text("Model", fontSize = 13.sp)
        ChipRow((DefaultModels + loaded + s.model).distinct(), s.model) { m -> vm.update { it.copy(model = m) } }
        OutlinedTextField(customModel, { customModel = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Or type a model name") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { if (customModel.isNotBlank()) vm.update { it.copy(model = customModel.trim()) } }) { Text("Use this model") }
            OutlinedButton(onClick = { vm.loadModels() }, enabled = hasKey) { Text("Load models from my key") }
        }
        SliderRow("Creativity (temperature)", s.temperature, 0f..1.5f, "%.1f".format(s.temperature)) { v -> vm.update { it.copy(temperature = v) } }
        Text("Reply length", fontSize = 13.sp)
        ChipRow(listOf("Short", "Balanced", "Detailed"), s.replyLength) { v -> vm.update { it.copy(replyLength = v) } }
        SwitchRow("Web search (Google Search grounding)", s.webSearch, "Lets Gemini look things up online. Sends your questions to Google's search tool.") { v -> vm.update { it.copy(webSearch = v) } }
        Button(onClick = { vm.testConnection() }, enabled = hasKey && status !is ApiStatus.Testing) { Text("Test connection") }
        val (txt, col) = when (val st = status) {
            is ApiStatus.Connected -> "Connected" to Color(0xFF4CAF50)
            is ApiStatus.Failed -> st.message to Color(0xFFFF6B6B)
            ApiStatus.Testing -> "Testing…" to MaterialTheme.colorScheme.onSurfaceVariant
            ApiStatus.NoKey -> "No API key saved" to MaterialTheme.colorScheme.onSurfaceVariant
            ApiStatus.Unknown -> "Not tested yet" to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text("Status: $txt", color = col, fontSize = 13.sp)
    }
}

@Composable
private fun VoiceSection(vm: MainViewModel) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsState()
    var consent by remember { mutableStateOf(false) }
    Section("2. Voice") {
        SwitchRow("Speak replies aloud", s.speakReplies) { v -> vm.update { it.copy(speakReplies = v) } }
        SliderRow("Pitch", s.voicePitch, 0.7f..1.6f, "%.2f".format(s.voicePitch)) { v -> vm.update { it.copy(voicePitch = v) } }
        SliderRow("Speed", s.voiceRate, 0.5f..1.5f, "%.2f".format(s.voiceRate)) { v -> vm.update { it.copy(voiceRate = v) } }
        SwitchRow("Continuous conversation", s.continuousMode, "After Kiara answers, the microphone reopens by itself until you tap stop or stay silent.") { v ->
            if (v) consent = true else vm.update { it.copy(continuousMode = false) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.speakSample() }) { Text("Test voice") }
            OutlinedButton(onClick = {
                try { ctx.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { vm.toast("Open Android Settings > Text-to-speech") }
            }) { Text("System voice settings") }
        }
        Hint("The voice comes from your phone's text-to-speech engine. For natural Hindi/Gujarati, install the Google Speech Services voice data for those languages. Kiara picks a voice labelled female when the engine offers one, otherwise she uses a higher pitch.")
        Hint("A \"Hey Kiara\" always-listening wake word is not included in this version; use the mic button.")
    }
    if (consent) AlertDialog(
        onDismissRequest = { consent = false },
        title = { Text("Turn on continuous conversation?") },
        text = { Text("Kiara will keep listening through your microphone after each reply while the app is open, until you tap stop or stay silent. Speech is processed by your phone's speech recognizer. You can turn this off any time.") },
        confirmButton = { TextButton(onClick = { vm.update { it.copy(continuousMode = true) }; consent = false }) { Text("I agree") } },
        dismissButton = { TextButton(onClick = { consent = false }) { Text("Cancel") } }
    )
}

@Composable
private fun LanguageSection(vm: MainViewModel) {
    val s by vm.settings.collectAsState()
    Section("3. Language") {
        ChipRow(listOf("Auto", "Hindi", "Gujarati", "English"), s.language) { v -> vm.update { it.copy(language = v) } }
        Hint("Auto = Kiara replies in the language you use. The language also sets what the microphone listens for (Auto uses your phone's language).")
    }
}

@Composable
private fun PersonalitySection(vm: MainViewModel) {
    val s by vm.settings.collectAsState()
    Section("4. Kiara personality") {
        OutlinedTextField(s.userNickname, { v -> vm.update { it.copy(userNickname = v) } }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("What should Kiara call you?") })
        Text("Style", fontSize = 13.sp)
        ChipRow(listOf("Sweet & Caring", "Playful Friend", "Professional Assistant"), s.personality) { v -> vm.update { it.copy(personality = v) } }
        SliderRow("Humor", s.humor, 0f..1f, "${(s.humor * 100).roundToInt()}%") { v -> vm.update { it.copy(humor = v) } }
    }
}

private fun hasPerm(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

@Composable
private fun PermissionsSection(vm: MainViewModel) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val micOk = remember(tick) { hasPerm(ctx, Manifest.permission.RECORD_AUDIO) }
    val notifOk = remember(tick) { Build.VERSION.SDK_INT < 33 || hasPerm(ctx, Manifest.permission.POST_NOTIFICATIONS) }
    val listenerOk = remember(tick) { NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName) }

    Section("5. Device permissions") {
        Text("Microphone: ${if (micOk) "granted" else "not granted"}")
        if (!micOk) OutlinedButton(onClick = { mic.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Grant microphone") }
        Text("Notifications (for reminders): ${if (notifOk) "granted" else "not granted"}")
        if (!notifOk) OutlinedButton(onClick = { notif.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow notifications") }
        SwitchRow("Let Kiara read my notifications", s.allowNotificationReading, "Only when you ask. Kept in memory, never saved or uploaded by the app.") { v -> vm.update { it.copy(allowNotificationReading = v) } }
        Text("Android notification access: ${if (listenerOk) "granted" else "not granted"}")
        OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Open notification access settings") }
        Hint("Reading notifications needs BOTH the switch above and Android's notification access.")
        OutlinedButton(onClick = {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }) { Text("App permission settings") }
    }
}

@Composable
private fun AccessibilitySection(vm: MainViewModel) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    val running = remember(tick) { KiaraAccessibilityService.instance != null }
    Section("6. Accessibility & screen control") {
        Text("Accessibility service: ${if (running) "running" else "off"}")
        OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Open Android accessibility settings") }
        SwitchRow("Allow Kiara to control the screen", s.allowScreenControl, "Go home/back, scroll, tap a button by its label and read on-screen text, only when you ask.") { v -> vm.update { it.copy(allowScreenControl = v) } }
        Hint("Both the Android service and this switch must be on. Kiara never acts on her own, and password fields are never read. Some apps (banking, etc.) block accessibility.")
    }
}

@Composable
private fun ThemeSection(vm: MainViewModel) {
    val s by vm.settings.collectAsState()
    Section("7. Theme") { ChipRow(ThemeNames, s.theme) { v -> vm.update { it.copy(theme = v) } } }
}

@Composable
private fun MemorySection(vm: MainViewModel) {
    val s by vm.settings.collectAsState()
    Section("8. Memory") {
        SwitchRow("Remember conversation context", s.memoryEnabled, "Sends recent messages to Gemini so Kiara follows the conversation.") { v -> vm.update { it.copy(memoryEnabled = v) } }
        SliderRow("Messages remembered", s.memoryTurns.toFloat(), 4f..40f, "${s.memoryTurns}") { v -> vm.update { it.copy(memoryTurns = v.roundToInt()) } }
        OutlinedTextField(
            s.memoryFacts, { v -> vm.update { it.copy(memoryFacts = v) } }, Modifier.fillMaxWidth(), minLines = 3,
            label = { Text("Things Kiara should always remember about you") }
        )
        OutlinedButton(onClick = { vm.clearHistory() }) { Text("Clear conversation history") }
    }
}

@Composable
private fun PrivacySection(vm: MainViewModel) {
    val s by vm.settings.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    Section("9. Privacy") {
        SwitchRow("Save chat history on this phone", s.saveHistory) { v -> vm.update { it.copy(saveHistory = v) } }
        Hint("What you type or say (and attached photos/files) is sent to Google's Gemini API using your own key. Offline commands (open apps, calculator, notes, reminders) never leave the phone. Notes, reminders and history are stored only on this device.")
        OutlinedButton(onClick = { confirm = true }) { Text("Delete all local data") }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Delete all local data?") },
        text = { Text("This removes chat history, notes, reminders and remembered facts. Your API key is kept (remove it separately).") },
        confirmButton = { TextButton(onClick = { vm.deleteAllData(); confirm = false }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
    )
}

@Composable
private fun AboutSection() {
    Section("10. About Kiara") {
        Text("KIARA AI 1.0.0")
        Hint("A personal AI companion and phone assistant powered by your own Google Gemini API key. Kiara is an AI, not a person. She can open apps, search YouTube (you tap the video to play), set reminders, keep notes, calculate, translate, summarize, understand photos, help with code, and use accessibility actions you explicitly enable. She can't send messages or make calls.")
    }
}
