package com.saathi.assistant.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arms any reminder that hadn't fired yet before the device rebooted. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.rescheduleAll(context)
        }
    }
}
