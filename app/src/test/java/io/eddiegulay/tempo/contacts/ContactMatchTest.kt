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
            DeviceContact(i.toLong(), "k$i", "Mina $i", listOf("071200000$i"))
        }
        assertEquals(8, matchContacts("Mina", book).size)
        assertTrue(matchContacts("  ", book).isEmpty())
    }

    @Test
    fun `a later number on the same person still matches`() {
        val mina = DeviceContact(
            1L,
            "k1",
            "Mina Okello",
            listOf("+255 712 345 678", "+255 754 111 222"),
        )
        assertTrue(matchContact(mina.displayName, mina.phones, "754"))
        assertEquals(listOf(mina), matchContacts("754", listOf(mina)))
    }

    @Test
    fun `plusPhone keeps every distinct number and puts a preferred one first`() {
        val first = null.plusPhone(1L, "k", "Mina", "0712 345 678")
        val two = first.plusPhone(1L, "k", "Mina", "0754 111 222")
        val again = two.plusPhone(1L, "k", "Mina", "0712345678")
        val preferred = again.plusPhone(1L, "k", "Mina", "+255 700 000 001", prefer = true)
        assertEquals(listOf("+255 700 000 001", "0712 345 678", "0754 111 222"), preferred.phones)
        assertEquals("+255 700 000 001", preferred.phone)
    }
}
