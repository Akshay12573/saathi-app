package com.saathi.assistant.reminders

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Tiny SharedPreferences-backed store so reminders survive a reboot (see BootReceiver). */
data class PendingReminder(val id: Int, val message: String, val epochMillis: Long)

object ReminderStore {
    private const val PREFS = "saathi_reminders"
    private const val KEY_LIST = "pending"

    fun add(context: Context, reminder: PendingReminder) {
        val all = getAll(context).toMutableList()
        all.removeAll { it.id == reminder.id }
        all.add(reminder)
        save(context, all)
    }

    fun remove(context: Context, id: Int) {
        val all = getAll(context).filterNot { it.id == id }
        save(context, all)
    }

    fun getAll(context: Context): List<PendingReminder> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LIST, null)
            ?: return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            PendingReminder(obj.getInt("id"), obj.getString("message"), obj.getLong("epochMillis"))
        }
    }

    private fun save(context: Context, reminders: List<PendingReminder>) {
        val array = JSONArray()
        reminders.forEach { reminder ->
            val obj = JSONObject()
            obj.put("id", reminder.id)
            obj.put("message", reminder.message)
            obj.put("epochMillis", reminder.epochMillis)
            array.put(obj)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LIST, array.toString())
            .apply()
    }
}
