package io.eddiegulay.tempo.gym

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One case per engine, all seven, because the interesting half of this table is the **absences** and
 * an absence has no field to test against.
 *
 * FOR_TIME's rounds column is populated — マーフ is one round — and the row still must not appear;
 * AMRAP's 巡数 slot is occupied by a phrase rather than a number; EMOM's between-station rest is not
 * zero, it is *nonexistent*. Each of those is one `if` away from being quietly re-decided by someone
 * simplifying the function, and each produces a page of rows that are not wrong so much as not about
 * anything.
 */
class EngineRowsTest {

    @Test
    fun `a circuit states both rests and its round count`() {
        assertEquals(
            listOf(
                EngineRow("種目の間の休息", "十秒"),
                EngineRow("巡の間の休息", "六十秒"),
                EngineRow("巡数", "三巡"),
            ),
            engineRows(Engine.INTERVAL_CIRCUIT, rounds = 3, timeCapSeconds = null, 10, 60),
        )
    }

    @Test
    fun `an AMRAP states a time cap and answers 巡数 with 時間内で`() {
        // `04-library-records.md` §3's シンディ mock, row for row and in its order. The answer to "how
        // many rounds" is genuinely "as many as fit", and §6 gives that exact phrase for this slot.
        assertEquals(
            listOf(
                EngineRow("制限時間", "二十分"),
                EngineRow("種目の間の休息", "なし"),
                EngineRow("巡数", "時間内で"),
            ),
            engineRows(Engine.AMRAP, rounds = null, timeCapSeconds = 1200, 0, 0),
        )
    }

    @Test
    fun `a for-time routine states nothing structural at all`() {
        // §3 edge case 4: FOR_TIME has no meaningful 巡数 — and マーフ prescribes no rest either. An
        // empty list is the honest answer; a 巡数 一巡 row would be information-shaped noise.
        assertTrue(engineRows(Engine.FOR_TIME, rounds = 1, timeCapSeconds = null, 0, 0).isEmpty())
    }

    @Test
    fun `for-time with rest states the rest, because the rest is the whole difference`() {
        // バーバラ's three minutes are *mandated* — the player disables ＋二十秒 and とばす on them
        // (`00-plan.md` §4) — so a page that omitted the row would be hiding the prescription.
        assertEquals(
            listOf(EngineRow("巡の間の休息", "百八十秒"), EngineRow("巡数", "五巡")),
            engineRows(Engine.FOR_TIME_WITH_REST, rounds = 5, timeCapSeconds = null, 0, 180),
        )
    }

    @Test
    fun `an EMOM has no between-station rest to state`() {
        // §3 edge case 5's own migration notice says it: 毎分では種目の間の休息はありません. Rendering
        // 種目の間の休息 / なし would describe a rest that the engine does not have a place for.
        assertEquals(
            listOf(EngineRow("巡数", "三十巡")),
            engineRows(Engine.EMOM, rounds = 30, timeCapSeconds = null, 0, 0),
        )
    }

    @Test
    fun `an ascending EMOM states nothing, because it runs until failure`() {
        assertTrue(engineRows(Engine.EMOM_ASCENDING, rounds = null, timeCapSeconds = null, 0, 0).isEmpty())
    }

    @Test
    fun `a stepped routine states its sets and the rest between them`() {
        // 段階 uses one station by construction, so between-station rest cannot exist; 巡 is the set.
        assertEquals(
            listOf(EngineRow("巡の間の休息", "九十秒"), EngineRow("巡数", "五巡")),
            engineRows(Engine.FIXED_SETS, rounds = 5, timeCapSeconds = null, 0, 90),
        )
    }

    @Test
    fun `a zero rest keeps its row and reads なし`() {
        // "No rest" is a prescription; a missing row is a question. §6 gives なし as the word for it.
        val rows = engineRows(Engine.INTERVAL_CIRCUIT, rounds = 1, timeCapSeconds = null, 0, 0)
        assertEquals(EngineRow("種目の間の休息", "なし"), rows.first())
    }

