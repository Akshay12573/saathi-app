package com.saathi.assistant.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlin.random.Random

object ReminderScheduler {

    const val EXTRA_MESSAGE = "extra_message"
    const val EXTRA_ID = "extra_id"

    /** Returns true only if AlarmManager actually accepted the alarm. */
    fun schedule(context: Context, message: String, epochMillis: Long): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return false

        val id = Random.nextInt()
        val pendingIntent = buildPendingIntent(context, id, message)

        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMillis, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMillis, pendingIntent)
            }
            ReminderStore.add(context, PendingReminder(id, message, epochMillis))
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        ReminderStore.getAll(context).forEach { reminder ->
            if (reminder.epochMillis > now) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@forEach
                val pendingIntent = buildPendingIntent(context, reminder.id, reminder.message)
                try {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.epochMillis, pendingIntent)
                } catch (e: SecurityException) {
                    // Permission revoked after reboot; drop silently, nothing more we can do.
                }
            } else {
                ReminderStore.remove(context, reminder.id)
            }
        }
    }

    private fun buildPendingIntent(context: Context, id: Int, message: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_MESSAGE, message)
        }
        return PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
