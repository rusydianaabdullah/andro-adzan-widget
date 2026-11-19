package com.rusydiana.adzantime.data

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.net.toUri
import java.io.File

/**
 * The user's own notification sound: either an audio file they picked, or something they
 * recorded here. Only one is kept, and every prayer can point at it.
 */
object CustomSound {

    private const val RECORDING_NAME = "custom_adzan.m4a"

    /** Copies nothing: the picked document is referenced by URI, with a persisted grant. */
    fun adopt(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val settings = Settings.get(context)
        settings.customSoundUri = uri.toString()
        val name = displayName(context, uri)
        settings.customSoundName = name
        return name
    }

    fun recordingFile(context: Context) = File(context.filesDir, RECORDING_NAME)

    /** True when the stored sound is still readable; a revoked grant makes it false. */
    fun isUsable(context: Context): Boolean {
        val uri = Settings.get(context).customSoundUri?.toUri() ?: return false
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        }.getOrDefault(false)
    }

    fun clear(context: Context) {
        val settings = Settings.get(context)
        settings.customSoundUri = null
        settings.customSoundName = null
    }

    private fun displayName(context: Context, uri: Uri): String {
        if (uri.scheme == "file") return File(uri.path.orEmpty()).name
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()
    }

    /**
     * A one-shot voice recorder writing straight to [recordingFile]. The caller owns the
     * lifecycle: [start] then [stop], and [stop] returns the file's name once it is saved.
     */
    class Recorder(private val context: Context) {
        private var recorder: MediaRecorder? = null

        fun start() {
            val target = recordingFile(context)
            target.delete()
            recorder = newRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setOutputFile(target.absolutePath)
                prepare()
                start()
            }
        }

        /** Returns the saved name, or null when the recording was too short to be written. */
        fun stop(): String? {
            val stopped = runCatching {
                recorder?.stop()
                true
            }.getOrDefault(false)
            recorder?.release()
            recorder = null
            val file = recordingFile(context)
            if (!stopped || !file.exists() || file.length() == 0L) {
                file.delete()
                return null
            }
            val settings = Settings.get(context)
            settings.customSoundUri = Uri.fromFile(file).toString()
            settings.customSoundName = context.getString(
                com.rusydiana.adzantime.R.string.sound_custom_recording
            )
            return settings.customSoundName
        }

        fun cancel() {
            runCatching { recorder?.stop() }
            recorder?.release()
            recorder = null
            recordingFile(context).delete()
        }

        private fun newRecorder() =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
            else @Suppress("DEPRECATION") MediaRecorder()
    }
}
