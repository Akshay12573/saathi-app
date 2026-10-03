package com.saathi.assistant.calendarutil

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import java.util.TimeZone

object CalendarHelper {

    /** Inserts a real event via CalendarContract. Returns the new event id, or null if it failed. */
    fun addEvent(
        context: Context,
        title: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
        location: String?
    ): Long? {
        val calendarId = findWritableCalendarId(context) ?: return null

        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startEpochMillis)
            put(CalendarContract.Events.DTEND, endEpochMillis)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            if (!location.isNullOrBlank()) put(CalendarContract.Events.EVENT_LOCATION, location)
        }

        val uri = try {
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        } catch (e: SecurityException) {
            null
        }

        return uri?.let { ContentUris.parseId(it) }
    }

    private fun findWritableCalendarId(context: Context): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
        val cursor = try {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)
        } catch (e: SecurityException) {
            null
        }

        cursor?.use {
            while (it.moveToNext()) {
                val accessLevel = it.getInt(1)
                if (accessLevel >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) {
                    return it.getLong(0)
                }
            }
        }
        return null
    }
}
