package com.saathi.assistant

import com.saathi.assistant.actions.LocalIntentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalIntentParserTest {

    @Test
    fun `recognizes a URL anywhere in the sentence`() {
        val action = LocalIntentParser.parse("open https://example.com please")
        assertEquals("OPEN_URL", action?.type)
        assertEquals("https://example.com", action?.params?.get("url"))
    }

    @Test
    fun `recognizes call command in Hinglish`() {
        val action = LocalIntentParser.parse("Rahul ko call karo")
        assertEquals("CALL", action?.type)
        assertTrue(action!!.requires_confirmation)
    }

    @Test
    fun `recognizes whatsapp message command`() {
        val action = LocalIntentParser.parse("whatsapp par Priya ko message karo: ghar aa raha hoon")
        assertEquals("WHATSAPP_MESSAGE", action?.type)
        assertEquals("ghar aa raha hoon", action?.params?.get("message"))
    }

    @Test
    fun `unrecognized free text returns null`() {
        val action = LocalIntentParser.parse("aaj mausam kaisa hai")
        assertNull(action)
    }
}
