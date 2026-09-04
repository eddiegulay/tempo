package io.eddiegulay.tempo.search

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import io.eddiegulay.tempo.i18n.Strings

/**
 * Builds the outbound intents for [HandOffKind]. Each one hands the typed query to another app.
 * Tempo does not read the result.
 */
fun launchHandOff(
    context: Context,
    kind: HandOffKind,
    query: String,
    availability: HandOffAvailability,
    strings: Strings,
) {
    val intent = resolveHandOffIntent(context, kind, query, availability) ?: run {
        Toast.makeText(context, strings.fault.launchFailed, Toast.LENGTH_SHORT).show()
        return
    }
    startSafely(context, intent, strings)
}

fun launchContactCall(context: Context, phone: String, strings: Strings) {
    val digits = telDigits(phone)
    if (digits.isEmpty()) {
        Toast.makeText(context, strings.fault.launchFailed, Toast.LENGTH_SHORT).show()
        return
    }
    startSafely(context, Intent(Intent.ACTION_DIAL, Uri.parse(telUri(phone))), strings)
}

fun launchContactMessage(context: Context, phone: String, strings: Strings) {
    val digits = telDigits(phone)
    if (digits.isEmpty()) {
        Toast.makeText(context, strings.fault.launchFailed, Toast.LENGTH_SHORT).show()
        return
    }
    startSafely(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$digits")), strings)
}

fun launchContactWhatsApp(
    context: Context,
    phone: String,
    packageName: String,
    strings: Strings,
) {
    val digits = whatsAppDigits(phone)
    if (digits.isEmpty()) {
        Toast.makeText(context, strings.fault.launchFailed, Toast.LENGTH_SHORT).show()
        return
    }
    startSafely(
        context,
        Intent(Intent.ACTION_VIEW, Uri.parse(whatsAppUri(phone))).setPackage(packageName),
        strings,
    )
}

private fun startSafely(context: Context, intent: Intent, strings: Strings) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, strings.fault.launchFailed, Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, strings.fault.launchFailed, Toast.LENGTH_SHORT).show()
    }
}

fun resolveHandOffIntent(
    context: Context,
    kind: HandOffKind,
    query: String,
    availability: HandOffAvailability,
): Intent? {
    val q = displayQuery(query)
    if (q.isEmpty()) return null
    return when (kind) {
        HandOffKind.Call -> Intent(Intent.ACTION_DIAL, Uri.parse(telUri(q)))
        HandOffKind.WhatsAppNumber -> {
            val pkg = availability.whatsAppPackage ?: return null
            Intent(Intent.ACTION_VIEW, Uri.parse(whatsAppUri(q))).setPackage(pkg)
        }
        HandOffKind.FindContacts ->
            Intent(ContactsContract.Intents.SHOW_OR_CREATE_CONTACT)
                .setData(Uri.fromParts("tel", telDigits(q), null))
        HandOffKind.SearchContacts -> {
            val search = Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, q)
            availability.contactsPackage?.let { search.setPackage(it) }
            search
        }
        HandOffKind.SearchWhatsApp -> {
            val pkg = availability.whatsAppPackage ?: return null
            context.packageManager.getLaunchIntentForPackage(pkg)
        }
        HandOffKind.SearchGoogle -> Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q)
        HandOffKind.ComposeEmail -> Intent(Intent.ACTION_SENDTO, Uri.parse(mailtoUri(q)))
        HandOffKind.SearchMail -> {
            val pkg = availability.mailPackage ?: return null
            Intent(Intent.ACTION_SEARCH)
                .putExtra(SearchManager.QUERY, q)
                .setPackage(pkg)
        }
    }
}
