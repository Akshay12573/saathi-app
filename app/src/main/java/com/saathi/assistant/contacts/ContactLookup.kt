package com.saathi.assistant.contacts

import android.content.Context
import android.provider.ContactsContract
import java.util.Locale

class ContactLookup(private val context: Context) {

    /** Naam (poora ya partial) se contact ka number dhoondta hai. */
    fun findNumberByName(spokenName: String): String? {
        val name = spokenName.trim().lowercase(Locale.getDefault())
        if (name.isBlank()) return null

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        )

        cursor?.use {
            while (it.moveToNext()) {
                val contactName = it.getString(0)?.lowercase(Locale.getDefault()) ?: continue
                val number = it.getString(1) ?: continue
                if (contactName.contains(name) || name.contains(contactName)) {
                    return number
                }
            }
        }
        return null
    }

    /** If the spoken value already looks like a phone number, use it as-is. */
    fun resolve(contactNameOrNumber: String): String? {
        val digitsOnly = contactNameOrNumber.filter { it.isDigit() || it == '+' }
        if (digitsOnly.length >= 7 && digitsOnly.length == contactNameOrNumber.trim().length) {
            return contactNameOrNumber.trim()
        }
        return findNumberByName(contactNameOrNumber)
    }
}
