package com.saathi.assistant

import com.saathi.assistant.actions.ActionMapper
import com.saathi.assistant.actions.MappedAction
import com.saathi.assistant.network.ActionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionMapperTest {

    @Test
    fun `CALL maps correctly and always requires confirmation`() {
        val dto = ActionDto(type = "CALL", params = mapOf("contact" to "Rahul"))
        val result = ActionMapper.map(dto)

        assertTrue(result is MappedAction.Call)
        result as MappedAction.Call
        assertEquals("Rahul", result.contactNameOrNumber)
        assertTrue("CALL must always require confirmation even if dto didn't say so", result.requiresConfirmation)
    }

    @Test
    fun `CALL without contact is Invalid`() {
        val dto = ActionDto(type = "CALL", params = emptyMap())
        val result = ActionMapper.map(dto)
        assertTrue(result is MappedAction.Invalid)
    }

    @Test
    fun `SMS requires both contact and message`() {
        val missingMessage = ActionMapper.map(ActionDto(type = "SMS", params = mapOf("contact" to "Amit")))
        assertTrue(missingMessage is MappedAction.Invalid)

        val valid = ActionMapper.map(
            ActionDto(type = "SMS", params = mapOf("contact" to "Amit", "message" to "Running late"))
        )
        assertTrue(valid is MappedAction.Sms)
    }

    @Test
    fun `WHATSAPP_MESSAGE parses auto_send flag`() {
        val result = ActionMapper.map(
            ActionDto(
                type = "WHATSAPP_MESSAGE",
                params = mapOf("contact" to "Priya", "message" to "On my way", "auto_send" to "true")
            )
        ) as MappedAction.WhatsAppMessage

        assertTrue(result.autoSend)
        assertTrue(result.requiresConfirmation)
    }

    @Test
    fun `OPEN_URL rejects non-http schemes`() {
        val result = ActionMapper.map(ActionDto(type = "OPEN_URL", params = mapOf("url" to "javascript:alert(1)")))
        assertTrue(result is MappedAction.Invalid)
    }

    @Test
    fun `OPEN_URL accepts https`() {
        val result = ActionMapper.map(ActionDto(type = "OPEN_URL", params = mapOf("url" to "https://example.com")))
        assertTrue(result is MappedAction.OpenUrl)
    }

    @Test
    fun `SET_ALARM validates hour and minute ranges`() {
        val badHour = ActionMapper.map(ActionDto(type = "SET_ALARM", params = mapOf("hour" to "25", "minute" to "0")))
        assertTrue(badHour is MappedAction.Invalid)

        val good = ActionMapper.map(ActionDto(type = "SET_ALARM", params = mapOf("hour" to "7", "minute" to "30")))
        assertTrue(good is MappedAction.SetAlarm)
    }

    @Test
    fun `ADD_CALENDAR_EVENT defaults end time to one hour after start when missing`() {
        val result = ActionMapper.map(
            ActionDto(
                type = "ADD_CALENDAR_EVENT",
                params = mapOf("title" to "Doctor visit", "start_epoch_millis" to "1000000")
            )
        ) as MappedAction.AddCalendarEvent

        assertEquals(1000000L + 60 * 60 * 1000L, result.endEpochMillis)
    }

    @Test
    fun `AGENT_TASK requires both app_name and goal, and always needs confirmation`() {
        val missingGoal = ActionMapper.map(ActionDto(type = "AGENT_TASK", params = mapOf("app_name" to "Instagram")))
        assertTrue(missingGoal is MappedAction.Invalid)

        val valid = ActionMapper.map(
            ActionDto(type = "AGENT_TASK", params = mapOf("app_name" to "Instagram", "goal" to "John ko hi bhejo"))
        ) as MappedAction.AgentTask

        assertEquals("Instagram", valid.appName)
        assertTrue(valid.requiresConfirmation)
    }

    @Test
    fun `unknown action type is Invalid`() {
        val result = ActionMapper.map(ActionDto(type = "DELETE_EVERYTHING", params = emptyMap()))
        assertTrue(result is MappedAction.Invalid)
    }

    @Test
    fun `NONE maps to MappedAction NONE singleton`() {
        val result = ActionMapper.map(ActionDto(type = "NONE"))
        assertEquals(MappedAction.None, result)
        assertFalse(result.requiresConfirmation)
    }
}
