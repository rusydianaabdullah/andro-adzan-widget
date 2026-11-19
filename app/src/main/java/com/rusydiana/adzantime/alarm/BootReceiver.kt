package com.rusydiana.adzantime.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rusydiana.adzantime.widget.AdzanWidgetProvider

/** Alarms do not survive a reboot, a clock change or an app update — rebuild the chain. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AdzanScheduler.rescheduleAll(context)
        AdzanWidgetProvider.refreshAll(context)
    }
}
