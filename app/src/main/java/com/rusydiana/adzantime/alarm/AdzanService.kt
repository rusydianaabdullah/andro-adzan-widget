package com.rusydiana.adzantime.alarm

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo
import com.rusydiana.adzantime.R
import com.rusydiana.adzantime.data.AdzanSound
import com.rusydiana.adzantime.data.Settings
import com.rusydiana.adzantime.prayer.Prayer

/**
 * Plays one adzan through the alarm stream, in the foreground so the system does not kill it
 * mid-call. A "Stop" action and a wake lock keep it predictable on a locked phone.
 */
class AdzanService : Service() {

    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val prayer = Prayer.fromKey(intent?.getStringExtra(EXTRA_PRAYER))
        val isJumuah = intent?.getBooleanExtra(EXTRA_JUMUAH, false) == true
        val sound = AdzanSound.fromName(intent?.getStringExtra(EXTRA_SOUND), AdzanSound.MAKKAH)
        val uri = soundUri(this, sound)
        if (uri == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground(prayer, isJumuah)
        play(uri)
        return START_NOT_STICKY
    }

    private fun startAsForeground(prayer: Prayer?, isJumuah: Boolean) {
        AdzanNotifier.ensureChannels(this)
        val label = when {
            isJumuah -> getString(R.string.notif_title_jumuah)
            prayer != null -> getString(R.string.notif_title_adzan, getString(prayer.labelRes))
            else -> getString(R.string.app_name)
        }
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, AdzanService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, AdzanNotifier.CHANNEL_PLAYBACK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(label)
            .setContentText(getString(R.string.playing_adzan))
            .setOngoing(true)
            .addAction(0, getString(R.string.action_stop), stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this, AdzanNotifier.NOTIF_PLAYBACK, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(AdzanNotifier.NOTIF_PLAYBACK, notification)
        }
    }

    private fun play(uri: Uri) {
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "adzantime:playback")
            .also { it.acquire(15 * 60 * 1000L) }

        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(this@AdzanService, uri)
            setOnCompletionListener { stopSelf() }
            setOnErrorListener { _, _, _ -> stopSelf(); true }
            prepare()
            start()
        }
    }

    override fun onDestroy() {
        player?.runCatching { stop() }
        player?.release()
        player = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        private const val ACTION_STOP = "com.rusydiana.adzantime.STOP_PLAYBACK"
        private const val EXTRA_PRAYER = "prayer"
        private const val EXTRA_JUMUAH = "jumuah"
        private const val EXTRA_SOUND = "sound"

        /**
         * Where a choice actually plays from: a bundled raw resource, or the user's own file.
         * Null means nothing to play — SILENT, or a custom sound that was never set.
         */
        fun soundUri(context: Context, sound: AdzanSound): Uri? = when {
            sound.isCustom -> Settings.get(context).customSoundUri?.let(Uri::parse)
            sound.rawRes != null ->
                Uri.parse("android.resource://${context.packageName}/${sound.rawRes}")
            else -> null
        }

        fun start(context: Context, prayer: Prayer, isJumuah: Boolean, sound: AdzanSound) {
            val intent = Intent(context, AdzanService::class.java).apply {
                putExtra(EXTRA_PRAYER, prayer.key)
                putExtra(EXTRA_JUMUAH, isJumuah)
                putExtra(EXTRA_SOUND, sound.name)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AdzanService::class.java))
        }

        /** Used by the settings screen to audition a sound. */
        fun preview(context: Context, sound: AdzanSound) {
            val uri = soundUri(context, sound) ?: return
            PreviewPlayer.play(context, uri)
        }
    }
}

/** A tiny separate player for the settings screen, so previews never touch the alarm service. */
object PreviewPlayer {
    private var player: MediaPlayer? = null

    fun play(context: Context, uri: Uri) {
        stop()
        player = MediaPlayer.create(context, uri)?.apply {
            setOnCompletionListener { stop() }
            start()
        }
    }

    fun stop() {
        player?.runCatching { if (isPlaying) stop() }
        player?.release()
        player = null
    }
}
