package com.rusydiana.adzantime.prayer

import com.rusydiana.adzantime.R

/** The six rows shown in the widget. Syuruq is not a prayer, but it is a time people watch. */
enum class Prayer(val key: String, val labelRes: Int) {
    FAJR("fajr", R.string.prayer_fajr),
    SYURUQ("syuruq", R.string.prayer_syuruq),
    DHUHR("dhuhr", R.string.prayer_dhuhr),
    ASR("asr", R.string.prayer_asr),
    MAGHRIB("maghrib", R.string.prayer_maghrib),
    ISHA("isha", R.string.prayer_isha);

    companion object {
        fun fromKey(key: String?): Prayer? = entries.firstOrNull { it.key == key }
    }
}

enum class Madhab(val shadowFactor: Int, val labelRes: Int) {
    SHAFI(1, R.string.madhab_shafi),
    HANAFI(2, R.string.madhab_hanafi),
}

/**
 * Calculation methods. [ishaInterval] > 0 means Isha is a fixed number of minutes after
 * Maghrib and [ishaAngle] is ignored (Umm al-Qura works this way).
 */
enum class Method(
    val fajrAngle: Double,
    val ishaAngle: Double,
    val ishaInterval: Int,
    val labelRes: Int,
    /**
     * The method's own safety margin in minutes ("ihtiyati"), added to every prayer and
     * subtracted from Syuruq.
     *
     * Published timetables are not raw astronomical times. Kemenag's — the ones Indonesian
     * mosques print and call the adzan from — carry a two-minute margin that covers the spread
     * across a kabupaten, the rounding, and simply not wanting to be early. Computing the bare
     * astronomy and showing it next to a mosque that uses the table is how this app read a few
     * minutes early everywhere.
     *
     * Zero for the methods whose published tables do not do this. A user's own per-prayer
     * offsets are added on top, so correcting for a specific mosque still works the same way.
     */
    val ihtiyatiMinutes: Int = 0,
) {
    KEMENAG(20.0, 18.0, 0, R.string.method_kemenag, ihtiyatiMinutes = 2),
    MWL(18.0, 17.0, 0, R.string.method_mwl),
    ISNA(15.0, 15.0, 0, R.string.method_isna),
    EGYPT(19.5, 17.5, 0, R.string.method_egypt),
    KARACHI(18.0, 18.0, 0, R.string.method_karachi),
    UMM_AL_QURA(18.5, 0.0, 90, R.string.method_umm_al_qura),
    SINGAPORE(20.0, 18.0, 0, R.string.method_singapore, ihtiyatiMinutes = 2),
    CUSTOM(18.0, 17.0, 0, R.string.method_custom),
}

/** Per-prayer manual correction in minutes ("ihtiyati"). */
data class Offsets(
    val fajr: Int = 0,
    val syuruq: Int = 0,
    val dhuhr: Int = 0,
    val asr: Int = 0,
    val maghrib: Int = 0,
    val isha: Int = 0,
)

data class CalculationParams(
    val fajrAngle: Double,
    val ishaAngle: Double,
    val ishaInterval: Int,
    val madhab: Madhab,
    val offsets: Offsets,
)
