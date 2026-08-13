package io.eddiegulay.tempo.gym.ui

import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.Measure
import io.eddiegulay.tempo.gym.Phase
import io.eddiegulay.tempo.gym.PrChip
import io.eddiegulay.tempo.gym.Rating
import io.eddiegulay.tempo.gym.SegmentResult
import io.eddiegulay.tempo.gym.SessionSummary
import io.eddiegulay.tempo.ui.ENSO_SWEEP_DEGREES
import io.eddiegulay.tempo.ui.gym.ENSO_CLOSED_DEGREES
import io.eddiegulay.tempo.ui.gym.RecordMode
import io.eddiegulay.tempo.ui.gym.RecordSummaryData
import io.eddiegulay.tempo.ui.gym.RecordTile
import io.eddiegulay.tempo.ui.gym.accoladeDelayMs
import io.eddiegulay.tempo.ui.gym.accoladeSemantics
import io.eddiegulay.tempo.ui.gym.breakdownRows
import io.eddiegulay.tempo.ui.gym.breakdownSemantics
import io.eddiegulay.tempo.ui.gym.failedOutChip
import io.eddiegulay.tempo.ui.gym.playsEnsoClosure
import io.eddiegulay.tempo.ui.gym.ratingGroupState
import io.eddiegulay.tempo.ui.gym.ratingOptionLabel
import io.eddiegulay.tempo.ui.gym.ratingPromptVisible
import io.eddiegulay.tempo.ui.gym.recordAccolades
import io.eddiegulay.tempo.ui.gym.recordHeaderChip
import io.eddiegulay.tempo.ui.gym.recordHeroLabel
import io.eddiegulay.tempo.ui.gym.recordHeroSemantics
import io.eddiegulay.tempo.ui.gym.recordSweepAngle
import io.eddiegulay.tempo.ui.gym.recordTiles
import io.eddiegulay.tempo.ui.gym.recordTilesSemantics
import io.eddiegulay.tempo.ui.gym.shouldSuppressAccolades
import io.eddiegulay.tempo.ui.gym.streakLine
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The eleven-row difference table, and rule 2, as assertions.
 *
 * `00-plan.md` §2 row 9 puts `GYM.SESSION.COMPLETE` and `GYM.RECORDS.SESSION_DETAIL` behind one
 * component, and `04-library-records.md` §4 lists the eleven ways they must still differ. Every row of
 * that table that is decided in code rather than in a page is pinned below, because the failure mode is
 * silent: a historical record that replays the ceremony, or shows today's streak, or keeps claiming a
 * crown it has since lost, looks completely fine in a screenshot.
 *
 * The other doctrine on trial is `00-plan.md` §4.1 rule 2 — **a partial session is a real session.** It
 * loses the accolades and keeps the numbers, identically in both modes, and the tests say so in both.
 */
class RecordSummaryTest {

    // ─── the hero and its chip ──────────────────────────────────────────────────────────────────

    @Test
    fun `the hero label is 活動時間, and 到達 only for a fail-out`() {
        // `03` §A COMPLETE's mock and its `FailedOut` state. The label changes because the number
        // means something different: a デス・バイ's clock is how far you got, not what you spent.
        assertEquals("活動時間", recordHeroLabel(failedOut = false))
        assertEquals("到達", recordHeroLabel(failedOut = true))
    }

    @Test
    fun `a fail-out chip states when the ladder ran out`() {
        assertEquals("十七分で 力尽きた", failedOutChip(17 * 60_000L))
    }

    @Test
    fun `a complete session carries no chip at all`() {
        assertNull(recordHeaderChip(data()))
    }

    @Test
    fun `a partial session carries 途中まで with the frozen denominator`() {
        // The denominator is `stations_planned`, frozen on the session row, not a count of the rows
        // that exist — the chip has to say the twenty that was on screen.
        assertEquals(
            "途中まで ・ 二十種目中 八",
            recordHeaderChip(
                data(summary = session(complete = false, stationsPlanned = 20, stationsCompleted = 8)),
            ),
        )
    }

    @Test
    fun `a fail-out chip wins over the partial chip`() {
        // It cannot arise against the shipped store — a fail-out is finished `complete = true` — but
        // the precedence is stated so that if one ever is written incomplete, the chip says *why* it
        // ended rather than merely that it did.
        assertEquals(
            "十七分で 力尽きた",
            recordHeaderChip(data(summary = session(complete = false, activeMs = 17 * 60_000L), failedOut = true)),
        )
    }

