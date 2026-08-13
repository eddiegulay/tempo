package io.eddiegulay.tempo.gym.data

import io.eddiegulay.tempo.gym.AdvanceRule
import io.eddiegulay.tempo.gym.DailyLoad
import io.eddiegulay.tempo.gym.Measure
import io.eddiegulay.tempo.gym.Phase
import io.eddiegulay.tempo.gym.ProgressionSet
import io.eddiegulay.tempo.gym.ProgressionState
import io.eddiegulay.tempo.gym.ProgressionStep
import io.eddiegulay.tempo.gym.SegmentResult
import io.eddiegulay.tempo.gym.SetVariant
import io.eddiegulay.tempo.gym.StepShape
import io.eddiegulay.tempo.gym.StepUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Phase 4's two decisions, at the boundaries where they are wrong quietly.
 *
 * Both live inside the finish transaction, which is the single most correctness-critical write in the
 * app (§E.5), and neither can be exercised through `GymStore` in a JVM test — `SQLiteDatabase` is not
 * on this classpath, which is the same reason `StoreGuardsTest` tests the signal rather than the
 * store. So the whole of both decisions is a pure function over plain values, and this file is where
 * they are pinned.
 *
 * The stakes are asymmetric in a way worth stating once. An advance that fires a week early costs the
 * user an easier week. A governor that holds when it should not costs them their programme, and a
 * governor that throws costs them the record of the session they just finished. Every ambiguity below
 * is therefore resolved toward *permitting*, and the tests say so explicitly rather than leaving it as
 * an accident of the implementation.
 */
class ProgressionAdvanceTest {

