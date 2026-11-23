package io.github.novapizza.licham

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.novapizza.licham.core.LunarEvent
import io.github.novapizza.licham.core.PersonalEvents
import io.github.novapizza.licham.core.ReminderFire
import io.github.novapizza.licham.core.toSolar
import io.github.novapizza.licham.widget.WidgetLinks
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Event reminders (shared/design/events.md, "Reminders"): one alarm for the earliest reminder still
 * to come (exact when the system allows exact alarms, else allowed while idle, which may come a few
 * minutes late); when it goes off, every reminder due in the last 15 minutes that was not posted yet
 * is posted, then the next alarm is set. Set again when events change, at app start, after a reboot
 * and after clock or time zone changes (ReminderReceiver).
 */
object Reminders {
    const val CHANNEL = "event_reminders"
    const val ACTION_FIRE = "io.github.novapizza.licham.REMINDER"
    /** Epoch ms of the latest reminder posted: never posted twice. */
    private const val LAST_POSTED = "reminderLastPosted"
    private const val LATE_MS = 15 * 60_000L

    private fun ms(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun time(ms: Long) = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms), ZoneId.systemDefault())

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Nhắc sự kiện", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Nhắc trước các sự kiện của bạn"
            },
        )
    }

    /** The first reminder firing at or after [from], looking up to three years ahead. */
    fun next(events: List<LunarEvent>, from: LocalDateTime): ReminderFire? {
        if (events.none { it.reminders.isNotEmpty() }) return null
        var lo = from
        for (days in longArrayOf(2, 30, 365, 3 * 365)) {
            val hi = from.plusDays(days)
            PersonalEvents.remindersBetween(events, lo, hi).firstOrNull()?.let { return it }
            lo = hi
        }
        return null
    }

    private fun alarm(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 1, Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Sets (or cancels) the alarm for the next reminder not posted yet. */
    fun schedule(context: Context) {
        val events = AppState.readEvents(context)
        val last = AppState.prefs(context).getLong(LAST_POSTED, 0L)
        val now = LocalDateTime.now().withNano(0)
        val from = if (last > 0 && time(last + 1) > now) time(last + 1) else now
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = alarm(context)
        val next = next(events, from)
        if (next == null) {
            am.cancel(pi)
            return
        }
        val at = ms(next.at)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** [schedule] off the main thread (after an edit in the app). */
    fun reschedule(context: Context) {
        val app = context.applicationContext
        Thread { runCatching { schedule(app) } }.start()
    }

    /** Posts every reminder due in the last 15 minutes and not posted yet. */
    fun postDue(context: Context) {
        val prefs = AppState.prefs(context)
        val last = prefs.getLong(LAST_POSTED, 0L)
        val nowMs = System.currentTimeMillis()
        val due = PersonalEvents.remindersBetween(AppState.readEvents(context), time(nowMs - LATE_MS), time(nowMs + 1_000))
            .filter { ms(it.at) > last }
        if (due.isEmpty()) return
        ensureChannel(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        val today = LocalDateTime.now().toLocalDate().toSolar()
        for (f in due) {
            val id = "${f.event.id}|${f.start}|${f.minutes}".hashCode()
            val open = PendingIntent.getActivity(
                context, id, WidgetLinks.open(context, Tab.CALENDAR, f.start),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val text = PersonalEvents.reminderText(f.event, f.start, today)
            val n = Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(f.event.title)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(listOfNotNull(text, f.event.location?.takeIf { it.isNotBlank() }).joinToString("\n")))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setWhen(ms(f.at))
                .setShowWhen(true)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            // Without POST_NOTIFICATIONS (Android 13+) the system drops it.
            runCatching { nm.notify(id, n) }
        }
        prefs.edit().putLong(LAST_POSTED, due.maxOf { ms(it.at) }).apply()
    }
}

/** The reminder alarm, a reboot, a clock or time zone change: post what is due, set the next alarm. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                runCatching { Reminders.postDue(context) }
                runCatching { Reminders.schedule(context) }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
