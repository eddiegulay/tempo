package io.eddiegulay.tempo.gym.session.ui

import io.eddiegulay.tempo.gym.Phase
import io.eddiegulay.tempo.gym.session.RestKind
import io.eddiegulay.tempo.gym.session.Segment
import io.eddiegulay.tempo.i18n.Strings
import io.eddiegulay.tempo.i18n.StringsEn
import io.eddiegulay.tempo.i18n.StringsJa
import io.eddiegulay.tempo.ui.gym.session.accruedLine
import io.eddiegulay.tempo.ui.gym.session.addedStateDescription
import io.eddiegulay.tempo.ui.gym.session.chosenSecondsLabel
import io.eddiegulay.tempo.ui.gym.session.counterLabel
import io.eddiegulay.tempo.ui.gym.session.countdownAnnouncement
import io.eddiegulay.tempo.ui.gym.session.cycleDotsLabel
import io.eddiegulay.tempo.ui.gym.session.elapsedLine
import io.eddiegulay.tempo.ui.gym.session.extendDisabledDescription
import io.eddiegulay.tempo.ui.gym.session.extendRestDescription
import io.eddiegulay.tempo.ui.gym.session.extendRestLabel
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
import io.eddiegulay.tempo.ui.gym.session.resumeLongDescription
import io.eddiegulay.tempo.ui.gym.session.resumePrepareNote
import io.eddiegulay.tempo.ui.gym.session.roundsOverflowLabel
import io.eddiegulay.tempo.ui.gym.session.skipDisabledDescription
import io.eddiegulay.tempo.ui.gym.session.skipRestLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every word the live player puts on screen, pinned — in both languages.
 *
 * The composables above these functions are deliberately thin, so this is where the page specs are
 * actually asserted: a label that drifts here is a label that drifts on a screen nobody can unit-test.
 *
 * ## Three kinds of test, and the difference matters
 *
 * 1. **Japanese, asserted verbatim.** These are the ones that were here before the i18n migration and
 *    they say the same thing they always did — the app ships these characters today and the move to a
 *    string table had to be behaviour-neutral. They now source the words from [StringsJa] rather than
 *    from a literal in `PlayerCopy.kt`, which is the point: they test *behaviour* (which message this
 *    dispatch selects, and how it is assembled) rather than *transcription*.
 * 2. **English, asserted verbatim** where the shape is the whole claim — a word order that had to
 *    invert, a rule §L7 deletes, a plural.
 * 3. **Language-independent properties**, run over both tables. The nulls are here: nine functions
 *    return `String?` because §A gives them no words, and *neither* table may fill those holes. A
 *    per-language `assertNull` would have caught one table and not the other.
 *
 * ## §Q4 and §Q10, and what happens to them
 *
 * The two numeral rules are pinned by name below, in Japanese, exactly as before. English deletes both
 * (§L7) and `[the paused numeral is spoken in kanji and drawn in arabic]` is where that is stated as a
 * test rather than only as a comment.
 */
class PlayerCopyTest {

    private val ja = StringsJa
    private val en = StringsEn

