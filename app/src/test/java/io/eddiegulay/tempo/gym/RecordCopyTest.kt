package io.eddiegulay.tempo.gym

import io.eddiegulay.tempo.i18n.StringsEn
import io.eddiegulay.tempo.i18n.StringsJa
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a finished session is allowed to say about itself.
 *
 * Two doctrines are on trial in every test here, and both are about restraint rather than about
 * formatting. **A partial session is a real session** (`00-plan.md` §4.1 rule 2): it keeps its numbers
 * and loses its accolades, in every surface, identically — a chip that appears on the history row and
 * not on the record screen is the app grading the user twice. And **a record since beaten stops
 * claiming the crown** (`04-library-records.md` §4): 自己最高 and 当時の自己最高 are two different
 * sentences and the difference is recomputed on read, so a test is the only thing that can catch them
 * being collapsed back into one boolean.
 */
class RecordCopyTest {

    // ─── comparison ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a faster session says how much faster`() {
        // design §12's one comparison sentence, and design §4's own numbers.
        assertEquals(
            "前回より 二十二秒 速い",
            comparisonCopy(session(activeMs = 374_000), session(activeMs = 396_000), StringsJa),
        )
    }

    @Test
    fun `a slower session says nothing, because no copy exists for it`() {
        // **This is a documented gap, not a decision.** design §12 carries 速い and nothing else — no
        // 遅い, no 同じ. Inventing one is forbidden, so the line is omitted and the hero time above it
        // still shows the honest number. If one string is ever added to this feature, add this one.
        assertNull(comparisonCopy(session(activeMs = 396_000), session(activeMs = 374_000), StringsJa))
        assertNull(comparisonCopy(session(activeMs = 374_000), session(activeMs = 374_000), StringsJa))
    }

    @Test
    fun `a first session has nothing to compare to`() {
        // The caller renders はじめての記録 for its own NoComparison state. That is a state, not a
        // comparison, so it is not this function's string.
        assertNull(comparisonCopy(session(activeMs = 374_000), null, StringsJa))
    }

    @Test
    fun `a partial session is not compared`() {
        // `03-player.md` §A's Partial state suppresses it: a session you stopped early is faster than
        // one you finished, and saying so is a taunt.
        assertNull(comparisonCopy(session(activeMs = 100_000, complete = false), session(activeMs = 374_000), StringsJa))
    }

    @Test
    fun `a session under thirty seconds beats nothing`() {
        // COMPLETE edge case 6. The arithmetic would be right and the sentence would be false.
        assertNull(comparisonCopy(session(activeMs = 20_000), session(activeMs = 374_000), StringsJa))
    }

    @Test
    fun `an engine whose clock is fixed is never compared on the clock`() {
        // AMRAP and EMOM run to a cap and are scored in rounds; EMOM_ASCENDING is scored on how long
        // you survived, so for a デス・バイ a shorter session is a *worse* one and 速い is backwards.
        assertNull(
            comparisonCopy(session(activeMs = 1_190_000, engine = Engine.AMRAP), session(activeMs = 1_200_000), StringsJa),
        )
        assertNull(comparisonCopy(session(activeMs = 100_000, engine = Engine.EMOM), session(activeMs = 200_000), StringsJa))
        assertNull(
            comparisonCopy(
                session(activeMs = 100_000, engine = Engine.EMOM_ASCENDING),
                session(activeMs = 200_000),
                StringsJa,
            ),
        )
    }

    @Test
    fun `a sub-second improvement is not a sentence`() {
        assertNull(comparisonCopy(session(activeMs = 374_000), session(activeMs = 374_400), StringsJa))
    }

    // ─── the personal-best chip ─────────────────────────────────────────────────────────────────

    @Test
    fun `a record that still stands says 自己最高`() {
        assertEquals(PrChip.CURRENT, prChip(session(personalBest = true), isStillBest = true))
        assertEquals("自己最高", PrChip.CURRENT.label(StringsJa))
    }

    @Test
    fun `a record since beaten is demoted rather than deleted`() {
        // §4's difference table: 当時の自己最高 in c.inkFaint. It happened; it is no longer the case.
        assertEquals(PrChip.FORMER, prChip(session(personalBest = true), isStillBest = false))
        assertEquals("当時の自己最高", PrChip.FORMER.label(StringsJa))
    }

    @Test
    fun `a partial session never holds a record, in any engine`() {
        // §4 edge case 1. A PR system that rewards quitting early is worse than none.
        assertNull(prChip(session(personalBest = true, complete = false), isStillBest = true))
    }

    @Test
    fun `a twelve-second session that beats a real one is noise, not a record`() {
        assertNull(prChip(session(personalBest = true, activeMs = 12_000), isStillBest = true))
    }

    @Test
    fun `an ordinary session carries no chip at all`() {
        assertNull(prChip(session(personalBest = false), isStillBest = true))
    }

    // ─── the partial chip ───────────────────────────────────────────────────────────────────────

    @Test
    fun `a partial session says how far it got`() {
        // design §12's 途中まで ・ 二十種目中 八, against the *frozen* denominator: skips and open rep
        // segments can move a recomputed one, and the chip has to say the twenty that was on screen.
        assertEquals(
            "途中まで ・ 二十種目中 八",
            partialChipCopy(session(complete = false, stationsPlanned = 20, stationsCompleted = 8), StringsJa),
        )
    }

    @Test
    fun `a complete session has no partial chip`() {
        assertNull(partialChipCopy(session(complete = true), StringsJa))
    }

    @Test
    fun `a partial session with no station plan still says 途中まで`() {
        // 〇種目中 〇 reads as a score. The bare word is the honest amount to say.
        assertEquals("途中まで", partialChipCopy(session(complete = false, stationsPlanned = 0), StringsJa))
    }

    // ─── the breakdown ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `a hold shows its time and no rep count`() {
        val row =
            breakdownRow(segment(measure = Measure.DURATION, prescribedSeconds = 30, actualMs = 30_000), "プランク", StringsJa)
        assertEquals(BreakdownRow("プランク", "0:30", "済", null, skipped = false), row)
    }

    @Test
    fun `a rep station that matched its prescription shows one number`() {
        val row = breakdownRow(
            segment(measure = Measure.REPS, prescribedReps = 20, actualReps = 20, actualMs = 41_000),
            "腕立て伏せ",
            StringsJa,
        )
        assertEquals(BreakdownRow("腕立て伏せ", "0:41", "済", "二十回", skipped = false), row)
    }

    @Test
    fun `a rep station that fell short shows both numbers`() {
        // design §4's own row: 十八回 / 二十回. Showing only the eighteen hides the prescription;
        // showing only the twenty is the wish rather than the record.
        val row = breakdownRow(
            segment(measure = Measure.REPS, prescribedReps = 20, actualReps = 18, actualMs = 38_000),
            "スクワット",
            StringsJa,
        )
        assertEquals("十八回 / 二十回", row?.reps)
    }

    @Test
    fun `a skipped station says so and shows a dash where its time would be`() {
        val row = breakdownRow(segment(measure = Measure.REPS, prescribedReps = 20, skipped = true), "バーピー", StringsJa)
        assertEquals(BreakdownRow("バーピー", "—", "とばした", null, skipped = true), row)
    }

    @Test
    fun `an uncounted rep station prints the prescription, which is not the same as a record`() {
        // The fifth shape the spec does not enumerate. The breakdown states what was asked for;
        // movementBests deliberately refuses to let that number set a best (§4 edge case 5). The two
        // are allowed to differ and this is the seam.
        val row = breakdownRow(
            segment(measure = Measure.REPS, prescribedReps = 20, actualReps = null, actualMs = 41_000),
            "腕立て伏せ",
            StringsJa,
        )
        assertEquals("二十回", row?.reps)
    }

    @Test
    fun `a station whose exercise the catalogue forgot is named, not blanked`() {
        // 不明な種目 is §6's own word for it. A nameless row in a list of names reads as a rendering
        // bug; this is a data fact and says so.
        val row = breakdownRow(segment(measure = Measure.REPS, actualReps = 20, actualMs = 41_000), null, StringsJa)
        assertEquals("不明な種目", row?.name)
    }

    @Test
    fun `rests and 支度 are not breakdown rows`() {
        // 内訳 is a list of what you did. Interleaving 休息 0:10 between every station would double the
        // list to say nothing — and the rest is already inside the hero, which counts active time.
        assertNull(breakdownRow(segment(phase = Phase.REST, actualMs = 10_000), null, StringsJa))
        assertNull(breakdownRow(segment(phase = Phase.PREPARE, actualMs = 5_000), null, StringsJa))
    }

    // ─── hero, rows, subtitle ───────────────────────────────────────────────────────────────────

    @Test
    fun `the hero is active time`() {
        assertEquals("六分十四秒", heroTime(374_000L, StringsJa))
    }

    @Test
    fun `a history row carries the day, the rounds and the reps`() {
        // The detail line says 十七日, not 六月十七日: the list is grouped under a plain 六月 header and
        // repeating the month on every row of that month is noise. The TalkBack string is the
        // opposite, and belongs to the caller.
        val lines = sessionRowLines(
            session(
                routineName = "七分間",
                activeMs = 374_000,
                roundsCompleted = 3,
                totalReps = 320,
                rating = Rating.HARD,
                personalBest = true,
            ),
            StringsJa,
        )
        assertEquals("七分間", lines.name)
        assertEquals("六分十四秒", lines.duration)
        assertEquals("十七日 ・ 三巡 ・ 三百二十回", lines.detail)
        assertEquals("きつい", lines.rating)
        assertEquals("自己最高", lines.chip)
        assertTrue(!lines.partial)
    }

    @Test
    fun `an unrated row simply ends after the duration`() {
        // §4 edge case 8. A dash there would be asking a question the user already declined.
        assertNull(sessionRowLines(session(rating = null), StringsJa).rating)
    }

    @Test
    fun `a pure-time circuit omits its rep fragment rather than printing zero`() {
        // Zero reps is an inapplicability, not a result — COMPLETE edge case 4 makes the same call for
        // the tile. A row can legitimately carry a detail line of just the day.
        val lines = sessionRowLines(session(roundsCompleted = 0, totalReps = 0), StringsJa)
        assertEquals("十七日", lines.detail)
    }

    @Test
    fun `a partial row carries the partial chip and never a record chip`() {
        val lines = sessionRowLines(
            session(complete = false, personalBest = true, stationsPlanned = 3, stationsCompleted = 2),
            StringsJa,
        )
        assertEquals("途中まで ・ 三種目中 二", lines.chip)
        assertTrue(lines.partial)
    }

    @Test
    fun `the history subtitle counts sessions and minutes`() {
        assertEquals("八十六回 ・ 二千四百分", historySubtitle(86, 144_000_000L, StringsJa))
    }

    @Test
    fun `a routine-filtered history names the routine and counts only it`() {
        // §4's own filtered subtitle. The minutes are dropped deliberately: opened from a routine's
        // page, the number that matters is how many times you have done it.
        assertEquals("「七分間」十四回", historySubtitle(14, 5_000_000L, StringsJa, routineName = "七分間"))
    }

    @Test
    fun `the subtitle's totals truncate rather than round up`() {
        assertEquals("一回 ・ 六分", historySubtitle(1, 374_000L, StringsJa))
    }

    // ─── the streak block ───────────────────────────────────────────────────────────────────────

    @Test
    fun `a live streak counts days`() {
        val copy = streakCopy(Streak(days = 4, forgivenThisMonth = 0, endedOn = null), StringsJa)
        assertEquals("四日 連続", copy.line)
        assertNull(copy.forgiveness)
        assertNull(copy.monotony)
        assertTrue(!copy.broken)
        assertEquals("四日 連続", copy.semantics)
    }

    @Test
    fun `a broken streak says so and never says zero`() {
        // §4's StreakZero state. 〇日 連続 is a scolding dressed as a statistic; とぎれています is a
        // fact with no verdict in it, and it can be read on a rest day without flinching.
        val copy = streakCopy(Streak(days = 0, forgivenThisMonth = 0, endedOn = LocalDate.of(2026, 6, 15)), StringsJa)
        assertEquals("連続は とぎれています", copy.line)
        assertTrue(copy.broken)
    }

    @Test
    fun `the forgiveness line says what was spent and never what is left`() {
        // §6's table says it beside the string itself, and Streak carries no remainder to print. A
        // number you can watch going down is a number you will spend, and the whole point of the
        // allowance is that the user should not be managing it.
        val copy = streakCopy(Streak(days = 9, forgivenThisMonth = 1, endedOn = null), StringsJa)
        assertEquals("ゆるし 一回 使いました", copy.forgiveness)
        assertTrue("残" !in copy.semantics)
        assertNull(streakCopy(Streak(days = 9, forgivenThisMonth = 0, endedOn = null), StringsJa).forgiveness)
    }

    @Test
    fun `the monotony nudge needs both a threshold and a fortnight`() {
        // §4: monotony7d > 2.0 && historyDays >= 14. Design §7.4's stance on premature metrics —
        // 同じ調子が続いています said to somebody in their first week is the app inventing a problem.
        assertEquals("同じ調子が続いています", streakCopy(streak4(), StringsJa, monotony7d = 2.4, historyDays = 14).monotony)
        assertNull(streakCopy(streak4(), StringsJa, monotony7d = 2.4, historyDays = 13).monotony)
        assertNull(streakCopy(streak4(), StringsJa, monotony7d = 2.0, historyDays = 30).monotony)
        // Null is the ordinary case and means *no answer* — an unrated day in the window, or a zero
        // standard deviation — rather than a low number.
        assertNull(streakCopy(streak4(), StringsJa, monotony7d = null, historyDays = 30).monotony)
    }

    @Test
    fun `the three lines are read as one node`() {
        // §4's accessibility note: three announcements for one thought is worse than one.
        val copy = streakCopy(Streak(days = 4, forgivenThisMonth = 1, endedOn = null), StringsJa, 2.4, 30)
        assertEquals("四日 連続、ゆるし 一回 使いました、同じ調子が続いています", copy.semantics)
    }

    // ─── the bests page ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `each engine is scored on the metric §4 assigns it`() {
        assertEquals(BestMetric.BEST_TIME, bestMetricFor(Engine.FOR_TIME))
        assertEquals(BestMetric.BEST_TIME, bestMetricFor(Engine.FOR_TIME_WITH_REST))
        assertEquals(BestMetric.MOST_ROUNDS, bestMetricFor(Engine.AMRAP))
        assertEquals(BestMetric.MOST_ROUNDS, bestMetricFor(Engine.EMOM_ASCENDING))
        assertEquals(BestMetric.MOST_VOLUME, bestMetricFor(Engine.INTERVAL_CIRCUIT))
        assertEquals(BestMetric.MOST_VOLUME, bestMetricFor(Engine.EMOM))
        assertEquals(BestMetric.MOST_VOLUME, bestMetricFor(Engine.FIXED_SETS))
    }

    @Test
    fun `bestMetricFor names the metric the detail page's first tile shows`() {
        // Three functions read one engine table — this one, `bestTilesFor`'s tile set, and
        // `metricsFor`'s eligibility list — because they answer three different questions (EMOM
        // proves it: scored on volume, one tile, allowed to take a MOST_REPS record it never shows).
        // They are held together by this test rather than by a merge, so a routine scored one way and
        // labelled another fails the build.
        val everyMetric = BestMetric.entries.map { best(metric = it) }
        Engine.entries.forEach { engine ->
            val tiles = bestTilesFor(engine, everyMetric, timesDone = 1, StringsJa)
            assertEquals(engine.name, bestMetricLabel(bestMetricFor(engine), StringsJa), tiles.first().label)
        }
    }

    @Test
    fun `a routine best renders as §4's four slots`() {
        val copy = bestValueCopy(
            best(
                routineName = "シンディ",
                engine = Engine.AMRAP,
                metric = BestMetric.MOST_ROUNDS,
                value = 17.0,
                timesDone = 6,
            ),
            StringsJa,
        )!!
        assertEquals("シンディ", copy.name)
        assertEquals("十七巡", copy.value)
        assertEquals("最高巡数 ・ 時間内", copy.meta)
        assertEquals("六月十七日", copy.date)
        assertEquals("六回", copy.count)
        assertNull(copy.note)
        assertEquals("シンディ、最高巡数 十七巡、時間内、六月十七日、六回", copy.semantics)
    }

    @Test
    fun `a record whose routine was edited states the fact and declines the judgement`() {
        // §4 edge case 4: deciding whether a twelve-station circuit's PR is invalidated by a
        // thirteenth is a policy this app cannot get right, so it says the one honest thing.
        val copy = bestValueCopy(best(structureChanged = true), StringsJa)!!
        assertEquals("中身が変わっています", copy.note)
        assertTrue(copy.semantics.endsWith("中身が変わっています"))
    }

    @Test
    fun `a deleted routine's record stays, and says it is deleted`() {
        val copy = bestValueCopy(best(routineArchived = true), StringsJa)!!
        assertTrue(copy.archived)
        assertTrue(copy.semantics.endsWith("削除済み"))
    }

    @Test
    fun `a record with no documented label is omitted rather than invented`() {
        // `DECISIONS.md` §Q9: §6's tile list has five entries and none of them is a step. Nothing
        // seeds HIGHEST_STEP in v1, so this is a defence against a restored backup — and where the
        // step you have reached belongs is stepFor, as 第九段 / 十八段のうち.
        assertNull(bestValueCopy(best(metric = BestMetric.HIGHEST_STEP), StringsJa))
    }

    @Test
    fun `a for-time record reads as a duration and a volume record carries no unit`() {
        // §4 edge case 7: 最高負荷 is deliberately unitless — it is never suffixed 回, because
        // weighted volume is not a rep count and printing it as one invites adding two together.
        assertEquals(
            "六分十四秒",
            bestValueCopy(best(engine = Engine.FOR_TIME, metric = BestMetric.BEST_TIME, value = 374_000.0), StringsJa)!!.value,
        )
        val volume = bestValueCopy(best(metric = BestMetric.MOST_VOLUME, value = 420.0), StringsJa)!!
        assertEquals("四百二十", volume.value)
        assertTrue(!volume.value.endsWith("回"))
    }

    // ─── English, where it is a different sentence and not a different spelling ──────────────────

    @Test
    fun `the partial chip counts up in English and down in Japanese`() {
        // 「二十種目中 八」 names the total first; "8 of 20 stations" names it last. The two are one
        // member on the table for exactly this reason — a call site gluing a prefix to a suffix would
        // have shipped Japanese word order to an English reader.
        val partial = session(complete = false, stationsPlanned = 20, stationsCompleted = 8)
        assertEquals("途中まで ・ 二十種目中 八", partialChipCopy(partial, StringsJa))
        assertEquals("Stopped early · 8 of 20 stations", partialChipCopy(partial, StringsEn))
    }

    @Test
    fun `a shortfall carries its counter twice in Japanese and once in English`() {
        // 十八回 / 二十回 against "18 / 20 reps". Both numerals reach the table bare, from `fmt.count`,
        // and each language attaches the unit where it belongs; two `fmt.reps` strings could only ever
        // have produced "18 reps / 20 reps".
        val short = segment(measure = Measure.REPS, prescribedReps = 20, actualReps = 18, actualMs = 38_000)
        assertEquals("十八回 / 二十回", breakdownRow(short, "スクワット", StringsJa)?.reps)
        assertEquals("18 / 20 reps", breakdownRow(short, "Air squat", StringsEn)?.reps)
    }

    @Test
    fun `a history row's day stands alone under its month header`() {
        // 十七日 in Japanese and a bare 17 in English: the row sits under a month header either way,
        // and "17 days" — which `fmt.days` would give — is a different fact entirely.
        assertEquals("十七日 ・ 三巡", sessionRowLines(session(roundsCompleted = 3), StringsJa).detail)
        assertEquals("17 · 3 rounds", sessionRowLines(session(roundsCompleted = 3), StringsEn).detail)
    }

    @Test
    fun `the streak and its grace read as sentences in both languages`() {
        val copy = streakCopy(Streak(days = 4, forgivenThisMonth = 1, endedOn = null), StringsEn, 2.4, 30)
        assertEquals("4 days in a row", copy.line)
        assertEquals("Grace used 1 time", copy.forgiveness)
        assertEquals("4 days in a row, Grace used 1 time, Your sessions have been much the same", copy.semantics)
    }

    @Test
    fun `a broken streak states a fact in both languages and passes no verdict`() {
        val copy = streakCopy(Streak(days = 0, forgivenThisMonth = 0, endedOn = null), StringsEn)
        assertEquals("The streak has ended", copy.line)
        assertTrue(copy.broken)
    }
}

