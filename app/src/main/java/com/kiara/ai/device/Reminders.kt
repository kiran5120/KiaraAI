package com.kiara.ai.device

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.kiara.ai.MainActivity
import com.kiara.ai.data.ReminderStore
import com.kiara.ai.domain.Reminder

object ReminderScheduler {
    fun schedule(ctx: Context, store: ReminderStore, text: String, at: Long): Reminder {
        val r = store.add(text, at)
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Inexact-while-idle alarm: battery friendly and needs no special permission.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx, r))
        return r
    }

    fun cancel(ctx: Context, store: ReminderStore, r: Reminder) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx, r))
        store.remove(r.id)
    }

    private fun pending(ctx: Context, r: Reminder): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).putExtra("text", r.text).putExtra("id", r.id)
        return PendingIntent.getBroadcast(ctx, r.id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra("text") ?: return
        val id = intent.getIntExtra("id", 0)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("kiara_reminders", "Kiara reminders", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, "kiara_reminders")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Kiara reminder")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { nm.notify(id, n) } catch (_: SecurityException) {}
        ReminderStore(context).remove(id)
    }
}
