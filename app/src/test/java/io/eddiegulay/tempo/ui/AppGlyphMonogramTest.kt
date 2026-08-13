package io.eddiegulay.tempo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The monogram tile's first mark — the fallback for an app Tempo cannot classify.
 *
 * The rule is one *grapheme cluster*, not one `Char`. A `Char` is a UTF-16 code unit, so an app named
 * with an emoji used to yield an unpaired surrogate: not a character, and drawn as tofu.
 */
class AppGlyphMonogramTest {

    @Test
    fun `a latin name gives its first letter`() {
        assertEquals("S", firstMark("Signal"))
    }

    @Test
    fun `a kanji name gives its first glyph`() {
        assertEquals("電", firstMark("電話"))
    }

    @Test
    fun `leading and trailing space is ignored`() {
        assertEquals("M", firstMark("  Maps  "))
    }

    @Test
    fun `an emoji name keeps its surrogate pair whole`() {
        // U+1F4D8 — two UTF-16 units. `firstOrNull()` returned only the high surrogate.
        val name = "📘 Reader"
        assertEquals("📘", firstMark(name))
        assertEquals(2, firstMark(name).length)
    }

    @Test
    fun `a flag stays one mark rather than half a pair of regional indicators`() {
        // 🇯🇵 is U+1F1EF U+1F1F5 — one grapheme cluster, four UTF-16 units.
        val flag = "🇯🇵"
        assertEquals(flag, firstMark("$flag Japan"))
    }

    @Test
    fun `an empty or blank name falls back to the middle dot`() {
        assertEquals("・", firstMark(""))
        assertEquals("・", firstMark("   "))
    }
}
