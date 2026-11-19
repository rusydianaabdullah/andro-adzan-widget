package com.rusydiana.adzantime.data

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import com.rusydiana.adzantime.prayer.Prayer

/**
 * A small journal of what the alarm chain actually did: when it was last armed, when an adzan
 * last fired, and when the widget was last redrawn.
 *
 * The chain in [com.rusydiana.adzantime.alarm.AdzanScheduler] keeps only one alarm alive at a
 * time, so anything that clears the app's alarms — a force-stop, an OEM battery manager —
 * ends it silently. Nothing in the app can notice that, because noticing would itself require
 * the app to run. What this journal buys is the *next* time any code does run: it can compare
 * what was armed against what fired and tell the user an adzan was missed, rather than leaving
 * them to work it out from the silence.
 */
object WidgetHealth {

    private const val PREFS = "widget_health"
    private const val KEY_REFRESH_ELAPSED = "refresh_elapsed"
    private const val KEY_REFRESH_WALL = "refresh_wall"
    private const val KEY_FIRED_AT = "fired_at"
    private const val KEY_FIRED_PRAYER = "fired_prayer"
    private const val KEY_ARMED_AT = "armed_at"
    private const val KEY_ARMED_PRAYER = "armed_prayer"
    private const val KEY_FIRST_SEEN = "first_seen"
    private const val KEY_ACKNOWLEDGED_AT = "acknowledged_at"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * When the widget was last redrawn, on the monotonic clock the launcher's Chronometer
     * counts from. Zero when nothing has been recorded yet.
     */
    fun lastRefreshElapsed(context: Context): Long =
        prefs(context).getLong(KEY_REFRESH_ELAPSED, 0L)

    fun lastRefreshWall(context: Context): Long = prefs(context).getLong(KEY_REFRESH_WALL, 0L)

    fun lastFiredAt(context: Context): Long = prefs(context).getLong(KEY_FIRED_AT, 0L)

    fun lastFiredPrayer(context: Context): Prayer? =
        Prayer.fromKey(prefs(context).getString(KEY_FIRED_PRAYER, null))

    /**
     * The last moment the user actually opened the app. A prayer missed before it has been
     * seen — the widget has already said so — so the countdown goes back to the next prayer
     * instead of running negative for the rest of the day.
     */
    fun acknowledgedAt(context: Context): Long = prefs(context).getLong(KEY_ACKNOWLEDGED_AT, 0L)

    /**
     * The latest point up to which every due adzan is accounted for, either because it rang
     * or because the user has since seen that it did not.
     */
    fun accountedThrough(context: Context): Long =
        maxOf(lastFiredAt(context), acknowledgedAt(context))

    fun lastArmedAt(context: Context): Long = prefs(context).getLong(KEY_ARMED_AT, 0L)

    fun lastArmedPrayer(context: Context): Prayer? =
        Prayer.fromKey(prefs(context).getString(KEY_ARMED_PRAYER, null))

    /**
     * The first moment this journal ever recorded anything. A prayer that passed before it is
     * not evidence of a missed adzan — the app simply was not keeping score yet, and a fresh
     * install must not open on a red widget.
     */
    fun firstSeen(context: Context): Long = prefs(context).getLong(KEY_FIRST_SEEN, 0L)

    /** Called wherever the widget is actually redrawn: this is the heartbeat the user sees. */
    fun markRefreshed(context: Context) {
        prefs(context).edit {
            putLong(KEY_REFRESH_ELAPSED, SystemClock.elapsedRealtime())
            putLong(KEY_REFRESH_WALL, System.currentTimeMillis())
            seedFirstSeen(context)
        }
    }

    fun markArmed(context: Context, prayer: Prayer, atMillis: Long) {
        prefs(context).edit {
            putLong(KEY_ARMED_AT, atMillis)
            putString(KEY_ARMED_PRAYER, prayer.key)
            seedFirstSeen(context)
        }
    }

    /** Called when the app comes to the foreground: the user is looking, the news is delivered. */
    fun markSeen(context: Context) {
        prefs(context).edit {
            putLong(KEY_ACKNOWLEDGED_AT, System.currentTimeMillis())
            seedFirstSeen(context)
        }
    }

    fun markFired(context: Context, prayer: Prayer, atMillis: Long) {
        prefs(context).edit {
            putLong(KEY_FIRED_AT, atMillis)
            putString(KEY_FIRED_PRAYER, prayer.key)
            seedFirstSeen(context)
        }
    }

    private fun android.content.SharedPreferences.Editor.seedFirstSeen(context: Context) {
        if (prefs(context).getLong(KEY_FIRST_SEEN, 0L) == 0L) {
            putLong(KEY_FIRST_SEEN, System.currentTimeMillis())
        }
    }
}
