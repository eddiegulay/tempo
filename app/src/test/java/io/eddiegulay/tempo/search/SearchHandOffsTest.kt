package io.eddiegulay.tempo.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Search hand-offs: when a typed query should offer Call / Contacts / WhatsApp / Google, without
 * Tempo ever reading contacts or the call log.
 */
class SearchHandOffsTest {

    private val allOpen = HandOffAvailability(
        whatsAppPackage = "com.whatsapp",
        contactsPackage = "com.google.android.contacts",
        contactsAllowed = true,
        googleAllowed = true,
    )

    @Test
    fun `a phone-shaped query offers call WhatsApp and contacts`() {
        val hits = visibleHandOffs("0712 345 678", emptyList(), allOpen)
        assertEquals(
            listOf(HandOffKind.Call, HandOffKind.WhatsAppNumber, HandOffKind.FindContacts),
            hits,
        )
        assertTrue(handOffsAboveApps("0712 345 678"))
    }

    @Test
    fun `a name with no app match offers contacts WhatsApp and Google`() {
        val hits = visibleHandOffs("Mina Okello", emptyList(), allOpen)
        assertEquals(
            listOf(HandOffKind.SearchContacts, HandOffKind.SearchWhatsApp, HandOffKind.SearchGoogle),
            hits,
        )
        assertFalse(handOffsAboveApps("Mina Okello"))
    }

    @Test
    fun `a confident app match does not dump person hand-offs`() {
        val chrome = listOf(AppMatch("Chrome", "com.android.chrome"))
        assertTrue(visibleHandOffs("Chr", chrome, allOpen).isEmpty())
        assertTrue(visibleHandOffs("Chrome", chrome, allOpen).isEmpty())
    }

    @Test
    fun `blockaded WhatsApp is omitted from both blocks`() {
        val noWa = allOpen.copy(whatsAppPackage = null)
        assertFalse(visibleHandOffs("0712345678", emptyList(), noWa).contains(HandOffKind.WhatsAppNumber))
        assertFalse(visibleHandOffs("Mina", emptyList(), noWa).contains(HandOffKind.SearchWhatsApp))
    }

    @Test
    fun `blank query offers nothing so the drawer stays apps only`() {
        assertTrue(visibleHandOffs("", emptyList(), allOpen).isEmpty())
        assertTrue(visibleHandOffs("   ", emptyList(), allOpen).isEmpty())
    }

    @Test
    fun `dial and WhatsApp uris keep digits and drop separators`() {
        assertEquals("tel:+255712345678", telUri("+255 712-345-678"))
        assertEquals("https://wa.me/255712345678", whatsAppUri("+255 712-345-678"))
    }

    @Test
    fun `availability hides WhatsApp when the package is only present behind the blockade`() {
        val avail = handOffAvailability(
            installedPackages = listOf("com.whatsapp", "com.google.android.contacts"),
            blockaded = listOf("com.whatsapp"),
        )
        assertEquals(null, avail.whatsAppPackage)
        assertTrue(avail.contactsAllowed)
    }

    @Test
    fun `the manifest adds no contacts call-log phone or internet permission`() {
        val manifest = repoFile("src/main/AndroidManifest.xml").readText()
        val declared = Regex("""<uses-permission[^>]*android:name="([^"]+)"""")
            .findAll(manifest)
            .map { it.groupValues[1] }
            .toList()
        listOf(
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.CALL_PHONE",
            "android.permission.READ_PHONE_STATE",
            "android.permission.READ_PHONE_NUMBERS",
            "android.permission.INTERNET",
        ).forEach { permission ->
            assertFalse("$permission must stay undeclared: $declared", permission in declared)
        }
    }

    private fun repoFile(relative: String): java.io.File {
        var dir: java.io.File? = java.io.File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            java.io.File(dir, relative).takeIf { it.isFile }?.let { return it }
            java.io.File(dir, "app/$relative").takeIf { it.isFile }?.let { return it }
            dir = dir.parentFile
        }
        throw AssertionError("$relative not found from ${System.getProperty("user.dir")}")
    }
}