private fun streak4() = Streak(days = 4, forgivenThisMonth = 0, endedOn = null)

private fun best(
    routineName: String = "シンディ",
    engine: Engine = Engine.INTERVAL_CIRCUIT,
    metric: BestMetric = BestMetric.MOST_VOLUME,
    value: Double = 420.0,
    timesDone: Int = 6,
    structureChanged: Boolean = false,
    routineArchived: Boolean = false,
) = RoutineBest(
    routineId = "cindy",
    routineName = routineName,
    engine = engine,
    metric = metric,
    value = value,
    tiebreak = 0.0,
    sessionId = 1L,
    achievedAt = 0L,
    localDate = LocalDate.of(2026, 6, 17),
    timesDone = timesDone,
    routineArchived = routineArchived,
    structureChanged = structureChanged,
)

// ─── fixtures ───────────────────────────────────────────────────────────────────────────────────

private fun session(
    activeMs: Long = 374_000L,
    complete: Boolean = true,
    engine: Engine = Engine.INTERVAL_CIRCUIT,
    personalBest: Boolean = false,
    rating: Rating? = null,
    roundsCompleted: Int = 0,
    routineName: String = "七分間",
    stationsCompleted: Int = 12,
    stationsPlanned: Int = 12,
    totalReps: Int = 0,
) = SessionSummary(
    sessionId = 1L,
    routineId = "seven",
    routineVersionId = 1L,
    routineName = routineName,
    engine = engine,
    tier = null,
    startedAt = 0L,
    finishedAt = activeMs,
    localDate = LocalDate.of(2026, 6, 17),
    activeMs = activeMs,
    wallMs = activeMs,
    complete = complete,
    reachedTimeCap = false,
    roundsPlanned = 1,
    roundsCompleted = roundsCompleted,
    stationsPlanned = stationsPlanned,
    stationsCompleted = stationsCompleted,
    totalReps = totalReps,
    volumeUnits = 0.0,
    rating = rating,
    personalBest = personalBest,
)

private fun segment(
    phase: Phase = Phase.REPS,
    measure: Measure? = null,
    prescribedReps: Int? = null,
    prescribedSeconds: Int? = null,
    actualReps: Int? = null,
    actualMs: Long = 0L,
    skipped: Boolean = false,
) = SegmentResult(
    ordinal = 1,
    phase = phase,
    roundIndex = 1,
    stationOrder = 1,
    exerciseId = "pushup",
    measure = measure,
    prescribedReps = prescribedReps,
    prescribedSeconds = prescribedSeconds,
    actualReps = actualReps,
    actualMs = actualMs,
    addedMs = 0L,
    skipped = skipped,
    difficultyCoefficient = 1.0,
    volumeUnits = 0.0,
    closedAtWallMs = 0L,
    closedAtElapsedMs = 0L,
)
