package io.eddiegulay.tempo.contacts

import io.eddiegulay.tempo.gym.foldKana

const val CONTACT_HIT_LIMIT = 8

fun matchContact(name: String, phone: String, query: String): Boolean {
    val q = query.trim()
    if (q.length < 2) return false
    val foldedQ = foldKana(q)
    if (foldedQ.isNotEmpty() && foldKana(name).contains(foldedQ)) return true
    val qDigits = q.filter { it.isDigit() }
    if (qDigits.length >= 2) {
        val phoneDigits = phone.filter { it.isDigit() }
        if (phoneDigits.contains(qDigits)) return true
    }
    return false
}

fun matchContacts(
    query: String,
    contacts: List<DeviceContact>,
    limit: Int = CONTACT_HIT_LIMIT,
): List<DeviceContact> {
    if (query.trim().length < 2) return emptyList()
    return contacts.asSequence()
        .filter { matchContact(it.displayName, it.phone, query) }
        .take(limit)
        .toList()
}
