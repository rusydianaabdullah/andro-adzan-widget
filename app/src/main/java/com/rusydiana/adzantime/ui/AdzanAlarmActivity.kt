package com.rusydiana.adzantime.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rusydiana.adzantime.R
import com.rusydiana.adzantime.alarm.AdzanService
import com.rusydiana.adzantime.prayer.Prayer

/**
 * The alarm-clock face: what the full-screen intent opens when "treat as alarm" is on. It shows
 * over the lock screen and turns the display on, so the adzan announces itself the way an alarm
 * does instead of waiting in the shade to be found.
 */
class AdzanAlarmActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        val prayer = Prayer.fromKey(intent?.getStringExtra(EXTRA_PRAYER))
        val isJumuah = intent?.getBooleanExtra(EXTRA_JUMUAH, false) == true
        val title = when {
            isJumuah -> getString(R.string.notif_title_jumuah)
            prayer == Prayer.SYURUQ -> getString(R.string.notif_title_syuruq)
            prayer != null -> getString(R.string.notif_title_adzan, getString(prayer.labelRes))
            else -> getString(R.string.app_name)
        }
        val body = when (prayer) {
            null -> ""
            Prayer.SYURUQ -> getString(R.string.notif_body_syuruq)
            else -> getString(R.string.notif_body_adzan, getString(prayer.labelRes))
        }

        setContent { AdzanTheme { AlarmFace(title, body, onDismiss = ::dismiss) } }
    }

    /** Dismissing is the user's answer to the alarm: silence the adzan and get out of the way. */
    private fun dismiss() {
        AdzanService.stop(this)
        finish()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        const val EXTRA_PRAYER = "prayer"
        const val EXTRA_JUMUAH = "jumuah"
    }
}

@Composable
private fun AlarmFace(title: String, body: String, onDismiss: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                if (body.isNotEmpty()) {
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
                Button(onClick = onDismiss) {
                    Text(stringResource(R.string.action_stop))
                }
            }
        }
    }
}
