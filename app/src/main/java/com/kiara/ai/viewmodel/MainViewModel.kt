package com.kiara.ai.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kiara.ai.data.ChatHistoryStore
import com.kiara.ai.data.GeminiClient
import com.kiara.ai.data.NotesStore
import com.kiara.ai.data.ReminderStore
import com.kiara.ai.data.SecureStore
import com.kiara.ai.data.SettingsStore
import com.kiara.ai.device.DeviceActions
import com.kiara.ai.device.ReminderScheduler
import com.kiara.ai.domain.ActionParser
import com.kiara.ai.domain.AppSettings
import com.kiara.ai.domain.Attachment
import com.kiara.ai.domain.Calculator
import com.kiara.ai.domain.ChatMessage
import com.kiara.ai.domain.CommandParser
import com.kiara.ai.domain.DeviceAction
import com.kiara.ai.domain.Local
import com.kiara.ai.domain.Note
import com.kiara.ai.domain.Persona
import com.kiara.ai.domain.Reminder
import com.kiara.ai.voice.VoiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale

sealed class ApiStatus {
    object Unknown : ApiStatus()
    object NoKey : ApiStatus()
    object Testing : ApiStatus()
    object Connected : ApiStatus()
    data class Failed(val message: String) : ApiStatus()
}

data class CodeResult(val code: String, val explanation: String)

class MainViewModel(app: Application) : AndroidViewModel(app), VoiceManager.Callback {
    private val settingsStore = SettingsStore(app)
    private val secure = SecureStore(app)
    private val history = ChatHistoryStore(app)
    private val notesStore = NotesStore(app)
    private val reminderStore = ReminderStore(app)
    private val gemini = GeminiClient()

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val actions = DeviceActions(app, { _settings.value }, notesStore, reminderStore)

