package com.saathi.assistant.actions

import com.saathi.assistant.network.ActionDto

/**
 * Offline, regex-based fallback used only when the backend is unreachable
 * (no internet, backend down). Covers the handful of Hindi/Hinglish phrasings
 * common enough to hardcode; everything else still needs the AI backend for
 * real NLU/intent classification. Returns null when nothing matches so the
 * caller can fall back to "backend unavailable" messaging instead of a wrong
 * guess.
 */
object LocalIntentParser {

    // "Rahul ko call karo" style — name before the call keyword (most common Hinglish order).
    private val callNameFirstRegex = Regex(
        "^([a-zA-Z]+(?:\\s[a-zA-Z]+)?)\\s+ko\\s+(?:call|phone|fon|dial)\\s*(?:karo|kardo|lagao)?\\s*$",
        RegexOption.IGNORE_CASE
    )

    // "call Rahul" / "call karo Rahul" style — name after the call keyword.
    private val callKeywordFirstRegex = Regex(
        "^(?:call|phone|fon|dial)\\s+(?:karo|kardo|lagao)?\\s*([a-zA-Z]+(?:\\s[a-zA-Z]+)?)\\s*$",
        RegexOption.IGNORE_CASE
    )
    private val whatsappRegex = Regex(
        "(?:whatsapp|whats app)\\s+(?:pe|par)?\\s*([a-zA-Z\\s]+?)\\s+(?:ko|ke liye)?\\s*(?:message|msg)\\s+(?:karo|bhejo|kardo)?\\s*[:\\-]?\\s*(.+)$",
        RegexOption.IGNORE_CASE
    )
    private val openAppRegex = Regex(
        "(?:open|khol|kholo)\\s+([a-zA-Z0-9\\s]+?)\\s*$",
        RegexOption.IGNORE_CASE
    )
    private val openUrlRegex = Regex(
        "(https?://\\S+)",
        RegexOption.IGNORE_CASE
    )

    fun parse(text: String): ActionDto? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        openUrlRegex.find(trimmed)?.let { match ->
            return ActionDto(type = "OPEN_URL", params = mapOf("url" to match.groupValues[1]))
        }

        whatsappRegex.find(trimmed)?.let { match ->
            val contact = match.groupValues[1].trim()
            val message = match.groupValues[2].trim()
            if (contact.isNotEmpty() && message.isNotEmpty()) {
                return ActionDto(
                    type = "WHATSAPP_MESSAGE",
                    params = mapOf("contact" to contact, "message" to message),
                    requires_confirmation = true,
                    confirmation_prompt = "$contact ko WhatsApp par bhejun: \"$message\"?"
                )
            }
        }

        (callNameFirstRegex.find(trimmed) ?: callKeywordFirstRegex.find(trimmed))?.let { match ->
            val contact = match.groupValues[1].trim()
            if (contact.isNotEmpty()) {
                return ActionDto(
                    type = "CALL",
                    params = mapOf("contact" to contact),
                    requires_confirmation = true,
                    confirmation_prompt = "$contact ko call karu?"
                )
            }
        }

        openAppRegex.find(trimmed)?.let { match ->
            val appName = match.groupValues[1].trim()
            if (appName.isNotEmpty()) {
                return ActionDto(type = "OPEN_APP", params = mapOf("app_name" to appName))
            }
        }

        return null
    }
}
