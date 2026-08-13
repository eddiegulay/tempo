package io.eddiegulay.tempo.gym

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a routine will cost, and — more importantly — when the app admits it is guessing.
 *
 * The arithmetic has one documented answer to check against, and it is worth stating why it is the
 * one: `00-plan.md` §2 row 18 writes 七分間 out in full as `12 × 30 + 11 × 10 + 5 prepare = 475`. The
 * **eleven** is the load-bearing part. A round has `n − 1` station rests, not `n`, and an
 * implementation that adds a trailing rest is out by exactly one rest per round — ten seconds here,
 * five minutes on a thirty-round EMOM, and a rest between the last station and the finish line in the
 * compiler that reads this shape.
 *
 * The other half of the file is design §3.5's instruction that the pace table exists "only for the
 * pacer and the routine's about-七分 estimate. It never advances anything" — and its corollary, which
 * these tests are here to hold: **never present a guess as arithmetic.** An unknown pace and a
 * max-effort station both flip the 目安 suffix rather than being silently absorbed into a total.
 */
class RoutineEstimateTest {

    @Test
    fun `七分間 computes to the four hundred and seventy-five seconds the plan writes out`() {
        val estimate = estimateRoutine(
            draft(
                engine = Engine.INTERVAL_CIRCUIT,
                stations = List(12) { hold(30) },
                rounds = 1,
                restBetweenStations = 10,
                prepareSeconds = 5,
            ),
            catalogue,
        )
        assertEquals(475, estimate.durationSeconds)
        assertFalse(estimate.approximate)
    }

    @Test
    fun `a round's rests are one fewer than its stations, on every round`() {
        // Three rounds of 七分間: the trailing-rest bug hides at one round and is 30 seconds here.
        val estimate = estimateRoutine(
            draft(
                engine = Engine.INTERVAL_CIRCUIT,
                stations = List(12) { hold(30) },
                rounds = 3,
                restBetweenStations = 10,
                restBetweenRounds = 60,
                prepareSeconds = 5,
            ),
            catalogue,
        )
        // 3 × (360 + 110) + 2 × 60 + 5
        assertEquals(1535, estimate.durationSeconds)
    }

    @Test
    fun `a rep station costs its reps times the exercise's own pace`() {
        // design §3.5's table, as seeded onto the exercise row: pull-up 3.0s, push-up 2.0s, squat 1.8s.
        assertEquals(15, paceEstimateSec(reps("pullup", 5), catalogue("pullup")))
        assertEquals(20, paceEstimateSec(reps("pushup", 10), catalogue("pushup")))
        assertEquals(27, paceEstimateSec(reps("squat", 15), catalogue("squat")))
    }

    @Test
    fun `a fractional pace rounds rather than truncating`() {
        // 15 sit-ups at 1.7s is 25.5. Truncating makes every odd station a second optimistic, and
        // twelve of them a whole station short.
        assertEquals(26, paceEstimateSec(reps("situp", 15), catalogue("situp")))
    }

    @Test
    fun `a hold costs exactly what it prescribes, with no catalogue lookup at all`() {
        assertEquals(30, paceEstimateSec(hold(30), null))
    }

    @Test
    fun `a max-effort station has no estimate of its own`() {
        // The picker replaces the wheel with できるところまで and *removes* the 目安 line — there is
        // nothing to estimate. The routine total substitutes its own fallback; a single station must
        // not.
        assertNull(paceEstimateSec(maxEffort("pullup"), catalogue("pullup")))
    }

    @Test
    fun `an exercise the catalogue does not know gets no invented pace`() {
        // §3.5's table omits most movements and the instruction is not to invent one. Null here is
        // what becomes the 目安 suffix one level up.
        assertNull(paceEstimateSec(reps("kettlebell_swing", 20), null))
    }