    private val _messages = MutableStateFlow(if (_settings.value.saveHistory) history.load() else emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    val thinking = MutableStateFlow(false)
    val listening = MutableStateFlow(false)
    val speaking = MutableStateFlow(false)
    val partial = MutableStateFlow("")
    val level = MutableStateFlow(0f)
    val apiStatus = MutableStateFlow<ApiStatus>(if (secure.apiKey.isBlank()) ApiStatus.NoKey else ApiStatus.Unknown)
    val hasKey = MutableStateFlow(secure.apiKey.isNotBlank())
    val keyHint = MutableStateFlow(hint(secure.apiKey))
    val availableModels = MutableStateFlow<List<String>>(emptyList())
    val pendingAttachment = MutableStateFlow<Attachment?>(null)
    val notes = MutableStateFlow<List<Note>>(notesStore.all())
    val reminders = MutableStateFlow<List<Reminder>>(reminderStore.all())
    val codeResult = MutableStateFlow<CodeResult?>(null)
    val codeBusy = MutableStateFlow(false)
    val toolOutput = MutableStateFlow("")
    val toolBusy = MutableStateFlow(false)

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val voice = VoiceManager(app, this)
    private var continuousActive = false
    private var idCounter = System.currentTimeMillis()

    private fun nextId() = ++idCounter
    private fun hint(k: String) = if (k.isBlank()) "" else "••••••••" + k.takeLast(4)
    fun toast(msg: String) { _events.tryEmit(msg) }

    // ---------- settings ----------
    fun update(block: (AppSettings) -> AppSettings) {
        val n = block(_settings.value)
        _settings.value = n
        settingsStore.save(n)
    }

    fun saveApiKey(key: String) {
        val k = key.trim()
        if (k.isEmpty()) { toast("Paste your Gemini API key first"); return }
        secure.apiKey = k
        hasKey.value = true
        keyHint.value = hint(k)
        apiStatus.value = ApiStatus.Unknown
        toast("API key saved securely")
    }

    fun removeApiKey() {
        secure.apiKey = ""
        hasKey.value = false
        keyHint.value = ""
        apiStatus.value = ApiStatus.NoKey
        availableModels.value = emptyList()
        toast("API key removed")
    }

    fun testConnection() {
        val key = secure.apiKey
        if (key.isBlank()) { apiStatus.value = ApiStatus.NoKey; return }
        apiStatus.value = ApiStatus.Testing
        viewModelScope.launch {
            val r = gemini.generate(key, _settings.value.model, "Reply with the single word OK.", emptyList(), "ping", temperature = 0f)
            apiStatus.value = when (r) {
                is GeminiClient.Result.Ok -> ApiStatus.Connected
                is GeminiClient.Result.Err -> ApiStatus.Failed(r.message)
            }
        }
    }

    fun loadModels() {
        val key = secure.apiKey
        if (key.isBlank()) { toast("Save your API key first"); return }
        viewModelScope.launch {
            gemini.listModels(key).fold(
                onSuccess = { availableModels.value = it; toast("Found ${it.size} models") },
                onFailure = { toast("Couldn't load models: ${it.message}") }
            )
        }
    }

    // ---------- chat ----------
    private fun appendMessage(m: ChatMessage) {
        _messages.value = _messages.value + m
        if (_settings.value.saveHistory) history.save(_messages.value)
    }

    fun clearHistory() {
        _messages.value = emptyList()
        history.clear()
        toast("Conversation cleared")
    }

    fun deleteAllData() {
        clearHistory(); notesStore.clear(); reminderStore.clear()
        notes.value = emptyList(); reminders.value = emptyList()
        update { it.copy(memoryFacts = "") }
        toast("Local data deleted")
    }

    fun attachImage(uri: Uri) {
        viewModelScope.launch {
            val att = withContext(Dispatchers.IO) {
                try {
                    val cr = getApplication<Application>().contentResolver
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    var sample = 1
                    while (opts.outWidth / sample > 1600 || opts.outHeight / sample > 1600) sample *= 2
                    val bmp = cr.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                    } ?: return@withContext null
                    val out = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
                    Attachment("photo", "image/jpeg", base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
                } catch (e: Exception) { null }
            }
            if (att == null) toast("Couldn't read that image") else pendingAttachment.value = att
        }
    }

    fun attachTextFile(uri: Uri) {
        viewModelScope.launch {
            val att = withContext(Dispatchers.IO) {
                try {
                    val cr = getApplication<Application>().contentResolver
                    val text = cr.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(30_000) } ?: return@withContext null
                    val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
                    Attachment(name, "text/plain", text = text)
                } catch (e: Exception) { null }
            }
            if (att == null) toast("Couldn't read that file as text") else pendingAttachment.value = att
        }
    }

    fun clearAttachment() { pendingAttachment.value = null }

    fun send(text: String, fromVoice: Boolean = false) {
        val clean = text.trim()
        val att = pendingAttachment.value
        if (clean.isEmpty() && att == null) return
        val cur = _settings.value
        appendMessage(ChatMessage(nextId(), "user", if (att != null) "📎 ${att.label}\n$clean".trim() else clean))
        pendingAttachment.value = null
        viewModelScope.launch {
            thinking.value = true
            val reply = try {
                val local = if (att == null) CommandParser.parse(clean) else null
                when {
                    local != null -> runLocal(local, clean)
                    cur.geminiEnabled && secure.apiKey.isNotBlank() -> askGemini(clean, att, cur)
                    cur.geminiEnabled -> "I need your Gemini API key to chat, ${cur.userNickname}. Add it in Settings > Gemini AI. Meanwhile I can still open apps, do calculations, take notes and set reminders offline."
                    else -> "Gemini AI is switched off in Settings. I can still run offline commands like \"open YouTube\", \"calculate 12*8\" or \"remind me in 10 minutes to drink water\"."
                }
            } catch (e: Exception) {
                "Something went wrong: ${e.message}"
            } finally {
                thinking.value = false
            }
            appendMessage(ChatMessage(nextId(), "model", reply))
            afterReply(reply, fromVoice)
        }
    }

