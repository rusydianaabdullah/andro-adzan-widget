package com.rusydiana.adzantime.data

import com.rusydiana.adzantime.R

/**
 * The bundled sounds, which live in res/raw so nothing is ever downloaded, plus [CUSTOM]:
 * a file the user picked or recorded, stored as a URI in [Settings].
 */
enum class AdzanSound(
    val rawRes: Int?,
    val labelRes: Int,
    val isFajrVersion: Boolean = false,
    val isCustom: Boolean = false,
) {
    SILENT(null, R.string.sound_silent),
    BEEP(R.raw.notif_beep, R.string.sound_beep),
    MAKKAH(R.raw.adzan_makkah, R.string.sound_makkah),
    HARAM(R.raw.adzan_haram, R.string.sound_haram),
    JUMUAH(R.raw.adzan_jumuah, R.string.sound_jumuah),
    SUBUH_MAKKAH(R.raw.adzan_subuh_makkah, R.string.sound_subuh_makkah, isFajrVersion = true),
    SUBUH_MADINAH(R.raw.adzan_subuh_madinah, R.string.sound_subuh_madinah, isFajrVersion = true),
    CUSTOM(null, R.string.sound_custom, isCustom = true);

    companion object {
        fun fromName(name: String?, fallback: AdzanSound): AdzanSound =
            entries.firstOrNull { it.name == name } ?: fallback

        /** Fajr gets the recordings that include "as-salatu khairum minan-naum". */
        val fajrChoices = listOf(SILENT, BEEP, SUBUH_MAKKAH, SUBUH_MADINAH, MAKKAH, HARAM, CUSTOM)
        val standardChoices = listOf(SILENT, BEEP, MAKKAH, HARAM, JUMUAH, CUSTOM)
        val syuruqChoices = listOf(SILENT, BEEP, MAKKAH, HARAM, CUSTOM)
    }
}
