package com.rusydiana.adzantime.prayer

import android.content.Context
import android.icu.util.IslamicCalendar
import com.rusydiana.adzantime.R
import java.util.Calendar

/**
 * The Hijri date as text, for the widget's header row. Umm al-Qura, matching the calendar
 * tab, so the two never disagree by a day.
 */
object HijriDate {

    private val MONTH_NAMES = intArrayOf(
        R.string.hijri_month_1, R.string.hijri_month_2, R.string.hijri_month_3,
        R.string.hijri_month_4, R.string.hijri_month_5, R.string.hijri_month_6,
        R.string.hijri_month_7, R.string.hijri_month_8, R.string.hijri_month_9,
        R.string.hijri_month_10, R.string.hijri_month_11, R.string.hijri_month_12,
    )

    /** "12 Rabiul Awal 1447 H". */
    fun today(context: Context, atMillis: Long = System.currentTimeMillis()): String {
        val hijri = IslamicCalendar().apply {
            calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
            timeInMillis = atMillis
        }
        val month = hijri.get(Calendar.MONTH).coerceIn(0, 11)
        return context.getString(
            R.string.hijri_date_format,
            hijri.get(Calendar.DAY_OF_MONTH),
            context.getString(MONTH_NAMES[month]),
            hijri.get(Calendar.YEAR),
        )
    }

    /** Day and month only, for the one-cell-high widget where the year does not fit. */
    fun todayShort(context: Context, atMillis: Long = System.currentTimeMillis()): String {
        val hijri = IslamicCalendar().apply {
            calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
            timeInMillis = atMillis
        }
        val month = hijri.get(Calendar.MONTH).coerceIn(0, 11)
        return "${hijri.get(Calendar.DAY_OF_MONTH)} ${context.getString(MONTH_NAMES[month])}"
    }
}