    @Test
    fun `a rest reads as the seconds it was set to, never as minutes`() {
        // `DECISIONS.md` §Q10. The builder wheel (`04` :350) and the settings row (`01` :735) both
        // spell sixty seconds 六十秒; a detail page that echoed it back as 一分 would be reporting a
        // value the user never chose. `durationKanji` belongs to durations the app *measured*.
        val sixty = engineRows(Engine.FIXED_SETS, rounds = 5, timeCapSeconds = null, 0, 60)
        assertEquals(EngineRow("巡の間の休息", "六十秒"), sixty.first())
        val ninety = engineRows(Engine.FIXED_SETS, rounds = 5, timeCapSeconds = null, 0, 90)
        assertEquals(EngineRow("巡の間の休息", "九十秒"), ninety.first())
    }

    @Test
    fun `a time cap is still read as a span of the clock`() {
        // The other half of §Q10's rule, pinned so the fix above does not spread: §3's mock prints a
        // twenty-minute cap as 二十分, and 千二百秒 would be nobody's idea of a cap.
        assertEquals(
            EngineRow("制限時間", "二十分"),
            engineRows(Engine.AMRAP, rounds = null, timeCapSeconds = 1200, 0, 0).first(),
        )
    }

    @Test
    fun `a routine with no round count omits the row rather than guessing one`() {
        val rows = engineRows(Engine.INTERVAL_CIRCUIT, rounds = null, timeCapSeconds = null, 10, 60)
        assertTrue(rows.none { it.label == "巡数" })
    }

    @Test
    fun `the snapshot overload reads the same rows as the parts`() {
        assertEquals(
            engineRows(Engine.AMRAP, rounds = null, timeCapSeconds = 1200, 0, 0),
            engineRows(snapshot(engine = Engine.AMRAP, timeCapSeconds = 1200)),
        )
    }

