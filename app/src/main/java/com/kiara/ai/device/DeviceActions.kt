package com.kiara.ai.device

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import com.kiara.ai.data.NotesStore
import com.kiara.ai.data.ReminderStore
import com.kiara.ai.domain.AppSettings
import com.kiara.ai.domain.DeviceAction
import java.text.DateFormat
import java.util.Date

class DeviceActions(
    private val ctx: Context,
    private val settings: () -> AppSettings,
    private val notes: NotesStore,
    private val reminders: ReminderStore
) {
    data class Result(val ok: Boolean, val message: String = "")

    private val aliases = mapOf(
        "youtube" to "com.google.android.youtube",
        "whatsapp" to "com.whatsapp",
        "chrome" to "com.android.chrome",
        "instagram" to "com.instagram.android",
        "telegram" to "org.telegram.messenger",
        "gmail" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps",
        "google maps" to "com.google.android.apps.maps"
    )

    fun execute(a: DeviceAction): Result = try {
        val arg = a.args.firstOrNull().orEmpty()
        when (a.type) {
            "OPEN_APP" -> if (arg.isBlank()) Result(false, "Which app should I open?") else openApp(arg)
            "OPEN_YOUTUBE" -> launchPackage("com.google.android.youtube") ?: view("https://www.youtube.com")
            "YOUTUBE_SEARCH" -> youtubeSearch(arg)
            "OPEN_WHATSAPP" -> launchPackage("com.whatsapp") ?: view("https://www.whatsapp.com")
            "OPEN_CHROME" -> launchPackage("com.android.chrome") ?: view("https://www.google.com")
            "OPEN_URL" -> if (arg.startsWith("https://")) view(arg) else Result(false, "I only open secure https links.")
            "OPEN_SETTINGS" -> start(Intent(Settings.ACTION_SETTINGS))
            "SETTINGS_WIFI" -> start(Intent(Settings.ACTION_WIFI_SETTINGS))
            "SETTINGS_BLUETOOTH" -> start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            "TORCH_ON" -> torch(true)
            "TORCH_OFF" -> torch(false)
            "VOLUME_UP" -> volume(AudioManager.ADJUST_RAISE)
            "VOLUME_DOWN" -> volume(AudioManager.ADJUST_LOWER)
            "SET_REMINDER" -> setReminder(a.args)
            "ADD_NOTE" -> if (arg.isBlank()) Result(false, "What should I note down?") else { notes.add(arg); Result(true, "Saved to your notes.") }
            "GO_HOME" -> screen { it.global(AccessibilityService.GLOBAL_ACTION_HOME) }
            "GO_BACK" -> screen { it.global(AccessibilityService.GLOBAL_ACTION_BACK) }
            "RECENTS" -> screen { it.global(AccessibilityService.GLOBAL_ACTION_RECENTS) }
            "CLICK_TEXT" -> screen { it.clickText(arg) }.let { if (!it.ok && it.message.isEmpty()) Result(false, "I couldn't find \"$arg\" on the screen.") else it }
            "SCROLL_DOWN" -> screen { it.scroll(true) }
            "SCROLL_UP" -> screen { it.scroll(false) }
            "READ_SCREEN" -> readScreen()
            "READ_NOTIFICATIONS" -> readNotifications()
            else -> Result(false, "I don't know how to do that yet.")
        }
    } catch (e: Exception) {
        Result(false, "That didn't work: ${e.message}")
    }

    private fun start(i: Intent): Result = try {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        Result(true)
    } catch (e: ActivityNotFoundException) {
        Result(false, "No app on this phone can handle that.")
    }

    private fun view(url: String) = start(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun launchPackage(pkg: String): Result? {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return null
        return start(i)
    }

    private fun openApp(name: String): Result {
        val q = name.trim().lowercase()
        aliases[q]?.let { pkg -> launchPackage(pkg)?.let { return it } }
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
        val match = apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == q }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) }
            ?: return Result(false, "I couldn't find an app called \"$name\".")
        return launchPackage(match.activityInfo.packageName) ?: Result(false, "I couldn't open \"$name\".")
    }

    private fun youtubeSearch(q: String): Result {
        if (q.isBlank()) return Result(false, "What should I search for on YouTube?")
        val uri = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))
        val yt = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.youtube")
        val r = start(yt)
        return if (r.ok) Result(true, "Showing YouTube results for \"$q\". Tap a video to play it.") else start(Intent(Intent.ACTION_VIEW, uri))
    }

    private fun torch(on: Boolean): Result {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return Result(false, "This phone has no flashlight.")
        cm.setTorchMode(id, on)
        return Result(true)
    }

    private fun volume(dir: Int): Result {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
        return Result(true)
    }

    private fun setReminder(args: List<String>): Result {
        val mins = args.getOrNull(0)?.toLongOrNull()
        val text = args.getOrNull(1).orEmpty()
        if (mins == null || mins <= 0 || text.isBlank()) return Result(false, "I need a time and what to remind you about.")
        val at = System.currentTimeMillis() + mins * 60_000
        ReminderScheduler.schedule(ctx, reminders, text, at)
        return Result(true, "Reminder set for ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(at))}: $text")
    }

    private fun screen(block: (KiaraAccessibilityService) -> Boolean): Result {
        if (!settings().allowScreenControl) return Result(false, "Screen control is switched off. You can turn it on in Settings > Accessibility.")
        val svc = KiaraAccessibilityService.instance
            ?: return Result(false, "Kiara's accessibility service isn't running. Enable it in Settings > Accessibility first.")
        return if (block(svc)) Result(true) else Result(false)
    }

    private fun readScreen(): Result {
        if (!settings().allowScreenControl) return Result(false, "Screen control is switched off. You can turn it on in Settings > Accessibility.")
        val svc = KiaraAccessibilityService.instance
            ?: return Result(false, "Kiara's accessibility service isn't running. Enable it in Settings > Accessibility first.")
        val t = svc.screenText()
        return if (t.isBlank()) Result(false, "I couldn't read any text on the screen.") else Result(true, "Here's what's on screen:\n$t")
    }

    private fun readNotifications(): Result {
        if (!settings().allowNotificationReading) return Result(false, "Notification reading is switched off. You can turn it on in Settings > Device permissions.")
        if (!KiaraNotificationListener.connected) return Result(false, "Notification access isn't granted. Enable it in Android's Notification access settings.")
        val list = KiaraNotificationListener.recent.take(8)
        return if (list.isEmpty()) Result(true, "No new notifications since I started listening.") else Result(true, "Recent notifications:\n" + list.joinToString("\n") { "• $it" })
    }
}
