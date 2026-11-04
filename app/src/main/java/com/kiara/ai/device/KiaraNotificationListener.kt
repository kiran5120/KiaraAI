package com.kiara.ai.device

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.kiara.ai.data.SettingsStore
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Keeps the last few notifications IN MEMORY only (never written to disk, never sent anywhere
 * unless the user asks Kiara to read them). Does nothing unless "Allow reading notifications"
 * is on in Kiara settings AND the user granted Notification access in Android settings.
 */
class KiaraNotificationListener : NotificationListenerService() {
    companion object {
        val recent = CopyOnWriteArrayList<String>()
        @Volatile var connected = false
    }

    override fun onListenerConnected() { connected = true }
    override fun onListenerDisconnected() { connected = false }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!SettingsStore(this).load().allowNotificationReading) return
        if (sbn.packageName == packageName) return
        val e = sbn.notification.extras
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = e.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val app = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (_: Exception) { sbn.packageName }
        recent.add(0, "$app: $title ${if (text.isNotBlank()) "- $text" else ""}".trim())
        while (recent.size > 25) recent.removeAt(recent.size - 1)
    }
}