    private suspend fun askGemini(clean: String, att: Attachment?, cur: AppSettings): String {
        val hist = if (cur.memoryEnabled) _messages.value.dropLast(1).takeLast(cur.memoryTurns) else emptyList()
        val userText = when {
            att?.text != null -> "${clean.ifBlank { "Help me with this file." }}\n\n[Attached file: ${att.label}]\n${att.text}"
            att?.base64 != null -> clean.ifBlank { "Describe this image." }
            else -> clean
        }
        return when (val r = gemini.generate(
            secure.apiKey, cur.model, Persona.system(cur), hist, userText, att, cur.temperature, cur.webSearch
        )) {
            is GeminiClient.Result.Err -> "I couldn't reach Gemini. ${r.message}"
            is GeminiClient.Result.Ok -> {
                val (text, acts) = ActionParser.extract(r.text)
                val extra = acts.map { actions.execute(it) }.mapNotNull { res ->
                    if (!res.ok || res.message.isNotBlank()) res.message.ifBlank { null } else null
                }
                (listOf(text) + extra).filter { it.isNotBlank() }.joinToString("\n\n").ifBlank { "Done." }
            }
        }
    }

    private fun runLocal(local: Local, original: String): String {
        val cur = _settings.value
        return when (local) {
            is Local.Calc -> try {
                "${local.expr.trim()} = ${Calculator.format(Calculator.eval(local.expr))}"
            } catch (e: Exception) { "I couldn't calculate that: ${e.message}" }
            is Local.Act -> {
                val res = actions.execute(local.action)
                if (local.action.type == "ADD_NOTE") notes.value = notesStore.all()
                if (local.action.type == "SET_REMINDER") reminders.value = reminderStore.all()
                when {
                    res.message.isNotBlank() -> res.message
                    res.ok -> done(detectLang(original, cur.language), cur.userNickname)
                    else -> "I couldn't do that."
                }
            }
        }
    }

    private fun done(lang: String, nick: String) = when (lang) {
        "Hindi" -> "ठीक है $nick, हो गया!"
        "Gujarati" -> "થઈ ગયું $nick!"
        else -> "Done, $nick!"
    }

    private fun detectLang(text: String, setting: String): String = when {
        text.any { it.code in 0x0900..0x097F } -> "Hindi"
        text.any { it.code in 0x0A80..0x0AFF } -> "Gujarati"
        setting == "Hindi" || setting == "Gujarati" -> setting
        else -> "English"
    }

    private fun spokenForm(reply: String): String =
        reply.replace(Regex("```[\\s\\S]*?```"), " ").replace(Regex("[*#`_>•]"), "").trim()

    private fun afterReply(reply: String, fromVoice: Boolean) {
        val s = _settings.value
        val spoken = spokenForm(reply)
        if (s.speakReplies && spoken.isNotBlank()) {
            voice.speak(spoken, s.voicePitch, s.voiceRate)
        } else if (fromVoice && continuousActive) {
            startListening()
        }
    }

    // ---------- voice ----------
    private fun localeTag(lang: String) = when (lang) {
        "Hindi" -> "hi-IN"
        "Gujarati" -> "gu-IN"
        "English" -> "en-IN"
        else -> Locale.getDefault().toLanguageTag()
    }

    private fun startListening() { voice.startListening(localeTag(_settings.value.language)) }

    /** Mic button: stop whatever is happening, or start listening. */
    fun toggleMic() {
        when {
            voice.listening -> { continuousActive = false; voice.stopListening() }
            speaking.value -> { continuousActive = false; voice.stopSpeaking() }
            else -> { continuousActive = _settings.value.continuousMode; partial.value = ""; startListening() }
        }
    }

    fun speakSample() {
        val s = _settings.value
        voice.speak("Hi ${s.userNickname}, I'm Kiara. This is how my voice sounds.", s.voicePitch, s.voiceRate)
    }

    override fun onPartial(text: String) { partial.value = text }
    override fun onFinal(text: String) { partial.value = ""; send(text, fromVoice = true) }
    override fun onListeningChanged(listening: Boolean) { this.listening.value = listening; if (!listening) level.value = 0f }
    override fun onSpeakingChanged(speaking: Boolean) { this.speaking.value = speaking }
    override fun onLevel(level: Float) { this.level.value = level }
    override fun onVoiceError(message: String, transient: Boolean) {
        partial.value = ""
        if (transient && continuousActive) {
            continuousActive = false
            toast("Conversation mode paused: no speech heard")
        } else toast(message)
    }
    override fun onSpeechDone() { if (continuousActive && !thinking.value) startListening() }

