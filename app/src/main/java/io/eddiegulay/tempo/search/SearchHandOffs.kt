package io.eddiegulay.tempo.search

import io.eddiegulay.tempo.gym.foldKana

/**
 * People-context hand-offs from Search: launch Phone, Contacts, WhatsApp, mail, or Google with the
 * typed query. Live contact rows come from READ_CONTACTS. Tempo never reads the call log or inbox.
 * Calendar hits use the agenda already loaded for 予定.
 */

enum class HandOffKind {
    Call,
    WhatsAppNumber,
    FindContacts,
    SearchContacts,
    SearchWhatsApp,
    SearchGoogle,
    ComposeEmail,
    SearchMail,
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
    val MAIL = listOf(
        "com.google.android.gm",
        "com.samsung.android.email.provider",
        "com.microsoft.office.outlook",
    )
    const val GOOGLE = "com.google.android.googlequicksearchbox"
}

data class HandOffAvailability(
    val whatsAppPackage: String?,
    val contactsPackage: String?,
    val contactsAllowed: Boolean,
    val googleAllowed: Boolean,
    val mailPackage: String?,
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
    val mail = SearchPackages.MAIL.firstOrNull { it in installed && it !in blocked }
    return HandOffAvailability(whatsApp, contacts, contactsAllowed, googleAllowed, mail)
}

fun isNumberShaped(raw: String): Boolean {
    val q = raw.trim()
    if (q.isEmpty() || isEmailShaped(q)) return false
    val digits = q.count { it.isDigit() }
    if (digits < 3) return false
    val nonWs = q.count { !it.isWhitespace() }
    if (nonWs == 0) return false
    if (q.startsWith("+") && digits >= 3) return true
    return digits.toDouble() / nonWs >= 0.55
}

fun isEmailShaped(raw: String): Boolean {
    val q = raw.trim()
    val at = q.indexOf('@')
    if (at <= 0 || at == q.lastIndex) return false
    val domain = q.substring(at + 1)
    val dot = domain.indexOf('.')
    return dot > 0 && dot < domain.lastIndex && !q.contains(' ')
}

fun hasConfidentAppMatch(query: String, apps: List<AppMatch>): Boolean {
    val q = foldKana(query)
    if (q.isEmpty()) return false
    return apps.any {
        val label = foldKana(it.label)
        label == q || label.startsWith(q)
    }
}

fun matchAppFields(label: String, packageName: String, query: String): Boolean {
    val q = foldKana(query)
    if (q.isEmpty()) return false
    return foldKana(label).contains(q) || foldKana(packageName).contains(q)
}

fun showPersonHandOffs(query: String, filtered: List<AppMatch>): Boolean {
    val q = query.trim()
    if (q.length < 2) return false
    if (isNumberShaped(q) || isEmailShaped(q)) return false
    if (hasConfidentAppMatch(q, filtered)) return false
    if (filtered.isEmpty()) return true
    if (q.contains(' ')) return true
    if (q.any { it.code >= 0x2E80 }) return true
    return filtered.size <= 2
}

fun visibleHandOffs(
    query: String,
    filtered: List<AppMatch>,
    availability: HandOffAvailability,
    areas: SearchAreas = SearchAreas(),
    hasContactHits: Boolean = false,
): List<HandOffKind> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    return when {
        isEmailShaped(q) -> emailHandOffs(availability, areas)
        isNumberShaped(q) -> numberHandOffs(availability, areas, hasContactHits)
        showPersonHandOffs(q, filtered) -> personHandOffs(availability, areas, hasContactHits)
        else -> emptyList()
    }
}

private fun numberHandOffs(
    availability: HandOffAvailability,
    areas: SearchAreas,
    hasContactHits: Boolean,
): List<HandOffKind> =
    buildList {
        if (!hasContactHits) {
            if (areas.phone) add(HandOffKind.Call)
            if (areas.whatsApp && availability.whatsAppPackage != null) add(HandOffKind.WhatsAppNumber)
        }
        if (areas.contacts && availability.contactsAllowed && !hasContactHits) {
            add(HandOffKind.FindContacts)
        }
    }

private fun emailHandOffs(availability: HandOffAvailability, areas: SearchAreas): List<HandOffKind> =
    buildList {
        if (!areas.email) return@buildList
        add(HandOffKind.ComposeEmail)
        if (availability.mailPackage != null) add(HandOffKind.SearchMail)
    }

private fun personHandOffs(
    availability: HandOffAvailability,
    areas: SearchAreas,
    hasContactHits: Boolean,
): List<HandOffKind> =
    buildList {
        if (areas.contacts && availability.contactsAllowed && !hasContactHits) {
            add(HandOffKind.SearchContacts)
        }
        if (areas.whatsApp && availability.whatsAppPackage != null) add(HandOffKind.SearchWhatsApp)
        if (areas.email && availability.mailPackage != null) add(HandOffKind.SearchMail)
        if (areas.google && availability.googleAllowed) add(HandOffKind.SearchGoogle)
    }

fun handOffsAboveApps(query: String): Boolean = isNumberShaped(query) || isEmailShaped(query)

fun matchCalendarFields(title: String, location: String?, calendarName: String, query: String): Boolean {
    val raw = query.trim()
    if (raw.length < 2) return false
    val q = foldKana(raw)
    if (q.isEmpty()) return false
    return foldKana(title).contains(q) ||
        (location != null && foldKana(location).contains(q)) ||
        foldKana(calendarName).contains(q)
}

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

fun mailtoUri(raw: String): String = "mailto:${displayQuery(raw)}"

fun displayQuery(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")