    // ─── the tiles ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the three tiles are 種目 巡 回, and the value carries its own unit`() {
        assertEquals(
            listOf(
                RecordTile("二十種目", "種目"),
                RecordTile("五巡", "巡"),
                RecordTile("三百二十回", "回"),
            ),
            recordTiles(session(stationsCompleted = 20, roundsCompleted = 5, totalReps = 320)),
        )
    }

    @Test
    fun `the 種目 tile counts what was done, not what was planned`() {
        // Otherwise a partial session's tile (二十種目) contradicts its own chip (二十種目中 八) on the
        // same screen, and the honest one is what you did.
        val tiles = recordTiles(session(complete = false, stationsPlanned = 20, stationsCompleted = 8))
        assertEquals("八種目", tiles[0].value)
    }

    @Test
    fun `a zero tile is a dash, never 〇`() {
        // COMPLETE edge case 4 states it for reps — zero there is an inapplicability, not a result —
        // and `sessionRowLines` already omits a zero 巡 for the same reason. A fixed three-column row
        // cannot omit a column without unaligning the labels, so the slot takes the app's empty value.
        val tiles = recordTiles(session(stationsCompleted = 0, roundsCompleted = 0, totalReps = 0))
        assertEquals(listOf("—", "—", "—"), tiles.map { it.value })
    }

    // ─── suppression, and the accolades ─────────────────────────────────────────────────────────

    @Test
    fun `a finished session of real length may be flattered`() {
        assertFalse(shouldSuppressAccolades(session()))
    }

    @Test
    fun `a partial session may not`() {
        assertTrue(shouldSuppressAccolades(session(complete = false)))
    }

    @Test
    fun `a session under thirty seconds may not`() {
        // COMPLETE edge case 6: a twelve-second session that beats a real one is noise.
        assertTrue(shouldSuppressAccolades(session(activeMs = 12_000)))
    }

    @Test
    fun `the live record shows the streak and the historical one never does`() {
        // §4 row 7. Showing *today's* streak on a March record is a category error, and showing March's
        // means walking the whole history for one line. The component drops it rather than trusting the
        // caller to pass null — a rule the caller can forget is not a rule.
        val d = data(streakDays = 4)
        assertEquals("四日 連続", recordAccolades(RecordMode.Live, d).streak)
        assertNull(recordAccolades(RecordMode.Historical, d).streak)
    }

    @Test
    fun `an unknown streak omits the line rather than drawing a dash`() {
        // COMPLETE edge case 7, and `04` §6's "never 〇日 連続".
        assertNull(streakLine(null))
        assertNull(streakLine(0))
        assertEquals("四日 連続", streakLine(4))
    }

    @Test
    fun `a first session says so, and is not compared`() {
        val accolades = recordAccolades(RecordMode.Live, data(previous = null))
        assertTrue(accolades.firstEver)
        assertNull(accolades.comparison)
    }

    @Test
    fun `a first session that was abandoned claims no milestone`() {
        // はじめての記録 over a session the user walked out of would announce something they did not
        // reach. Rule 2 keeps the numbers; it does not hand out the sentence.
        val accolades = recordAccolades(
            RecordMode.Live,
            data(summary = session(complete = false), previous = null),
        )
        assertFalse(accolades.firstEver)
    }

    @Test
    fun `a faster session is compared and the record chip is the current one`() {
        val accolades = recordAccolades(
            RecordMode.Live,
            data(summary = session(activeMs = 374_000, personalBest = true), previous = session(activeMs = 396_000)),
        )
        assertEquals("前回より 二十二秒 速い", accolades.comparison)
        assertEquals(PrChip.CURRENT, accolades.chip)
    }

    @Test
    fun `a record since beaten is demoted rather than dropped`() {
        // §4 row 6. 当時の自己最高 in `c.inkFaint`: it was true then and the screen still says so.
        val accolades = recordAccolades(
            RecordMode.Historical,
            data(summary = session(personalBest = true), isStillBest = false),
        )
        assertEquals(PrChip.FORMER, accolades.chip)
    }

    @Test
    fun `a partial session gets no chip and no comparison in either mode`() {
        // §4 row 12: the one row deliberately identical. A partial session is a real session, and it
        // reads the same wherever it appears — otherwise the app is grading the user twice.
        val d = data(
            summary = session(complete = false, personalBest = true, activeMs = 100_000),
            previous = session(activeMs = 396_000),
        )
        for (mode in RecordMode.entries) {
            val accolades = recordAccolades(mode, d)
            assertNull(accolades.chip)
            assertNull(accolades.comparison)
        }
    }

    @Test
    fun `a block with nothing in it is never inserted`() {
        // An accolade section that appears, animates and says nothing is worse than one that never
        // appears — and a `liveRegion` announcing an empty node is worse still.
        val accolades = recordAccolades(RecordMode.Live, data(summary = session(complete = false), previous = null))
        assertTrue(accolades.isEmpty)
        assertNull(accoladeSemantics(accolades))
    }

    // ─── the ring ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `only a live finished session closes the ring`() {
        // §4 row 2: ceremony you can replay is not ceremony.
        assertTrue(playsEnsoClosure(RecordMode.Live, session()))
        assertFalse(playsEnsoClosure(RecordMode.Historical, session()))
        assertFalse(playsEnsoClosure(RecordMode.Live, session(complete = false)))
    }

    @Test
    fun `a finished ring is a closed circle, not the ensō's own sweep`() {
        // 312° is what makes the mark an ensō; closing the gap is the whole ceremony (design §4).
        assertEquals(360f, ENSO_CLOSED_DEGREES, 0f)
        assertEquals(ENSO_CLOSED_DEGREES, recordSweepAngle(session()), 0f)
    }

    @Test
    fun `a partial ring holds at the fraction of the stations it reached`() {
        // Stations, not time: a session that ran long and stopped early has no meaningful fraction of a
        // clock, and this shares the 途中まで chip's frozen denominator so the two cannot disagree.
        assertEquals(
            ENSO_SWEEP_DEGREES * 0.4f,
            recordSweepAngle(session(complete = false, stationsPlanned = 20, stationsCompleted = 8)),
            0.001f,
        )
    }

    @Test
    fun `an unknown denominator draws no ink rather than a full ring`() {
        assertEquals(
            0f,
            recordSweepAngle(session(complete = false, stationsPlanned = 0, stationsCompleted = 0)),
            0f,
        )
    }

    // ─── the breakdown ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `the breakdown lists what you did and not the rests between`() {
        // Interleaving 休息 0:10 between every station doubles the list's length to say nothing, and
        // the rest is already inside the hero — `active_ms` includes it.
        val rows = breakdownRows(
            listOf(
                result(ordinal = 1, phase = Phase.WORK, exerciseId = "pushup", actualMs = 41_000),
                result(ordinal = 2, phase = Phase.REST, exerciseId = null),
                result(ordinal = 3, phase = Phase.REPS, exerciseId = "squat", actualReps = 18, prescribedReps = 20),
            ),
        ) { id -> if (id == "pushup") "腕立て伏せ" else "スクワット" }

        assertEquals(listOf("腕立て伏せ", "スクワット"), rows.map { it.name })
        assertEquals("0:41", rows[0].duration)
        assertEquals("十八回 / 二十回", rows[1].reps)
    }

    @Test
    fun `a station the catalogue no longer knows is named, not blanked`() {
        val rows = breakdownRows(listOf(result(exerciseId = "gone"))) { null }
        assertEquals("不明な種目", rows.single().name)
    }

    // ─── the rating ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the live record always asks, even after it has been answered`() {
        // §A COMPLETE's `Rated` state: the block does not collapse, because the user may change their
        // mind and a heading that vanishes on the first tap hides that the second is offered.
        assertTrue(ratingPromptVisible(RecordMode.Live, null))
        assertTrue(ratingPromptVisible(RecordMode.Live, Rating.JUST_RIGHT))
    }

    @Test
    fun `a historical record drops the question once it has an answer`() {
        // §4 rows 3 and 4: no どうでしたか over an answered record, but an old *un*answered one still
        // gets the prompt — later is better than never.
        assertFalse(ratingPromptVisible(RecordMode.Historical, Rating.HARD))
        assertTrue(ratingPromptVisible(RecordMode.Historical, null))
    }

    @Test
    fun `the rating group says whether it has been answered`() {
        assertEquals("未評価", ratingGroupState(null))
        assertEquals("ちょうど を選択", ratingGroupState(Rating.JUST_RIGHT))
    }

    @Test
    fun `each rating says what tapping it records`() {
        assertEquals("きついとして記録する", ratingOptionLabel(Rating.HARD))
    }

    @Test
    fun `the accolades wait six seconds, or four hundred milliseconds after an answer`() {
        // §A COMPLETE's "Ordering is load-bearing": the rating is asked before the streak and the PR
        // chip can bias it, without moving either of them out of design §4's reading order.
        assertEquals(6_000L, accoladeDelayMs(rated = false))
        assertEquals(400L, accoladeDelayMs(rated = true))
    }

    // ─── what TalkBack hears ────────────────────────────────────────────────────────────────────

    @Test
    fun `the header is one node and the chip is inside it`() {
        // §4's accessibility note: the 途中まで chip is announced as part of the header, not stranded
        // between the ring and the tiles where nothing explains it.
        assertEquals(
            "七分間、活動時間 六分十四秒、途中まで ・ 二十種目中 八",
            recordHeroSemantics(
                data(summary = session(complete = false, stationsPlanned = 20, stationsCompleted = 8)),
            ),
        )
    }

    @Test
    fun `the tiles are one node and do not say their units twice`() {
        // Three separate nodes read as three orphan numbers. The labels are left out because each
        // value already carries its unit — 「種目 二十種目」 says it twice.
        assertEquals(
            "二十種目、五巡、三百二十回",
            recordTilesSemantics(recordTiles(session(stationsCompleted = 20, roundsCompleted = 5, totalReps = 320))),
        )
    }

    @Test
    fun `the comparison and the chip are announced together`() {
        val accolades = recordAccolades(
            RecordMode.Live,
            data(
                summary = session(activeMs = 374_000, personalBest = true),
                previous = session(activeMs = 396_000),
                streakDays = 4,
            ),
        )
        assertEquals("前回より 二十二秒 速い、四日 連続、自己最高", accoladeSemantics(accolades))
    }

    @Test
    fun `a first session announces the words it renders`() {
        assertEquals("はじめての記録", accoladeSemantics(recordAccolades(RecordMode.Live, data(previous = null))))
    }

    @Test
    fun `a skipped station reads as skipped and nothing else`() {
        val rows = breakdownRows(listOf(result(exerciseId = "burpee", skipped = true))) { "バーピー" }
        assertEquals("バーピー、とばした", breakdownSemantics(rows.single()))
    }
}

