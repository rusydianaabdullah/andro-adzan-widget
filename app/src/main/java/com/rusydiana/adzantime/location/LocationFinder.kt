package com.rusydiana.adzantime.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.rusydiana.adzantime.data.Settings
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Uses the platform LocationManager only — no Play Services — so the app works on any device
 * and needs no network. Triggered by the "Update location" button, never automatically.
 */
object LocationFinder {

    sealed interface Result {
        /** [elevation] is metres above sea level, 0.0 when the fix did not carry one.
         *
         * It is not decoration: the horizon dips with height, so sunset is later and sunrise
         * earlier the higher you stand, and PrayerCalculator already corrects for it. Dropping
         * it here meant that correction was always fed zero, which in a highland city like
         * Bandung (~700 m) put Maghrib and Syuruq several minutes off the local tables. */
        data class Success(
            val latitude: Double,
            val longitude: Double,
            val elevation: Double,
            val city: String?,
        ) : Result
        data object PermissionMissing : Result
        data object Unavailable : Result
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * [onResult] always arrives on the main thread, exactly once. Everything before it runs on
     * [worker]: the geocoder blocks for tens of seconds when the device is offline, and doing
     * that on the caller's thread froze the UI long enough for the system to offer to close the
     * app. The platform calls us back on whatever thread it likes, which is why the hop back is
     * explicit rather than assumed.
     */
    @SuppressLint("MissingPermission")
    fun requestOnce(context: Context, onResult: (Result) -> Unit) {
        if (!hasPermission(context)) {
            onResult(Result.PermissionMissing)
            return
        }
        // The caller is a composable; holding its Activity past a rotation would leak it.
        val app = context.applicationContext
        val deliver = deliverOnce(onResult)

        worker.execute {
            val manager = app.getSystemService(LocationManager::class.java)
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

            // A recent cached fix is good enough for prayer times and costs nothing.
            val cached = providers
                .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
            if (cached != null && System.currentTimeMillis() - cached.time < CACHE_MAX_AGE_MS) {
                deliver(cached.toResult(app))
                return@execute
            }

            if (providers.isEmpty()) {
                deliver(if (cached != null) cached.toResult(app) else Result.Unavailable)
                return@execute
            }

            val provider = providers.first()
            val fallback = { deliver(cached?.toResult(app) ?: Result.Unavailable) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                // Without this a fix that never comes leaves the button spinning forever.
                main.postDelayed({ signal.cancel() }, FIX_TIMEOUT_MS)
                runCatching {
                    manager.getCurrentLocation(provider, signal, worker) { location ->
                        if (location != null) deliver(location.toResult(app)) else fallback()
                    }
                }.onFailure { fallback() }
            } else {
                @Suppress("DEPRECATION")
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        worker.execute { deliver(location.toResult(app)) }
                    }
                    override fun onProviderDisabled(provider: String) {
                        worker.execute { fallback() }
                    }
                }
                runCatching {
                    @Suppress("DEPRECATION")
                    manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                    main.postDelayed({
                        runCatching { manager.removeUpdates(listener) }
                        worker.execute { fallback() }
                    }, FIX_TIMEOUT_MS)
                }.onFailure { fallback() }
            }
        }
    }

    /** At most one result, always on the main thread — the UI toasts and writes Compose state. */
    private fun deliverOnce(onResult: (Result) -> Unit): (Result) -> Unit {
        val done = AtomicBoolean(false)
        return { result ->
            if (done.compareAndSet(false, true)) main.post { onResult(result) }
        }
    }

    private fun Location.toResult(context: Context): Result.Success =
        Result.Success(
            latitude = latitude,
            longitude = longitude,
            // Network fixes rarely carry an altitude and GPS ones occasionally report an absurd
            // one, so anything outside the range real inhabited ground occupies is treated as
            // absent rather than trusted — sea level is the safe assumption, and it is what the
            // app did everywhere before this.
            elevation = if (hasAltitude() && altitude > MIN_ELEVATION_M && altitude < MAX_ELEVATION_M) {
                altitude
            } else {
                0.0
            },
            city = reverseGeocode(context, latitude, longitude),
        )

    /**
     * Best effort only. The platform geocoder needs a network on most devices, so a null result
     * is normal and the user simply keeps the name they typed. Blocking, and never called from
     * the main thread.
     */
    private fun reverseGeocode(context: Context, lat: Double, lon: Double): String? {
        if (!Geocoder.isPresent()) return null
        return runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.getDefault())
                .getFromLocation(lat, lon, 1)
                ?.firstOrNull()
                ?.let { it.subAdminArea ?: it.locality ?: it.adminArea }
        }.getOrNull()
    }

    private val main = Handler(Looper.getMainLooper())

    // One thread for the whole app: a per-request executor was never shut down, so every tap of
    // "Update location" left a thread behind.
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "location-finder").apply { isDaemon = true }
    }

    private const val CACHE_MAX_AGE_MS = 6 * 60 * 60 * 1000L
    private const val FIX_TIMEOUT_MS = 20_000L

    // The Dead Sea shore is about -430 m and the highest permanently inhabited places sit under
    // 5500 m; a fix outside that is a bad reading, not a location.
    private const val MIN_ELEVATION_M = -500.0
    private const val MAX_ELEVATION_M = 6000.0
}
