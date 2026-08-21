package com.veyro.p2p.features.contacts

import android.content.ContentProviderOperation
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.util.Patterns
import com.veyro.p2p.protocol.ContactRecord

class ContactSyncManager(context: Context) {
    private val contentResolver = context.applicationContext.contentResolver

    fun readSelectedContact(uri: Uri): ContactRecord? {
        val projection = arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME)
        val contact = contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            if (idIndex < 0) return@use null
            cursor.getLong(idIndex) to if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
        } ?: return null

        val details = querySelectedContactDetails(uri)
        val phones = details.first
        val emails = details.second
        if (contact.second.isBlank() && phones.isEmpty() && emails.isEmpty()) return null
        return ContactRecord.newBuilder()
            .setDisplayName(contact.second.take(MAX_NAME_LENGTH))
            .addAllPhoneNumbers(phones.take(MAX_VALUES))
            .addAllEmailAddresses(emails.take(MAX_VALUES))
            .build()
    }

    fun importContact(contact: ContactRecord): Result<Unit> = runCatching {
        val cleanPhones = contact.phoneNumbersList.map(String::trim)
            .filter { it.any(Char::isDigit) }
            .distinct()
            .take(MAX_VALUES)
        val cleanEmails = contact.emailAddressesList.map(String::trim)
            .filter { Patterns.EMAIL_ADDRESS.matcher(it).matches() }
            .distinct()
            .take(MAX_VALUES)
        require(contact.displayName.isNotBlank() || cleanPhones.isNotEmpty() || cleanEmails.isNotEmpty()) {
            "Contato vazio."
        }
        val operations = arrayListOf<ContentProviderOperation>()
        operations += ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
            .build()
        if (contact.displayName.isNotBlank()) {
            operations += dataInsert(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(
                    ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                    contact.displayName.take(MAX_NAME_LENGTH)
                )
                .build()
        }
        cleanPhones.forEach { number ->
            operations += dataInsert(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number.take(MAX_VALUE_LENGTH))
                .withValue(
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                )
                .build()
        }
        cleanEmails.forEach { email ->
            operations += dataInsert(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email.take(MAX_VALUE_LENGTH))
                .withValue(
                    ContactsContract.CommonDataKinds.Email.TYPE,
                    ContactsContract.CommonDataKinds.Email.TYPE_OTHER
                )
                .build()
        }
        contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
        Unit
    }

    private fun dataInsert(mimeType: String): ContentProviderOperation.Builder =
        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, mimeType)

    private fun querySelectedContactDetails(contactUri: Uri): Pair<List<String>, List<String>> =
        contentResolver.query(
        Uri.withAppendedPath(contactUri, ContactsContract.Contacts.Data.CONTENT_DIRECTORY),
        arrayOf(ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1),
        null,
        null,
        null
    )?.use { cursor ->
        val mimeIndex = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
        val valueIndex = cursor.getColumnIndex(ContactsContract.Data.DATA1)
        if (mimeIndex < 0 || valueIndex < 0) return@use emptyList<String>() to emptyList()
        val phones = mutableListOf<String>()
        val emails = mutableListOf<String>()
        while (cursor.moveToNext() && phones.size + emails.size < MAX_VALUES * 2) {
            val value = cursor.getString(valueIndex)?.trim()?.takeIf(String::isNotBlank)
                ?.take(MAX_VALUE_LENGTH) ?: continue
            when (cursor.getString(mimeIndex)) {
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> if (phones.size < MAX_VALUES) {
                    phones += value
                }
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> if (emails.size < MAX_VALUES) {
                    emails += value
                }
            }
        }
        phones.distinct() to emails.distinct()
    } ?: (emptyList<String>() to emptyList())

    private companion object {
        const val MAX_NAME_LENGTH = 160
        const val MAX_VALUE_LENGTH = 320
        const val MAX_VALUES = 20
    }
}
