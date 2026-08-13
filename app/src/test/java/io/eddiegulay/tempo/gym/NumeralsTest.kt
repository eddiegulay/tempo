package io.eddiegulay.tempo.gym

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The two numeral shapes 鍛錬 adds, and the boundaries where each stops being readable.
 *
 * Both functions are printed straight into Mincho at 22.sp or larger, so every failure here is one a
 * user sees rather than one a log records: a coefficient that reads `1.0` in a column of brushwork, a
 * hero time that claims a second the user did not train, a duration that says 〇秒 where it meant
 * nothing at all.
 */
class NumeralsTest {

    @Test
    fun `a coefficient is kanji digits around an ASCII full stop`() {
        // `04-library-records.md` §6's Numerals note, and the picker's own column: 一.〇 / 〇.五.
        assertEquals("一.〇", coefficientLabel(1.0))
        assertEquals("〇.五", coefficientLabel(0.5))
        assertEquals("二.五", coefficientLabel(2.5))
        assertEquals("一.八", coefficientLabel(1.8))
    }

    @Test
    fun `a two-digit coefficient keeps its place value`() {
        // The rejected implementation substituted kanji per character and would print 一〇.〇 here.
        assertEquals("十.〇", coefficientLabel(10.0))
    }

    @Test
    fun `a coefficient rounds once, before it is split`() {
        // Splitting first and rounding the parts prints 一.十 for this, which is not a number.
        assertEquals("一.三", coefficientLabel(1.25))
        assertEquals("二.〇", coefficientLabel(1.96))
    }

    @Test
    fun `a movement with no rep semantics shows a dash, not a zero`() {
        // 走る, `04-library-records.md` §3 EXERCISE_INDEX edge case 5. 〇.〇 would imply a countable
        // movement worth nothing, which is a different and false claim.
        assertEquals("—", coefficientLabel(null))
    }

    @Test
    fun `a coefficient that cannot exist reports as missing rather than as a number`() {
        assertEquals("—", coefficientLabel(-1.0))
        assertEquals("—", coefficientLabel(Double.NaN))
        assertEquals("—", coefficientLabel(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `a duration under a minute is seconds alone`() {
        assertEquals("五秒", durationKanji(5))
        assertEquals("五十九秒", durationKanji(59))
    }

    @Test
    fun `a whole number of minutes drops its seconds`() {
        // 二十分, the routine detail page's time cap — never 二十分〇秒.
        assertEquals("一分", durationKanji(60))
        assertEquals("二十分", durationKanji(1200))
    }

    @Test
    fun `the record screen's hero time reads six minutes fourteen seconds`() {
        // design §4's own example, which is what makes it the one to pin.
        assertEquals("六分十四秒", durationKanji(374))
        assertEquals("一分三十秒", durationKanji(90))
    }

    @Test
    fun `a long duration counts in minutes, never in hours`() {
        // The history subtitle's 二千四百分 is the spec's own large duration and it is bare minutes.
        // An hours form would need copy nobody has written and a hero layout nobody has designed.
        assertEquals("二千四百分", durationKanji(144_000))
    }

    @Test
    fun `a duration that cannot exist reads as zero rather than as a negative`() {
        assertEquals("〇秒", durationKanji(0))
        assertEquals("〇秒", durationKanji(-5))
    }

    @Test
    fun `a millisecond clock truncates, so the hero never claims a second that did not elapse`() {
        // 6:14.9 has completed fourteen seconds. Rounding up would also disagree with the active_ms
        // a personal best was decided on, which is the one number that must match exactly.
        assertEquals("六分十四秒", durationKanjiFromMs(374_900L))
        assertEquals("六分十四秒", durationKanjiFromMs(374_000L))
        assertEquals("〇秒", durationKanjiFromMs(-1L))
    }

    @Test
    fun `a breakdown duration stays arabic so a column of them can be scanned`() {
        assertEquals("0:41", clockDuration(41_000L))
        assertEquals("0:00", clockDuration(0L))
        // Minutes are not capped at 59: a for-time station really can run this long, and a wrapped
        // 12:03 would be a worse report than a long one.
        assertEquals("12:03", clockDuration(723_000L))
    }
}
