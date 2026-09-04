package io.eddiegulay.tempo.contacts

/** One address-book row Tempo may show in Search. In memory only; never written to disk. */
data class DeviceContact(
    val contactId: Long,
    val lookupKey: String,
    val displayName: String,
    val phone: String,
)
