package io.eddiegulay.tempo.gym.session.ui

import io.eddiegulay.tempo.gym.Phase
import io.eddiegulay.tempo.gym.session.RestKind
import io.eddiegulay.tempo.gym.session.Segment
import io.eddiegulay.tempo.ui.gym.session.accruedLine
import io.eddiegulay.tempo.ui.gym.session.addedStateDescription
import io.eddiegulay.tempo.ui.gym.session.chosenSecondsLabel
import io.eddiegulay.tempo.ui.gym.session.counterLabel
import io.eddiegulay.tempo.ui.gym.session.countdownAnnouncement
import io.eddiegulay.tempo.ui.gym.session.cycleDotsLabel
import io.eddiegulay.tempo.ui.gym.session.elapsedLine
import io.eddiegulay.tempo.ui.gym.session.extendDisabledDescription
import io.eddiegulay.tempo.ui.gym.session.extendedSuffix
import io.eddiegulay.tempo.ui.gym.session.formatCountdown
import io.eddiegulay.tempo.ui.gym.session.nextUpDescription
import io.eddiegulay.tempo.ui.gym.session.nextUpLabel
import io.eddiegulay.tempo.ui.gym.session.pacerLabel
import io.eddiegulay.tempo.ui.gym.session.pausedAnnouncement
import io.eddiegulay.tempo.ui.gym.session.pausedNumeralDescription
import io.eddiegulay.tempo.ui.gym.session.prepareAnnouncement
import io.eddiegulay.tempo.ui.gym.session.prepareNumeral
import io.eddiegulay.tempo.ui.gym.session.prescriptionLabel
import io.eddiegulay.tempo.ui.gym.session.progressLabel
import io.eddiegulay.tempo.ui.gym.session.quitOptions
import io.eddiegulay.tempo.ui.gym.session.quitSummaryLine
import io.eddiegulay.tempo.ui.gym.session.repDoneDescription
import io.eddiegulay.tempo.ui.gym.session.repHero
import io.eddiegulay.tempo.ui.gym.session.repHeroDescription
import io.eddiegulay.tempo.ui.gym.session.restAnnouncement
import io.eddiegulay.tempo.ui.gym.session.restLabel
import io.eddiegulay.tempo.ui.gym.session.roundsOverflowLabel
import io.eddiegulay.tempo.ui.gym.session.skipDisabledDescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every word the live player puts on screen, pinned.
 *
 * The composables above these functions are deliberately thin, so this is where the page specs are
 * actually asserted: a label that drifts here is a label that drifts on a screen nobody can unit-test,
 * and the two rules most likely to be "fixed" by a later reader — `DECISIONS.md` §Q4's arabic
 * countdown and §Q10's bare-second rests — are pinned by name below.
 */
class PlayerCopyTest {

    private fun segment(
        ordinal: Int = 1,
        phase: Phase = Phase.WORK,
        plannedMs: Long = 30_000L,
        open: Boolean = false,
        reps: Int? = null,
        restKind: RestKind? = null,
    ) = Segment(
        ordinal = ordinal,
        phase = phase,
        startMs = 0L,
        plannedMs = plannedMs,
        open = open,
        prescribedReps = reps,
        restKind = restKind,
    )

