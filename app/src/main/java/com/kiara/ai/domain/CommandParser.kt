package com.kiara.ai.domain

/** Offline command recognition (English + common Hinglish phrasing). */
object CommandParser {
    private val mathOnly = Regex("""^[\d\s+\-*/().^%x×÷]+$""")

    fun parse(raw: String): Local? {
        var t = raw.trim().lowercase().trimEnd('.', '!', '?').trim()
        t = t.replace(Regex("""^(hey|hi|ok|okay)\s+kiara[,\s]*"""), "").replace(Regex("""^kiara[,\s]+"""), "").trim()
        if (t.isEmpty()) return null

        // Calculator
        Regex("""^(?:calculate|calc|compute|solve)\s+(.+)$""").find(t)?.let { return Local.Calc(it.groupValues[1]) }
        if (mathOnly.matches(t) && t.any { it.isDigit() } && t.any { it in "+-*/^%x×÷" } && t.length > 2) return Local.Calc(t)

        // Notes
        Regex("""(?i)^(?:note(?: down)?|add note|save note)[:\s]+(.+)$""").find(raw.trim().replace(Regex("""(?i)^(hey|hi|ok|okay)?\s*kiara[,\s]*"""), ""))
            ?.let { return Local.Act(DeviceAction("ADD_NOTE", listOf(it.groupValues[1].trim()))) }

        // Reminders
        Regex("""^remind me in (\d+)\s*(minutes?|mins?|hours?|hrs?)\s*(?:to\s+)?(.+)$""").find(t)?.let {
            val n = it.groupValues[1].toLong()
            val mins = if (it.groupValues[2].startsWith("h")) n * 60 else n
            return Local.Act(DeviceAction("SET_REMINDER", listOf(mins.toString(), it.groupValues[3].trim())))
        }

        // YouTube search / music
        Regex("""^(?:play|search)\s+(.+?)\s+(?:on|in)\s+youtube$""").find(t)?.let { return yt(it.groupValues[1]) }
        Regex("""^youtube\s+(?:par|pe|me|mein)\s+(.+?)(?:\s+(?:chalao|play karo|search karo|dhundo|bajao))?$""").find(t)?.let { return yt(it.groupValues[1]) }
        Regex("""^youtube\s+search\s+(.+)$""").find(t)?.let { return yt(it.groupValues[1]) }
        Regex("""^play\s+(.+)$""").find(t)?.let { return yt(it.groupValues[1]) }

        // System navigation / screen control
        when {
            t in setOf("go home", "home screen", "go to home screen", "home") -> return act("GO_HOME")
            t in setOf("go back", "back") -> return act("GO_BACK")
            t in setOf("recent apps", "recents", "show recent apps") -> return act("RECENTS")
            t in setOf("scroll down") -> return act("SCROLL_DOWN")
            t in setOf("scroll up") -> return act("SCROLL_UP")
            t in setOf("read screen", "read my screen", "read the screen") -> return act("READ_SCREEN")
            t in setOf("read notifications", "read my notifications", "read notification") -> return act("READ_NOTIFICATIONS")
        }
        Regex("""^(?:click|tap|press)\s+(?:on\s+)?(.+)$""").find(t)?.let { return Local.Act(DeviceAction("CLICK_TEXT", listOf(it.groupValues[1].trim()))) }

        // Torch / volume (short commands only)
        if (t.length < 40) {
            if (t.contains("torch") || t.contains("flashlight") || t.contains("टॉर्च")) {
                val off = t.contains("off") || t.contains("band") || t.contains("बंद")
                return act(if (off) "TORCH_OFF" else "TORCH_ON")
            }
            if (t.contains("volume")) {
                if (t.contains("up") || t.contains("increase") || t.contains("badha") || t.contains("zyada")) return act("VOLUME_UP")
                if (t.contains("down") || t.contains("decrease") || t.contains("kam") || t.contains("low")) return act("VOLUME_DOWN")
            }
        }

        // Open apps
        val name = Regex("""^(?:open|launch|start)\s+(?:the\s+)?(.+)$""").find(t)?.groupValues?.get(1)
            ?: Regex("""^(.+?)\s+(?:kholo|khol do|khol|chalu karo|chalao|open karo)$""").find(t)?.groupValues?.get(1)
        if (name != null) {
            val n = name.trim()
            return when (n) {
                "youtube" -> act("OPEN_YOUTUBE")
                "whatsapp", "whats app" -> act("OPEN_WHATSAPP")
                "chrome", "google chrome" -> act("OPEN_CHROME")
                "settings", "setting", "phone settings" -> act("OPEN_SETTINGS")
                "wifi", "wi-fi", "wifi settings" -> act("SETTINGS_WIFI")
                "bluetooth" -> act("SETTINGS_BLUETOOTH")
                else -> Local.Act(DeviceAction("OPEN_APP", listOf(n)))
            }
        }
        return null
    }

    private fun act(type: String) = Local.Act(DeviceAction(type))
    private fun yt(q: String) = Local.Act(DeviceAction("YOUTUBE_SEARCH", listOf(q.trim())))
}