    // ---------- quick actions / notes / reminders ----------
    fun runAction(type: String, vararg args: String) {
        val res = actions.execute(DeviceAction(type, args.toList()))
        if (res.message.isNotBlank()) toast(res.message) else if (!res.ok) toast("That didn't work")
    }

    fun addNote(text: String) { if (text.isNotBlank()) { notesStore.add(text); notes.value = notesStore.all() } }
    fun deleteNote(id: Long) { notesStore.remove(id); notes.value = notesStore.all() }

    fun addReminder(minutes: Long, text: String) {
        if (minutes <= 0 || text.isBlank()) { toast("Enter minutes and a message"); return }
        ReminderScheduler.schedule(getApplication(), reminderStore, text, System.currentTimeMillis() + minutes * 60_000)
        reminders.value = reminderStore.all()
        toast("Reminder set")
    }

    fun deleteReminder(r: Reminder) {
        ReminderScheduler.cancel(getApplication(), reminderStore, r)
        reminders.value = reminderStore.all()
    }

    fun refreshReminders() { reminders.value = reminderStore.all() }

    // ---------- coding assistant ----------
    fun runCode(mode: String, language: String, prompt: String, code: String) {
        val key = secure.apiKey
        if (key.isBlank() || !_settings.value.geminiEnabled) { toast("Add your Gemini key in Settings and enable Gemini first"); return }
        val q = when (mode) {
            "Generate" -> "Write complete, working $language code for: $prompt\nReturn the full code in ONE fenced block, then a short explanation."
            "Explain" -> "Explain this $language code step by step, simply:\n```\n$code\n```\n$prompt"
            "Fix" -> "Find and fix the bugs/errors in this $language code. Error or context: $prompt\nReturn the corrected full code in ONE fenced block, then list exactly what you changed and why.\n```\n$code\n```"
            "Debug" -> "Help me debug this $language code. Problem: $prompt\nWalk through likely causes, then show a corrected version in ONE fenced block.\n```\n$code\n```"
            else -> "Design a project structure for: $prompt (language/stack: $language). Show the folder tree, then each file's full content in separate fenced blocks, each preceded by its file path."
        }
        codeBusy.value = true
        viewModelScope.launch {
            val r = gemini.generate(key, _settings.value.model, Persona.CODING, emptyList(), q, temperature = 0.3f)
            codeBusy.value = false
            when (r) {
                is GeminiClient.Result.Err -> toast(r.message)
                is GeminiClient.Result.Ok -> {
                    val fence = Regex("```[a-zA-Z0-9+#]*\\n([\\s\\S]*?)```").find(r.text)
                    codeResult.value = if (mode == "Project" || mode == "Explain" || fence == null) CodeResult(
                        if (mode == "Explain") code else "", r.text
                    ) else CodeResult(
                        fence.groupValues[1].trimEnd(),
                        r.text.replace(fence.value, "").trim()
                    )
                }
            }
        }
    }

    // ---------- smart tools ----------
    fun runTool(kind: String, input: String, target: String) {
        val key = secure.apiKey
        if (key.isBlank() || !_settings.value.geminiEnabled) { toast("Add your Gemini key in Settings and enable Gemini first"); return }
        if (input.isBlank()) { toast("Enter some text first"); return }
        val q = when (kind) {
            "Translate" -> "Translate the following text into $target. Return only the translation.\n\n$input"
            "Summarize" -> "Summarize the following text clearly: a one-line TL;DR, then key bullet points.\n\n$input"
            else -> "Create a practical daily plan with time blocks for today based on this (tasks, goals, constraints):\n\n$input"
        }
        toolBusy.value = true
        viewModelScope.launch {
            val r = gemini.generate(key, _settings.value.model, Persona.plain(_settings.value), emptyList(), q, temperature = 0.4f)
            toolBusy.value = false
            when (r) {
                is GeminiClient.Result.Err -> toast(r.message)
                is GeminiClient.Result.Ok -> toolOutput.value = r.text
            }
        }
    }

    override fun onCleared() {
        voice.shutdown()
        super.onCleared()
    }
}
