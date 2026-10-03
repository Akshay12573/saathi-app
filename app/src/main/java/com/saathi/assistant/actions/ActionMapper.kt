package com.saathi.assistant.actions

import com.saathi.assistant.network.ActionDto

/**
 * Pure Kotlin, no Android dependency on purpose — this is the piece that gets
 * unit tested (see app/src/test/.../ActionMapperTest.kt) without an emulator.
 *
 * Sensitive actions (CALL, SMS, WHATSAPP_MESSAGE auto-send, SET_ALARM,
 * ADD_CALENDAR_EVENT) default to requiring confirmation even if the backend
 * forgets to set the flag — never trust a remote JSON to skip user consent.
 */
object ActionMapper {

    private val ALWAYS_CONFIRM = setOf(
        ActionType.CALL,
        ActionType.SMS,
        ActionType.WHATSAPP_MESSAGE,
        ActionType.SET_ALARM,
        ActionType.ADD_CALENDAR_EVENT,
        ActionType.SET_REMINDER
    )

    fun map(dto: ActionDto): MappedAction {
        val type = ActionType.fromWire(dto.type)
            ?: return MappedAction.Invalid("Unknown action type: ${dto.type}")

        val requiresConfirmation = dto.requires_confirmation || type in ALWAYS_CONFIRM

        return when (type) {
            ActionType.CALL -> {
                val contact = dto.params["contact"]?.trim()
                if (contact.isNullOrBlank()) return MappedAction.Invalid("CALL missing 'contact'")
                MappedAction.Call(contact, requiresConfirmation, dto.confirmation_prompt)
            }

            ActionType.SMS -> {
                val contact = dto.params["contact"]?.trim()
                val message = dto.params["message"]?.trim()
                if (contact.isNullOrBlank()) return MappedAction.Invalid("SMS missing 'contact'")
                if (message.isNullOrBlank()) return MappedAction.Invalid("SMS missing 'message'")
                MappedAction.Sms(contact, message, requiresConfirmation, dto.confirmation_prompt)
            }

            ActionType.WHATSAPP_MESSAGE -> {
                val contact = dto.params["contact"]?.trim()
                val message = dto.params["message"]?.trim()
                val autoSend = dto.params["auto_send"]?.trim()?.equals("true", ignoreCase = true) == true
                if (contact.isNullOrBlank()) return MappedAction.Invalid("WHATSAPP_MESSAGE missing 'contact'")
                if (message.isNullOrBlank()) return MappedAction.Invalid("WHATSAPP_MESSAGE missing 'message'")
                MappedAction.WhatsAppMessage(contact, message, autoSend, requiresConfirmation, dto.confirmation_prompt)
            }

            ActionType.OPEN_APP -> {
                val appName = dto.params["app_name"]?.trim()
                if (appName.isNullOrBlank()) return MappedAction.Invalid("OPEN_APP missing 'app_name'")
                MappedAction.OpenApp(appName)
            }

            ActionType.OPEN_URL -> {
                val url = dto.params["url"]?.trim()
                if (url.isNullOrBlank()) return MappedAction.Invalid("OPEN_URL missing 'url'")
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    return MappedAction.Invalid("OPEN_URL must be http(s): $url")
                }
                MappedAction.OpenUrl(url)
            }

            ActionType.SET_ALARM -> {
                val hour = dto.params["hour"]?.toIntOrNull()
                val minute = dto.params["minute"]?.toIntOrNull()
                if (hour == null || hour !in 0..23) return MappedAction.Invalid("SET_ALARM invalid 'hour'")
                if (minute == null || minute !in 0..59) return MappedAction.Invalid("SET_ALARM invalid 'minute'")
                MappedAction.SetAlarm(hour, minute, dto.params["label"], requiresConfirmation, dto.confirmation_prompt)
            }

            ActionType.SET_REMINDER -> {
                val message = dto.params["message"]?.trim()
                val epochMillis = dto.params["epoch_millis"]?.toLongOrNull()
                if (message.isNullOrBlank()) return MappedAction.Invalid("SET_REMINDER missing 'message'")
                if (epochMillis == null || epochMillis <= 0L) return MappedAction.Invalid("SET_REMINDER invalid 'epoch_millis'")
                MappedAction.SetReminder(message, epochMillis, requiresConfirmation, dto.confirmation_prompt)
            }

            ActionType.ADD_CALENDAR_EVENT -> {
                val title = dto.params["title"]?.trim()
                val start = dto.params["start_epoch_millis"]?.toLongOrNull()
                val end = dto.params["end_epoch_millis"]?.toLongOrNull()
                if (title.isNullOrBlank()) return MappedAction.Invalid("ADD_CALENDAR_EVENT missing 'title'")
                if (start == null || start <= 0L) return MappedAction.Invalid("ADD_CALENDAR_EVENT invalid 'start_epoch_millis'")
                val resolvedEnd = end ?: (start + 60 * 60 * 1000L)
                MappedAction.AddCalendarEvent(
                    title, start, resolvedEnd, dto.params["location"], requiresConfirmation, dto.confirmation_prompt
                )
            }

            ActionType.WEB_SEARCH -> {
                val query = dto.params["query"]?.trim()
                if (query.isNullOrBlank()) return MappedAction.Invalid("WEB_SEARCH missing 'query'")
                MappedAction.WebSearch(query)
            }

            ActionType.NONE -> MappedAction.None
        }
    }
}
