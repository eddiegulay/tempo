package io.eddiegulay.tempo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The segmentation rule behind Home's vertical corner: kanji and kana stack upright one glyph per
 * cell, a run of Latin becomes a single rotated cell, and a short number sits upright as one cell.
 */
class TategakiTest {

    @Test
    fun `kanji stacks one glyph per cell, upright`() {
        val cells = layoutTategaki("会議").cells
        assertEquals(listOf("会", "議"), cells.map { it.text })
        assertTrue(cells.none { it.rotated })
    }

    @Test
    fun `a latin word is one rotated cell, not one cell per letter`() {
        val cells = layoutTategaki("Standup").cells
        assertEquals(listOf("Standup"), cells.map { it.text })
        assertTrue(cells.single().rotated)
    }

    @Test
    fun `a latin phrase stays one run rather than fracturing at its spaces`() {
        val cells = layoutTategaki("Design review").cells
        assertEquals(listOf("Design review"), cells.map { it.text })
        assertTrue(cells.single().rotated)
    }

    @Test
    fun `mixed scripts split into upright kanji and a rotated latin run`() {
        val cells = layoutTategaki("会議 Standup").cells
        assertEquals(listOf("会", "議", "Standup"), cells.map { it.text })
        assertEquals(listOf(false, false, true), cells.map { it.rotated })
    }

    @Test
    fun `a short number sits upright as a single cell`() {
        // 縦中横: "2" is one upright cell between the kanji, not a rotated fragment.
        val cells = layoutTategaki("第2会議室").cells
        assertEquals(listOf("第", "2", "会", "議", "室"), cells.map { it.text })
        assertTrue(cells.none { it.rotated })
    }

    @Test
    fun `a long number rotates rather than becoming a tall stack of digits`() {
        val cells = layoutTategaki("2026年").cells
        assertEquals(listOf("2026", "年"), cells.map { it.text })
        assertEquals(listOf(true, false), cells.map { it.rotated })
    }

    @Test
    fun `punctuation rides along with its latin run`() {
        val cells = layoutTategaki("1:1 with Mei").cells
        assertEquals(listOf("1:1 with Mei"), cells.map { it.text })
        assertTrue(cells.single().rotated)
    }

    @Test
    fun `an over-long upright title truncates and reports the overflow`() {
        val laid = layoutTategaki("一二三四五六七八九十", maxCells = 8)
        assertEquals(8, laid.cells.size)
        assertTrue(laid.truncated)
    }

    @Test
    fun `a title that just fits is not marked truncated`() {
        val laid = layoutTategaki("一二三四五六七八", maxCells = 8)
        assertEquals(8, laid.cells.size)
        assertFalse(laid.truncated)
    }

    @Test
    fun `whitespace between upright runs produces no empty cell`() {
        val cells = layoutTategaki("  会議　").cells
        assertEquals(listOf("会", "議"), cells.map { it.text })
    }

    @Test
    fun `an empty title yields no cells`() {
        assertTrue(layoutTategaki("   ").cells.isEmpty())
    }
}