    @Test
    fun `a max-effort station contributes forty-five seconds and flips the guess flag`() {
        // BUILDER edge case 7's own number, and its own rule: it contributes **zero reps**, because a
        // set with no prescribed count cannot be added into 三百回 without making that number
        // half-measured and wholly unlabelled.
        val estimate = estimateRoutine(
            draft(engine = Engine.INTERVAL_CIRCUIT, stations = listOf(maxEffort("pullup")), rounds = 1),
            catalogue,
        )
        assertEquals(MAX_EFFORT_FALLBACK_SECONDS, estimate.durationSeconds)
        assertEquals(0, estimate.totalReps)
        assertTrue(estimate.approximate)
    }

    @Test
    fun `a max-effort station with history costs its median, not the fallback`() {
        // The first half of BUILDER edge case 7 — "its *median historical* seconds (or 45s with no
        // history)" — which had no parameter to arrive through and so was simply unimplemented. 目安
        // still goes on: a median of this user's own sets is a measurement, but the total built from
        // it is still not arithmetic, and the rule appends 目安 to itself rather than to its fallback.
        val estimate = estimateRoutine(
            draft(engine = Engine.INTERVAL_CIRCUIT, stations = listOf(maxEffort("pullup")), rounds = 1),
            catalogue,
            maxEffortSeconds = { if (it == "pullup") 62 else null },
        )
        assertEquals(62, estimate.durationSeconds)
        assertEquals(0, estimate.totalReps)
        assertTrue(estimate.approximate)
    }

    @Test
    fun `a median is taken per movement, and an absent one falls back alone`() {
        // A routine can hold several 限界まで stations on different movements; a 懸垂 hold and a
        // 腕立て伏せ hold are not the same guess, and one missing median must not discard the other's.
        val estimate = estimateRoutine(
            draft(
                engine = Engine.INTERVAL_CIRCUIT,
                stations = listOf(maxEffort("pullup"), maxEffort("pushup")),
                rounds = 1,
            ),
            catalogue,
            maxEffortSeconds = { if (it == "pullup") 62 else null },
        )
        assertEquals(62 + MAX_EFFORT_FALLBACK_SECONDS, estimate.durationSeconds)
    }

    @Test
    fun `a non-positive median is a store bug, not a very fast set`() {
        val estimate = estimateRoutine(
            draft(engine = Engine.INTERVAL_CIRCUIT, stations = listOf(maxEffort("pullup")), rounds = 1),
            catalogue,
            maxEffortSeconds = { 0 },
        )
        assertEquals(MAX_EFFORT_FALLBACK_SECONDS, estimate.durationSeconds)
    }

    @Test
    fun `an unresolvable station makes the whole estimate a guess`() {
        val estimate = estimateRoutine(
            draft(
                engine = Engine.INTERVAL_CIRCUIT,
                stations = listOf(hold(30), reps("kettlebell_swing", 20)),
                rounds = 1,
            ),
            catalogue,
        )
        assertEquals(30, estimate.durationSeconds)
        assertTrue(estimate.approximate)
    }

    @Test
    fun `an AMRAP's duration is its cap and its reps are a ceiling`() {
        // §3 edge case 6: the estimate on an AMRAP is a cap, not a duration. まで is an upper bound, so
        // a twenty-minute cap over a sixty-two-second round rounds *up* to twenty rounds.
        val estimate = estimateRoutine(cindy(), catalogue)
        assertEquals(1200, estimate.durationSeconds)
        assertEquals(600, estimate.totalReps)
        assertEquals("約 二十分 ・ 六百回まで", estimateLabel(Engine.AMRAP, estimate))
    }

    @Test
    fun `an EMOM costs its window times its rounds, whatever the work inside it costs`() {
        val estimate = estimateRoutine(
            draft(
                engine = Engine.EMOM,
                stations = listOf(reps("pullup", 5), reps("pushup", 10), reps("squat", 15)),
                rounds = 30,
                intervalSeconds = 60,
                prepareSeconds = 5,
            ),
            catalogue,
        )
        assertEquals(1805, estimate.durationSeconds)
        assertEquals(900, estimate.totalReps)
    }

