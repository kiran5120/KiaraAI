package com.kiara.ai.data

import android.content.Context
import com.kiara.ai.domain.AppSettings
import com.kiara.ai.domain.ChatMessage
import com.kiara.ai.domain.Note
import com.kiara.ai.domain.Reminder
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SettingsStore(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("kiara_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            geminiEnabled = p.getBoolean("geminiEnabled", d.geminiEnabled),
            model = p.getString("model", d.model) ?: d.model,
            temperature = p.getFloat("temperature", d.temperature),
            replyLength = p.getString("replyLength", d.replyLength) ?: d.replyLength,
            webSearch = p.getBoolean("webSearch", d.webSearch),
            language = p.getString("language", d.language) ?: d.language,
            userNickname = p.getString("userNickname", d.userNickname) ?: d.userNickname,
            personality = p.getString("personality", d.personality) ?: d.personality,
            humor = p.getFloat("humor", d.humor),
            speakReplies = p.getBoolean("speakReplies", d.speakReplies),
            voicePitch = p.getFloat("voicePitch", d.voicePitch),
            voiceRate = p.getFloat("voiceRate", d.voiceRate),
            continuousMode = p.getBoolean("continuousMode", d.continuousMode),
            allowScreenControl = p.getBoolean("allowScreenControl", d.allowScreenControl),
            allowNotificationReading = p.getBoolean("allowNotificationReading", d.allowNotificationReading),
            memoryEnabled = p.getBoolean("memoryEnabled", d.memoryEnabled),
            memoryTurns = p.getInt("memoryTurns", d.memoryTurns),
            memoryFacts = p.getString("memoryFacts", d.memoryFacts) ?: d.memoryFacts,
            saveHistory = p.getBoolean("saveHistory", d.saveHistory),
            theme = p.getString("theme", d.theme) ?: d.theme
        )
    }

    fun save(s: AppSettings) {
        p.edit()
            .putBoolean("geminiEnabled", s.geminiEnabled)
            .putString("model", s.model)
            .putFloat("temperature", s.temperature)
            .putString("replyLength", s.replyLength)
            .putBoolean("webSearch", s.webSearch)
            .putString("language", s.language)
            .putString("userNickname", s.userNickname)
            .putString("personality", s.personality)
            .putFloat("humor", s.humor)
            .putBoolean("speakReplies", s.speakReplies)
            .putFloat("voicePitch", s.voicePitch)
            .putFloat("voiceRate", s.voiceRate)
            .putBoolean("continuousMode", s.continuousMode)
            .putBoolean("allowScreenControl", s.allowScreenControl)
            .putBoolean("allowNotificationReading", s.allowNotificationReading)
            .putBoolean("memoryEnabled", s.memoryEnabled)
            .putInt("memoryTurns", s.memoryTurns)
            .putString("memoryFacts", s.memoryFacts)
            .putBoolean("saveHistory", s.saveHistory)
            .putString("theme", s.theme)
            .apply()
    }

    fun clear() { p.edit().clear().apply() }
}

class ChatHistoryStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "history.json")

    fun load(): List<ChatMessage> = try {
        if (!file.exists()) emptyList() else {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                ChatMessage(o.getLong("id"), o.getString("role"), o.getString("text"), o.getLong("time"))
            }
        }
    } catch (e: Exception) { emptyList() }

    fun save(list: List<ChatMessage>) {
        try {
            val arr = JSONArray()
            list.takeLast(200).forEach {
                arr.put(JSONObject().put("id", it.id).put("role", it.role).put("text", it.text).put("time", it.time))
            }
            file.writeText(arr.toString())
        } catch (_: Exception) {}
    }

    fun clear() { file.delete() }
}

class NotesStore(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("kiara_notes", Context.MODE_PRIVATE)

    fun all(): List<Note> = try {
        val arr = JSONArray(p.getString("notes", "[]"))
        (0 until arr.length()).map { val o = arr.getJSONObject(it); Note(o.getLong("id"), o.getString("text"), o.getLong("time")) }
    } catch (e: Exception) { emptyList() }

    private fun write(list: List<Note>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("text", it.text).put("time", it.time)) }
        p.edit().putString("notes", arr.toString()).apply()
    }

    fun add(text: String): Note {
        val n = Note(System.currentTimeMillis(), text.trim(), System.currentTimeMillis())
        write(listOf(n) + all())
        return n
    }

    fun remove(id: Long) = write(all().filter { it.id != id })
    fun clear() = p.edit().clear().apply()
}

class ReminderStore(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("kiara_reminders", Context.MODE_PRIVATE)

    fun all(): List<Reminder> = try {
        val arr = JSONArray(p.getString("reminders", "[]"))
        (0 until arr.length()).map { val o = arr.getJSONObject(it); Reminder(o.getInt("id"), o.getString("text"), o.getLong("at")) }.sortedBy { it.at }
    } catch (e: Exception) { emptyList() }

    private fun write(list: List<Reminder>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("text", it.text).put("at", it.at)) }
        p.edit().putString("reminders", arr.toString()).apply()
    }

    fun add(text: String, at: Long): Reminder {
        val r = Reminder((System.currentTimeMillis() % 1_000_000_000L).toInt(), text.trim(), at)
        write(all() + r)
        return r
    }

    fun remove(id: Int) = write(all().filter { it.id != id })
    fun clear() = p.edit().clear().apply()
}
