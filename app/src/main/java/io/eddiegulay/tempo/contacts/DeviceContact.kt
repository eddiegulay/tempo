package io.eddiegulay.tempo.contacts

/** One address-book person Tempo may show in Search. In memory only; never written to disk. */
data class DeviceContact(
    val contactId: Long,
    val lookupKey: String,
    val displayName: String,
    val phones: List<String>,
) {
    /** First / preferred number — keyboard Go and a single-number row use this. */
    val phone: String get() = phones.firstOrEmpty()
}

private fun List<String>.firstOrEmpty(): String = firstOrNull().orEmpty()

/** Digits only, so "0712 345 678" and "0712345678" collapse to one line. */
fun phoneKey(raw: String): String = raw.filter { it.isDigit() }

/**
 * Adds [phone] to an existing person, or starts a new one. Super-primary numbers go first.
 * Duplicate digit-strings are ignored.
 */
fun DeviceContact?.plusPhone(
    contactId: Long,
    lookupKey: String,
    displayName: String,
    phone: String,
    prefer: Boolean = false,
): DeviceContact {
    val name = displayName.ifBlank { phone }
    if (this == null) {
        return DeviceContact(contactId, lookupKey, name, listOf(phone))
    }
    val key = phoneKey(phone)
    if (key.isEmpty() || phones.any { phoneKey(it) == key }) return this
    val next = if (prefer) listOf(phone) + phones else phones + phone
    return copy(phones = next)
}
