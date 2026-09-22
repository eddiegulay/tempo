package io.eddiegulay.tempo.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Share-face rules that do not need a device: last-three thread, reply gate, placeholder bodies.
 */
class NotificationShareContentTest {

    private fun msg(text: String, sender: String = "") =
        TempoNotificationMessage(text = text, sender = sender)

    @Test
    fun `last three of five are kept in order`() {
        val messages = (1..5).map { msg("m$it") }
        assertEquals(
            listOf("m3", "m4", "m5"),
            shareThread(messages, hasReply = true).map { it.text },
        )
    }

    @Test
    fun `fewer than three messages are not padded`() {
        val messages = listOf(msg("a"), msg("b"))
        assertEquals(listOf("a", "b"), shareThread(messages, hasReply = true).map { it.text })
    }

    @Test
    fun `no messages stays empty`() {
        assertTrue(shareThread(emptyList(), hasReply = true).isEmpty())
    }

    @Test
    fun `no reply action hides the thread even when messages exist`() {
        val messages = listOf(msg("a"), msg("b"), msg("c"))
        assertTrue(shareThread(messages, hasReply = false).isEmpty())
    }

    @Test
    fun `placeholder body is hidden only when a picture is present`() {
        assertNull(displayBody("Sticker", hasPicture = true))
        assertNull(displayBody("  スタンプ  ", hasPicture = true))
        assertEquals("Sticker", displayBody("Sticker", hasPicture = false))
        assertEquals("look at this", displayBody("look at this", hasPicture = true))
    }

    @Test
    fun `body that only repeats the last thread line is dropped`() {
        val thread = listOf(msg("hi"), msg("later"))
        assertNull(displayBody("later", hasPicture = false, shareMessages = thread))
        assertEquals("a caption", displayBody("a caption", hasPicture = false, shareMessages = thread))
    }
}
