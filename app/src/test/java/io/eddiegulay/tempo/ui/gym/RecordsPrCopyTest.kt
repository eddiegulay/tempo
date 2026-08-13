package io.eddiegulay.tempo.ui.gym

import io.eddiegulay.tempo.gym.BestMetric
import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.MovementBest
import io.eddiegulay.tempo.gym.RoutineBest
import io.eddiegulay.tempo.i18n.StringsEn
import io.eddiegulay.tempo.i18n.StringsJa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * What `GYM.RECORDS.PR` *says*, pinned away from the composables that draw it.
 *
 * The page is read, never edited, so its failure modes are all silent ones: a routine that quietly
 * disappears because its engine was changed, two rows for one AMRAP, a 〇回 presented as a personal
 * best, an いちばん上 fragment claiming a rung the user is already standing on. None of those show up
 * in a screenshot and every one of them is a plain function here.
 */
class RecordsPrCopyTest {

    // ─── 型ごと: one row per routine, and which record it is ────────────────────────────────────

    @Test
    fun `an AMRAP shows one row, scored on rounds rather than on the reps that ride along`() {
        // `metricsFor` writes a MOST_REPS record alongside an AMRAP's MOST_ROUNDS so the detail page's
        // second tile has a row behind it. §4's PR page is one line per thing you are good at, so the
        // engine decides which of the two is printed — edge case 2's table, through `bestMetricFor`.
        val rows = prRoutineRows(
            listOf(
                best(metric = BestMetric.MOST_ROUNDS, value = 17.0, achievedAt = 200L),
                best(metric = BestMetric.MOST_REPS, value = 510.0, achievedAt = 300L),
            ),
            StringsJa,
        )

        assertEquals(1, rows.size)
        assertEquals("十七巡", rows.single().copy.value)
        assertEquals("最高巡数 ・ 時間内", rows.single().copy.meta)
    }

    @Test
    fun `a routine whose engine changed keeps the record it set instead of vanishing`() {
        // The tempting one-liner — filter on `metric == bestMetricFor(engine)` — deletes this routine
        // from the page: the stored MOST_ROUNDS row matches nothing once the engine scores volume.
        // §4 edge case 4's stance is that an edited routine *keeps* its old best and the honest amount
        // to say is 中身が変わっています, not to hide it.
        val rows = prRoutineRows(
            listOf(best(engine = Engine.INTERVAL_CIRCUIT, metric = BestMetric.MOST_ROUNDS, value = 9.0)),
            StringsJa,
        )

        assertEquals(1, rows.size)
        assertEquals("九巡", rows.single().copy.value)
    }

    @Test
    fun `a HIGHEST_STEP record is dropped rather than printed with an invented label`() {
        // `DECISIONS.md` §Q9: no Japanese label exists for it anywhere in the specs and none is to be
        // invented. Nothing seeds it in v1, so this is the restored-backup case.
        assertTrue(prRoutineRows(listOf(best(metric = BestMetric.HIGHEST_STEP, value = 9.0)), StringsJa).isEmpty())
    }

    @Test
    fun `the fallback skips an unlabellable record and takes the next most recent`() {
        // Falling back to "most recent" without the labellable filter would pick the HIGHEST_STEP row
        // and drop the routine after all the trouble taken to keep it.
        val rows = prRoutineRows(
            listOf(
                best(engine = Engine.FOR_TIME, metric = BestMetric.HIGHEST_STEP, value = 9.0, achievedAt = 900L),
                best(engine = Engine.FOR_TIME, metric = BestMetric.MOST_VOLUME, value = 420.0, achievedAt = 100L),
            ),
            StringsJa,
        )

        assertEquals(1, rows.size)
        assertEquals("最高負荷 ・ 完走", rows.single().copy.meta)
    }

