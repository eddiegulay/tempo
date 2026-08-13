package io.eddiegulay.tempo.gym

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one function in this feature most likely to be subtly wrong, given its own class because
 * `04-library-records.md` §7 asks for one.
 *
 * "Subtly" is the operative word. Every failure mode here compiles, runs, and returns a string that
 * looks plausible in a debugger — it just never matches the row the user was looking at. A stripped
 * voicing mark makes ｶﾞｯﾂ and ガッツ two different words; a preserved one makes them the same word
 * spelled two ways. Both are one line of code and neither announces itself.
 *
 * The four cases named in the brief — script equivalence, half width, long vowels, voicing — are each
 * a separate way a Japanese keyboard can hand you the same word.
 */
class KanaFoldingTest {

    /** U+3099, the combining voicing mark an NFD string carries instead of a precomposed が. */
    private val DAKUTEN = "\u3099"

    /** U+309A, its handakuten twin. */
    private val HANDAKUTEN = "\u309A"

    @Test
    fun `katakana and hiragana are the same word`() {
        // `04-library-records.md` §3, edge case 1: スクワット must be found by typing すくわっと.
        assertEquals(foldKana("スクワット"), foldKana("すくわっと"))
        assertEquals("すくわっと", foldKana("スクワット"))
    }

    @Test
    fun `a prefix of the folded form is what makes すく find スクワット`() {
        // The same edge case's second half. `contains` does the work, but only if both sides fold.
        val needle = foldKana("すく")
        assertTrue(foldKana("スクワット").contains(needle))
    }

    @Test
    fun `half-width katakana widen to the same word`() {
        assertEquals(foldKana("スクワット"), foldKana("ｽｸﾜｯﾄ"))
        assertEquals("らんじ", foldKana("ﾗﾝｼﾞ"))
    }

    @Test
    fun `a half-width voicing mark composes onto the character before it`() {
        // This is the case a naive width conversion gets wrong: ｶﾞ is two codepoints where ガ is one,
        // so a per-character map leaves か followed by an orphaned mark and the two spellings stop
        // matching each other for good.
        assertEquals(foldKana("ガッツ"), foldKana("ｶﾞｯﾂ"))
        assertEquals("がっつ", foldKana("ｶﾞｯﾂ"))
    }

    @Test
    fun `a half-width handakuten composes too`() {
        assertEquals(foldKana("パピプペポ"), foldKana("ﾊﾟﾋﾟﾌﾟﾍﾟﾎﾟ"))
        assertEquals("ぱぴぷぺぽ", foldKana("ﾊﾟﾋﾟﾌﾟﾍﾟﾎﾟ"))
    }

    @Test
    fun `a decomposed voicing mark composes as well as a half-width one`() {
        // A string that has been through NFD — a paste from another app, a restored backup — carries
        // U+3099 rather than a precomposed が. It has to reach the same normal form or the same word
        // typed and pasted will not match itself.
        assertEquals(foldKana("が"), foldKana("か" + DAKUTEN))
        assertEquals(foldKana("ぱ"), foldKana("は" + HANDAKUTEN))
        assertEquals(foldKana("ガ"), foldKana("カ" + DAKUTEN))
    }

    @Test
    fun `an orphaned voicing mark is kept rather than swallowed`() {
        // Nothing precedes it, so there is nothing to voice. Dropping it would make a query of one
        // stray mark fold to the empty string, which matches every routine in the library.
        assertEquals(DAKUTEN, foldKana(DAKUTEN))
        // And it reaches the same form whichever width it arrived in, or the same orphan would have
        // two normal forms — which is the exact bug this function exists to remove.
        assertEquals(foldKana(DAKUTEN), foldKana("ﾞ"))
        assertEquals(foldKana("あ" + DAKUTEN), foldKana("ｱﾞ"))
    }

    @Test
    fun `voicing is preserved, so ハンド does not find バンド`() {
        // The tempting over-fold. It would help a fumbled IME and would also make the results list the
        // thing the user has to disambiguate, which is worse than one more keystroke.
        assertNotEquals(foldKana("はんど"), foldKana("ばんど"))
    }

