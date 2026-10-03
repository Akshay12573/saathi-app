package com.saathi.assistant.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.saathi.assistant.actions.MappedAction

object PermissionUtils {

    fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Which runtime permission(s) a mapped action needs before it can execute. */
    fun requiredPermissions(action: MappedAction): List<String> = when (action) {
        is MappedAction.Call -> listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS)
        is MappedAction.Sms -> listOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS)
        is MappedAction.WhatsAppMessage -> listOf(Manifest.permission.READ_CONTACTS)
        is MappedAction.AddCalendarEvent -> listOf(Manifest.permission.WRITE_CALENDAR)
        is MappedAction.SetReminder -> emptyList()
        is MappedAction.SetAlarm -> emptyList()
        is MappedAction.OpenApp -> emptyList()
        is MappedAction.OpenUrl -> emptyList()
        is MappedAction.WebSearch -> emptyList()
        is MappedAction.AgentTask -> emptyList()
        is MappedAction.None -> emptyList()
        is MappedAction.Invalid -> emptyList()
    }

    fun missingPermissions(context: Context, action: MappedAction): List<String> =
        requiredPermissions(action).filterNot { hasPermission(context, it) }
}