    @Test
    fun `rows are ordered by when the record was set, newest first`() {
        // §4's `Ready` state: *a bests page should show momentum, not an alphabet*. The grouping picks
        // a row per routine that need not be the group's newest, so the order is re-established over
        // what was chosen rather than inherited from the query.
        val rows = prRoutineRows(
            listOf(
                best(routineId = "r_old", name = "七分間", achievedAt = 100L),
                best(routineId = "r_new", name = "タバタ", achievedAt = 900L),
            ),
            StringsJa,
        )

        assertEquals(listOf("タバタ", "七分間"), rows.map { it.copy.name })
    }

    @Test
    fun `a scaled tier is its own row and is never decorated with its tier a second time`() {
        // §4 edge case 3. Scaled tiers are separate stored routines and the seed already names one
        // 「シンディ（やさしい）」 — appending a tier here would render 「… ・ やさしい」.
        val rows = prRoutineRows(
            listOf(
                best(routineId = "r_cindy", name = "シンディ", achievedAt = 200L),
                best(routineId = "r_cindy_easy", name = "シンディ（やさしい）", achievedAt = 100L),
            ),
            StringsJa,
        )

        assertEquals(listOf("シンディ", "シンディ（やさしい）"), rows.map { it.copy.name })
    }

    @Test
    fun `an edited routine says its structure changed and stays on the page`() {
        val row = prRoutineRows(listOf(best(structureChanged = true)), StringsJa).single()
        assertEquals("中身が変わっています", row.copy.note)
    }

    @Test
    fun `an archived routine keeps its row, its chip and its taps`() {
        // §4's `ArchivedRoutineBest`: 削除済み chip, still tappable — `04` §1 rule 3 keeps the detail
        // page reachable from a record.
        val row = prRoutineRows(listOf(best(archived = true)), StringsJa).single()

        assertTrue(row.copy.archived)
        assertEquals("r_cindy", row.routineId)
        assertTrue("削除済み must be spoken, or 型を見る lands somewhere unexplained", row.copy.semantics.contains("削除済み"))
    }

    @Test
    fun `the row carries the ids its three destinations need`() {
        val row = prRoutineRows(listOf(best(sessionId = 42L)), StringsJa).single()

        assertEquals("r_cindy", row.routineId)
        assertEquals(42L, row.sessionId)
    }

    @Test
    fun `the spoken row is one sentence in the order the card is read`() {
        // §4's accessibility line, verbatim: 「シンディ、最高巡数 十七巡、時間内、六月十七日、六回」.
        val row = prRoutineRows(listOf(best(sessionId = 42L)), StringsJa).single()
        assertEquals("シンディ、最高巡数 十七巡、時間内、六月十七日、六回", row.copy.semantics)
    }

    // ─── 動きごと ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a movement row reads its best single set over the lifetime total`() {
        // §4's mock: 腕立て伏せ / 三十二回, then 一度に ・ のべ 四百回.
        val row = prMovementRow(movement(), StringsJa)!!

