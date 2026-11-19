package com.rusydiana.adzantime.prayer

import android.content.Context
import com.rusydiana.adzantime.data.AdzanSound
import com.rusydiana.adzantime.data.Settings
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.floor

/** One prayer on one concrete day. */
data class PrayerEvent(
    val prayer: Prayer,
    val timeMillis: Long,
    val isJumuah: Boolean,
)

/** A whole day of prayer times, already resolved to wall-clock instants. */
data class DaySchedule(
    val dayStart: Calendar,
    val events: List<PrayerEvent>,
) {
    fun timeOf(prayer: Prayer): Long = events.first { it.prayer == prayer }.timeMillis
    val isFriday: Boolean get() = dayStart.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY
}

object Schedule {

    /** Builds the schedule for the day containing [dayCalendar]. */
    fun forDay(context: Context, dayCalendar: Calendar): DaySchedule {
        val settings = Settings.get(context)
        val day = (dayCalendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val times = PrayerCalculator.calculate(
            calendar = day,
            latitude = settings.latitude,
            longitude = settings.longitude,
            elevation = settings.elevation,
            params = settings.calculationParams(),
        )

        val friday = day.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY
        val jumuahActive = friday && settings.jumuahEnabled
        val fixedJumuah = settings.jumuahFixedMinutes

        val events = Prayer.entries.map { prayer ->
            val isJumuah = jumuahActive && prayer == Prayer.DHUHR
            val minutes = if (isJumuah && fixedJumuah >= 0) {
                fixedJumuah.toDouble()
            } else {
                times.forPrayer(prayer)
            }
            PrayerEvent(
                prayer = prayer,
                timeMillis = day.timeInMillis + minutesToMillis(prayer, minutes),
                isJumuah = isJumuah,
            )
        }.sortedBy { it.timeMillis }

        return DaySchedule(day, events)
    }

    fun today(context: Context): DaySchedule = forDay(context, Calendar.getInstance())

    /**
     * The next event strictly after [fromMillis] that the user asked to be notified about.
     * Looks up to 3 days ahead so a fully-disabled today still resolves.
     */
    fun nextNotifiable(context: Context, fromMillis: Long = System.currentTimeMillis()): PrayerEvent? {
        val settings = Settings.get(context)
        val cursor = Calendar.getInstance().apply { timeInMillis = fromMillis }
        repeat(3) {
            val schedule = forDay(context, cursor)
            val hit = schedule.events.firstOrNull { event ->
                event.timeMillis > fromMillis && settings.isNotificationEnabled(event.prayer)
            }
            if (hit != null) return hit
            cursor.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }

    /**
     * The event an alarm firing at [now] is for: the most recent one whose time has just
     * passed. An alarm carries the prayer it was armed with, but that payload can go stale
     * while the alarm stays pending, so the clock — not the payload — is the authority on
     * which adzan is actually due. Null when nothing is close enough to [tolerance], which
     * is the case for an alarm the system held back for a long time; the caller then has
     * nothing better than the payload to go on.
     */
    fun dueAt(
        context: Context,
        now: Long = System.currentTimeMillis(),
        tolerance: Long = 15 * 60_000L,
    ): PrayerEvent? {
        val yesterday = forDay(
            context, Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -1) }
        )
        val today = forDay(context, Calendar.getInstance().apply { timeInMillis = now })
        // An exact alarm may land a hair before the whole minute it was set for, so times
        // slightly in the future still count as due.
        return (yesterday.events + today.events).lastOrNull { event ->
            event.timeMillis <= now + 60_000L && now - event.timeMillis <= tolerance
        }
    }

    /**
     * The most recent notifiable event whose time has already passed, looking back over the
     * last two days. This is what an adzan *should* have fired for, so comparing it against
     * [com.rusydiana.adzantime.data.WidgetHealth.lastFiredAt] says whether the chain is alive.
     */
    fun lastNotifiable(context: Context, now: Long = System.currentTimeMillis()): PrayerEvent? {
        val settings = Settings.get(context)
        val yesterday = forDay(
            context, Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -1) }
        )
        val today = forDay(context, Calendar.getInstance().apply { timeInMillis = now })
        return (yesterday.events + today.events).lastOrNull { event ->
            event.timeMillis <= now && settings.isNotificationEnabled(event.prayer)
        }
    }

    /**
     * The adzan that was due and never announced, or null when the chain is keeping up.
     *
     * Only ever reports an event the journal was already running for: before [since] the app
     * was not keeping score, and a fresh install must not open on a red widget. The one-minute
     * grace absorbs an alarm delivered a hair early, which [dueAt] already allows for.
     */
    fun missedNotifiable(
        context: Context,
        lastFiredAt: Long,
        since: Long,
        now: Long = System.currentTimeMillis(),
    ): PrayerEvent? {
        if (since <= 0L) return null
        val due = lastNotifiable(context, now) ?: return null
        if (due.timeMillis < since) return null
        return due.takeIf { lastFiredAt < it.timeMillis - 60_000L }
    }

    /** The event currently in progress — used to highlight a row in the widget. */
    fun currentAndNext(context: Context, now: Long = System.currentTimeMillis()): Pair<PrayerEvent?, PrayerEvent?> {
        val today = today(context)
        val next = today.events.firstOrNull { it.timeMillis > now }
            ?: forDay(context, Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) })
                .events.firstOrNull()
        val current = today.events.lastOrNull { it.timeMillis <= now }
            ?: forDay(context, Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) })
                .events.lastOrNull()
        return current to next
    }

    /** Friday's Dhuhr gets its own sound; everything else uses the per-prayer choice. */
    fun soundFor(settings: Settings, event: PrayerEvent): AdzanSound =
        if (event.isJumuah) settings.jumuahSound else settings.soundOf(event.prayer)

    /**
     * Whole minutes, rounded the way a printed prayer timetable rounds — which is not to the
     * nearest minute.
     *
     * A prayer time is the instant a window *opens*, so the printed minute is rounded up: 11:45:40
     * is announced as 11:46, never 11:45, because 11:45 would have people praying forty seconds
     * early. Syuruq is the mirror image — it closes Fajr's window rather than opening one — so it
     * rounds down for exactly the same reason.
     *
     * roundToInt() went to the nearest minute in both directions, which is how every time here
     * could read up to a minute earlier than the local mosque's table.
     */
    private fun minutesToMillis(prayer: Prayer, minutes: Double): Long {
        val whole = if (prayer == Prayer.SYURUQ) floor(minutes) else ceil(minutes)
        return whole.toLong() * 60_000L
    }
}
