package com.rusydiana.adzantime

import com.rusydiana.adzantime.prayer.CalculationParams
import com.rusydiana.adzantime.prayer.Madhab
import com.rusydiana.adzantime.prayer.Offsets
import com.rusydiana.adzantime.prayer.PrayerCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Reference values come from published schedules for each city, so a regression in the solar
 * maths shows up as a wrong clock time rather than a wrong number of radians.
 */
class PrayerCalculatorTest {

    private fun times(
        year: Int, month: Int, day: Int,
        latitude: Double, longitude: Double, zone: String,
        params: CalculationParams,
    ): List<String> {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone(zone)).apply {
            set(year, month - 1, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val t = PrayerCalculator.calculate(calendar, latitude, longitude, 0.0, params)
        return listOf(t.fajr, t.sunrise, t.dhuhr, t.asr, t.maghrib, t.isha).map(::format)
    }

    private fun format(minutes: Double): String {
        val m = minutes.roundToInt()
        return String.format(Locale.US, "%02d:%02d", m / 60, m % 60)
    }

    private fun params(
        fajr: Double, isha: Double, interval: Int = 0,
        madhab: Madhab = Madhab.SHAFI, offsets: Offsets = Offsets(),
    ) = CalculationParams(fajr, isha, interval, madhab, offsets)

    /**
     * The published Kemenag schedule includes the couple of safety minutes ("ihtiyati") that
     * printed timetables add. [PrayerCalculator] deliberately knows nothing about calculation
     * methods — it is handed angles and offsets — so the test supplies that margin itself, the
     * same way Settings.calculationParams() supplies it from Method.ihtiyatiMinutes for anyone
     * on Kemenag. Leaving it out was why the app read a couple of minutes early against every
     * mosque in Indonesia.
     */
    @Test
    fun `jakarta matches kemenag schedule`() {
        val ihtiyati = Offsets(fajr = 2, syuruq = -2, dhuhr = 2, asr = 2, maghrib = 2, isha = 2)
        assertEquals(
            listOf("04:36", "05:49", "11:53", "15:11", "17:54", "19:03"),
            times(
                2026, 9, 5, -6.2088, 106.8456, "Asia/Jakarta",
                params(20.0, 18.0, offsets = ihtiyati),
            ),
        )
    }

    /** The same day and place with no correction: every time is the raw calculated one. */
    @Test
    fun `offsets default to none`() {
        assertEquals(
            listOf("04:34", "05:51", "11:51", "15:09", "17:52", "19:01"),
            times(2026, 9, 5, -6.2088, 106.8456, "Asia/Jakarta", params(20.0, 18.0)),
        )
    }

    @Test
    fun `makkah matches umm al qura`() {
        assertEquals(
            listOf("04:48", "06:05", "12:19", "15:45", "18:33", "20:03"),
            times(
                2026, 9, 5, 21.4225, 39.8262, "Asia/Riyadh",
                params(18.5, 0.0, interval = 90),
            ),
        )
    }

    @Test
    fun `london matches muslim world league`() {
        assertEquals(
            listOf("04:23", "06:15", "12:09", "15:20", "18:05", "19:51"),
            times(2026, 3, 15, 51.5074, -0.1278, "Europe/London", params(18.0, 17.0)),
        )
    }

    @Test
    fun `hanafi asr falls later than shafii`() {
        val shafii = times(2026, 9, 5, -6.2088, 106.8456, "Asia/Jakarta", params(20.0, 18.0))
        val hanafi = times(
            2026, 9, 5, -6.2088, 106.8456, "Asia/Jakarta",
            params(20.0, 18.0, madhab = Madhab.HANAFI),
        )
        assert(hanafi[3] > shafii[3]) { "Hanafi Asr ${hanafi[3]} should be after ${shafii[3]}" }
    }
}
