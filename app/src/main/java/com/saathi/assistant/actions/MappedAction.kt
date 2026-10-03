package com.saathi.assistant.actions

/**
 * Typed, validated result of mapping a backend ActionDto onto something the
 * Android ActionExecutor can actually run. Producing `Invalid` instead of
 * throwing keeps ActionMapper pure and trivially unit-testable.
 */
sealed class MappedAction {
    abstract val requiresConfirmation: Boolean
    abstract val confirmationPrompt: String?

    data class Call(
        val contactNameOrNumber: String,
        override val requiresConfirmation: Boolean,
        override val confirmationPrompt: String?
    ) : MappedAction()

    data class Sms(
        val contactNameOrNumber: String,
        val message: String,
        override val requiresConfirmation: Boolean,
        override val confirmationPrompt: String?
    ) : MappedAction()

    data class WhatsAppMessage(
        val contactNameOrNumber: String,
        val message: String,
        val autoSend: Boolean,
        override val requiresConfirmation: Boolean,
        override val confirmationPrompt: String?
    ) : MappedAction()

    data class OpenApp(
        val appName: String,
        override val requiresConfirmation: Boolean = false,
        override val confirmationPrompt: String? = null
    ) : MappedAction()

    data class OpenUrl(
        val url: String,
        override val requiresConfirmation: Boolean = false,
        override val confirmationPrompt: String? = null
    ) : MappedAction()

    data class SetAlarm(
        val hour: Int,
        val minute: Int,
        val label: String?,
        override val requiresConfirmation: Boolean,
        override val confirmationPrompt: String?
    ) : MappedAction()

    data class SetReminder(
        val message: String,
        val epochMillis: Long,
        override val requiresConfirmation: Boolean,
        override val confirmationPrompt: String?
    ) : MappedAction()

    data class AddCalendarEvent(
        val title: String,
        val startEpochMillis: Long,
        val endEpochMillis: Long,
        val location: String?,
        override val requiresConfirmation: Boolean,
        override val confirmationPrompt: String?
    ) : MappedAction()

    data class WebSearch(
        val query: String,
        override val requiresConfirmation: Boolean = false,
        override val confirmationPrompt: String? = null
    ) : MappedAction()

    data class AgentTask(
        val appName: String,
        val goal: String,
        override val requiresConfirmation: Boolean,
        override val confirmationPrompt: String?
    ) : MappedAction()

    object None : MappedAction() {
        override val requiresConfirmation = false
        override val confirmationPrompt: String? = null
    }

    data class Invalid(
        val reason: String,
        override val requiresConfirmation: Boolean = false,
        override val confirmationPrompt: String? = null
    ) : MappedAction()
}