        assertEquals("腕立て伏せ", row.name)
        assertEquals("三十二回", row.value)
        assertEquals("一度に ・ のべ 四百回", row.meta)
        assertEquals("六月十日", row.date)
    }

    @Test
    fun `いちばん上 names the hardest rung reached, and nothing when that is this movement`() {
        // §4 edge case 6: the row rolls up the whole ladder. `hardestReachedExerciseName` is null
        // exactly when the hardest rung *is* the row's own movement, so the fragment is absent then
        // rather than repeating the name back at the user.
        assertEquals("いちばん上 足上げ腕立て", prMovementRow(movement(), StringsJa)!!.hardest)
        assertNull(prMovementRow(movement(hardestName = null), StringsJa)!!.hardest)
    }

    @Test
    fun `a movement with no counted set is omitted rather than shown as zero`() {
        // 一度に counts only sets with a recorded `actual_reps` (§4 edge case 5). 〇回 as a personal
        // best is a scolding dressed as a record.
        assertNull(prMovementRow(movement(singleSet = 0), StringsJa))
    }

    @Test
    fun `a movement with no recorded date omits the line rather than printing a dash`() {
        val row = prMovementRow(movement(date = null), StringsJa)!!
        assertNull(row.date)
    }

    @Test
    fun `the spoken movement row puts each label before its number`() {
        assertEquals(
            "腕立て伏せ、一度に 三十二回、のべ 四百回、六月十日、いちばん上 足上げ腕立て",
            prMovementRow(movement(), StringsJa)!!.semantics,
        )
    }

    @Test
    fun `movement rows are ordered by the most recent outing, with undated rows last`() {
        val rows = prMovementRows(
            listOf(
                movement(id = "e_situp", name = "腹筋", lastAt = null),
                movement(id = "e_pushup", name = "腕立て伏せ", lastAt = 100L),
                movement(id = "e_squat", name = "スクワット", lastAt = 900L),
            ),
            StringsJa,
        )

        assertEquals(listOf("スクワット", "腕立て伏せ", "腹筋"), rows.map { it.name })
    }

    // ─── The tabs ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the tabs are the two words §6 gives them, in the order the mock draws`() {
        assertEquals(listOf("型ごと", "動きごと"), PrTab.entries.map { it.label(StringsJa) })
        assertEquals(listOf("By routine", "By movement"), PrTab.entries.map { it.label(StringsEn) })
        assertEquals(PrTab.Routines, PrTab.Default)
    }

    // ─── English ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a movement row splits its two facts differently when drawn and when spoken`() {
        // 「一度に ・ のべ 四百回」 visible against 「一度に 三十二回、のべ 四百回」 spoken, and the same
        // split in English. The label alone and the label-with-value are two members for that reason.
        //
        // The names fall back to the frozen `exerciseName` here — the fixture's `e_pushup` is not a
        // catalogue id (the seeds are bare: `pushup`, `burpee`), so this exercises exactly the path a
        // record for a movement the catalogue has forgotten takes. A live id resolves through
        // `Exercise.displayName`, which is the fix for `MovementBest`'s frozen Japanese name.
        val row = prMovementRow(movement(), StringsEn)!!
        assertEquals("32 reps", row.value)
        assertEquals("In one set · 400 reps in total", row.meta)
        assertEquals("Highest reached 足上げ腕立て", row.hardest)
        assertEquals(
            "腕立て伏せ, 32 reps in one set, 400 reps in total, 10 June, Highest reached 足上げ腕立て",
            row.semantics,
        )
    }

    // ─── Fixtures ───────────────────────────────────────────────────────────────────────────────

    private fun best(
        routineId: String = "r_cindy",
        name: String = "シンディ",
        engine: Engine = Engine.AMRAP,
        metric: BestMetric = BestMetric.MOST_ROUNDS,
        value: Double = 17.0,
        sessionId: Long = 1L,
        achievedAt: Long = 500L,
        archived: Boolean = false,
        structureChanged: Boolean = false,
    ) = RoutineBest(
        routineId = routineId,
        routineName = name,
        engine = engine,
        metric = metric,
        value = value,
        tiebreak = 0.0,
        sessionId = sessionId,
        achievedAt = achievedAt,
        localDate = LocalDate.of(2026, 6, 17),
        timesDone = 6,
        routineArchived = archived,
        structureChanged = structureChanged,
    )

    private fun movement(
        id: String = "e_pushup",
        name: String = "腕立て伏せ",
        singleSet: Int = 32,
        lifetime: Int = 400,
        lastAt: Long? = 900L,
        date: LocalDate? = LocalDate.of(2026, 6, 10),
        hardestName: String? = "足上げ腕立て",
    ) = MovementBest(
        ladderId = "push",
        exerciseId = id,
        exerciseName = name,
        singleSetReps = singleSet,
        lifetimeReps = lifetime,
        sessionId = 7L,
        lastPerformedAt = lastAt,
        lastLocalDate = date,
        hardestReachedExerciseId = hardestName?.let { "e_decline_pushup" },
        hardestReachedExerciseName = hardestName,
    )
}
