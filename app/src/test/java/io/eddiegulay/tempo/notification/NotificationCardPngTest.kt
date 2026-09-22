package io.eddiegulay.tempo.notification

import io.eddiegulay.tempo.ui.theme.PaperColors
import io.eddiegulay.tempo.ui.theme.SumiColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The export helpers that can be checked without a device: the opaque fill (so a pasted bubble
 * is not invisible) and the filename stem (so MediaStore always gets a legal display name).
 */
class NotificationCardPngTest {

    @Test
    fun `opaque card fill is locked at alpha 1 on both palettes`() {
        assertEquals(1f, PaperColors.opaqueCardFill().alpha)
        assertEquals(1f, SumiColors.opaqueCardFill().alpha)
    }

    @Test
    fun `paper fill stays cream and sumi fill stays charcoal`() {
        // compositeOver of 3.5% ink on #F2EEE4 must remain a light wash, not the ink itself.
        val paper = PaperColors.opaqueCardFill()
        assertTrue("paper red ${paper.red}", paper.red > 0.85f)
        assertTrue("paper green ${paper.green}", paper.green > 0.85f)
        // compositeOver of 5% bone on #1A1814 must remain dark.
        val sumi = SumiColors.opaqueCardFill()
        assertTrue("sumi red ${sumi.red}", sumi.red < 0.20f)
    }

    @Test
    fun `file stem strips punctuation and keeps the post time`() {
        assertEquals("Tempo-LINE-家族-1710000000000", exportFileStem("LINE / 家族", 1_710_000_000_000L))
        assertEquals("Tempo-notification-1", exportFileStem("   ", 1L))
        assertEquals("Tempo-notification-1", exportFileStem("", 1L))
    }

    @Test
    fun `a long app label is truncated so the display name stays a name`() {
        val stem = exportFileStem("あ".repeat(80), 9L)
        assertTrue(stem.startsWith("Tempo-"))
        assertTrue(stem.endsWith("-9"))
        assertEquals(40, stem.removePrefix("Tempo-").removeSuffix("-9").length)
    }
}
