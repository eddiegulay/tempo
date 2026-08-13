package io.eddiegulay.tempo.ui.gym

import io.eddiegulay.tempo.gym.BestMetric
import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.Exercise
import io.eddiegulay.tempo.gym.MovementBest
import io.eddiegulay.tempo.gym.Pattern
import io.eddiegulay.tempo.gym.RoutineSummary
import io.eddiegulay.tempo.i18n.StringsEn
import io.eddiegulay.tempo.i18n.StringsJa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The pure half of `GYM.LIBRARY.EXERCISE_INDEX` and `GYM.LIBRARY.EXERCISE_DETAIL`.
 *
 * Every numbered edge case in `04-library-records.md` §3's two exercise pages that can be stated as a
 * value is stated here, because the alternative is an emulator (`00-plan.md` §4.1 rule 10). The four
 * that cannot — the ladder is composables and not a `Canvas`, `UnknownId` pops, a tap replaces rather
 * than pushes, the spine is decoration — are structural and belong to `ExerciseScreenStructureTest`.
 */
class ExerciseCatalogueCopyTest {

    private fun exercise(
        id: String,
        nameJa: String,
        nameEn: String = id,
        pattern: Pattern = Pattern.H_PUSH,
        secondsPerRep: Double = 2.0,
        difficulty: Double = 1.0,
        isometric: Boolean = false,
        cue: String? = null,
        ladderId: String? = null,
    ) = Exercise(
        id = id,
        nameJa = nameJa,
        nameEn = nameEn,
        pattern = pattern,
        secondsPerRep = secondsPerRep,
        difficulty = difficulty,
        isIsometric = isometric,
        cue = cue,
        ladderId = ladderId,
        catalogVersion = 1,
        archived = false,
    )

    private val pushup = exercise("pushup", "腕立て伏せ", "Push-up", cue = "体は一直線に", ladderId = "push")
    private val kneePushup =
        exercise("knee_pushup", "膝つき腕立て", "Knee push-up", difficulty = 0.5, ladderId = "push")
    private val wallPushup =
        exercise("wall_pushup", "壁腕立て", "Wall push-up", difficulty = 0.2, ladderId = "push")
    private val oneArm =
        exercise("one_arm_pushup", "片手腕立て", "One-arm push-up", difficulty = 2.5, ladderId = "push")
    private val plank = exercise(
        "plank", "プランク", "Plank",
        pattern = Pattern.CORE, secondsPerRep = 10.0, isometric = true, cue = "肘は肩の真下に",
    )
    private val run = exercise(
        "run", "走る", "Run", pattern = Pattern.LOCOMOTION, secondsPerRep = 10.0, isometric = true,
    )
    private val pullup = exercise(
        "pullup", "懸垂", "Pull-up", pattern = Pattern.V_PULL, secondsPerRep = 3.0, difficulty = 2.0,
    )

    private val ladder = listOf(wallPushup, kneePushup, pushup, oneArm)

    /**
     * One row of `GymRepository.exerciseBests()` — **per exercise**, which is what both pages now read.
     *
     * `hardestReached*` is always null here because that is what the per-exercise read emits: いちばん上
     * is a ladder's answer and a single rung has none. The 段階 block derives the current rung from
     * which rungs have a row at all, so nothing on these pages needs the field.
     */
    private fun best(
        exerciseId: String,
        ladderId: String? = null,
        singleSetReps: Int = 0,
        lifetimeReps: Int = 0,
        lastDate: LocalDate? = null,
    ) = MovementBest(
        ladderId = ladderId,
        exerciseId = exerciseId,
        exerciseName = exerciseId,
        singleSetReps = singleSetReps,
        lifetimeReps = lifetimeReps,
        sessionId = if (singleSetReps > 0) 1L else null,
        lastPerformedAt = lastDate?.let { 1L },
        lastLocalDate = lastDate,
        hardestReachedExerciseId = null,
        hardestReachedExerciseName = null,
    )