    // ── numerals ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a fresh thirty second station reads 0-30 rather than 0-29`() {
        // Truncation would never show the number the routine prescribes, and would then sit on 0:00
        // for a whole second before the boundary.
        assertEquals("0:30", formatCountdown(30_000L))
        assertEquals("0:30", formatCountdown(29_999L))
        assertEquals("0:29", formatCountdown(29_000L))
    }

    @Test
    fun `the countdown floors at zero instead of running negative`() {
        // An open segment past its estimate reports a negative remaining; the ring holds empty and so
        // does the numeral. Nothing about overrun is a failure, and 「-0:07」 would read as one.
        assertEquals("0:00", formatCountdown(0L))
        assertEquals("0:00", formatCountdown(-7_400L))
    }

    @Test
    fun `the countdown keeps minutes past an hour rather than wrapping`() {
        assertEquals("12:03", formatCountdown(12L * 60_000L + 3_000L))
    }

    @Test
    fun `支度 counts in bare integers, not on a clock face`() {
        assertEquals("3", prepareNumeral(2_400L))
        assertEquals("5", prepareNumeral(5_000L))
        assertEquals("0", prepareNumeral(-1L))
    }

    @Test
    fun `a rest renders as the seconds it was set to`() {
        // `DECISIONS.md` §Q10: the user dialled 六十秒 on a wheel and must not read 一分 back.
        assertEquals("六十秒", chosenSecondsLabel(60_000L))
        assertEquals("十五秒", chosenSecondsLabel(15_000L))
        assertEquals("九十秒", chosenSecondsLabel(90_000L))
    }

    // ── the counter line ────────────────────────────────────────────────────────────────────────

    @Test
    fun `the counter names the round and the station`() {
        assertEquals(
            "三巡目 ・ 四種目中 三",
            counterLabel(round = 3, totalRounds = 5, station = 2, stationsPerRound = 4, lastRound = false),
        )
    }

    @Test
    fun `a single round routine drops the 巡 clause`() {
        // 七分間 is twelve stations in one lap; 「一巡目」 on it is chrome, not information.
        assertEquals(
            "十二種目中 一",
            counterLabel(round = 1, totalRounds = 1, station = 0, stationsPerRound = 12, lastRound = false),
        )
    }

    @Test
    fun `the final round's first effort reads 最後の巡`() {
        assertEquals(
            "最後の巡 ・ 四種目中 一",
            counterLabel(round = 5, totalRounds = 5, station = 0, stationsPerRound = 4, lastRound = true),
        )
    }

    @Test
    fun `a segment with neither clause draws no stray separator`() {
        assertNull(counterLabel(round = 1, totalRounds = 1, station = null, stationsPerRound = 0, lastRound = false))
    }

    @Test
    fun `rounds overflow into words past nine`() {
        assertEquals("三巡目 / 十二巡", roundsOverflowLabel(3, 12))
        assertEquals("三巡目、五巡中", cycleDotsLabel(3, 5))
    }

    @Test
    fun `the hairline announces whole percents`() {
        assertEquals("全体 四十パーセント", progressLabel(0.4f))
        assertEquals("全体 〇パーセント", progressLabel(0f))
        assertEquals("全体 百パーセント", progressLabel(1f))
    }

    // ── the next-up line ────────────────────────────────────────────────────────────────────────

    @Test
    fun `next up names the rest and the movement after it`() {
        val rest = segment(phase = Phase.REST, plannedMs = 15_000L, restKind = RestKind.STATION)
        assertEquals("次 ・ 休息 十五秒 → プランク", nextUpLabel(rest, "プランク"))
        assertEquals("次、休息 十五秒、そのあと プランク", nextUpDescription(rest, "プランク"))
    }

    @Test
    fun `next up with no rest between stations is just the movement`() {
        assertEquals("次 ・ プランク", nextUpLabel(segment(), "プランク"))
        assertEquals("次、プランク", nextUpDescription(segment(), "プランク"))
    }

    @Test
    fun `the last segment says 完了`() {
        assertEquals("次 ・ 完了", nextUpLabel(null, null))
        assertEquals("次、完了", nextUpDescription(null, null))
    }

    @Test
    fun `an unknown movement leaves the slot blank rather than claiming the session is over`() {
        // 「次 ・ 完了」 would be a lie — there is more session — and no other word is in any table.
        assertNull(nextUpLabel(segment(), null))
        assertNull(nextUpDescription(segment(), null))
    }

    // ── announcements ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `the countdown announces at thirty and ten and nowhere else`() {
        val minute = 60_000L
        assertNull(countdownAnnouncement(45_000L, minute))
        assertEquals("残り 三十秒", countdownAnnouncement(29_000L, minute))
        assertEquals("残り 十秒", countdownAnnouncement(9_000L, minute))
        // At zero the arriving phase announces itself; two utterances for one boundary is one too many.
        assertNull(countdownAnnouncement(0L, minute))
    }

    @Test
    fun `a twenty second interval never announces 三十秒`() {
        // タバタ is a seeded built-in: eight rounds of a twenty-second station. Branching on what is
        // *left* alone, its opening frame — 20_000ms remaining — fell into the >10s arm and told a
        // TalkBack user that thirty seconds remained, eight times a workout. §A WORK's rule is
        // "announces at 30s, 10s, and 0 only", and a threshold this segment never had is a false
        // statement rather than an early one.
        val tabata = 20_000L
        for (remaining in listOf(20_000L, 19_000L, 15_000L, 10_001L)) {
            assertNull("a $tabata ms interval has no 三十秒", countdownAnnouncement(remaining, tabata))
        }
        // It still gets its ten, which it really does pass through.
        assertEquals("残り 十秒", countdownAnnouncement(10_000L, tabata))
    }

    @Test
    fun `a segment exactly as long as a threshold does not announce it at its own start`() {
        // 「残り 三十秒」 on the first frame of a thirty-second station states the whole of what the
        // station is, before the user has moved. Strict, both ways: a thirty-second interval announces
        // only at ten, and a ten-second one announces nothing at all.
        assertNull(countdownAnnouncement(30_000L, 30_000L))
        assertEquals("残り 十秒", countdownAnnouncement(10_000L, 30_000L))
        assertNull(countdownAnnouncement(10_000L, 10_000L))
        assertNull(countdownAnnouncement(5_000L, 10_000L))
    }

    @Test
    fun `支度 announces once, with the countdown's length rather than its remainder`() {
        assertEquals("支度、五秒後に ジャンピングジャック", prepareAnnouncement(5_000L, "ジャンピングジャック"))
        assertEquals("支度", prepareAnnouncement(5_000L, null))
    }

    @Test
    fun `a rest announces itself with the form cue attached`() {
        assertEquals(
            "休息 十五秒、次は プランク、三十秒、肘は肩の真下に",
            restAnnouncement(RestKind.STATION, 15_000L, "プランク", "三十秒", "肘は肩の真下に"),
        )
    }

    @Test
    fun `a rest with no cue and nothing after it still announces its own length`() {
        assertEquals("巡の間 六十秒", restAnnouncement(RestKind.ROUND, 60_000L, null, null, null))
    }

    @Test
    fun `休止 announces what has accrued, once`() {
        assertEquals("休止中、六分十四秒 経過、八種目 済", pausedAnnouncement(374_000L, 8))
        assertEquals("残り 二十三秒、休止中", pausedNumeralDescription(23_000L))
    }

    // ── 運動・回数 ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the rep hero is kanji and 限界まで has no count`() {
        assertEquals("二十回", repHero(20))
        assertEquals("限界まで", repHero(null))
        assertEquals("腕立て伏せ、二十回", repHeroDescription("腕立て伏せ", 20))
        assertEquals("済、二十回として記録", repDoneDescription(20))
    }

    @Test
    fun `a 限界まで set gets the bare 済 rather than an invented sentence`() {
        assertEquals("済", repDoneDescription(null))
    }

    @Test
    fun `the pacer counts down while pacing and up once past the estimate`() {
        assertEquals("目安 0:38", pacerLabel(20, 37_500L, 0L, emomWindow = false))
        // Counting up truncates: seven and a half seconds over is seven seconds actually spent.
        assertEquals("＋0:07", pacerLabel(20, -7_500L, 7_500L, emomWindow = false))
    }

    @Test
    fun `an EMOM cell shows what is left of the minute instead of an estimate`() {
        assertEquals("残り 0:22", pacerLabel(20, 21_500L, 0L, emomWindow = true))
    }

    @Test
    fun `限界まで has no estimate line at all`() {
        assertNull(pacerLabel(null, 45_000L, 0L, emomWindow = false))
    }

    // ── 休息 ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `each kind of rest says what it is`() {
        assertEquals("休息", restLabel(RestKind.STATION))
        assertEquals("巡の間", restLabel(RestKind.ROUND))
        assertEquals("決められた休息", restLabel(RestKind.MANDATED))
        assertEquals("残り", restLabel(RestKind.EMOM_REMAINDER))
        assertEquals("休息", restLabel(null))
    }

    @Test
    fun `added time shows only once some has been added`() {
        assertNull(extendedSuffix(0L))
        assertNull(addedStateDescription(0L))
        assertEquals("＋0:20", extendedSuffix(20_000L))
        assertEquals("四十秒 追加済み", addedStateDescription(40_000L))
    }

    @Test
    fun `a mandated rest states why both of its controls are dead`() {
        assertEquals("とばす、決められた休息のため使えません", skipDisabledDescription(RestKind.MANDATED))
        assertEquals("二十秒 追加、決められた休息のため使えません", extendDisabledDescription(RestKind.MANDATED))
        // An EMOM remainder hides its controls instead, so there is nothing there to describe.
        assertNull(skipDisabledDescription(RestKind.EMOM_REMAINDER))
        assertNull(extendDisabledDescription(RestKind.EMOM_REMAINDER))
    }

    // ── prescriptions ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `a station prints what it asks for`() {
        assertEquals("三十秒", prescriptionLabel(segment(plannedMs = 30_000L)))
        assertEquals("二十回", prescriptionLabel(segment(reps = 20)))
        assertEquals("限界まで", prescriptionLabel(segment(open = true, plannedMs = 45_000L)))
        assertNull(prescriptionLabel(null))
    }

    @Test
    fun `a rep prescription wins over the box it sits in`() {
        // 七分間's rep stations are rep stations; the fixed box is the protocol's pacing.
        assertEquals("二十回", prescriptionLabel(segment(plannedMs = 30_000L, reps = 20)))
    }

    // ── 休止 and the quit sheet ─────────────────────────────────────────────────────────────────

    @Test
    fun `休止 reports active time and what is done`() {
        assertEquals("六分十四秒 経過", elapsedLine(374_000L))
        assertEquals("八種目 ・ 二巡 済", accruedLine(8, 2))
        assertEquals("八種目 済", accruedLine(8, 0))
    }

    @Test
    fun `nothing has accrued before the first station closes`() {
        assertNull(accruedLine(0, 0))
    }

    @Test
    fun `the quit sheet states the session it is about to end`() {
        assertEquals("六分十四秒 ・ 二十種目中 八", quitSummaryLine(374_000L, 8, 20))
    }

    @Test
    fun `with nothing recorded the sheet loses 記録する and asks only once`() {
        val options = quitOptions(0)
        assertEquals(false, options.canRecord)
        assertEquals("終える", options.discardLabel)
        assertEquals(false, options.confirmsDiscard)
        assertEquals(true, options.subtitleIsWarning)
    }

    @Test
    fun `once work exists the destructive row asks twice`() {
        val options = quitOptions(1)
        assertEquals(true, options.canRecord)
        assertEquals("記録せずに終える", options.discardLabel)
        assertEquals(true, options.confirmsDiscard)
        assertEquals(false, options.subtitleIsWarning)
    }
}
