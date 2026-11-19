package com.rusydiana.adzantime.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import com.rusydiana.adzantime.alarm.AdzanNotifier
import com.rusydiana.adzantime.alarm.AdzanScheduler
import com.rusydiana.adzantime.data.Settings
import com.rusydiana.adzantime.widget.AdzanWidgetProvider

class MainActivity : AppCompatActivity() {

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) onLocationPermissionGranted?.invoke()
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private var onLocationPermissionGranted: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLanguage(Settings.get(this).language)
        AdzanNotifier.ensureChannels(this)
        requestNotificationPermissionIfNeeded()

        setContent {
            AdzanTheme {
                AdzanApp(
                    onRequestLocationPermission = { onGranted ->
                        onLocationPermissionGranted = onGranted
                        locationPermission.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            )
                        )
                    },
                    onOpenExactAlarmSettings = ::openExactAlarmSettings,
                    onOpenAppSettings = ::openAppSettings,
                    onOpenNotificationSettings = ::openNotificationSettings,
                    onOpenBatterySettings = ::openBatterySettings,
                    onOpenFullScreenIntentSettings = ::openFullScreenIntentSettings,
                    onVisitSupport = ::openSupportSite,
                    onLanguageChanged = ::applyLanguage,
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Any setting the user touched changes the schedule, so rebuild it on the way out.
        AdzanScheduler.rescheduleAll(this)
        AdzanWidgetProvider.refreshAll(this)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * Android 14+ only: the per-app switch that lets a notification take over the screen. On
     * older releases the grant comes with the manifest permission, so there is nothing to open.
     */
    private fun openFullScreenIntentSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        runCatching {
            startActivity(
                Intent(
                    AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:$packageName"),
                )
            )
        }.onFailure { openAppSettings() }
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            startActivity(
                Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.parse("package:$packageName"))
            )
        }
    }

    /** Straight to the adzan channel, where a muted channel is switched back on. */
    private fun openNotificationSettings() {
        val opened = runCatching {
            startActivity(
                Intent(AndroidSettings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, packageName)
                    .putExtra(AndroidSettings.EXTRA_CHANNEL_ID, AdzanNotifier.CHANNEL_ADZAN)
            )
        }.isSuccess
        if (!opened) {
            runCatching {
                startActivity(
                    Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, packageName)
                )
            }
        }
    }

    /**
     * Aggressive battery savers are the usual reason an exact alarm never wakes the phone,
     * so the About screen offers the exemption directly.
     */
    private fun openBatterySettings() {
        // The direct dialog is one tap, but some ROMs remove it; the system-wide list is
        // always there as the fallback.
        val asked = runCatching {
            startActivity(
                Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        }.isSuccess
        if (!asked) {
            runCatching {
                startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    private fun openAppSettings() {
        runCatching {
            startActivity(
                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:$packageName"))
            )
        }
    }

    /** Opens in the user's browser, outside the app — this is the only link we ship. */
    private fun openSupportSite() {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_URL)))
        }
    }

    private fun applyLanguage(tag: String) {
        val locales = if (tag == "system") {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    private companion object {
        // The tag tells us the visitor came from the Play Store build of the app.
        const val SUPPORT_URL = "https://larus.id/inquiry?fr=google-playstore"
    }
}
