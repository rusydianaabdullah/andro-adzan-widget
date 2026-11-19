package com.rusydiana.adzantime.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.rusydiana.adzantime.data.Settings
import com.rusydiana.adzantime.data.WidgetHealth
import com.rusydiana.adzantime.prayer.HijriHolidays
import com.rusydiana.adzantime.prayer.PrayerEvent
import com.rusydiana.adzantime.prayer.Schedule
import com.rusydiana.adzantime.ui.MainActivity
import java.util.Calendar

/**
 * Keeps exactly two alarms alive at a time: the next adzan, and the next widget refresh.
 * Each firing reschedules the following one, so the chain survives reboots and Doze without
 * a background service.
 */
object AdzanScheduler {

    private const val TAG = "AdzanScheduler"
    const val ACTION_ADZAN = "com.rusydiana.adzantime.ADZAN"
    const val ACTION_REFRESH = "com.rusydiana.adzantime.REFRESH"
    const val ACTION_HOLIDAY = "com.rusydiana.adzantime.HOLIDAY"
    const val ACTION_TEST = "com.rusydiana.adzantime.TEST"
    const val EXTRA_PRAYER = "prayer"
    const val EXTRA_JUMUAH = "jumuah"
    const val EXTRA_HOLIDAY_LABEL = "holiday_label"

    private const val REQ_ADZAN = 1001
    private const val REQ_REFRESH = 1002
    private const val REQ_HOLIDAY = 1003
    private const val REQ_TEST = 1004
    private const val REQ_SHOW_ALARM = 1005

    /**
     * How late an unannounced adzan may still be caught up. Long enough to cover an alarm lost
     * to a reschedule landing on top of it, short enough that a phone switched back on in the
     * evening does not suddenly call Subuh.
     */
    private const val CATCH_UP_WINDOW = 5 * 60_000L

    /**
     * The widget refresh trails the adzan rather than chasing it by a second. Every firing
     * reschedules the chain, so a refresh that runs while the adzan alarm is still pending
     * rewrites it to the *next* prayer and that adzan is never announced.
     */
    private const val REFRESH_LAG = 90_000L

    fun rescheduleAll(context: Context) {
        // Independent of each other: one failing must not leave the others unarmed.
        runCatching { scheduleNextAdzan(context) }
        runCatching { scheduleNextRefresh(context) }
        runCatching { scheduleNextHolidayReminder(context) }
    }

    /**
     * Arms a one-off alarm that walks the whole delivery path — AlarmManager, the receiver,
     * the notification channel — so the About screen can prove it end to end.
     */
    fun scheduleTest(context: Context, delayMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context, REQ_TEST,
            Intent(context, AlarmReceiver::class.java).apply { action = ACTION_TEST },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setExact(alarmManager, System.currentTimeMillis() + delayMillis, pending)
        Log.d(TAG, "test alarm in ${delayMillis}ms")
    }

    /**
     * One alarm for the next Islamic holiday, set for the eve — the Hijri day turns at sunset,
     * so the reminder lands just before that evening's Maghrib.
     */
    private fun scheduleNextHolidayReminder(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context, REQ_HOLIDAY,
            Intent(context, AlarmReceiver::class.java).apply { action = ACTION_HOLIDAY },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (!Settings.get(context).notifyHijriHolidays) {
            pending?.let { alarmManager.cancel(it); it.cancel() }
            return
        }
        val reminder = HijriHolidays.nextReminder(context) ?: return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_HOLIDAY
            putExtra(EXTRA_HOLIDAY_LABEL, reminder.labelRes)
        }
        setExact(
            alarmManager, reminder.atMillis,
            PendingIntent.getBroadcast(
                context, REQ_HOLIDAY, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        Log.d(TAG, "next holiday reminder at ${reminder.atMillis}")
    }

    private fun scheduleNextAdzan(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        // An adzan that is overdue and was never announced is re-armed for right now, instead
        // of being skipped: nextNotifiable only looks forward, so without this a reschedule
        // that lands in the gap between an alarm being due and being delivered silently
        // swallows that prayer. Bounded by CATCH_UP_WINDOW, and the journal's fired-at stamp
        // keeps it from re-firing what already rang.
        val missed = Schedule.missedNotifiable(
            context,
            lastFiredAt = WidgetHealth.lastFiredAt(context),
            since = WidgetHealth.firstSeen(context),
            now = now,
        )?.takeIf { now - it.timeMillis <= CATCH_UP_WINDOW }
        val next: PrayerEvent = missed ?: Schedule.nextNotifiable(context) ?: return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ADZAN
            putExtra(EXTRA_PRAYER, next.prayer.key)
            putExtra(EXTRA_JUMUAH, next.isJumuah)
        }
        // FLAG_UPDATE_CURRENT rewrites the extras of the alarm that is already pending, before
        // the new trigger time is set. If setting that time then fails, the old alarm survives
        // at the old time carrying the new prayer's name — Maghrib announced at Asr. Cancel it
        // rather than leave a mislabelled alarm armed.
        val pending = PendingIntent.getBroadcast(
            context, REQ_ADZAN, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            // A catch-up is already late; it wants to be delivered now, not to claim the
            // system's next-alarm slot with a time in the past.
            if (missed != null) setExact(alarmManager, now, pending)
            else setAlarmClock(context, alarmManager, next.timeMillis, pending)
        } catch (t: Throwable) {
            alarmManager.cancel(pending)
            pending.cancel()
            throw t
        }
        WidgetHealth.markArmed(context, next.prayer, next.timeMillis)
        Log.d(TAG, "next adzan ${next.prayer.key} at ${next.timeMillis}")
    }

    /**
     * The widget shows no live clock, so it only needs a refresh when the highlighted row
     * changes — at each prayer — and at midnight for the new Hijri date.
     */
    private fun scheduleNextRefresh(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        val midnight = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 5)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val nextPrayer = Schedule.currentAndNext(context, now).second?.timeMillis ?: midnight
        val target = minOf(midnight, nextPrayer + REFRESH_LAG)

        val pending = PendingIntent.getBroadcast(
            context, REQ_REFRESH,
            Intent(context, AlarmReceiver::class.java).apply { action = ACTION_REFRESH },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setExact(alarmManager, target, pending)
    }

    /**
     * The adzan is an alarm clock, and only [AlarmManager.setAlarmClock] is exempt from Doze
     * and from the app-standby buckets — `setExactAndAllowWhileIdle` is quota'd, so on a phone
     * that has not been unlocked for hours the adzan arrives late, at the moment the screen
     * comes on. The cost is the system alarm icon, which is honest: an adzan is what it says.
     */
    private fun setAlarmClock(
        context: Context,
        alarmManager: AlarmManager,
        atMillis: Long,
        pending: PendingIntent,
    ) {
        if (!canScheduleExact(context)) {
            // setAlarmClock needs the exact-alarm grant like any other exact alarm.
            setExact(alarmManager, atMillis, pending)
            return
        }
        val show = PendingIntent.getActivity(
            context, REQ_SHOW_ALARM,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, show), pending)
    }

    /** For the cosmetic alarms — a late widget refresh costs nothing. */
    private fun setExact(alarmManager: AlarmManager, atMillis: Long, pending: PendingIntent) {
        val canBeExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        if (canBeExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        } else {
            // Without the exact-alarm permission the adzan may be a few minutes late, but it
            // still fires. The settings screen offers a shortcut to grant it.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
}
