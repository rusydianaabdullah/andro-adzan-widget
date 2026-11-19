package com.rusydiana.adzantime.prayer

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Offline prayer-time calculation, based on the classical solar-position algorithm used by
 * PrayTimes.org. Everything is arithmetic on the device clock and the stored coordinates:
 * no network, no external library.
 */
object PrayerCalculator {

    /** Result of one day of calculation, as minutes-of-day in the local time zone. */
    data class DayTimes(
        val fajr: Double,
        val sunrise: Double,
        val dhuhr: Double,
        val asr: Double,
        val maghrib: Double,
        val isha: Double,
    ) {
        fun forPrayer(p: Prayer): Double = when (p) {
            Prayer.FAJR -> fajr
            Prayer.SYURUQ -> sunrise
            Prayer.DHUHR -> dhuhr
            Prayer.ASR -> asr
            Prayer.MAGHRIB -> maghrib
            Prayer.ISHA -> isha
        }
    }

    /**
     * @param calendar the day to compute (only the Y/M/D fields and time zone are used)
     * @param params   method, madhab, manual offsets
     */
    fun calculate(
        calendar: Calendar,
        latitude: Double,
        longitude: Double,
        elevation: Double,
        params: CalculationParams,
    ): DayTimes {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val jd = julianDate(year, month, day) - longitude / (15.0 * 24.0)

        // Sun-angle depression for sunrise/sunset, corrected for observer elevation.
        val horizon = 0.833 + 0.0347 * Math.sqrt(if (elevation > 0) elevation else 0.0)

        // Iterate: each estimate feeds the next, since the sun moves during the day.
        var fajr = 5.0 / 24.0
        var sunrise = 6.0 / 24.0
        var dhuhr = 12.0 / 24.0
        var asr = 13.0 / 24.0
        var sunset = 18.0 / 24.0
        var isha = 18.0 / 24.0

        repeat(3) {
            dhuhr = midDay(jd, dhuhr)
            fajr = sunAngleTime(jd, params.fajrAngle, latitude, fajr, ccw = true)
            sunrise = sunAngleTime(jd, horizon, latitude, sunrise, ccw = true)
            asr = asrTime(jd, params.madhab.shadowFactor, latitude, asr)
            sunset = sunAngleTime(jd, horizon, latitude, sunset, ccw = false)
            isha = if (params.ishaInterval > 0) {
                sunset + params.ishaInterval / 60.0 / 24.0
            } else {
                sunAngleTime(jd, params.ishaAngle, latitude, isha, ccw = false)
            }
        }

        // Convert day-fractions to local minutes-of-day.
        val tzOffsetHours = calendar.timeZone.getOffset(calendar.timeInMillis) / 3_600_000.0
        val shift = tzOffsetHours - longitude / 15.0
        fun toMinutes(dayFraction: Double, offset: Int): Double =
            (dayFraction * 24.0 + shift) * 60.0 + offset

        return DayTimes(
            fajr = toMinutes(fajr, params.offsets.fajr),
            sunrise = toMinutes(sunrise, params.offsets.syuruq),
            dhuhr = toMinutes(dhuhr, params.offsets.dhuhr),
            asr = toMinutes(asr, params.offsets.asr),
            maghrib = toMinutes(sunset, params.offsets.maghrib),
            isha = toMinutes(isha, params.offsets.isha),
        )
    }

    // ---- solar geometry -------------------------------------------------------------------

    private fun julianDate(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    /** Equation of time (in hours) and declination (in degrees) for a Julian day. */
    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * dSin(g) + 0.020 * dSin(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = fixHour(dArcTan2(dCos(e) * dSin(l), dCos(l)) / 15.0)
        val eqt = q / 15.0 - ra
        val decl = dArcSin(dSin(e) * dSin(l))
        return eqt to decl
    }

    private fun midDay(jd: Double, t: Double): Double {
        val (eqt, _) = sunPosition(jd + t)
        return fixHour(12.0 - eqt) / 24.0
    }

    /**
     * Time (as a day-fraction) at which the sun sits [angle] degrees below the horizon.
     * @param ccw true for the morning side of noon, false for the evening side
     */
    private fun sunAngleTime(
        jd: Double,
        angle: Double,
        latitude: Double,
        t: Double,
        ccw: Boolean,
    ): Double {
        val (_, decl) = sunPosition(jd + t)
        val noon = midDay(jd, t)
        val numerator = -dSin(angle) - dSin(decl) * dSin(latitude)
        val denominator = dCos(decl) * dCos(latitude)
        val ratio = (numerator / denominator).coerceIn(-1.0, 1.0)
        val hourAngle = dArcCos(ratio) / 15.0
        return noon + (if (ccw) -hourAngle else hourAngle) / 24.0
    }

    private fun asrTime(jd: Double, shadowFactor: Int, latitude: Double, t: Double): Double {
        val (_, decl) = sunPosition(jd + t)
        val angle = -dArcCot(shadowFactor + dTan(abs(latitude - decl)))
        return sunAngleTime(jd, angle, latitude, t, ccw = false)
    }

    // ---- degree-based trig helpers --------------------------------------------------------

    private fun dSin(d: Double) = sin(Math.toRadians(d))
    private fun dCos(d: Double) = cos(Math.toRadians(d))
    private fun dTan(d: Double) = tan(Math.toRadians(d))
    private fun dArcSin(x: Double) = Math.toDegrees(asin(x))
    private fun dArcCos(x: Double) = Math.toDegrees(acos(x))
    private fun dArcTan2(y: Double, x: Double) = Math.toDegrees(atan2(y, x))
    private fun dArcCot(x: Double) = Math.toDegrees(atan(1.0 / x))

    private fun fixAngle(a: Double) = fix(a, 360.0)
    private fun fixHour(a: Double) = fix(a, 24.0)
    private fun fix(a: Double, mode: Double): Double {
        val v = a - mode * floor(a / mode)
        return if (v < 0) v + mode else v
    }
}

/** Convenience: the time zone the device is currently in. */
internal fun deviceCalendar(timeZone: TimeZone = TimeZone.getDefault()): Calendar =
    Calendar.getInstance(timeZone)
