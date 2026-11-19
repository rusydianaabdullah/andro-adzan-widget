package com.rusydiana.adzantime.prayer

import android.content.Context
import android.icu.util.IslamicCalendar
import com.rusydiana.adzantime.R
import java.util.Calendar

/**
 * The Islamic holidays the calendar marks, and the reminder that precedes them.
 *
 * A Hijri day begins at sunset, so the eve of a holiday is the Gregorian day before it: the
 * reminder fires shortly before that evening's Maghrib, which is the moment the day turns.
 */
object HijriHolidays {

    /** A holiday that falls on the same Hijri date every year. Month is 0-based. */
    data class Holiday(
        val month: Int,
        val day: Int,
        val labelRes: Int,
        /** False for the trailing days of Tashriq, so one holiday sends one reminder. */
        val notifiable: Boolean = true,
    )

    val ALL = listOf(
        Holiday(0, 1, R.string.holiday_new_year),
        Holiday(0, 10, R.string.holiday_ashura),
        Holiday(2, 12, R.string.holiday_mawlid),
        Holiday(6, 27, R.string.holiday_isra_miraj),
        Holiday(7, 15, R.string.holiday_nisfu_shaban),
        Holiday(8, 1, R.string.holiday_ramadan_start),
        Holiday(8, 17, R.string.holiday_nuzulul_quran),
        Holiday(9, 1, R.string.holiday_eid_fitr),
        Holiday(11, 9, R.string.holiday_arafah),
        Holiday(11, 10, R.string.holiday_eid_adha),
        Holiday(11, 11, R.string.holiday_tasyrik),
        Holiday(11, 12, R.string.holiday_tasyrik, notifiable = false),
        Holiday(11, 13, R.string.holiday_tasyrik, notifiable = false),
    )

    /** A reminder to fire at [atMillis] for the holiday starting that evening. */
    data class Reminder(val labelRes: Int, val atMillis: Long)

    /** How long before the eve's Maghrib the reminder arrives. */
    private const val LEAD_MINUTES = 15

    /** How far ahead to look; a Hijri year is under 355 days, so this always finds one. */
    private const val SEARCH_DAYS = 400

    fun of(month: Int, day: Int): Holiday? =
        ALL.firstOrNull { it.month == month && it.day == day }

    /**
     * The first reminder strictly after [from]. Walks forward day by day converting to the
     * Hijri calendar, which sidesteps the era pitfalls of building a Hijri date field by field.
     */
    fun nextReminder(context: Context, from: Long = System.currentTimeMillis()): Reminder? {
        val cursor = Calendar.getInstance().apply { timeInMillis = from }
        val hijri = IslamicCalendar().apply {
            calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        }
        repeat(SEARCH_DAYS) {
            hijri.timeInMillis = cursor.timeInMillis
            val holiday = of(hijri.get(Calendar.MONTH), hijri.get(Calendar.DAY_OF_MONTH))
            if (holiday != null && holiday.notifiable) {
                val eve = (cursor.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
                val maghrib = Schedule.forDay(context, eve).timeOf(Prayer.MAGHRIB)
                val at = maghrib - LEAD_MINUTES * 60_000L
                if (at > from) return Reminder(holiday.labelRes, at)
            }
            cursor.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }
}