    // ─── 最高 tiles ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `an AMRAP is the one engine with two best tiles`() {
        // §4 edge case 2: AMRAP is scored on rounds then reps, so both are records worth holding.
        val tiles = bestTilesFor(
            Engine.AMRAP,
            listOf(best(BestMetric.MOST_ROUNDS, 17.0), best(BestMetric.MOST_REPS, 304.0)),
            timesDone = 6,
        )
        assertEquals(
            listOf(
                BestTile("最高巡数", "十七巡"),
                BestTile("最高反復", "三百四回"),
                BestTile("やった回数", "六回"),
            ),
            tiles,
        )
    }

    @Test
    fun `a for-time routine's best is a duration`() {
        val tiles = bestTilesFor(Engine.FOR_TIME, listOf(best(BestMetric.BEST_TIME, 374_000.0)), timesDone = 2)
        assertEquals(BestTile("最速", "六分十四秒"), tiles.first())
    }

    @Test
    fun `a circuit's best is weighted volume, and it is unitless`() {
        // §4 edge case 7: 最高負荷 is never shown as a rep count, because it is not one — duration
        // stations enter it through an approximation and two of them must not look addable.
        val tiles = bestTilesFor(Engine.INTERVAL_CIRCUIT, listOf(best(BestMetric.MOST_VOLUME, 286.4)), timesDone = 14)
        assertEquals(BestTile("最高負荷", "二百八十六"), tiles.first())
        assertEquals(BestTile("やった回数", "十四回"), tiles.last())
    }

    @Test
    fun `a routine nobody has finished shows no tiles at all`() {
        // The NoAttempts state: 最高 tiles absent, これまで replaced by まだ やっていません. A row of 〇
        // is a scolding, which the records page's empty state says in as many words. Nothing done and
        // nothing stored is the *only* condition for it (`DECISIONS.md` §Q9).
        assertTrue(bestTilesFor(Engine.AMRAP, emptyList(), timesDone = 0).isEmpty())
    }

    @Test
    fun `a best in the wrong metric for the engine is skipped, but the count is not`() {
        // A stored MOST_REPS on a circuit is not the circuit's record; printing it under 最高負荷 would
        // be labelling a rep count as a load. Dropping やった回数 with it is the §Q9 failure: the count
        // is engine-independent and always sourced, and a user with four sessions must not be shown
        // the page a user with none is shown.
        val tiles = bestTilesFor(Engine.INTERVAL_CIRCUIT, listOf(best(BestMetric.MOST_REPS, 300.0)), 4)
        assertEquals(listOf(BestTile("やった回数", "四回")), tiles)
    }

    @Test
    fun `an unlabelled metric costs its own tile and nothing else`() {
        // §Q9's own case, and the one that shipped リーコン・ロン's 最高 block empty forever: a stored
        // HIGHEST_STEP has no documented Japanese and is skipped — the count beside it is not.
        val tiles = bestTilesFor(Engine.FIXED_SETS, listOf(best(BestMetric.HIGHEST_STEP, 7.0)), timesDone = 6)
        assertEquals(listOf(BestTile("やった回数", "六回")), tiles)
        assertTrue(tiles.isNotEmpty())
    }

    @Test
    fun `a stepped routine is scored on volume, which is what the seed now says`() {
        // §Q9 moved リーコン・ロン's primary_metric to MOST_VOLUME: `04` :281 and :885 both map
        // FIXED_SETS → 最高負荷 and §6's tile list has no step tile at all, so §F.5 was the outlier.
        assertEquals(
            listOf(BestTile("最高負荷", "六十"), BestTile("やった回数", "六回")),
            bestTilesFor(Engine.FIXED_SETS, listOf(best(BestMetric.MOST_VOLUME, 60.0)), timesDone = 6),
        )
    }

    @Test
    fun `the one metric with no documented label gets no label`() {
        // HIGHEST_STEP has no Japanese anywhere in the specs. Four labels sourced and a fifth invented
        // is precisely the failure the string discipline exists to prevent.
        assertNull(bestMetricLabel(BestMetric.HIGHEST_STEP))
        assertEquals("最速", bestMetricLabel(BestMetric.BEST_TIME))
    }

    // ─── 段階 ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a stepped routine shows one step and how many there are`() {
        // §3 edge case 5, verbatim: 第七段 ・ 七 六 五 四 四, under a 十八段のうち caption. Eighteen rows
        // of five numbers is a reference table; the page is answering "what am I doing today".
        val copy = stepFor(
            progression(stepIndex = 7, stepCount = 18, reps = listOf(7, 6, 5, 4, 4)),
        )
        assertEquals("第七段 ・ 七 六 五 四 四", copy?.line)
        assertEquals("十八段のうち", copy?.caption)
    }

    @Test
    fun `a routine with no history stands on the first step`() {
        val copy = stepFor(progression(stepIndex = 1, stepCount = 18, reps = listOf(10, 6, 4, 3, 3)))
        assertEquals("第一段 ・ 十 六 四 三 三", copy?.line)
    }

    @Test
    fun `a step whose sets are not numbers keeps its title and drops the run`() {
        // Armstrong's max-effort and pyramid days (§F.3), none of which is seeded in Phase 1. 限界まで
        // is the measure chip's word and borrowing it into a numeral run would be inventing copy.
        val copy = stepFor(progression(stepIndex = 2, stepCount = 5, reps = listOf(null, null)))
        assertEquals("第二段", copy?.line)
        assertEquals("五段のうち", copy?.caption)
    }

    @Test
    fun `a step mixing numbers with max-effort sets prints no run at all`() {
        // A pyramid whose last set is to failure. Printing 第二段 ・ 十 八 would state a two-set
        // prescription for a three-set step — a truncated run misstates the prescription rather than
        // abbreviating it, and there is no per-set copy for the third one.
        val copy = stepFor(progression(stepIndex = 2, stepCount = 5, reps = listOf(10, 8, null)))
        assertEquals("第二段", copy?.line)
    }

    @Test
    fun `a day-based program is counted in days, and uses the label the seed stored`() {
        // `02-data.md` :261 documents label_ja as "'第一日' for day-based programs" and seeds
        // p_armstrong with step_unit = DAY and 第一日..第五日. Synthesising 第三段 / 五段のうち over that
        // seed is only invisible while リーコン・ロン is the sole routine with a progression.
        val copy = stepFor(
            progression(
                stepIndex = 3,
                stepCount = 5,
                reps = listOf(null),
                stepUnit = StepUnit.DAY,
                labelJa = "第三日",
            ),
        )
        assertEquals("第三日", copy?.line)
        assertEquals("五日のうち", copy?.caption)
    }

    @Test
    fun `a day-based step with no stored label is still counted in days`() {
        // Both words are the seeds' own; taking 段 for the title and 日 for the caption would be this
        // function disagreeing with itself inside one block.
        val copy = stepFor(progression(stepIndex = 3, stepCount = 5, reps = listOf(null), stepUnit = StepUnit.DAY))
        assertEquals("第三日", copy?.line)
        assertEquals("五日のうち", copy?.caption)
    }

    @Test
    fun `a routine with no progression shows no step line`() {
        assertNull(stepFor(null))
    }
}

