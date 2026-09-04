package io.eddiegulay.tempo.search

/**
 * People-context hand-offs from Search: launch Phone, Contacts, WhatsApp, or Google with the
 * typed query. Tempo never reads the address book or the call log, and it adds no dangerous
 * permission. The list is built from the query shape plus which apps are installed and not hidden.
 */

enum class HandOffKind {
    Call,
    WhatsAppNumber,
    FindContacts,
    SearchContacts,
    SearchWhatsApp,
    SearchGoogle,
}

data class AppMatch(
    val label: String,
    val packageName: String,
)

object SearchPackages {
    val WHATSAPP = listOf("com.whatsapp", "com.whatsapp.w4b")
    val CONTACTS = listOf(
        "com.google.android.contacts",
        "com.samsung.android.app.contacts",
        "com.android.contacts",
    )
    const val GOOGLE = "com.google.android.googlequicksearchbox"
}

data class HandOffAvailability(
    val whatsAppPackage: String?,
    val contactsPackage: String?,
    val contactsAllowed: Boolean,
    val googleAllowed: Boolean,
)

fun handOffAvailability(
    installedPackages: Collection<String>,
    blockaded: Collection<String>,
): HandOffAvailability {
    val installed = installedPackages.toSet()
    val blocked = blockaded.toSet()
    val whatsApp = SearchPackages.WHATSAPP.firstOrNull { it in installed && it !in blocked }
    val contacts = SearchPackages.CONTACTS.firstOrNull { it in installed && it !in blocked }
    val contactsInstalled = SearchPackages.CONTACTS.any { it in installed }
    val contactsAllowed = contacts != null || !contactsInstalled
    val googleAllowed = SearchPackages.GOOGLE !in blocked
    return HandOffAvailability(whatsApp, contacts, contactsAllowed, googleAllowed)
}

fun isNumberShaped(raw: String): Boolean {
    val q = raw.trim()
    if (q.isEmpty()) return false
    val digits = q.count { it.isDigit() }
    if (digits < 3) return false
    val nonWs = q.count { !it.isWhitespace() }
    if (nonWs == 0) return false
    if (q.startsWith("+") && digits >= 3) return true
    return digits.toDouble() / nonWs >= 0.55
}

fun hasConfidentAppMatch(query: String, apps: List<AppMatch>): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return false
    return apps.any {
        it.label.equals(q, ignoreCase = true) || it.label.startsWith(q, ignoreCase = true)
    }
}

fun showPersonHandOffs(query: String, filtered: List<AppMatch>): Boolean {
    val q = query.trim()
    if (q.length < 2) return false
    if (isNumberShaped(q)) return false
    if (hasConfidentAppMatch(q, filtered)) return false
    if (filtered.isEmpty()) return true
    if (q.contains(' ')) return true
    if (q.any { it.code >= 0x2E80 }) return true
    return filtered.size <= 2
}

fun visibleHandOffs(query: String, filtered: List<AppMatch>, availability: HandOffAvailability): List<HandOffKind> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    return if (isNumberShaped(q)) {
        buildList {
            add(HandOffKind.Call)
            if (availability.whatsAppPackage != null) add(HandOffKind.WhatsAppNumber)
            if (availability.contactsAllowed) add(HandOffKind.FindContacts)
        }
    } else if (showPersonHandOffs(q, filtered)) {
        buildList {
            if (availability.contactsAllowed) add(HandOffKind.SearchContacts)
            if (availability.whatsAppPackage != null) add(HandOffKind.SearchWhatsApp)
            if (availability.googleAllowed) add(HandOffKind.SearchGoogle)
        }
    } else {
        emptyList()
    }
}

fun handOffsAboveApps(query: String): Boolean = isNumberShaped(query)

/** Digits for ACTION_DIAL, keeping a leading plus. */
fun telDigits(raw: String): String {
    val q = raw.trim()
    val plus = q.startsWith("+")
    val digits = q.filter { it.isDigit() }
    return if (plus) "+$digits" else digits
}

fun telUri(raw: String): String = "tel:${telDigits(raw)}"

fun whatsAppDigits(raw: String): String = raw.filter { it.isDigit() }

fun whatsAppUri(raw: String): String = "https://wa.me/${whatsAppDigits(raw)}"

fun displayQuery(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")
