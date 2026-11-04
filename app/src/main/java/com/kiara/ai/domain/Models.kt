package com.kiara.ai.domain

data class ChatMessage(
    val id: Long,
    val role: String, // "user" or "model"
    val text: String,
    val time: Long = System.currentTimeMillis()
)

/** A file/image the user attached to the next message. Either base64 (images) or text (text files). */
data class Attachment(
    val label: String,
    val mimeType: String,
    val base64: String? = null,
    val text: String? = null
)

data class DeviceAction(val type: String, val args: List<String> = emptyList())

data class Note(val id: Long, val text: String, val time: Long)

data class Reminder(val id: Int, val text: String, val at: Long)

data class AppSettings(
    val geminiEnabled: Boolean = true,
    val model: String = "gemini-2.5-flash",
    val temperature: Float = 0.8f,
    val replyLength: String = "Balanced",
    val webSearch: Boolean = false,
    val language: String = "Auto",
    val userNickname: String = "Dear",
    val personality: String = "Sweet & Caring",
    val humor: Float = 0.6f,
    val speakReplies: Boolean = true,
    val voicePitch: Float = 1.15f,
    val voiceRate: Float = 0.95f,
    val continuousMode: Boolean = false,
    val allowScreenControl: Boolean = false,
    val allowNotificationReading: Boolean = false,
    val memoryEnabled: Boolean = true,
    val memoryTurns: Int = 20,
    val memoryFacts: String = "",
    val saveHistory: Boolean = true,
    val theme: String = "Neon Purple"
)

/** Commands Kiara can handle on-device, without any network. */
sealed class Local {
    data class Act(val action: DeviceAction) : Local()
    data class Calc(val expr: String) : Local()
}
