package com.rusydiana.adzantime.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rusydiana.adzantime.R
import com.rusydiana.adzantime.data.AdzanSound
import com.rusydiana.adzantime.data.Settings
import com.rusydiana.adzantime.prayer.Prayer
import com.rusydiana.adzantime.ui.AdzanAlarmActivity
import com.rusydiana.adzantime.ui.MainActivity

/** Why a notification would not reach the user, so the About screen can say which. */
enum class NotificationBlock {
    NONE,

    /** POST_NOTIFICATIONS was never granted (Android 13+). */
    PERMISSION,

    /** Notifications are switched off for the whole app in system settings. */
    APP_DISABLED,

    /** The app may notify, but the adzan channel itself was muted by the user. */
    CHANNEL_DISABLED,
}

object AdzanNotifier {

    /** Silent by design: the audio comes from [AdzanService], not from the channel. */
    const val CHANNEL_ADZAN = "adzan_v1"
    const val CHANNEL_PLAYBACK = "adzan_playback_v1"
    const val CHANNEL_HOLIDAY = "hijri_holiday_v1"
    const val NOTIF_ADZAN = 2001
    const val NOTIF_PLAYBACK = 2002
    const val NOTIF_HOLIDAY = 2003
    const val NOTIF_TEST = 2004

    /**
     * The first reason a notification would be dropped, checked in the order the system
     * applies them. [NotificationBlock.NONE] means the delivery path is clear.
     */
    fun diagnose(context: Context): NotificationBlock {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return NotificationBlock.PERMISSION
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return NotificationBlock.APP_DISABLED
        }
        ensureChannels(context)
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL_ADZAN)
        if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
            return NotificationBlock.CHANNEL_DISABLED
        }
        return NotificationBlock.NONE
    }

    /**
     * Posts a sample adzan notification on the real channel. Returns false if the system
     * refused it, so the caller can show why instead of leaving the user guessing.
     */
    fun notifyTest(context: Context): Boolean {
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ADZAN)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_title_test))
            .setContentText(context.getString(R.string.notif_body_test))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.notif_body_test))
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()
        return runCatching {
            context.getSystemService(NotificationManager::class.java)
                .notify(NOTIF_TEST, notification)
        }.isSuccess && diagnose(context) == NotificationBlock.NONE
    }

    /**
     * Android 14 gates full-screen intents behind a per-app grant that only alarm and calling
     * apps get by default. Without it the notification still posts, just without taking over
     * the screen — so the caller can offer the user the switch instead of failing silently.
     */
    fun canUseFullScreenIntent(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ADZAN,
                context.getString(R.string.channel_adzan),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_adzan_desc)
                setSound(null, null)
                enableVibration(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_HOLIDAY,
                context.getString(R.string.channel_holiday),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.channel_holiday_desc)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PLAYBACK,
                context.getString(R.string.channel_playback),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_playback_desc)
                setSound(null, null)
            }
        )
    }

    /** The eve-of-holiday reminder: the Hijri day turns at the Maghrib that follows. */
    fun notifyHoliday(context: Context, labelRes: Int) {
        ensureChannels(context)
        val name = context.getString(labelRes)
        val notification = NotificationCompat.Builder(context, CHANNEL_HOLIDAY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_title_holiday, name))
            .setContentText(context.getString(R.string.notif_body_holiday, name))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.notif_body_holiday, name))
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                .notify(NOTIF_HOLIDAY, notification)
        }
    }

    fun notifyPrayer(context: Context, prayer: Prayer, isJumuah: Boolean, sound: AdzanSound) {
        ensureChannels(context)
        val title = when {
            isJumuah -> context.getString(R.string.notif_title_jumuah)
            prayer == Prayer.SYURUQ -> context.getString(R.string.notif_title_syuruq)
            else -> context.getString(R.string.notif_title_adzan, context.getString(prayer.labelRes))
        }
        val body = when {
            prayer == Prayer.SYURUQ -> context.getString(R.string.notif_body_syuruq)
            else -> context.getString(R.string.notif_body_adzan, context.getString(prayer.labelRes))
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ADZAN)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
        // "Treat as alarm": the full-screen intent is what lets the system light the screen and
        // show the adzan over the keyguard, the way a clock alarm does. Without it the post only
        // lands in the shade and is found whenever the phone is next unlocked.
        if (Settings.get(context).alarmStyleNotifications && canUseFullScreenIntent(context)) {
            val alarmIntent = Intent(context, AdzanAlarmActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(AdzanAlarmActivity.EXTRA_PRAYER, prayer.key)
                putExtra(AdzanAlarmActivity.EXTRA_JUMUAH, isJumuah)
            }
            builder
                .setFullScreenIntent(
                    PendingIntent.getActivity(
                        context, 1, alarmIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                    true,
                )
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        }
        val notification = builder.build()
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                .notify(NOTIF_ADZAN, notification)
        }
    }
}
