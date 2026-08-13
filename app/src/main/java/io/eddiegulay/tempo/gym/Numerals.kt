package io.eddiegulay.tempo.gym

import io.eddiegulay.tempo.data.JapaneseDate

/*
 * 鍛錬's two numeral shapes that the launcher never needed: a difficulty coefficient, and a duration
 * you read rather than watch.
 *
 * **There is no `kanjiExtended` here.** `04-library-records.md` §7 lists one, and it is wrong on that
 * point — `DECISIONS.md` §Q7 rules that `JapaneseDate.kanjiExtended` (Phase 0, P5) is the one kanji
 * numeral formatter in this app and that this file delegates to it. A second formatter would not fail
 * loudly; it would agree on every number anyone tested and disagree on 千百 or on the 9999 fallback,
 * in one of two screens, for one release.
 *
 * Both functions here return a string for *every* input, including inputs that are caller bugs. A
 * formatter that throws takes down the records screen over a bad aggregate, which is strictly worse
 * than a screen showing an obviously wrong number — the same bargain `kanjiExtended` struck for
 * negatives.
 */

/** What a coefficient, a pace or a duration says when there is nothing to say. Never `〇`, never blank. */
private const val NO_VALUE = "—"

/**
 * A difficulty coefficient as the library and picker print it: `一.〇`, `〇.五`, `二.五`.
 *
 * Kanji digits with an **ASCII full stop** between them, which is `04-library-records.md` §6's
 * Numerals note verbatim: a bare `1.0` in a Mincho line reads as foreign matter, and the picker sets
 * this value in a column beside 腕立て伏せ and ディップス where a lone pair of latin digits is the only
 * thing on the page that is not brushwork.
 *
 * *Rejected* — an ideographic full stop `。`, which is a sentence terminator in Japanese and would
 * read as `一。` followed by a stray 〇. The spec's own examples use the ASCII stop and so does this.
 *
 * *Rejected* — `String.format("%.1f")` on the whole value and then a per-character kanji substitution.
 * It works for the eight coefficients that exist and produces `一.〇` for `10.0` the day someone seeds
 * a two-digit one, because the substitution has no notion of place. The integer part goes through
 * [JapaneseDate.kanjiExtended] so 10 is 十 and not 一〇.
 *
 * @param coefficient the exercise's `difficulty`, or null for a movement that has no rep semantics —
 *   走る is the shipped case, and `04-library-records.md` §3's EXERCISE_INDEX edge case 5 says its
 *   coefficient renders [NO_VALUE] rather than a number that would imply you could count it.
 *
 * A negative or non-finite value gets [NO_VALUE] too. There is no such coefficient; getting one is a
 * seed bug, and `—` is what a missing value already looks like everywhere else on the page, which is
 * a truer report than 負一.〇 dressed up as considered copy.
 */
fun coefficientLabel(coefficient: Double?): String {
    if (coefficient == null || !coefficient.isFinite() || coefficient < 0.0) return NO_VALUE
    // Round to the one decimal place every seeded coefficient carries, then split — rounding after
    // splitting would print 一.九 for 1.95 and 一.十 for 1.96.
    val tenths = Math.round(coefficient * 10.0)
    val whole = (tenths / 10).toInt()
    val fraction = (tenths % 10).toInt()
    return JapaneseDate.kanjiExtended(whole) + "." + JapaneseDate.kanji(fraction)
}

/**
 * A duration you read: `五秒`, `一分三十秒`, `六分十四秒`, `二十分`.
 *
 * This is the counts-are-kanji half of `DECISIONS.md` §Q4. A *ticking* value stays arabic — the
 * player's countdown, the breakdown's `0:41`, the wheel mid-spin — because a kanji column changing
 * under the finger is unreadable. Everything that has stopped moving is kanji, and by the time a
 * duration reaches this function it has stopped: it is a hero time, a rest length in a read-only row,
 * an estimate.
 *
 * **A whole number of minutes drops its seconds** — 1200 is 二十分, not 二十分〇秒 — because that is
 * how `04-library-records.md` §3's routine detail prints a twenty-minute cap, and 〇秒 is a glyph that
 * says only that the author did not think about it.
 *
 * **There are no hours.** 6000 seconds is 百分, not 一時間四十分. The specs never write a duration with
 * 時間 in it and the one large duration they do write — the history subtitle's 二千四百分 — is in bare
 * minutes, so minutes is the unit this app counts in. Inventing an hours form would also mean deciding
 * what 一時間 does to a hero number that design §4 sizes at 64.sp, which is a layout decision nobody
 * has made.
 *
 * A negative reads as 〇秒. Durations are differences of monotonic clocks and cannot be negative
 * (`03-player.md` §E.4); one that is has already been mismeasured, and 負六分 would state that with a
 * confidence the value has not earned.
 */
fun durationKanji(totalSeconds: Int): String {
    if (totalSeconds <= 0) return JapaneseDate.kanji(0) + "秒"
    if (totalSeconds < 60) return JapaneseDate.kanji(totalSeconds) + "秒"
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val head = JapaneseDate.kanjiExtended(minutes) + "分"
    return if (seconds == 0) head else head + JapaneseDate.kanjiExtended(seconds) + "秒"
}

/**
 * The same duration from a millisecond clock, **truncated** rather than rounded.
 *
 * A stopwatch that has run 6:14.9 has completed six minutes and fourteen seconds; rounding it up to
 * 六分十五秒 reports a second the user did not train. It also makes the hero number disagree with the
 * `session.active_ms` a PR was decided on, which is the one number on the screen that has to be
 * exactly the stored one.
 */
fun durationKanjiFromMs(millis: Long): String =
    durationKanji(if (millis <= 0L) 0 else (millis / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())

/**
 * The arabic `m:ss` the breakdown rows use — `0:41`, `12:03`.
 *
 * Deliberately *not* kanji, and deliberately not [durationKanji]. The 内訳 is a column of a dozen
 * durations read against each other (design §4), and a column of 四十一秒 / 三十秒 / 三十八秒 cannot be
 * scanned — the digits do not line up and the eye has to parse each one. `03-player.md`'s COMPLETE
 * mock prints exactly this form, and it is the same reasoning that keeps the countdown in arabic.
 *
 * Minutes are not zero-padded and are not capped at 59: a for-time station can genuinely run 12:03,
 * and `72:00` is a better report than a wrapped `12:00`.
 */
fun clockDuration(millis: Long): String {
    val totalSeconds = if (millis <= 0L) 0L else millis / 1000L
    return "${totalSeconds / 60}:%02d".format(totalSeconds % 60)
}