// ─── fixtures ───────────────────────────────────────────────────────────────────────────────────

private fun data(
    summary: SessionSummary = session(),
    results: List<SegmentResult> = emptyList(),
    previous: SessionSummary? = session(activeMs = 396_000),
    isStillBest: Boolean = true,
    streakDays: Int? = null,
    failedOut: Boolean = false,
) = RecordSummaryData(
    summary = summary,
    results = results,
    previous = previous,
    isStillBest = isStillBest,
    streakDays = streakDays,
    failedOut = failedOut,
)

private fun session(
    activeMs: Long = 374_000L,
    complete: Boolean = true,
    engine: Engine = Engine.INTERVAL_CIRCUIT,
    personalBest: Boolean = false,
    roundsCompleted: Int = 5,
    stationsCompleted: Int = 20,
    stationsPlanned: Int = 20,
    totalReps: Int = 320,
) = SessionSummary(
    sessionId = 1L,
    routineId = "seven",
    routineVersionId = 1L,
    routineName = "七分間",
    engine = engine,
    tier = null,
    startedAt = 0L,
    finishedAt = activeMs,
    localDate = LocalDate.of(2026, 6, 17),
    activeMs = activeMs,
    wallMs = activeMs,
    complete = complete,
    reachedTimeCap = false,
    roundsPlanned = 5,
    roundsCompleted = roundsCompleted,
    stationsPlanned = stationsPlanned,
    stationsCompleted = stationsCompleted,
    totalReps = totalReps,
    volumeUnits = 0.0,
    rating = null,
    personalBest = personalBest,
)

private fun result(
    ordinal: Int = 1,
    phase: Phase = Phase.WORK,
    exerciseId: String? = "pushup",
    actualReps: Int? = null,
    prescribedReps: Int? = null,
    actualMs: Long = 30_000L,
    skipped: Boolean = false,
) = SegmentResult(
    ordinal = ordinal,
    phase = phase,
    roundIndex = 1,
    stationOrder = ordinal,
    exerciseId = exerciseId,
    measure = Measure.DURATION,
    prescribedReps = prescribedReps,
    prescribedSeconds = 30,
    actualReps = actualReps,
    actualMs = actualMs,
    addedMs = 0L,
    skipped = skipped,
    difficultyCoefficient = 1.0,
    volumeUnits = 0.0,
    closedAtWallMs = 0L,
    closedAtElapsedMs = 0L,
)
