package io.eddiegulay.tempo.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactMatchTest {

    @Test
    fun `a name or number fragment hits the contact`() {
        assertTrue(matchContact("Mina Okello", "+255 712 345 678", "mina"))
        assertTrue(matchContact("Mina Okello", "+255 712 345 678", "712"))
        assertFalse(matchContact("Mina Okello", "+255 712 345 678", "x"))
        assertFalse(matchContact("Mina Okello", "+255 712 345 678", "M"))
    }

    @Test
    fun `kana folding treats hiragana and katakana as the same name`() {
        assertTrue(matchContact("ヤマダ", "09012345678", "やまだ"))
        assertTrue(matchContact("やまだ", "09012345678", "ヤマダ"))
    }

    @Test
    fun `matchContacts caps how many people Search shows`() {
        val book = (1..12).map { i ->
            DeviceContact(i.toLong(), "k$i", "Mina $i", "071200000$i")
        }
        assertEquals(8, matchContacts("Mina", book).size)
        assertTrue(matchContacts("  ", book).isEmpty())
    }
}
