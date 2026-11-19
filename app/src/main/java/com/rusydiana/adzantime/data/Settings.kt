package com.rusydiana.adzantime.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.rusydiana.adzantime.prayer.CalculationParams
import com.rusydiana.adzantime.prayer.Madhab
import com.rusydiana.adzantime.prayer.Method
import com.rusydiana.adzantime.prayer.Offsets
import com.rusydiana.adzantime.prayer.Prayer

/**
 * Every user choice, in one SharedPreferences file. Deliberately synchronous: broadcast
 * receivers and the widget provider read this on their own threads with no coroutine scope.
 */
class Settings private constructor(private val prefs: SharedPreferences) {

    // ---- location ---------------------------------------------------------------------

    var latitude: Double
        get() = prefs.getFloat(KEY_LAT, DEFAULT_LAT).toDouble()
        set(v) = prefs.edit { putFloat(KEY_LAT, v.toFloat()) }

    var longitude: Double
        get() = prefs.getFloat(KEY_LON, DEFAULT_LON).toDouble()
        set(v) = prefs.edit { putFloat(KEY_LON, v.toFloat()) }

    var elevation: Double
        get() = prefs.getFloat(KEY_ELEVATION, 0f).toDouble()
        set(v) = prefs.edit { putFloat(KEY_ELEVATION, v.toFloat()) }

    var cityName: String
        get() = prefs.getString(KEY_CITY, "") ?: ""
        set(v) = prefs.edit { putString(KEY_CITY, v) }