    private fun summary(routineId: String, versionId: Long, name: String) = RoutineSummary(
        routineId = routineId,
        versionId = versionId,
        name = name,
        engine = Engine.INTERVAL_CIRCUIT,
        builtIn = true,
        tier = null,
        origin = null,
        favourite = false,
        sortOrder = 0,
        rounds = 1,
        timeCapSeconds = null,
        intervalSeconds = null,
        restBetweenStations = 10,
        restBetweenRounds = 0,
        stationCount = 3,
        estimatedDurationSeconds = 300,
        estimatedTotalReps = 30,
        primaryMetric = BestMetric.MOST_ROUNDS,
        timesDone = 0,
        lastStartedAt = null,
        lastActiveMs = null,
        best = null,
        archivedAt = null,
    )

    // ─── EXERCISE_INDEX ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `the subtitle counts the catalogue in kanji`() {
        assertEquals("十六の動き", exerciseIndexSubtitle(16, StringsJa))
        assertEquals("二十三の動き", exerciseIndexSubtitle(23, StringsJa))
        assertEquals("23 movements", exerciseIndexSubtitle(23, StringsEn))
    }

    @Test
    fun `a card carries name, coefficient, cue and pattern with its pace`() {
        val copy = exerciseCardCopy(pushup, singleSetReps = null, StringsJa)

        assertEquals("腕立て伏せ", copy.name)
        assertEquals("一.〇", copy.coefficient)
        assertEquals("体は一直線に", copy.cue)
        assertEquals("押す ・ 二.〇秒/回", copy.meta)
    }

    @Test
    fun `the card reads the row's own English column, which the app has never drawn`() {
        // `name_en` has been NOT NULL and correctly seeded for all twenty-three movements since schema
        // v1 and was read into `Exercise` without ever reaching a `Text`. The cue comes from the
        // catalogue table rather than the row, because there is no `cue_en` column to read.
        val copy = exerciseCardCopy(pushup, singleSetReps = 32, StringsEn)

        assertEquals("Push-up", copy.name)
        assertEquals("1.0", copy.coefficient)
        assertEquals("Body in a straight line", copy.cue)
        assertEquals("Push · 2.0s/rep", copy.meta)
        assertEquals("Best 32 reps", copy.best)
        assertEquals("Push-up, Push, Difficulty 1.0, Best 32 reps", copy.description)
    }

    @Test
    fun `edge case 3 - a movement with no cue omits the line rather than blanking it`() {
        assertNull(exerciseCardCopy(run, singleSetReps = null, StringsJa).cue)
    }

    @Test
    fun `edge case 4 - no history means no 最高 fragment, never 最高 dash`() {
        val copy = exerciseCardCopy(pushup, singleSetReps = null, StringsJa)

        assertNull(copy.best)
        assertTrue("最高 must not appear at all", "最高" !in copy.description)
    }

    @Test
    fun `a recorded best renders as 最高 三十二回`() {
        assertEquals("最高 三十二回", exerciseCardCopy(pushup, singleSetReps = 32, StringsJa).best)
        // A zero is the store having nothing to attribute, not a record of zero.
        assertNull(bestRepsLabel(0, StringsJa))
    }

    @Test
    fun `edge case 5 - 走る renders no coefficient and no pace`() {
        val copy = exerciseCardCopy(run, singleSetReps = null, StringsJa)

        assertEquals("—", copy.coefficient)
        assertEquals("移動", copy.meta)
        assertEquals("走る、移動、難度 —", copy.description)
    }

    @Test
    fun `edge case 5 - the em dash is a documented hole and English does not fill it`() {
        // 走る has no rep semantics, so its coefficient is deliberately absent rather than 1.0. An
        // English number here would say the run can be counted, which is the fact the null prevents.
        val copy = exerciseCardCopy(run, singleSetReps = null, StringsEn)

        assertEquals("—", copy.coefficient)
        assertEquals("Travel", copy.meta)
        assertEquals("Run, Travel, Difficulty —", copy.description)
        assertNull("§F.1 writes 走る's cue as none, in both languages", copy.cue)
    }

    @Test
    fun `an isometric that is not 走る keeps its coefficient and loses only the pace`() {
        // プランク's 一.〇 is what volume_units multiplies by; its ten seconds are a volume conversion
        // and not a pace, so 十.〇秒/回 would be the one wrong fact of the two.
        val copy = exerciseCardCopy(plank, singleSetReps = null, StringsJa)

        assertEquals("一.〇", copy.coefficient)
        assertEquals("体幹", copy.meta)
        assertNull(paceLabel(plank, StringsJa))
        assertNull(paceLabel(plank, StringsEn))
    }

    @Test
    fun `the card description is the station picker's sentence, plus the best`() {
        assertEquals(
            "腕立て伏せ、押す、難度 一.〇、最高 三十二回",
            exerciseCardCopy(pushup, singleSetReps = 32, StringsJa).description,
        )
    }

    @Test
    fun `edge case 1 - within a pattern, rows climb by difficulty and tie by name`() {
        val sections = exerciseSections(listOf(pushup, oneArm, wallPushup, kneePushup), "", StringsJa)

        assertEquals(1, sections.size)
        assertEquals(
            listOf("壁腕立て", "膝つき腕立て", "腕立て伏せ", "片手腕立て"),
            sections.single().exercises.map { it.nameJa },
        )
    }

    @Test
    fun `edge case 1 - a difficulty tie breaks by name, not by insertion order`() {
        val zeta = exercise("z", "ゼータ", difficulty = 1.0)
        val alpha = exercise("a", "アルファ", difficulty = 1.0)
        val sections = exerciseSections(listOf(zeta, alpha), "", StringsJa)

        assertEquals(listOf("アルファ", "ゼータ"), sections.single().exercises.map { it.nameJa })
    }

    @Test
    fun `edge case 1 - the tiebreak is the displayed name, so an English list reads in order`() {
        // **A deliberate behaviour change.** The tie used to break on `nameJa` in every language, which
        // under an English UI is a codepoint sort — by kanji block — and produces an order no reader of
        // the labels can see. 空気椅子 precedes 踏み台昇降 and "Step-up" precedes "Wall sit", so those
        // two genuinely swap. Only within-section order moves; the sections are still `Pattern`'s
        // declaration order.
        //
        // `ExerciseCatalogSource.ladder` breaks its tie on `id` instead, and that is right there for
        // the opposite reason: a ladder's order IS its meaning and must not reshuffle when the user
        // flips the toggle. This list's order is a reading aid for the names beside it.
        val wallSit = exercise("wall_sit", "空気椅子", "Wall sit", pattern = Pattern.SQUAT, difficulty = 0.8)
        val stepUp = exercise("step_up", "踏み台昇降", "Step-up", pattern = Pattern.SQUAT, difficulty = 0.8)

        assertEquals(
            listOf("空気椅子", "踏み台昇降"),
            exerciseSections(listOf(stepUp, wallSit), "", StringsJa).single().exercises.map { it.nameJa },
        )
        assertEquals(
            listOf("Step-up", "Wall sit"),
            exerciseSections(listOf(wallSit, stepUp), "", StringsEn).single().exercises.map { it.nameEn },
        )
    }

    @Test
    fun `edge case 2 - pattern order is the declaration order, not alphabetical or by count`() {
        val sections = exerciseSections(listOf(run, plank, pullup, pushup, kneePushup), "", StringsJa)

        assertEquals(
            listOf(Pattern.H_PUSH, Pattern.V_PULL, Pattern.CORE, Pattern.LOCOMOTION),
            sections.map { it.pattern },
        )
        // The English list is the same sequence of sections: only the within-section tiebreak moved.
        assertEquals(
            sections.map { it.pattern },
            exerciseSections(listOf(run, plank, pullup, pushup, kneePushup), "", StringsEn).map { it.pattern },
        )
    }

    @Test
    fun `an empty pattern gets no heading`() {
        val sections = exerciseSections(listOf(pushup), "", StringsJa)

        assertEquals(listOf(Pattern.H_PUSH), sections.map { it.pattern })
    }

    @Test
    fun `a query collapses the headings into one flat list in the same order`() {
        val sections = exerciseSections(listOf(run, pullup, pushup, kneePushup), "腕立", StringsJa)

        assertEquals(1, sections.size)
        assertNull("a searched list has no pattern heading", sections.single().pattern)
        assertEquals(listOf("膝つき腕立て", "腕立て伏せ"), sections.single().exercises.map { it.nameJa })
    }

    @Test
    fun `an English query matches the English column, which the fold has always searched`() {
        // `matchExercise` folds both names, so routine and movement search keeps working in either
        // language whichever the UI is in — the fold tables are code and are not translated (§L10).
        val sections = exerciseSections(listOf(run, pullup, pushup, kneePushup), "push", StringsEn)

        assertEquals(
            listOf("Knee push-up", "Push-up"),
            sections.single().exercises.map { it.nameEn },
        )
    }

    @Test
    fun `a blank query is not a search and keeps the headings`() {
        val sections = exerciseSections(listOf(pushup, pullup), "   ", StringsJa)

        assertEquals(listOf(Pattern.H_PUSH, Pattern.V_PULL), sections.map { it.pattern })
    }

    @Test
    fun `a query that matches nothing returns no sections`() {
        assertEquals(emptyList<ExerciseSection>(), exerciseSections(listOf(pushup), "けんすい", StringsJa))
    }

    @Test
    fun `every rung that has been trained carries its own 最高, not the family's`() {
        // The fix's whole point. `exerciseBests()` emits a row per movement, so 膝つき腕立て's forty and
        // 腕立て伏せ's thirty-two land on their own cards — where the ladder-rolled-up read named one
        // row 膝つき腕立て, printed its number there and left 腕立て伏せ with no fragment at all, which
        // §3 edge case 4 defines as "no history".
        val bests = listOf(
            best("knee_pushup", ladderId = "push", singleSetReps = 40),
            best("pushup", ladderId = "push", singleSetReps = 32),
        )

        assertEquals(mapOf("knee_pushup" to 40, "pushup" to 32), bestRepsByExercise(bests))
        // A rung with no row of its own is genuinely untrained and still gets nothing.
        assertNull(bestRepsByExercise(bests)["wall_pushup"])
    }

    @Test
    fun `a row with no recorded set contributes no best`() {
        assertEquals(emptyMap<String, Int>(), bestRepsByExercise(listOf(best("pushup", singleSetReps = 0))))
    }

    // ─── EXERCISE_DETAIL ────────────────────────────────────────────────────────────────────────

    @Test
    fun `the subtitle is the pattern and the difficulty`() {
        assertEquals("押す ・ 難度 一.〇", exerciseDetailSubtitle(pushup, StringsJa))
        assertEquals("移動 ・ 難度 —", exerciseDetailSubtitle(run, StringsJa))
        assertEquals("Push · Difficulty 1.0", exerciseDetailSubtitle(pushup, StringsEn))
        assertEquals("Travel · Difficulty —", exerciseDetailSubtitle(run, StringsEn))
    }

    @Test
    fun `the current rung is the hardest rung that has a row of its own`() {
        // A row exists exactly when that movement has a completed set with actual_reps recorded, so
        // "reached" is membership and "how far" is max difficulty — the same definition GymStore
        // applies to fill hardestReachedExerciseId, restated over rows the page already holds.
        val bests = listOf(
            best("wall_pushup", ladderId = "push", singleSetReps = 20),
            best("one_arm_pushup", ladderId = "push", singleSetReps = 2),
            best("knee_pushup", ladderId = "push", singleSetReps = 40),
        )

        assertEquals(oneArm, currentRung(ladder, bests))
    }

    @Test
    fun `a rung another family recorded is not this ladder's current rung`() {
        // The list is every movement the store has a row for, not this family's; the ladder filters.
        val bests = listOf(best("pullup", singleSetReps = 8), best("knee_pushup", ladderId = "push", singleSetReps = 40))

        assertEquals(kneePushup, currentRung(ladder, bests))
    }

    @Test
    fun `no history marks no rung`() {
        assertNull(currentRung(ladder, emptyList()))
        assertTrue(ladderRungs(ladder, null).none { it.current })
        assertTrue(ladderRungs(ladder, null).none { it.reached })
    }

    @Test
    fun `edge case 2 - rungs at or below the current one are reached and harder ones are not`() {
        val rungs = ladderRungs(ladder, pushup).associateBy { it.exercise.nameJa }

        assertTrue(rungs.getValue("壁腕立て").reached)
        assertTrue(rungs.getValue("膝つき腕立て").reached)
        assertTrue(rungs.getValue("腕立て伏せ").reached)
        assertTrue("a harder rung is not climbed", !rungs.getValue("片手腕立て").reached)
        assertTrue(rungs.getValue("腕立て伏せ").current)
        assertTrue("exactly one rung is current", ladderRungs(ladder, pushup).count { it.current } == 1)
    }

    @Test
    fun `edge case 3 - an easier rung stays reached, so a warm-up does not un-climb the ladder`() {
        // The family's hardest reached is 片手腕立て; the user is on the page for 壁腕立て. Everything
        // below 片手腕立て is still climbed.
        val rungs = ladderRungs(ladder, oneArm)

        assertTrue(rungs.all { it.reached })
        assertEquals("片手腕立て", rungs.single { it.current }.exercise.nameJa)
    }

    @Test
    fun `a rung reads as いまここ or まだ, and nothing else`() {
        val rungs = ladderRungs(ladder, pushup)

        assertEquals("腕立て伏せ、難度 一.〇、いまここ", rungSemantics(rungs.single { it.current }, StringsJa))
        assertEquals("壁腕立て、難度 〇.二、まだ", rungSemantics(rungs.first(), StringsJa))
        // A climbed-past rung is まだ too: there is no third documented word.
        assertEquals("膝つき腕立て、難度 〇.五、まだ", rungSemantics(rungs[1], StringsJa))
        // Still exactly two forms in English, and still no third word for "climbed past".
        assertEquals(
            "Push-up, Difficulty 1.0, you are here",
            rungSemantics(rungs.single { it.current }, StringsEn),
        )
        assertEquals("Wall push-up, Difficulty 0.2, not yet", rungSemantics(rungs.first(), StringsEn))
    }

    @Test
    fun `the three tiles are 一度に, のべ回数 and 最後, in that order`() {
        val row = best(
            "pushup", ladderId = "push", singleSetReps = 32, lifetimeReps = 400,
            lastDate = LocalDate.of(2026, 6, 10),
        )

        val tiles = movementTiles(row, StringsJa)!!
        assertEquals(listOf("一度に", "のべ回数", "最後"), tiles.map { it.label })
        assertEquals(listOf("三十二回", "四百回", "六月十日"), tiles.map { it.value })

        val english = movementTiles(row, StringsEn)!!
        assertEquals(listOf("In one set", "Lifetime", "Last"), english.map { it.label })
        assertEquals(listOf("32 reps", "400 reps", "10 June"), english.map { it.value })
    }

    @Test
    fun `no history means no tiles at all, which is what まだ やっていません is drawn from`() {
        assertNull(movementTiles(null, StringsJa))
    }

    @Test
    fun `edge case 5 - のべ回数 falls back to arabic above 9999`() {
        val tiles = movementTiles(best("pushup", singleSetReps = 32, lifetimeReps = 12_500), StringsJa)!!

        assertEquals(
            "九千九百九十九回",
            movementTiles(best("p", singleSetReps = 1, lifetimeReps = 9_999), StringsJa)!![1].value,
        )
        assertEquals("12500回", tiles[1].value)
    }

    @Test
    fun `a missing column renders a dash rather than a zero or a fabricated day`() {
        val row = best("pushup", singleSetReps = 0, lifetimeReps = 0, lastDate = null)

        assertEquals(listOf("—", "—", "—"), movementTiles(row, StringsJa)!!.map { it.value })
        // The em dash is not Japanese and is not copy (§L10); it is the same character in both.
        assertEquals(listOf("—", "—", "—"), movementTiles(row, StringsEn)!!.map { it.value })
    }

    @Test
    fun `the tiles take the exercise's own row while the ladder still reads the whole family`() {
        val bests = listOf(
            best("knee_pushup", ladderId = "push", singleSetReps = 40, lifetimeReps = 600),
            best("pushup", ladderId = "push", singleSetReps = 32, lifetimeReps = 120),
        )

        // The shipped failure: 朝の五分 often, 七分間 rarely. 腕立て伏せ used to render まだ やっていません
        // because the family's row was named 膝つき腕立て; it must now render its own two numbers…
        assertEquals(
            listOf("三十二回", "百二十回"),
            movementTiles(exerciseBestFor(pushup, bests), StringsJa)!!.take(2).map { it.value },
        )
        // …and 膝つき腕立て must render its own, never the family's 七百二十.
        assertEquals(
            listOf("四十回", "六百回"),
            movementTiles(exerciseBestFor(kneePushup, bests), StringsJa)!!.take(2).map { it.value },
        )
        // 壁腕立て genuinely has no record, so まだ やっていません is true rather than a false silence.
        assertNull(exerciseBestFor(wallPushup, bests))
        // The 段階 block beside all three still knows how far the family has climbed.
        assertEquals(pushup, currentRung(ladder, bests))
    }

    @Test
    fun `a movement with no ladder is answered by its own row`() {
        val bests = listOf(best("plank", ladderId = null, singleSetReps = 1))

        assertEquals("plank", exerciseBestFor(plank, bests)?.exerciseId)
        assertNull(exerciseBestFor(pushup, bests))
    }

    @Test
    fun `使われている型 lists only the routines whose stations contain the movement`() {
        val seven = summary("r_seven", 1L, "七分間")
        val cindy = summary("r_cindy", 2L, "シンディ")
        val stations = mapOf(1L to listOf("pushup", "situp"), 2L to listOf("pullup", "squat"))

        assertEquals(listOf(seven), routinesUsing(listOf(seven, cindy), stations, "pushup"))
        assertEquals(listOf(cindy), routinesUsing(listOf(seven, cindy), stations, "squat"))
        assertEquals(emptyList<RoutineSummary>(), routinesUsing(listOf(seven, cindy), stations, "burpee"))
    }

    @Test
    fun `a routine whose stations could not be read is never counted as not using the movement`() {
        // The map is the caller's completeness contract; this pins that a missing entry excludes the
        // routine rather than silently asserting it does not use the exercise. The caller must not
        // reach `Ready` with a hole — `rememberRoutinesUsing` reports Failed instead.
        val seven = summary("r_seven", 1L, "七分間")

        assertEquals(emptyList<RoutineSummary>(), routinesUsing(listOf(seven), emptyMap(), "pushup"))
    }

    @Test
    fun `the used-by count is kanji and carries 件`() {
        assertEquals("四件", usedByCount(4, StringsJa))
        assertEquals("一件", usedByCount(1, StringsJa))
        // 件 is the generic counter and `fmt.items` is where the migration routes it. Reported: the
        // thing counted here is routines, and a `routines(n)` formatter would read better than "items".
        assertEquals("4 items", usedByCount(4, StringsEn))
        assertEquals("1 item", usedByCount(1, StringsEn))
    }

    @Test
    fun `the three notices keep their sentences, which the structure test only counts`() {
        // `ExerciseScreenStructureTest` pins how many branches can reach each notice; the sentences
        // themselves live here, so a wrong word in the table is a failure with a diff rather than a
        // page that still draws exactly one of the wrong thing.
        assertEquals("読み込み中", StringsJa.gymExercise.loading)
        assertEquals("まだ やっていません", StringsJa.gymExercise.noHistory)
        assertEquals("どの型にも入っていません", StringsJa.gymExercise.noRoutines)
        assertEquals("該当する種目はありません", StringsJa.gymExercise.noMatch)
    }
}
