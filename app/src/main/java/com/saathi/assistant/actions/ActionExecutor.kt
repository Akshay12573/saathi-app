package com.saathi.assistant.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.telephony.SmsManager
import com.saathi.assistant.accessibility.VoiceAccessibilityService
import com.saathi.assistant.calendarutil.CalendarHelper
import com.saathi.assistant.contacts.ContactLookup
import com.saathi.assistant.reminders.ReminderScheduler
import java.net.URLEncoder

/**
 * Executes an already-confirmed, already-permission-checked MappedAction.
 * Every branch either does the real platform call and reports what actually
 * happened, or returns ExecutionResult.fail with the real reason — nothing
 * here is allowed to claim success optimistically.
 */
class ActionExecutor(private val context: Context) {

    private val contactLookup = ContactLookup(context)

    fun execute(action: MappedAction): ExecutionResult = when (action) {
        is MappedAction.Call -> executeCall(action)
        is MappedAction.Sms -> executeSms(action)
        is MappedAction.WhatsAppMessage -> executeWhatsApp(action)
        is MappedAction.OpenApp -> executeOpenApp(action)
        is MappedAction.OpenUrl -> executeOpenUrl(action)
        is MappedAction.SetAlarm -> executeSetAlarm(action)
        is MappedAction.SetReminder -> executeSetReminder(action)
        is MappedAction.AddCalendarEvent -> executeAddCalendarEvent(action)
        is MappedAction.WebSearch -> ExecutionResult.fail("WEB_SEARCH must be routed to the backend research endpoint, not ActionExecutor")
        is MappedAction.AgentTask -> ExecutionResult.fail("AGENT_TASK must be routed to AgentRunner, not ActionExecutor")
        is MappedAction.None -> ExecutionResult.ok("")
        is MappedAction.Invalid -> ExecutionResult.fail(action.reason)
    }

    private fun executeCall(action: MappedAction.Call): ExecutionResult {
        val number = contactLookup.resolve(action.contactNameOrNumber)
            ?: return ExecutionResult.fail("'${action.contactNameOrNumber}' contacts mein nahi mila")

        return try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            ExecutionResult.ok("Call shuru kar diya: $number")
        } catch (e: SecurityException) {
            ExecutionResult.fail("CALL_PHONE permission nahi hai")
        } catch (e: Exception) {
            ExecutionResult.fail("Call nahi lag paaya: ${e.message}")
        }
    }

    private fun executeSms(action: MappedAction.Sms): ExecutionResult {
        val number = contactLookup.resolve(action.contactNameOrNumber)
            ?: return ExecutionResult.fail("'${action.contactNameOrNumber}' contacts mein nahi mila")

        return try {
            val smsManager = context.getSystemService(SmsManager::class.java)
                ?: SmsManager.getDefault()
            smsManager.sendTextMessage(number, null, action.message, null, null)
            ExecutionResult.ok("SMS bhej diya $number ko")
        } catch (e: SecurityException) {
            ExecutionResult.fail("SEND_SMS permission nahi hai")
        } catch (e: Exception) {
            ExecutionResult.fail("SMS fail ho gaya: ${e.message}")
        }
    }

    private fun executeWhatsApp(action: MappedAction.WhatsAppMessage): ExecutionResult {
        val number = contactLookup.resolve(action.contactNameOrNumber)
            ?: return ExecutionResult.fail("'${action.contactNameOrNumber}' contacts mein nahi mila")

        val cleanNumber = number.filter { it.isDigit() }
        val encodedMessage = URLEncoder.encode(action.message, "UTF-8")
        val uri = Uri.parse("https://wa.me/$cleanNumber?text=$encodedMessage")

        return try {
            if (action.autoSend) {
                // Only actually arms the tap-to-send if the user has separately
                // enabled the Accessibility Service; otherwise this flag just
                // sits unused and the user still has to tap Send themselves.
                VoiceAccessibilityService.pendingAutoSend = true
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            val note = if (action.autoSend) {
                " (auto-send ON hai agar Accessibility Service enabled hai, warna aapko Send dabana hoga)"
            } else {
                " — Send aapko dabana hoga"
            }
            ExecutionResult.ok("WhatsApp khol diya $number ke liye$note")
        } catch (e: Exception) {
            ExecutionResult.fail("WhatsApp nahi khul paya: ${e.message}")
        }
    }

    private fun executeOpenApp(action: MappedAction.OpenApp): ExecutionResult {
        val packageManager = context.packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val matches = packageManager.queryIntentActivities(launchIntent, 0)

        val target = matches.firstOrNull {
            it.loadLabel(packageManager).toString().contains(action.appName, ignoreCase = true)
        } ?: return ExecutionResult.fail("'${action.appName}' naam ka app install nahi mila")

        return try {
            val intent = packageManager.getLaunchIntentForPackage(target.activityInfo.packageName)
                ?: return ExecutionResult.fail("'${action.appName}' launch nahi ho saka")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            ExecutionResult.ok("${target.loadLabel(packageManager)} khol diya")
        } catch (e: Exception) {
            ExecutionResult.fail("App open nahi hua: ${e.message}")
        }
    }

    private fun executeOpenUrl(action: MappedAction.OpenUrl): ExecutionResult {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(action.url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            ExecutionResult.ok("URL khol diya: ${action.url}")
        } catch (e: Exception) {
            ExecutionResult.fail("URL nahi khul paya: ${e.message}")
        }
    }

    private fun executeSetAlarm(action: MappedAction.SetAlarm): ExecutionResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, action.hour)
                putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, action.label ?: "Saathi alarm")
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            ExecutionResult.ok("Alarm set ho gaya ${String.format("%02d:%02d", action.hour, action.minute)} ke liye")
        } catch (e: Exception) {
            ExecutionResult.fail("Alarm set nahi hua: ${e.message}")
        }
    }

    private fun executeSetReminder(action: MappedAction.SetReminder): ExecutionResult {
        val scheduled = ReminderScheduler.schedule(context, action.message, action.epochMillis)
        return if (scheduled) {
            ExecutionResult.ok("Reminder set ho gaya: ${action.message}")
        } else {
            ExecutionResult.fail("Reminder schedule nahi ho paya (exact alarm permission check karo)")
        }
    }

    private fun executeAddCalendarEvent(action: MappedAction.AddCalendarEvent): ExecutionResult {
        val eventId = CalendarHelper.addEvent(
            context, action.title, action.startEpochMillis, action.endEpochMillis, action.location
        )
        return if (eventId != null) {
            ExecutionResult.ok("Calendar event bana diya: ${action.title}")
        } else {
            ExecutionResult.fail("Calendar event nahi ban paya (permission ya writable calendar check karo)")
        }
    }
}