    /** Refresh the coordinates from the device every time the app is opened. */
    var autoUpdateLocation: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LOCATION, true)
        set(v) = prefs.edit { putBoolean(KEY_AUTO_LOCATION, v) }

    var hasLocation: Boolean
        get() = prefs.getBoolean(KEY_HAS_LOCATION, false)
        set(v) = prefs.edit { putBoolean(KEY_HAS_LOCATION, v) }

    // ---- calculation ------------------------------------------------------------------

    var method: Method
        get() = runCatching { Method.valueOf(prefs.getString(KEY_METHOD, null) ?: "") }
            .getOrDefault(Method.KEMENAG)
        set(v) = prefs.edit { putString(KEY_METHOD, v.name) }

    var madhab: Madhab
        get() = runCatching { Madhab.valueOf(prefs.getString(KEY_MADHAB, null) ?: "") }
            .getOrDefault(Madhab.SHAFI)
        set(v) = prefs.edit { putString(KEY_MADHAB, v.name) }

    /** Only consulted when [method] is CUSTOM. */
    var customFajrAngle: Double
        get() = prefs.getFloat(KEY_CUSTOM_FAJR, 20f).toDouble()
        set(v) = prefs.edit { putFloat(KEY_CUSTOM_FAJR, v.toFloat()) }

    var customIshaAngle: Double
        get() = prefs.getFloat(KEY_CUSTOM_ISHA, 18f).toDouble()
        set(v) = prefs.edit { putFloat(KEY_CUSTOM_ISHA, v.toFloat()) }

    /**
     * Zero by default, for every method. The correction is the user's own adjustment, so the
     * app should not quietly start two minutes away from what the method computes.
     */
    fun offsetOf(prayer: Prayer): Int = prefs.getInt(KEY_OFFSET + prayer.key, 0)

    fun setOffset(prayer: Prayer, minutes: Int) =
        prefs.edit { putInt(KEY_OFFSET + prayer.key, minutes) }

    fun calculationParams(): CalculationParams {
        val m = method
        // The method's own safety margin, on top of which the user's per-prayer corrections are
        // added rather than replaced — so "Kemenag, and my mosque is a minute after that" stays
        // expressible, and a manual offset still means what it says. Syuruq moves the other way:
        // it ends Fajr's window instead of beginning one, so a margin there has to be earlier,
        // not later.
        val safety = m.ihtiyatiMinutes
        return CalculationParams(
            fajrAngle = if (m == Method.CUSTOM) customFajrAngle else m.fajrAngle,
            ishaAngle = if (m == Method.CUSTOM) customIshaAngle else m.ishaAngle,
            ishaInterval = m.ishaInterval,
            madhab = madhab,
            offsets = Offsets(
                fajr = offsetOf(Prayer.FAJR) + safety,
                syuruq = offsetOf(Prayer.SYURUQ) - safety,
                dhuhr = offsetOf(Prayer.DHUHR) + safety,
                asr = offsetOf(Prayer.ASR) + safety,
                maghrib = offsetOf(Prayer.MAGHRIB) + safety,
                isha = offsetOf(Prayer.ISHA) + safety,
            ),
        )
    }

    // ---- notifications ----------------------------------------------------------------

    fun isNotificationEnabled(prayer: Prayer): Boolean =
        prefs.getBoolean(KEY_NOTIFY + prayer.key, prayer != Prayer.SYURUQ)

    fun setNotificationEnabled(prayer: Prayer, enabled: Boolean) =
        prefs.edit { putBoolean(KEY_NOTIFY + prayer.key, enabled) }

    fun soundOf(prayer: Prayer): AdzanSound {
        val fallback = when (prayer) {
            Prayer.FAJR -> AdzanSound.SUBUH_MAKKAH
            Prayer.SYURUQ -> AdzanSound.BEEP
            else -> AdzanSound.MAKKAH
        }
        return AdzanSound.fromName(prefs.getString(KEY_SOUND + prayer.key, null), fallback)
    }

    fun setSound(prayer: Prayer, sound: AdzanSound) =
        prefs.edit { putString(KEY_SOUND + prayer.key, sound.name) }

    /**
     * Treat the adzan like an alarm clock: the notification carries a full-screen intent, so
     * it lights the screen and takes over the lock screen instead of waiting to be found.
     */
    var alarmStyleNotifications: Boolean
        get() = prefs.getBoolean(KEY_ALARM_STYLE, false)
        set(v) = prefs.edit { putBoolean(KEY_ALARM_STYLE, v) }

    /** The user's own sound — a picked document or a recording. Null when none is set. */
    var customSoundUri: String?
        get() = prefs.getString(KEY_CUSTOM_SOUND_URI, null)
        set(v) = prefs.edit { putString(KEY_CUSTOM_SOUND_URI, v) }

    var customSoundName: String?
        get() = prefs.getString(KEY_CUSTOM_SOUND_NAME, null)
        set(v) = prefs.edit { putString(KEY_CUSTOM_SOUND_NAME, v) }

    // ---- jumuah -----------------------------------------------------------------------

    /** When on, Friday's Dhuhr uses [jumuahSound] and optionally [jumuahFixedMinutes]. */
    var jumuahEnabled: Boolean
        get() = prefs.getBoolean(KEY_JUMUAH_ENABLED, true)
        set(v) = prefs.edit { putBoolean(KEY_JUMUAH_ENABLED, v) }

    var jumuahSound: AdzanSound
        get() = AdzanSound.fromName(prefs.getString(KEY_JUMUAH_SOUND, null), AdzanSound.SILENT)
        set(v) = prefs.edit { putString(KEY_JUMUAH_SOUND, v.name) }

    /** Minutes past midnight for a fixed khutbah time, or -1 to follow the calculated Dhuhr. */
    var jumuahFixedMinutes: Int
        get() = prefs.getInt(KEY_JUMUAH_TIME, -1)
        set(v) = prefs.edit { putInt(KEY_JUMUAH_TIME, v) }

    // ---- app --------------------------------------------------------------------------

    /** "system", "en" or "in". */
    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "system") ?: "system"
        set(v) = prefs.edit { putString(KEY_LANGUAGE, v) }

    var use24Hour: Boolean
        get() = prefs.getBoolean(KEY_24H, true)
        set(v) = prefs.edit { putBoolean(KEY_24H, v) }

    /** Marks the Islamic holidays on the Hijri calendar and lists them under the month. */
    var showHijriHolidays: Boolean
        get() = prefs.getBoolean(KEY_HIJRI_HOLIDAYS, true)
        set(v) = prefs.edit { putBoolean(KEY_HIJRI_HOLIDAYS, v) }

    /** Sends a reminder before Maghrib on the eve of each Islamic holiday. */
    var notifyHijriHolidays: Boolean
        get() = prefs.getBoolean(KEY_HIJRI_HOLIDAY_NOTIF, true)
        set(v) = prefs.edit { putBoolean(KEY_HIJRI_HOLIDAY_NOTIF, v) }

    companion object {
        private const val FILE = "adzan_settings"
        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"
        private const val KEY_ELEVATION = "elevation"
        private const val KEY_CITY = "city"
        private const val KEY_HAS_LOCATION = "has_location"
        private const val KEY_AUTO_LOCATION = "auto_location"
        private const val KEY_METHOD = "method"
        private const val KEY_MADHAB = "madhab"
        private const val KEY_CUSTOM_FAJR = "custom_fajr_angle"
        private const val KEY_CUSTOM_ISHA = "custom_isha_angle"
        private const val KEY_OFFSET = "offset_"
        private const val KEY_NOTIFY = "notify_"
        private const val KEY_SOUND = "sound_"
        private const val KEY_ALARM_STYLE = "alarm_style"
        private const val KEY_CUSTOM_SOUND_URI = "custom_sound_uri"
        private const val KEY_CUSTOM_SOUND_NAME = "custom_sound_name"
        private const val KEY_JUMUAH_ENABLED = "jumuah_enabled"
        private const val KEY_JUMUAH_SOUND = "jumuah_sound"
        private const val KEY_JUMUAH_TIME = "jumuah_time"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_24H = "use_24h"
        private const val KEY_HIJRI_HOLIDAYS = "hijri_holidays"
        private const val KEY_HIJRI_HOLIDAY_NOTIF = "hijri_holiday_notif"

        /** Jakarta, so a fresh install shows something sensible before the first GPS fix. */
        private const val DEFAULT_LAT = -6.2088f
        private const val DEFAULT_LON = 106.8456f

        @Volatile
        private var instance: Settings? = null

        fun get(context: Context): Settings = instance ?: synchronized(this) {
            instance ?: Settings(
                context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            ).also { instance = it }
        }
    }
}