    @Test
    fun `the long vowel mark is dropped from both sides`() {
        // バーピー and ばぴ reach the same place, in every width.
        assertEquals("ばぴ", foldKana("バーピー"))
        assertEquals(foldKana("バーピー"), foldKana("ﾊﾞｰﾋﾟｰ"))
        assertEquals(foldKana("バーピー"), foldKana("ばぴ"))
    }

    @Test
    fun `small kana stay small`() {
        // Rejected fold: ャ to ヤ. It would make きょう and きよう one string, and they are different
        // words. The small-kana slip is far rarer than the four this function does fold.
        assertNotEquals(foldKana("きょう"), foldKana("きよう"))
        // ッ is small and must survive, or すくわっと stops matching スクワット.
        assertEquals("っ", foldKana("ッ"))
    }

    @Test
    fun `ヴ has a hiragana form and reaches it`() {
        assertEquals("ゔ", foldKana("ヴ"))
    }

    @Test
    fun `latin folds case and width, because origin is latin`() {
        // §3 edge case 2: `origin` is Latin (CrossFit, 2004-12-29) and ASCII search is
        // case-insensitive.
        assertEquals("crossfit", foldKana("CrossFit"))
        assertEquals(foldKana("crossfit"), foldKana("ＣｒｏｓｓＦｉｔ"))
    }

    @Test
    fun `surrounding space is trimmed before anything else happens`() {
        // The station picker's edge case 6, and the reason it matters: an untrimmed trailing space
        // makes every query match nothing at all once the IME commits one.
        assertEquals("すくわっと", foldKana("  スクワット  "))
        assertEquals("すくわっと", foldKana("　スクワット　"))
    }

    @Test
    fun `an inner ideographic space narrows rather than surviving as a second kind of space`() {
        assertEquals("あさ の", foldKana("あさ　の"))
    }

    @Test
    fun `kanji and everything else pass through untouched`() {
        // Folding must not reach into the script that carries most routine names. 懸垂 surfacing
        // シンディ depends on this string being left exactly as typed.
        assertEquals("懸垂", foldKana("懸垂"))
        assertEquals("七分間", foldKana("七分間"))
    }

    @Test
    fun `an empty query folds to empty, which every caller reads as no filter`() {
        assertEquals("", foldKana(""))
        assertEquals("", foldKana("   "))
    }

    @Test
    fun `the half-width table lines up with its full-width twin at every segment boundary`() {
        // A one-character drift in either constant silently shifts every kana after the gap — ｽ would
        // widen to セ, and the search would return the *wrong* routines rather than none, which is far
        // harder to notice than a crash. The anchors are the first entry of each run in the table, so
        // a drift anywhere lands on one of them.
        assertEquals("を", foldKana("ｦ"))
        assertEquals("ぁ", foldKana("ｧ"))
        assertEquals("ゃ", foldKana("ｬ"))
        assertEquals("っ", foldKana("ｯ"))
        assertEquals("あ", foldKana("ｱ"))
        assertEquals("か", foldKana("ｶ"))
        assertEquals("さ", foldKana("ｻ"))
        assertEquals("た", foldKana("ﾀ"))
        assertEquals("な", foldKana("ﾅ"))
        assertEquals("は", foldKana("ﾊ"))
        assertEquals("ま", foldKana("ﾏ"))
        assertEquals("や", foldKana("ﾔ"))
        assertEquals("ら", foldKana("ﾗ"))
        assertEquals("わ", foldKana("ﾜ"))
        assertEquals("ん", foldKana("ﾝ"))
    }

    @Test
    fun `every half-width kana in the table reaches a full-width form`() {
        // The whole range, so a table one character short cannot hide in the gaps between anchors.
        // U+FF70 is excluded on purpose: it is the half-width long vowel mark, and reaching the empty
        // string is exactly what this function is supposed to do to it.
        for (code in 0xFF61..0xFF9D) {
            if (code == 0xFF70) continue
            val half = code.toChar().toString()
            assertNotEquals("no full-width twin for U+%04X".format(code), "", foldKana(half))
            assertNotEquals("U+%04X did not widen".format(code), half, foldKana(half))
        }
        assertEquals("", foldKana("ｰ"))
    }
}
