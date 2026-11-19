package com.rusydiana.adzantime.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rusydiana.adzantime.data.AdzanSound
import com.rusydiana.adzantime.data.Settings
import com.rusydiana.adzantime.data.WidgetHealth
import com.rusydiana.adzantime.prayer.Prayer
import com.rusydiana.adzantime.prayer.Schedule
import com.rusydiana.adzantime.widget.AdzanWidgetProvider

/** Fired by [AdzanScheduler]: notify, play, refresh the widget, then arm the next alarm. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Rescheduling runs in the finally, not first: anything below can throw — a blocked
        // foreground-service start, a missing custom sound — and the chain must outlive that,
        // but it also has to see this firing's fired-at stamp. Reschedule before the stamp is
        // written and the catch-up in the scheduler re-arms the adzan that is ringing right
        // now, announcing it twice.
        try {
            handle(context, intent)
        } finally {
            runCatching { AdzanScheduler.rescheduleAll(context) }
            runCatching { AdzanWidgetProvider.refreshAll(context) }
        }
    }

    private fun handle(context: Context, intent: Intent) {
        when (intent.action) {
            AdzanScheduler.ACTION_ADZAN -> {
                // What the alarm was armed with, and what the clock says is actually due. They
                // disagree when a pending alarm's payload was rewritten under it, and then the
                // payload is the wrong one — announcing Maghrib at Asr time. Trust the clock,
                // and keep the payload only for an alarm delivered too late to place.
                val armed = Prayer.fromKey(intent.getStringExtra(AdzanScheduler.EXTRA_PRAYER))
                val due = Schedule.dueAt(context)
                val prayer = due?.prayer ?: armed
                val isJumuah = due?.isJumuah
                    ?: intent.getBooleanExtra(AdzanScheduler.EXTRA_JUMUAH, false)
                if (prayer != null) runCatching { fire(context, prayer, isJumuah) }
            }
            AdzanScheduler.ACTION_TEST -> {
                AdzanNotifier.notifyTest(context)
            }
            AdzanScheduler.ACTION_HOLIDAY -> {
                val label = intent.getIntExtra(AdzanScheduler.EXTRA_HOLIDAY_LABEL, 0)
                if (label != 0 && Settings.get(context).notifyHijriHolidays) {
                    AdzanNotifier.notifyHoliday(context, label)
                }
            }
        }
    }

    private fun fire(context: Context, prayer: Prayer, isJumuah: Boolean) {
        val settings = Settings.get(context)
        if (!settings.isNotificationEnabled(prayer)) return

        val sound = if (isJumuah) settings.jumuahSound else settings.soundOf(prayer)
        AdzanNotifier.notifyPrayer(context, prayer, isJumuah, sound)
        // Recorded against the time the adzan was *due*, not the time it was announced: a
        // late delivery still counts as delivered, and the widget's missed-adzan check
        // compares against the scheduled instant.
        val dueAt = Schedule.dueAt(context)?.timeMillis ?: System.currentTimeMillis()
        WidgetHealth.markFired(context, prayer, dueAt)

        if (sound != AdzanSound.SILENT) {
            // The notification is already posted, so a refused foreground start only costs
            // the audio.
            runCatching { AdzanService.start(context, prayer, isJumuah, sound) }
        }
    }
}
