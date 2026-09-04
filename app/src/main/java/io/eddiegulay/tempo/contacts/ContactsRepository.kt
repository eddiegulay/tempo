package io.eddiegulay.tempo.contacts

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract.CommonDataKinds.Phone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

private const val CHANGE_DEBOUNCE_MS = 300L
private const val MAX_CONTACTS = 2500

private val PHONE_PROJECTION = arrayOf(
    Phone.CONTACT_ID,
    Phone.LOOKUP_KEY,
    Phone.DISPLAY_NAME,
    Phone.NUMBER,
    Phone.IS_SUPER_PRIMARY,
)

/**
 * Reads the device address book through [Phone]. In-memory only: a cache here would ride Auto Backup
 * and send other people's numbers to the user's Google account.
 */
class ContactsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val resolver get() = appContext.contentResolver

    @OptIn(FlowPreview::class)
    fun contacts(): Flow<List<DeviceContact>> = callbackFlow {
        val trigger = Channel<Unit>(Channel.CONFLATED)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trigger.trySend(Unit)
            }
        }
        resolver.registerContentObserver(Phone.CONTENT_URI, true, observer)
        val pump = launch {
            trigger.consumeAsFlow()
                .debounce(CHANGE_DEBOUNCE_MS)
                .collect { send(runCatching { queryPhones() }.getOrDefault(emptyList())) }
        }
        trigger.trySend(Unit)
        awaitClose {
            pump.cancel()
            resolver.unregisterContentObserver(observer)
        }
    }.catch { emit(emptyList()) }.flowOn(Dispatchers.IO)

    private fun queryPhones(): List<DeviceContact> {
        val cursor = resolver.query(
            Phone.CONTENT_URI,
            PHONE_PROJECTION,
            "${Phone.NUMBER} IS NOT NULL AND ${Phone.NUMBER} != ''",
            null,
            "${Phone.DISPLAY_NAME} ASC",
        ) ?: return emptyList()

        val byId = LinkedHashMap<Long, DeviceContact>()
        cursor.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val phone = c.getString(3)?.trim().orEmpty()
                if (phone.isEmpty()) continue
                val existing = byId[id]
                if (existing == null && byId.size >= MAX_CONTACTS) continue
                val next = DeviceContact(
                    contactId = id,
                    lookupKey = c.getString(1).orEmpty(),
                    displayName = c.getString(2).orEmpty().ifBlank { phone },
                    phone = phone,
                )
                val superPrimary = c.getInt(4) == 1
                if (existing == null || superPrimary) byId[id] = next
            }
        }
        return byId.values.toList()
    }
}