    /** Both tables, for the claims that are about shape rather than about words. */
    private val both = listOf<Strings>(StringsJa, StringsEn)

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
        for (s in both) {
            assertEquals("0:30", formatCountdown(s, 30_000L))
            assertEquals("0:30", formatCountdown(s, 29_999L))
            assertEquals("0:29", formatCountdown(s, 29_000L))
        }
    }

    @Test
    fun `the countdown floors at zero instead of running negative`() {
        // An open segment past its estimate reports a negative remaining; the ring holds empty and so
        // does the numeral. Nothing about overrun is a failure, and 「-0:07」 would read as one.
        for (s in both) {
            assertEquals("0:00", formatCountdown(s, 0L))
            assertEquals("0:00", formatCountdown(s, -7_400L))
        }
    }

    @Test
    fun `the countdown keeps minutes past an hour rather than wrapping`() {
        for (s in both) assertEquals("12:03", formatCountdown(s, 12L * 60_000L + 3_000L))
    }

    @Test
    fun `支度 counts in bare integers, not on a clock face`() {
        // Takes no table and must not: it is a *ticking* value, so §Q4 keeps it arabic in Japanese as
        // well, and the kanji formatter would be wrong in both languages.
        assertEquals("3", prepareNumeral(2_400L))
        assertEquals("5", prepareNumeral(5_000L))
        assertEquals("0", prepareNumeral(-1L))
    }

    @Test
    fun `a rest renders as the seconds it was set to`() {
        // `DECISIONS.md` §Q10: the user dialled 六十秒 on a wheel and must not read 一分 back.
        assertEquals("六十秒", chosenSecondsLabel(ja, 60_000L))
        assertEquals("十五秒", chosenSecondsLabel(ja, 15_000L))
        assertEquals("九十秒", chosenSecondsLabel(ja, 90_000L))
    }

    @Test
    fun `English deletes §Q10 rather than translating it`() {
        // §L7. The chosen-versus-measured split was carried by orthography — bare kanji seconds against
        // a spelled-out duration — and English has one. Sixty seconds is a minute here, which is
        // exactly the thing 六十秒 exists to prevent in Japanese, and is the documented cost.
        assertEquals("15s", chosenSecondsLabel(en, 15_000L))
        assertEquals("1m", chosenSecondsLabel(en, 60_000L))
        assertEquals("1m 30s", chosenSecondsLabel(en, 90_000L))
    }

    // ── the counter line ────────────────────────────────────────────────────────────────────────

    @Test
    fun `the counter names the round and the station`() {
        assertEquals(
            "三巡目 ・ 四種目中 三",
            counterLabel(ja, round = 3, totalRounds = 5, station = 2, stationsPerRound = 4, lastRound = false),
        )
    }

    @Test
    fun `English counts the station out of the total rather than into it`() {
        // 「四種目中 三」 puts the total first; English cannot, and a concatenation at the call site
        // could only ever have been right in one of the two languages.
        assertEquals(
            "Round 3 · 3 of 4 stations",
            counterLabel(en, round = 3, totalRounds = 5, station = 2, stationsPerRound = 4, lastRound = false),
        )
    }

    @Test
    fun `a single round routine drops the 巡 clause`() {
        // 七分間 is twelve stations in one lap; 「一巡目」 on it is chrome, not information.
        assertEquals(
            "十二種目中 一",
            counterLabel(ja, round = 1, totalRounds = 1, station = 0, stationsPerRound = 12, lastRound = false),
        )
        assertEquals(
            "1 of 12 stations",
            counterLabel(en, round = 1, totalRounds = 1, station = 0, stationsPerRound = 12, lastRound = false),
        )
    }

    @Test
    fun `the final round's first effort reads 最後の巡`() {
        assertEquals(
            "最後の巡 ・ 四種目中 一",
            counterLabel(ja, round = 5, totalRounds = 5, station = 0, stationsPerRound = 4, lastRound = true),
        )
        assertEquals(
            "Last round · 1 of 4 stations",
            counterLabel(en, round = 5, totalRounds = 5, station = 0, stationsPerRound = 4, lastRound = true),
        )
    }

    @Test
    fun `a segment with neither clause draws no stray separator`() {
        // One of the nine documented holes, and neither table may fill it: an empty string here would
        // be a lone ・ above the ensō.
        for (s in both) {
            assertNull(counterLabel(s, round = 1, totalRounds = 1, station = null, stationsPerRound = 0, lastRound = false))
        }
    }

    @Test
    fun `rounds overflow into words past nine`() {
        assertEquals("三巡目 / 十二巡", roundsOverflowLabel(ja, 3, 12))
        assertEquals("三巡目、五巡中", cycleDotsLabel(ja, 3, 5))
        assertEquals("Round 3 / 12", roundsOverflowLabel(en, 3, 12))
        assertEquals("Round 3 of 5", cycleDotsLabel(en, 3, 5))
    }

    @Test
    fun `the hairline announces whole percents`() {
        assertEquals("全体 四十パーセント", progressLabel(ja, 0.4f))
        assertEquals("全体 〇パーセント", progressLabel(ja, 0f))
        assertEquals("全体 百パーセント", progressLabel(ja, 1f))
        // パーセント is a word in Japanese and a sign in English; both are the same rounding.
        assertEquals("40% overall", progressLabel(en, 0.4f))
        assertEquals("100% overall", progressLabel(en, 1f))
    }

    @Test
    fun `a NaN fraction announces zero rather than a number nobody can act on`() {
        for (s in both) assertNotNull(progressLabel(s, Float.NaN))
        assertEquals(progressLabel(ja, 0f), progressLabel(ja, Float.NaN))
    }

    // ── the next-up line ────────────────────────────────────────────────────────────────────────

    @Test
    fun `next up names the rest and the movement after it`() {
        val rest = segment(phase = Phase.REST, plannedMs = 15_000L, restKind = RestKind.STATION)
        assertEquals("次 ・ 休息 十五秒 → プランク", nextUpLabel(ja, rest, "プランク"))
        assertEquals("次、休息 十五秒、そのあと プランク", nextUpDescription(ja, rest, "プランク"))
        assertEquals("Next · Rest 15s → Plank", nextUpLabel(en, rest, "Plank"))
        assertEquals("Next, rest 15s, then Plank", nextUpDescription(en, rest, "Plank"))
    }

    @Test
    fun `next up with no rest between stations is just the movement`() {
        assertEquals("次 ・ プランク", nextUpLabel(ja, segment(), "プランク"))
        assertEquals("次、プランク", nextUpDescription(ja, segment(), "プランク"))
        assertEquals("Next · Plank", nextUpLabel(en, segment(), "Plank"))
        assertEquals("Next, Plank", nextUpDescription(en, segment(), "Plank"))
    }

    @Test
    fun `the last segment says 完了`() {
        assertEquals("次 ・ 完了", nextUpLabel(ja, null, null))
        assertEquals("次、完了", nextUpDescription(ja, null, null))
        assertEquals("Next · Done", nextUpLabel(en, null, null))
        assertEquals("Next, done", nextUpDescription(en, null, null))
    }

    @Test
    fun `the drawn line and the spoken line are never the same string`() {
        // 「→」 reads as nothing, so the spoken form spells そのあと; ` ・ ` is a visible joiner and `、`
        // a heard one. Two phrasings for two senses, in both languages — a merge would be silent.
        val rest = segment(phase = Phase.REST, plannedMs = 15_000L, restKind = RestKind.STATION)
        for (s in both) {
            assertTrue(nextUpLabel(s, rest, "X") != nextUpDescription(s, rest, "X"))
            assertTrue(nextUpLabel(s, null, null) != nextUpDescription(s, null, null))
        }
    }

    @Test
    fun `an unknown movement leaves the slot blank rather than claiming the session is over`() {
        // 「次 ・ 完了」 would be a lie — there is more session — and no other word is in any table.
        for (s in both) {
            assertNull(nextUpLabel(s, segment(), null))
            assertNull(nextUpDescription(s, segment(), null))
        }
    }

    // ── announcements ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `the countdown announces at thirty and ten and nowhere else`() {
        val minute = 60_000L
        assertNull(countdownAnnouncement(ja, 45_000L, minute))
        assertEquals("残り 三十秒", countdownAnnouncement(ja, 29_000L, minute))
        assertEquals("残り 十秒", countdownAnnouncement(ja, 9_000L, minute))
        // At zero the arriving phase announces itself; two utterances for one boundary is one too many.
        assertNull(countdownAnnouncement(ja, 0L, minute))

        assertEquals("30s left", countdownAnnouncement(en, 29_000L, minute))
        assertEquals("10s left", countdownAnnouncement(en, 9_000L, minute))
    }

    @Test
    fun `a twenty second interval never announces 三十秒`() {
        // タバタ is a seeded built-in: eight rounds of a twenty-second station. Branching on what is
        // *left* alone, its opening frame — 20_000ms remaining — fell into the >10s arm and told a
        // TalkBack user that thirty seconds remained, eight times a workout. §A WORK's rule is
        // "announces at 30s, 10s, and 0 only", and a threshold this segment never had is a false
        // statement rather than an early one.
        val tabata = 20_000L
        for (s in both) {
            for (remaining in listOf(20_000L, 19_000L, 15_000L, 10_001L)) {
                assertNull("a $tabata ms interval has no 三十秒", countdownAnnouncement(s, remaining, tabata))
            }
            // It still gets its ten, which it really does pass through.
            assertNotNull(countdownAnnouncement(s, 10_000L, tabata))
        }
        assertEquals("残り 十秒", countdownAnnouncement(ja, 10_000L, tabata))
    }

    @Test
    fun `a segment exactly as long as a threshold does not announce it at its own start`() {
        // 「残り 三十秒」 on the first frame of a thirty-second station states the whole of what the
        // station is, before the user has moved. Strict, both ways: a thirty-second interval announces
        // only at ten, and a ten-second one announces nothing at all.
        for (s in both) {
            assertNull(countdownAnnouncement(s, 30_000L, 30_000L))
            assertNotNull(countdownAnnouncement(s, 10_000L, 30_000L))
            assertNull(countdownAnnouncement(s, 10_000L, 10_000L))
            assertNull(countdownAnnouncement(s, 5_000L, 10_000L))
        }
        assertEquals("残り 十秒", countdownAnnouncement(ja, 10_000L, 30_000L))
    }

    @Test
    fun `支度 announces once, with the countdown's length rather than its remainder`() {
        assertEquals("支度、五秒後に ジャンピングジャック", prepareAnnouncement(ja, 5_000L, "ジャンピングジャック"))
        assertEquals("支度", prepareAnnouncement(ja, 5_000L, null))
    }

    @Test
    fun `English puts the movement before the countdown, because 後に cannot lead`() {
        // 「支度、五秒後に プランク」 binds the postposition to the number and trails the name. Reversed.
        assertEquals("Get ready, Jumping jacks in 5s", prepareAnnouncement(en, 5_000L, "Jumping jacks"))
        // And with no movement known it is the ring's own word — one key, drawn and spoken.
        assertEquals("Get ready", prepareAnnouncement(en, 5_000L, null))
    }

    @Test
    fun `支度 is the same noun on the player, in 設定 and on the notification`() {
        // Three surfaces name one segment. The settings row is "Get-ready time" and the service
        // notification is "Get ready"; a player that called it a third thing would be the page that
        // disagreed. Hyphens normalised because the settings row uses the compound adjective form.
        fun norm(v: String) = v.lowercase().replace("-", " ")
        assertTrue(
            "the player and 設定 must name 支度 the same way: ${en.gymSettings.rowPrepareSeconds}",
            norm(en.gymSession.prepareTitle) in norm(en.gymSettings.rowPrepareSeconds),
        )
        assertEquals(en.gymCue.phasePrepare, en.gymSession.prepareTitle)
        assertEquals("支度", ja.gymSession.prepareTitle)
        assertEquals(ja.gymCue.phasePrepare, ja.gymSession.prepareTitle)
    }

    @Test
    fun `a rest announces itself with the form cue attached`() {
        assertEquals(
            "休息 十五秒、次は プランク、三十秒、肘は肩の真下に",
            restAnnouncement(ja, RestKind.STATION, 15_000L, "プランク", "三十秒", "肘は肩の真下に"),
        )
        assertEquals(
            "Rest 15s, next: Plank, 30s, Elbows under the shoulders",
            restAnnouncement(en, RestKind.STATION, 15_000L, "Plank", "30s", "Elbows under the shoulders"),
        )
    }

    @Test
    fun `a rest with no cue and nothing after it still announces its own length`() {
        assertEquals("巡の間 六十秒", restAnnouncement(ja, RestKind.ROUND, 60_000L, null, null, null))
        assertEquals("Round rest 1m", restAnnouncement(en, RestKind.ROUND, 60_000L, null, null, null))
    }

    @Test
    fun `a prescription with no movement to attach it to is dropped, not orphaned`() {
        // The prescription is a clause of 「次は プランク」 and means nothing on its own.
        for (s in both) {
            val announced = restAnnouncement(s, RestKind.STATION, 15_000L, null, "30s", null)
            assertTrue("an orphaned prescription leaked: $announced", !announced.contains("30s"))
        }
    }

    @Test
    fun `休止 announces what has accrued, once`() {
        assertEquals("休止中、六分十四秒 経過、八種目 済", pausedAnnouncement(ja, 374_000L, 8))
        assertEquals("Paused, 6m 14s elapsed, 8 stations done", pausedAnnouncement(en, 374_000L, 8))
    }

    @Test
    fun `the paused numeral is spoken in kanji and drawn in arabic, and English has only one`() {
        // §Q4's sharpest instance and §L7's clearest casualty. Japanese keeps both numeral systems on
        // one value; English speaks what it draws, and this is the assertion that says so out loud
        // rather than a comment claiming it.
        assertEquals("0:23", formatCountdown(ja, 23_000L))
        assertEquals("残り 二十三秒、休止中", pausedNumeralDescription(ja, 23_000L))
        assertTrue(!pausedNumeralDescription(ja, 23_000L).contains("0:23"))

        assertEquals("0:23", formatCountdown(en, 23_000L))
        assertEquals("23s left, paused", pausedNumeralDescription(en, 23_000L))
    }

    // ── 運動・回数 ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the rep hero is kanji and 限界まで has no count`() {
        assertEquals("二十回", repHero(ja, 20))
        assertEquals("限界まで", repHero(ja, null))
        assertEquals("腕立て伏せ、二十回", repHeroDescription(ja, "腕立て伏せ", 20))
        assertEquals("済、二十回として記録", repDoneDescription(ja, 20))
    }

    @Test
    fun `English pluralises the rep hero and reuses the builder's word for an open set`() {
        assertEquals("1 rep", repHero(en, 1))
        assertEquals("20 reps", repHero(en, 20))
        // 限界まで belongs to `Measure.MAX_EFFORT`, not to the player: this line shows the prescription
        // the builder set, and a second key would be two words for one fact.
        assertEquals(en.gymShared.measureMaxEffort, repHero(en, null))
        assertEquals(ja.gymShared.measureMaxEffort, repHero(ja, null))
        assertEquals("Push-up, 20 reps", repHeroDescription(en, "Push-up", 20))
        assertEquals("Done, recorded as 20 reps", repDoneDescription(en, 20))
    }

    @Test
    fun `a 限界まで set gets the bare 済 rather than an invented sentence`() {
        // 「限界までとして記録」 is in no table and would be a sentence this file made up about the one
        // prescription with no number. Both languages take the bare word — and it is the same word the
        // button carries, from one key.
        assertEquals("済", repDoneDescription(ja, null))
        assertEquals("Done", repDoneDescription(en, null))
        for (s in both) assertEquals(s.gymSession.repsDone, repDoneDescription(s, null))
    }

    @Test
    fun `the pacer counts down while pacing and up once past the estimate`() {
        assertEquals("目安 0:38", pacerLabel(ja, 20, 37_500L, 0L, emomWindow = false))
        // Counting up truncates: seven and a half seconds over is seven seconds actually spent.
        assertEquals("＋0:07", pacerLabel(ja, 20, -7_500L, 7_500L, emomWindow = false))
        assertEquals("Est. 0:38", pacerLabel(en, 20, 37_500L, 0L, emomWindow = false))
        assertEquals("+0:07", pacerLabel(en, 20, -7_500L, 7_500L, emomWindow = false))
    }

    @Test
    fun `an EMOM cell shows what is left of the minute instead of an estimate`() {
        assertEquals("残り 0:22", pacerLabel(ja, 20, 21_500L, 0L, emomWindow = true))
        assertEquals("0:22 left", pacerLabel(en, 20, 21_500L, 0L, emomWindow = true))
    }

    @Test
    fun `限界まで has no estimate line at all`() {
        for (s in both) assertNull(pacerLabel(s, null, 45_000L, 0L, emomWindow = false))
    }

    @Test
    fun `an EMOM window overrides 限界まで, because the boundary is real`() {
        for (s in both) assertNotNull(pacerLabel(s, null, 21_500L, 0L, emomWindow = true))
    }

    // ── 休息 ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `each kind of rest says what it is`() {
        assertEquals("休息", restLabel(ja, RestKind.STATION))
        assertEquals("巡の間", restLabel(ja, RestKind.ROUND))
        assertEquals("決められた休息", restLabel(ja, RestKind.MANDATED))
        assertEquals("残り", restLabel(ja, RestKind.EMOM_REMAINDER))
        assertEquals("休息", restLabel(ja, null))
    }

    @Test
    fun `the four ring words stay four different words`() {
        // Each is doing work — 巡の間 replaces the hidden dots, 決められた休息 is why the controls beside
        // it are dead, 残り says the grid is anchored — so collapsing any two in translation would
        // delete a distinction the page relies on. STATION and null are the same word by design.
        for (s in both) {
            val words = listOf(RestKind.STATION, RestKind.ROUND, RestKind.MANDATED, RestKind.EMOM_REMAINDER)
                .map { restLabel(s, it) }
            assertEquals("the ring's four words must stay four: $words", 4, words.toSet().size)
            assertEquals(restLabel(s, RestKind.STATION), restLabel(s, null))
        }
    }

    @Test
    fun `the inline controls carry their amount and their action`() {
        assertEquals("＋二十秒", extendRestLabel(ja))
        assertEquals("とばす ▷", skipRestLabel(ja))
        assertEquals("二十秒 追加", extendRestDescription(ja))
        assertEquals("+20s", extendRestLabel(en))
        assertEquals("Skip ▷", skipRestLabel(en))
        assertEquals("Add 20s", extendRestDescription(en))
    }

    @Test
    fun `added time shows only once some has been added`() {
        for (s in both) {
            assertNull(extendedSuffix(s, 0L))
            assertNull(addedStateDescription(s, 0L))
        }
        assertEquals("＋0:20", extendedSuffix(ja, 20_000L))
        assertEquals("四十秒 追加済み", addedStateDescription(ja, 40_000L))
        assertEquals("+0:20", extendedSuffix(en, 20_000L))
        assertEquals("40s added", addedStateDescription(en, 40_000L))
    }

    @Test
    fun `a mandated rest states why both of its controls are dead`() {
        assertEquals("とばす、決められた休息のため使えません", skipDisabledDescription(ja, RestKind.MANDATED))
        assertEquals("二十秒 追加、決められた休息のため使えません", extendDisabledDescription(ja, RestKind.MANDATED))
        assertEquals("Skip — not available on a fixed rest", skipDisabledDescription(en, RestKind.MANDATED))
        assertEquals("Add 20s — not available on a fixed rest", extendDisabledDescription(en, RestKind.MANDATED))
    }

    @Test
    fun `both refusals give the same reason and name their own control`() {
        // The spec writes the reason once, against とばす, and disables both controls in the same
        // breath. One clause, two control names — a second, differently-worded reason for the
        // neighbouring button would be the actual divergence.
        for (s in both) {
            val skip = skipDisabledDescription(s, RestKind.MANDATED)!!
            val extend = extendDisabledDescription(s, RestKind.MANDATED)!!
            assertTrue(skip.startsWith(s.gymSession.controlsForward))
            assertTrue(extend.startsWith(extendRestDescription(s)))
            assertTrue("the two must not invent separate reasons", skip != extend)
        }
    }

    @Test
    fun `an EMOM remainder hides its controls, so there is nothing there to describe`() {
        for (s in both) {
            assertNull(skipDisabledDescription(s, RestKind.EMOM_REMAINDER))
            assertNull(extendDisabledDescription(s, RestKind.EMOM_REMAINDER))
            assertNull(skipDisabledDescription(s, RestKind.STATION))
            assertNull(extendDisabledDescription(s, null))
        }
    }

    // ── prescriptions ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `a station prints what it asks for`() {
        assertEquals("三十秒", prescriptionLabel(ja, segment(plannedMs = 30_000L)))
        assertEquals("二十回", prescriptionLabel(ja, segment(reps = 20)))
        assertEquals("限界まで", prescriptionLabel(ja, segment(open = true, plannedMs = 45_000L)))
        assertEquals("30s", prescriptionLabel(en, segment(plannedMs = 30_000L)))
        assertEquals("20 reps", prescriptionLabel(en, segment(reps = 20)))
        assertEquals("All out", prescriptionLabel(en, segment(open = true, plannedMs = 45_000L)))
        for (s in both) assertNull(prescriptionLabel(s, null))
    }

    @Test
    fun `a rep prescription wins over the box it sits in`() {
        // 七分間's rep stations are rep stations; the fixed box is the protocol's pacing.
        assertEquals("二十回", prescriptionLabel(ja, segment(plannedMs = 30_000L, reps = 20)))
        assertEquals("20 reps", prescriptionLabel(en, segment(plannedMs = 30_000L, reps = 20)))
    }

    @Test
    fun `the hero and the prescription agree about what a station asks for`() {
        // They are two slots showing one fact, on two pages, and 限界まで is the case that used to be
        // two literals a keystroke apart.
        for (s in both) {
            assertEquals(repHero(s, 20), prescriptionLabel(s, segment(reps = 20)))
            assertEquals(repHero(s, null), prescriptionLabel(s, segment(open = true)))
        }
    }

    // ── 休止 and the quit sheet ─────────────────────────────────────────────────────────────────

    @Test
    fun `休止 reports active time and what is done`() {
        assertEquals("六分十四秒 経過", elapsedLine(ja, 374_000L))
        assertEquals("八種目 ・ 二巡 済", accruedLine(ja, 8, 2))
        assertEquals("八種目 済", accruedLine(ja, 8, 0))
        assertEquals("6m 14s elapsed", elapsedLine(en, 374_000L))
        assertEquals("8 stations · 2 rounds done", accruedLine(en, 8, 2))
        assertEquals("8 stations done", accruedLine(en, 8, 0))
    }

    @Test
    fun `nothing has accrued before the first station closes`() {
        for (s in both) assertNull(accruedLine(s, 0, 0))
    }

    @Test
    fun `the resume button says where the clock restarts`() {
        assertEquals("三秒の支度から", resumePrepareNote(ja))
        assertEquals("続ける、三秒の支度から", resumeLongDescription(ja))
        assertEquals("After a 3s get-ready", resumePrepareNote(en))
        assertEquals("Resume, after a 3s get-ready", resumeLongDescription(en))
        // The two lines as one node: it must open with the button's own word.
        for (s in both) assertTrue(resumeLongDescription(s).startsWith(s.gymSession.pausedResume))
    }

    @Test
    fun `the quit sheet states the session it is about to end`() {
        assertEquals("六分十四秒 ・ 二十種目中 八", quitSummaryLine(ja, 374_000L, 8, 20))
        assertEquals("6m 14s · 8 of 20 stations", quitSummaryLine(en, 374_000L, 8, 20))
    }

    @Test
    fun `with nothing recorded the sheet loses 記録する and asks only once`() {
        val options = quitOptions(ja, 0)
        assertEquals(false, options.canRecord)
        assertEquals("終える", options.discardLabel)
        assertEquals(false, options.confirmsDiscard)
        assertEquals(true, options.subtitleIsWarning)
        assertEquals("End", quitOptions(en, 0).discardLabel)
    }

    @Test
    fun `once work exists the destructive row asks twice`() {
        val options = quitOptions(ja, 1)
        assertEquals(true, options.canRecord)
        assertEquals("記録せずに終える", options.discardLabel)
        assertEquals(true, options.confirmsDiscard)
        assertEquals(false, options.subtitleIsWarning)
        assertEquals("End without recording", quitOptions(en, 1).discardLabel)
    }

    @Test
    fun `the destructive row softens rather than reusing the same words at zero`() {
        // §A QUIT_SHEET: at zero results nothing is being destroyed, so the row must not say it is.
        for (s in both) {
            assertTrue(quitOptions(s, 0).discardLabel != quitOptions(s, 1).discardLabel)
        }
    }

    @Test
    fun `つづける is never the word that means abandon, and never 続ける either`() {
        // `QuitSheet.kt`'s KDoc forbids matching the calendar composer's やめる, and the resume button's
        // 続ける is a different act on a different screen. Three words, three keys, in both languages.
        for (s in both) {
            assertTrue(s.gymSession.quitContinue != s.gymSession.pausedResume)
            assertTrue(s.gymSession.quitContinue != s.gymSession.quitDiscard)
            assertTrue(s.gymSession.quitContinue != s.gymSession.quitDiscardNothing)
        }
        assertEquals("つづける", ja.gymSession.quitContinue)
        assertEquals("続ける", ja.gymSession.pausedResume)
    }
}