// ─── fixtures ───────────────────────────────────────────────────────────────────────────────────

private fun snapshot(
    engine: Engine,
    rounds: Int? = null,
    timeCapSeconds: Int? = null,
    restBetweenStations: Int = 0,
    restBetweenRounds: Int = 0,
) = RoutineSnapshot(
    versionId = 1L,
    routineId = "cindy",
    versionNumber = 1,
    name = "シンディ",
    engine = engine,
    rounds = rounds,
    timeCapSeconds = timeCapSeconds,
    intervalSeconds = null,
    restBetweenStations = restBetweenStations,
    restBetweenRounds = restBetweenRounds,
    prepareSeconds = 5,
    progressionProgramId = null,
    primaryMetric = BestMetric.MOST_ROUNDS,
    stationCount = 3,
    estimatedDurationSeconds = 1200,
    estimatedTotalReps = 600,
    structuralHash = 0,
    createdAt = 0L,
    stations = emptyList(),
)

private fun best(metric: BestMetric, value: Double) = RoutineBest(
    routineId = "cindy",
    routineName = "シンディ",
    engine = Engine.AMRAP,
    metric = metric,
    value = value,
    tiebreak = 0.0,
    sessionId = 1L,
    achievedAt = 0L,
    localDate = LocalDate.of(2026, 6, 17),
    timesDone = 6,
    routineArchived = false,
    structureChanged = false,
)

private fun progression(
    stepIndex: Int,
    stepCount: Int,
    reps: List<Int?>,
    stepUnit: StepUnit = StepUnit.STEP,
    labelJa: String? = null,
) = ProgressionState(
    programId = "recon_ron",
    nameJa = "リーコン・ロン",
    stepUnit = stepUnit,
    stepCount = stepCount,
    advanceRule = AdvanceRule.WEEKS_ELAPSED,
    advanceParam = 2,
    cycleDays = null,
    origin = "Pasieka, Marine Corps Gazette, 1981-12",
    noteJa = null,
    currentStepIndex = stepIndex,
    currentStep = ProgressionStep(
        stepIndex = stepIndex,
        labelJa = labelJa,
        shape = StepShape.FIXED,
        totalReps = reps.filterNotNull().sum(),
        restSeconds = 90,
        noteJa = null,
        sets = reps.mapIndexed { i, r -> ProgressionSet(setIndex = i + 1, reps = r, variant = null) },
    ),
    sessionsAtStep = 0,
    stepEnteredAt = 0L,
    lastSessionId = null,
    cycleDay = 1,
)
