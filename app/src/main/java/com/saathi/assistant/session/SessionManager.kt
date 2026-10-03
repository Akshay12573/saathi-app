package com.saathi.assistant.session

import android.content.Context
import java.util.UUID

/**
 * Stable per-install session id, sent with every /api/chat call so the
 * backend can keep short multi-turn context (command #19: context-aware
 * multi-step commands) without the Android app itself storing conversation
 * history.
 */
object SessionManager {
    private const val PREFS = "saathi_session"
    private const val KEY_SESSION_ID = "session_id"

    fun getOrCreateSessionId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_SESSION_ID, null)
        if (existing != null) return existing

        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_SESSION_ID, newId).apply()
        return newId
    }
}
