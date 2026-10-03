package com.saathi.assistant.actions

/** Mirrors the "type" field the backend puts on each structured action. */
enum class ActionType(val wireName: String) {
    CALL("CALL"),
    SMS("SMS"),
    WHATSAPP_MESSAGE("WHATSAPP_MESSAGE"),
    OPEN_APP("OPEN_APP"),
    OPEN_URL("OPEN_URL"),
    SET_ALARM("SET_ALARM"),
    SET_REMINDER("SET_REMINDER"),
    ADD_CALENDAR_EVENT("ADD_CALENDAR_EVENT"),
    WEB_SEARCH("WEB_SEARCH"),
    NONE("NONE");

    companion object {
        fun fromWire(value: String): ActionType? = values().find { it.wireName == value.trim().uppercase() }
    }
}