    @Test
    fun `a routine that runs until failure estimates nothing and says nothing`() {
        // デス・バイ. 約 〇分 would be a claim; the empty string is the caller's cue to omit the line.
        val estimate = estimateRoutine(
            draft(engine = Engine.EMOM_ASCENDING, stations = listOf(reps("burpee", 1)), rounds = null),
            catalogue,
        )
        assertEquals(0, estimate.durationSeconds)
        assertEquals("", estimateLabel(Engine.EMOM_ASCENDING, estimate))
    }

    @Test
    fun `the builder line reads as design writes it`() {
        assertEquals(
            "約 十八分 ・ 三百回",
            estimateLabel(Engine.INTERVAL_CIRCUIT, RoutineEstimate(1080, 300, approximate = false)),
        )
    }

    @Test
    fun `a guessed estimate is labelled 目安, every time`() {
        assertEquals(
            "約 十八分 ・ 三百回 目安",
            estimateLabel(Engine.INTERVAL_CIRCUIT, RoutineEstimate(1080, 300, approximate = true)),
        )
    }

    @Test
    fun `七分間 rounds to about eight minutes, which is what it actually is`() {
        // `00-plan.md` §2 row 18: render the computed 475s. "7-minute workout" is ACSM's branding
        // rounding, not our arithmetic, and 約 is not a licence to repeat someone else's rounding.
        assertEquals(
            "約 八分",
            estimateLabel(Engine.INTERVAL_CIRCUIT, RoutineEstimate(475, 0, approximate = false)),
        )
    }

    @Test
    fun `a pure-time circuit omits its rep fragment rather than printing zero`() {
        assertEquals(
            "約 四分",
            estimateLabel(Engine.INTERVAL_CIRCUIT, RoutineEstimate(240, 0, approximate = false)),
        )
    }

    @Test
    fun `a very short routine floors at one minute rather than reading 約 〇分`() {
        assertEquals(
            "約 一分",
            estimateLabel(Engine.INTERVAL_CIRCUIT, RoutineEstimate(20, 0, approximate = false)),
        )
    }
}

// ─── fixtures ───────────────────────────────────────────────────────────────────────────────────

/**
 * The six paces design §3.5 documents, keyed by the id the catalogue is seeded with. Nothing else has
 * a pace here, on purpose: a seventh entry would be this test inventing the number the spec refuses to.
 */
private val PACES = mapOf(
    "pushup" to 2.0,
    "pullup" to 3.0,
    "squat" to 1.8,
    "situp" to 1.7,
    "dip" to 2.5,
    "burpee" to 4.0,
)

private val catalogue: (String) -> Exercise? = { id ->
    PACES[id]?.let { pace ->
        Exercise(
            id = id,
            nameJa = id,
            nameEn = id,
            pattern = Pattern.H_PUSH,
            secondsPerRep = pace,
            difficulty = 1.0,
            isIsometric = false,
            cue = null,
            ladderId = null,
            catalogVersion = 1,
            archived = false,
        )
    }
}

private fun reps(id: String, count: Int) = StationDraft(id, Measure.REPS, count, null)
private fun hold(seconds: Int) = StationDraft("plank", Measure.DURATION, null, seconds)
private fun maxEffort(id: String) = StationDraft(id, Measure.MAX_EFFORT, null, null)

private fun draft(
    engine: Engine,
    stations: List<StationDraft>,
    rounds: Int?,
    timeCapSeconds: Int? = null,
    intervalSeconds: Int? = null,
    restBetweenStations: Int = 0,
    restBetweenRounds: Int = 0,
    prepareSeconds: Int = 0,
) = RoutineDraft(
    routineId = null,
    name = "型",
    engine = engine,
    stations = stations,
    rounds = rounds,
    timeCapSeconds = timeCapSeconds,
    intervalSeconds = intervalSeconds,
    restBetweenStations = restBetweenStations,
    restBetweenRounds = restBetweenRounds,
    prepareSeconds = prepareSeconds,
)

/** 5 懸垂 / 10 腕立て / 15 スクワット, AMRAP 20:00 — design §9's real シンディ, 5/10/15. */
private fun cindy() = draft(
    engine = Engine.AMRAP,
    stations = listOf(reps("pullup", 5), reps("pushup", 10), reps("squat", 15)),
    rounds = null,
    timeCapSeconds = 1200,
)
