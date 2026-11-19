package com.rusydiana.adzantime.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * A note the user pinned to a Hijri date. Anchored on the Hijri calendar, not the Gregorian
 * one, because that is the calendar the grid is drawn in and the one a yearly repeat is meant
 * to follow — a birthday kept in Hijri comes back on the same Hijri day.
 */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val year: Int,
    /** Zero-based, matching Calendar.MONTH. */
    val month: Int,
    val day: Int,
    val repeatsYearly: Boolean,
) {
    fun falls(onYear: Int, onMonth: Int, onDay: Int): Boolean =
        month == onMonth && day == onDay && (repeatsYearly || year == onYear)
}

/**
 * The user's own calendar entries, in their own preferences file so a settings reset never
 * takes them with it. Synchronous like the rest of the stores here; the lists are tiny.
 */
object CalendarEvents {

    fun all(context: Context): List<CalendarEvent> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getJSONObject(it).toEvent() }
        }.getOrDefault(emptyList())
            .sortedWith(compareBy({ it.month }, { it.day }, { it.id }))
    }

    /** Replaces the entry with the same id, or appends when it is new. */
    fun save(context: Context, event: CalendarEvent) {
        val kept = all(context).filterNot { it.id == event.id }
        write(context, kept + event)
    }

    fun delete(context: Context, id: Long) {
        write(context, all(context).filterNot { it.id == id })
    }

    private fun write(context: Context, events: List<CalendarEvent>) {
        val array = JSONArray()
        events.forEach { array.put(it.toJson()) }
        prefs(context).edit { putString(KEY, array.toString()) }
    }

    private fun JSONObject.toEvent() = CalendarEvent(
        id = getLong("id"),
        title = getString("title"),
        year = getInt("year"),
        month = getInt("month"),
        day = getInt("day"),
        repeatsYearly = optBoolean("repeats", false),
    )

    private fun CalendarEvent.toJson() = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("year", year)
        put("month", month)
        put("day", day)
        put("repeats", repeatsYearly)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private const val FILE = "adzan_events"
    private const val KEY = "events"
}