    // ── ALL_SETS_MADE ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `a session that made every prescribed set advances the step`() {
        val session = listOf(set(reps = 7, actual = 7), rest(), set(reps = 6, actual = 6))
        assertTrue(allSetsMade(session))
    }

    @Test
    fun `a set the user skipped did not make its target`() {
        // `skipped = 1` with `actual_reps` NULL is the schema's own definition of とばした — nothing
        // was recorded — and `03-player.md` §A REPS edge case 4 folds a set wound down to zero into
        // the same row. Four perfect sets and one skipped one is not "every set".
        val session = listOf(set(reps = 7, actual = 7), set(reps = 6, actual = null, skipped = true))
        assertFalse(allSetsMade(session))
    }

    @Test
    fun `a set that fell short of its prescription holds the step`() {
        assertFalse(allSetsMade(listOf(set(reps = 7, actual = 7), set(reps = 6, actual = 5))))
    }

    @Test
    fun `more than prescribed is still made`() {
        assertTrue(allSetsMade(listOf(set(reps = 7, actual = 9))))
    }

    @Test
    fun `a set closed by 済 without the wheel counts as its prescription`() {
        // The common path: the user taps 済 and never opens the rep wheel, so `actual_reps` is NULL on
        // a row that is not skipped. The button's own accessibility label is 「済、七回として記録」 —
        // the app has already told the user that this records the prescription — so reading the
        // silence as a shortfall would make this rule unsatisfiable on the path the app describes as
        // satisfying it.
        assertTrue(allSetsMade(listOf(set(reps = 7, actual = null), set(reps = 6, actual = null))))
    }

    @Test
    fun `a set with no prescribed number cannot fall short of one`() {
        // Armstrong's max-effort and grip-rotation days carry `progression_set.reps` NULL (§A.5,
        // §F.3). There is no target, so only a skip can fail it.
        assertTrue(allSetsMade(listOf(set(reps = null, actual = 12), set(reps = null, actual = null))))
        assertFalse(allSetsMade(listOf(set(reps = null, actual = null, skipped = true))))
    }

    @Test
    fun `a session with no sets at all does not advance vacuously`() {
        // "Made every set" is vacuously true over an empty list, and a vacuous advance is the same bug
        // shape §Q9 and §Q24 keep catching: an absence of rows reported as an accomplishment.
        assertFalse(allSetsMade(emptyList()))
        assertFalse(allSetsMade(listOf(rest(), rest())))
    }

    @Test
    fun `a skipped rest is not a missed set`() {
        // とばす on a rest is an ordinary thing to do and says nothing about the sets. Only REPS rows
        // are consulted, which is also why a time-boxed WORK segment — whose reps are advisory by the
        // compiler's own words — cannot fail this rule.
        val session = listOf(set(reps = 7, actual = 7), rest(skipped = true), set(reps = 6, actual = 6))
        assertTrue(allSetsMade(session))
    }

    @Test
    fun `a time-boxed work segment is not judged against its advisory reps`() {
        // 七分間 asks for as many push-ups as you manage in thirty seconds. A WORK row with a
        // prescription and no count is the normal outcome and must not be read as a failure — but no
        // progression can attach to that engine anyway, so the row is simply not a set.
        val session = listOf(work(reps = 20, actual = null), set(reps = 7, actual = 7))
        assertTrue(allSetsMade(session))
    }

    // ── The four rules ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `ALL_SETS_MADE is now a live arm rather than a refusal`() {
        // The arm this phase exists to complete. The rule was in the schema CHECK and in AdvanceRule
        // from schema v1; only the branch was missing.
        assertTrue(rule(AdvanceRule.ALL_SETS_MADE, results = listOf(set(reps = 7, actual = 7))))
        assertFalse(rule(AdvanceRule.ALL_SETS_MADE, results = listOf(set(reps = 7, actual = 3))))
    }

    @Test
    fun `MANUAL never advances, because the user does`() {
        assertFalse(rule(AdvanceRule.MANUAL, results = listOf(set(reps = 7, actual = 7))))
    }

    @Test
    fun `SESSIONS_COMPLETED counts the session being finished`() {
        assertFalse(rule(AdvanceRule.SESSIONS_COMPLETED, sessionsAtStep = 2, advanceParam = 3))
        assertTrue(rule(AdvanceRule.SESSIONS_COMPLETED, sessionsAtStep = 3, advanceParam = 3))
    }

    @Test
    fun `WEEKS_ELAPSED measures wall time since the step was entered`() {
        // リーコン・ロン: two weeks per rung, whatever you did inside them.
        val week = 7L * 24 * 60 * 60 * 1000
        assertFalse(rule(AdvanceRule.WEEKS_ELAPSED, advanceParam = 2, stepEnteredAt = 0, nowMs = 2 * week - 1))
        assertTrue(rule(AdvanceRule.WEEKS_ELAPSED, advanceParam = 2, stepEnteredAt = 0, nowMs = 2 * week))
    }

    @Test
    fun `a partial session never reaches the rule at all`() {
        // The judgement `prEligible` already makes for records, made once rather than twice: a quit
        // session's rows are a partial prescription, not a made one (`00-plan.md` §4.1 rule 2). The
        // guard is the call site, so this is the one assertion in the file that reads the source —
        // the same move `AcwrRestraintTest` makes for a rule that has no value to compare.
        val store = source("gym/data/GymStore.kt")
        assertTrue(
            "finishSession must call advanceProgression only under `complete`",
            store.contains("if (complete) advanceProgression("),
        )
    }

    // ── The ramp governor ───────────────────────────────────────────────────────────────────────

    @Test
    fun `below twenty-eight days the governor has no opinion at all`() {
        // §7.4: suppress entirely until 28 days of history exist. Not a weaker cap, not a default — no
        // opinion, so a proposal that doubles the prescription still goes through.
        val young = flat(days = 27, load = 100.0)
        assertNull(rampCap(young))
        assertTrue(rampAllowed(young, proposedIncrease = 1.0))
    }

    @Test
    fun `at exactly twenty-eight days the governor starts`() {
        val spine = flat(days = 28, load = 100.0)
        assertNotNull(rampCap(spine))
        assertEquals(0.10, rampCap(spine)!!, 1e-9)
    }

    @Test
    fun `a flat spine permits ten percent and refuses eleven`() {
        // The whole of the boring, defensible cap. A flat spine is ACWR 1.0, so the headroom is
        // exactly the cap.
        val spine = flat(days = 28, load = 100.0)
        assertTrue(rampAllowed(spine, proposedIncrease = 0.10))
        assertFalse(rampAllowed(spine, proposedIncrease = 0.11))
    }

    @Test
    fun `a spike holds the advance`() {
        // Three quiet weeks and one heavy one: the week's ramp has already been spent by the sessions
        // themselves, so there is nothing left to give the prescription.
        val spiked = flat(days = 21, load = 50.0) + flat(days = 7, load = 150.0)
        assertTrue(rampCap(spiked)!! < 0.0)
        assertFalse(rampAllowed(spiked, proposedIncrease = 0.05))
    }

    @Test
    fun `a held advance is re-offered once the spike has aged out`() {
        // The property that makes this a governor rather than a gate: the same proposal, the same
        // programme, a week later. Nothing about the held step was lost — `progressionAfterSession`
        // reset nothing — so the next completed session asks again and this time it passes.
        val proposal = 0.077 // リーコン・ロン, step 1 → step 2
        val duringTheSpike = flat(days = 21, load = 50.0) + flat(days = 7, load = 150.0)
        val aWeekLater = flat(days = 14, load = 50.0) + flat(days = 7, load = 150.0) +
            flat(days = 7, load = 50.0)

        assertFalse(rampAllowed(duringTheSpike, proposal))
        assertTrue(rampAllowed(aWeekLater, proposal))
    }

    @Test
    fun `an unrated session in the window silences the governor`() {
        // Foster load is CR10 × minutes, so an unrated session contributes nothing to the sum and the
        // window understates itself. `monotony` already refuses a number for exactly this reason;
        // refusing in one direction and guessing in the other would be worse than refusing in both.
        val spine = flat(days = 27, load = 100.0) + listOf(day(28, load = 0.0, sessions = 1, unrated = 1))
        assertNull(rampCap(spine))
        assertTrue(rampAllowed(spine, proposedIncrease = 0.5))
    }

    @Test
    fun `a chronic mean of zero is not a verdict about the user`() {
        // Someone returning after a month off has no ramp to govern. Holding them would be §Q24's
        // mistake — an absence of recent rows read as a fact about the person.
        val away = flat(days = 28, load = 0.0)
        assertNull(rampCap(away))
        assertTrue(rampAllowed(away, proposedIncrease = 0.5))
    }

    @Test
    fun `a deload is never held`() {
        val spiked = flat(days = 21, load = 50.0) + flat(days = 7, load = 150.0)
        assertTrue(rampAllowed(spiked, proposedIncrease = 0.0))
        assertEquals(0.0, workIncrease(current = 60, next = 40)!!, 1e-9)
        assertTrue(rampAllowed(spiked, workIncrease(current = 60, next = 40)!!))
    }

    @Test
    fun `the governor never renders a ratio, only a yes or a no`() {
        // The type is the guarantee. `rampAllowed` is a Boolean and `rampCap` is headroom — neither is
        // an ACWR value, and nothing in the gym reads either into a string. §7.4: no injury-risk
        // percentage, no ratio gauge, no chart. `AcwrRestraintTest` guards the other half.
        val spine = flat(days = 28, load = 100.0)
        assertEquals(0.10, RAMP_CAP_PER_WEEK, 1e-9)
        assertEquals(0.10, rampCap(spine)!!, 1e-9)
    }

    // ── What a step is worth ────────────────────────────────────────────────────────────────────

    @Test
    fun `a step is worth the sum of its prescribed sets, not its published total`() {
        // Deliberately not `progression_step.total_reps`: §A.5 says that column exists only so the
        // seed regression can be a plain SQL assertion, and ProgressionStep's own KDoc says it is not
        // used at run time and must not become so. Here the two disagree on purpose.
        val step = step(sets = listOf(7, 6, 5, 4, 4), totalReps = 999)
        assertEquals(26, stepWorkUnits(step))
    }

    @Test
    fun `a step whose sets resolve at run time is unknowable, not zero`() {
        assertNull(stepWorkUnits(step(sets = listOf(7, 6), unknownSets = 1)))
        assertNull(stepWorkUnits(step(sets = emptyList())))
        assertNull(stepWorkUnits(null))
        // And unknowable propagates as no opinion rather than as a hold: the store reads a null
        // increase as "nothing to govern" and never even queries the spine.
        assertNull(workIncrease(stepWorkUnits(step(sets = emptyList())), 30))
        assertNull(workIncrease(26, stepWorkUnits(step(sets = listOf(7), unknownSets = 1))))
    }

    @Test
    fun `every rung of リーコン・ロン fits under the cap on a flat spine`() {
        // The shipped programme is not in tension with the governor: 24 + 2×step means the biggest
        // jump is the first one, 26 → 28, which is 7.7%. A user whose week is steady walks the whole
        // eighteen rungs without ever being held — the governor only meets someone who is already
        // ramping through their sessions.
        val spine = flat(days = 28, load = 100.0)
        val steps = SeedCatalog.programs.first { it.id == "p_recon_ron" }.steps
            .sortedBy { it.stepIndex }
            .map { seed -> step(sets = seed.sets.map { it.reps ?: 0 }) }

        steps.zipWithNext().forEach { (current, next) ->
            val increase = workIncrease(stepWorkUnits(current), stepWorkUnits(next))
            assertNotNull(increase)
            assertTrue("a rung asks for more than ten percent more: $increase", rampAllowed(spine, increase!!))
        }
    }

    // ── The state a finished session leaves behind ──────────────────────────────────────────────

    @Test
    fun `an advance resets the count and re-enters the step now`() {
        val next = progressionAfterSession(state(stepIndex = 3, sessionsAtStep = 1), advance = true, nowMs = 5_000)
        assertEquals(4, next.stepIndex)
        assertEquals(0, next.sessionsAtStep)
        assertEquals(5_000, next.stepEnteredAt)
    }

    @Test
    fun `a held advance keeps its session count and its clock, so the next session re-asks`() {
        // This is what "held, not blocked" means in the data. Nothing resets, so a SESSIONS_COMPLETED
        // rule that was satisfied stays satisfied and a WEEKS_ELAPSED one only becomes more so.
        val before = state(stepIndex = 3, sessionsAtStep = 4, stepEnteredAt = 1_000)
        val next = progressionAfterSession(before, advance = false, nowMs = 5_000)
        assertEquals(3, next.stepIndex)
        assertEquals(5, next.sessionsAtStep)
        assertEquals(1_000, next.stepEnteredAt)
        // And the rule it was holding is still satisfied on the next pass.
        assertTrue(rule(AdvanceRule.SESSIONS_COMPLETED, sessionsAtStep = next.sessionsAtStep + 1, advanceParam = 3))
    }

    @Test
    fun `the last rung does not advance past the table`() {
        val next = progressionAfterSession(state(stepIndex = 18, stepCount = 18), advance = true, nowMs = 5_000)
        assertEquals(18, next.stepIndex)
    }

    @Test
    fun `a day programme rotates its cycle day rather than progressing`() {
        // アームストロング: day 5 is followed by day 1 (§F.3), which is why the two counters are
        // separate columns.
        val armstrong = state(stepIndex = 5, stepCount = 5, unit = StepUnit.DAY, cycleDay = 5)
        assertEquals(1, progressionAfterSession(armstrong, advance = true, nowMs = 0).cycleDay)
        assertEquals(3, progressionAfterSession(armstrong.copy(cycleDay = 2), advance = false, nowMs = 0).cycleDay)
    }

    // ── fixtures ────────────────────────────────────────────────────────────────────────────────

    private fun rule(
        rule: AdvanceRule,
        sessionsAtStep: Int = 1,
        advanceParam: Int? = 1,
        stepEnteredAt: Long = 0,
        nowMs: Long = 0,
        results: List<SegmentResult> = emptyList(),
    ): Boolean = ruleSatisfied(rule, sessionsAtStep, advanceParam, stepEnteredAt, nowMs, results)

    private fun set(reps: Int?, actual: Int?, skipped: Boolean = false): SegmentResult =
        segment(Phase.REPS, reps, actual, skipped)

    private fun work(reps: Int?, actual: Int?): SegmentResult = segment(Phase.WORK, reps, actual, false)

    private fun rest(skipped: Boolean = false): SegmentResult = segment(Phase.REST, null, null, skipped)

    private fun segment(phase: Phase, reps: Int?, actual: Int?, skipped: Boolean) = SegmentResult(
        ordinal = 0,
        phase = phase,
        roundIndex = 0,
        stationOrder = 0,
        exerciseId = "pullup",
        measure = if (phase == Phase.REST) null else Measure.REPS,
        prescribedReps = reps,
        prescribedSeconds = null,
        actualReps = actual,
        actualMs = 30_000,
        addedMs = 0,
        skipped = skipped,
        difficultyCoefficient = 1.0,
        volumeUnits = 0.0,
        closedAtWallMs = 0,
        closedAtElapsedMs = 0,
    )

    private fun step(sets: List<Int>, unknownSets: Int = 0, totalReps: Int? = null) = ProgressionStep(
        stepIndex = 1,
        labelJa = null,
        shape = StepShape.FIXED,
        totalReps = totalReps,
        restSeconds = 90,
        noteJa = null,
        sets = sets.mapIndexed { i, reps -> ProgressionSet(i + 1, reps, null) } +
            (1..unknownSets).map { ProgressionSet(sets.size + it, null, SetVariant.OVERHAND) },
    )

    private fun state(
        stepIndex: Int = 1,
        stepCount: Int = 18,
        sessionsAtStep: Int = 0,
        stepEnteredAt: Long = 0,
        unit: StepUnit = StepUnit.STEP,
        cycleDay: Int = 1,
    ) = ProgressionState(
        programId = "p_test",
        nameJa = "テスト",
        stepUnit = unit,
        stepCount = stepCount,
        advanceRule = AdvanceRule.ALL_SETS_MADE,
        advanceParam = null,
        cycleDays = null,
        origin = "test",
        noteJa = null,
        currentStepIndex = stepIndex,
        currentStep = null,
        sessionsAtStep = sessionsAtStep,
        stepEnteredAt = stepEnteredAt,
        lastSessionId = null,
        cycleDay = cycleDay,
    )

    /** [days] identical days ending today — the spine shape the store zero-fills. */
    private fun flat(days: Int, load: Double): List<DailyLoad> =
        (1..days).map { day(it, load, sessions = if (load > 0) 1 else 0, unrated = 0) }

    private fun day(index: Int, load: Double, sessions: Int, unrated: Int) = DailyLoad(
        date = LocalDate.of(2026, 1, 1).plusDays(index.toLong()),
        load = load,
        sessions = sessions,
        unrated = unrated,
    )

    /** `AcwrRestraintTest`'s resolver in spirit: find main sources without trusting the working dir. */
    private fun source(relative: String): String {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            for (prefix in listOf("app/src/main/java/io/eddiegulay/tempo/", "src/main/java/io/eddiegulay/tempo/")) {
                val candidate = File(dir, prefix + relative)
                if (candidate.exists()) return candidate.readText()
            }
            dir = dir.parentFile
        }
        throw IllegalStateException("could not locate $relative from ${File("").absolutePath}")
    }
}
