package com.kiara.ai.domain

object ActionParser {
    private val rx = Regex("""\[\[ACTION:([A-Z_]+)(?:\|([^\]]*))?\]\]""")
    val allowed = setOf(
        "OPEN_APP", "OPEN_YOUTUBE", "YOUTUBE_SEARCH", "OPEN_WHATSAPP", "OPEN_CHROME", "OPEN_URL",
        "OPEN_SETTINGS", "SETTINGS_WIFI", "SETTINGS_BLUETOOTH", "TORCH_ON", "TORCH_OFF",
        "VOLUME_UP", "VOLUME_DOWN", "SET_REMINDER", "ADD_NOTE", "GO_HOME", "GO_BACK", "RECENTS",
        "CLICK_TEXT", "SCROLL_DOWN", "SCROLL_UP", "READ_SCREEN", "READ_NOTIFICATIONS"
    )

    /** Returns the reply with action tags removed, plus the (whitelisted) actions found. */
    fun extract(text: String): Pair<String, List<DeviceAction>> {
        val actions = rx.findAll(text).mapNotNull { m ->
            val type = m.groupValues[1]
            if (type !in allowed) null
            else DeviceAction(type, m.groupValues[2].split("|").map { it.trim() }.filter { it.isNotEmpty() })
        }.take(3).toList()
        return rx.replace(text, "").trim() to actions
    }
}
