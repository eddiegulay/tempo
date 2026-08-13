package io.eddiegulay.tempo.ui.gym

import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.EngineRowKind
import io.eddiegulay.tempo.gym.GymPreferences
import io.eddiegulay.tempo.gym.GymRoute
import io.eddiegulay.tempo.gym.Measure
import io.eddiegulay.tempo.gym.Pattern
import io.eddiegulay.tempo.gym.RestSlot
import io.eddiegulay.tempo.gym.RoutineDraft
import io.eddiegulay.tempo.gym.StationDraft
import io.eddiegulay.tempo.gym.WheelOption
import io.eddiegulay.tempo.gym.engineRows
import io.eddiegulay.tempo.gym.repOptions
import io.eddiegulay.tempo.gym.repWheelValueLabel
import io.eddiegulay.tempo.gym.restOptions
import io.eddiegulay.tempo.gym.restWheelValueLabel
import io.eddiegulay.tempo.gym.roundOptions
import io.eddiegulay.tempo.gym.roundWheelValueLabel
import io.eddiegulay.tempo.gym.secondWheelValueLabel
import io.eddiegulay.tempo.i18n.Strings
import io.eddiegulay.tempo.i18n.StringsEn
import io.eddiegulay.tempo.i18n.StringsJa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `GYM.LIBRARY.BUILDER`'s decisions, made where a JVM test can reach them.
 *
 * The page is deliberately thin over `gym/BuilderDraft.kt` — `isDirty`, `canSave`, `reorderShift`,
 * `moveItem` and `migrateDraft` are all tested next door and are not re-tested here. What is left is
 * this file's own: the copy a row carries, the arithmetic a drag needs, and where 保存 lands when the
 * store hands back an id the page did not open with.
 */
class BuilderScreenTest {

    private val prefs = GymPreferences(prepareSeconds = 7, defaultStationRest = 20, defaultRoundRest = 90)

    private fun draft(
        stations: List<StationDraft> = emptyList(),
        engine: Engine = Engine.INTERVAL_CIRCUIT,
        rounds: Int? = 1,
        name: String = "朝の五分",
    ) = RoutineDraft(
        routineId = "r_user",
        name = name,
        engine = engine,
        stations = stations,
        rounds = rounds,
        timeCapSeconds = null,
        intervalSeconds = null,
        restBetweenStations = 15,
        restBetweenRounds = 60,
        prepareSeconds = 5,
    )

    private fun station(id: String, measure: Measure = Measure.REPS, reps: Int? = 20, seconds: Int? = null) =
        StationDraft(id, measure, reps, seconds)

    // ─── The header ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the title says whether a routine exists yet`() {
        assertEquals("型を作る", builderTitle(null, StringsJa))
        assertEquals("型を編集", builderTitle("r_cindy", StringsJa))
    }

    @Test
    fun `a disabled save says what it is waiting for`() {
        // §3: 「保存 disabled → "保存、名前と種目が要ります", never an unexplained inert word.」
        assertEquals("保存", saveSemantics(canSave = true, strings = StringsJa))
        assertEquals("保存、名前と種目が要ります", saveSemantics(canSave = false, strings = StringsJa))
    }

    @Test
    fun `a save in flight says 保存中, never a reason that is untrue`() {
        // `canSave` carries the `saving` flag, so it is false for the whole `Saving` state — and the
        // name and the stations are both present, which is *why* the write is running. §3's rule is
        // that a disabled word says why; 「名前と種目が要ります」 here says the wrong why.
        assertEquals("保存中", saveDescription(saving = true, canSave = false, strings = StringsJa))
        assertEquals("保存中", saveDescription(saving = true, canSave = true, strings = StringsJa))
        assertEquals("保存", saveDescription(saving = false, canSave = true, strings = StringsJa))
        assertEquals(
            "保存、名前と種目が要ります",
            saveDescription(saving = false, canSave = false, strings = StringsJa),
        )
    }

    @Test
    fun `a read failure takes the page only before the routine has arrived`() {
        // A routine that could not be read is not a routine with no stations, so an unloaded edit is
        // never a builder. But `routineDetail` re-emits on any table change, and a later transient
        // failure must not replace a draft full of unsaved work with a panel whose only action is
        // もう一度 — §3's `Error` state puts that fault above the fields, never over them.
        assertTrue(builderFaultPanelOwed(readFailed = true, routineLoaded = false))
        assertFalse(builderFaultPanelOwed(readFailed = true, routineLoaded = true))
        assertFalse(builderFaultPanelOwed(readFailed = false, routineLoaded = false))
    }

    // ─── A station row ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `a station reads as the value it prescribes`() {
        assertEquals("二十回", stationValueLabel(station("e_pushup"), StringsJa))
        assertEquals("三十秒", stationValueLabel(station("e_plank", Measure.DURATION, null, 30), StringsJa))
        assertEquals("限界まで", stationValueLabel(station("e_run", Measure.MAX_EFFORT, null, null), StringsJa))
    }

    @Test
    fun `a rest renders as bare seconds and never through durationKanji`() {
        // `DECISIONS.md` §Q10 pins the wheel, the builder row and the detail page to one form. Sixty
        // seconds is 六十秒 in all three; 一分 here would be the user reading back a value they never
        // picked. The station value is the same act and takes the same rule.
        assertEquals("六十秒", stationValueLabel(station("e_plank", Measure.DURATION, null, 60), StringsJa))
        assertEquals("九十秒", stationValueLabel(station("e_plank", Measure.DURATION, null, 90), StringsJa))
    }

    @Test
    fun `a prescription with no number says nothing rather than zero`() {
        // The schema's CHECK refuses this row, so it is a store bug rather than a prescription — and
        // 〇回 would be a prescription.
        assertEquals("", stationValueLabel(station("e_pushup", Measure.REPS, null, null), StringsJa))
    }

    @Test
    fun `a row announces its position, its movement and its prescription`() {
        assertEquals("一番目、腕立て伏せ、二十回", stationSemantics(0, "腕立て伏せ", "二十回", StringsJa))
        assertEquals("三番目、懸垂、限界まで", stationSemantics(2, "懸垂", "限界まで", StringsJa))
    }

    @Test
    fun `a row with no prescription still announces cleanly`() {
        assertEquals("一番目、腕立て伏せ", stationSemantics(0, "腕立て伏せ", "", StringsJa))
    }

    @Test
    fun `the handle and the drop announce themselves`() {
        assertEquals("腕立て伏せ の並べ替え", handleSemantics("腕立て伏せ", StringsJa))
        assertEquals("三番目に移動しました", moveAnnouncement(2, StringsJa))
    }

    // ─── The lines under the fields ─────────────────────────────────────────────────────────────

    @Test
    fun `the history line appears only when there is history and the structure changed`() {
        assertEquals(
            "これまでの六回の記録はそのまま残ります",
            historySafeLine(sessions = 6, structureDirty = true, strings = StringsJa),
        )
        // A typo corrected in the title writes no new version, so nothing is owed about records.
        assertNull(historySafeLine(sessions = 6, structureDirty = false, strings = StringsJa))
        assertNull(historySafeLine(sessions = 0, structureDirty = true, strings = StringsJa))
    }

    @Test
    fun `a duplicate name warns, and a routine never collides with itself`() {
        val library = listOf(summary("r_a", "朝の五分"), summary("r_b", "夜の十分"))

        assertTrue(duplicateName("朝の五分", routineId = "r_new", library = library))
        assertFalse(duplicateName("朝の五分", routineId = "r_a", library = library))
        assertFalse(duplicateName("昼の五分", routineId = null, library = library))
        // Blank cannot save at all, and 保存 already says why.
        assertFalse(duplicateName("   ", routineId = null, library = library))
        // The name is trimmed before it is compared, as it is before it is saved.
        assertTrue(duplicateName("  朝の五分 ", routineId = "r_new", library = library))
    }

    // ─── The drag ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a drag drops on the row it has travelled past`() {
        // Half a row is the tipping point: less and the row has not moved, more and it has.
        assertEquals(0, dropIndex(from = 0, offsetPx = 20f, rowPx = 56f, size = 4))
        assertEquals(1, dropIndex(from = 0, offsetPx = 30f, rowPx = 56f, size = 4))
        assertEquals(2, dropIndex(from = 0, offsetPx = 112f, rowPx = 56f, size = 4))
        assertEquals(1, dropIndex(from = 3, offsetPx = -112f, rowPx = 56f, size = 4))
    }

    @Test
    fun `a drag off either end drops at the end`() {
        // `moveItem` answers an out-of-range drop by doing nothing at all, which reads as the gesture
        // being ignored; clamping means a fling always lands somewhere.
        assertEquals(3, dropIndex(from = 0, offsetPx = 4000f, rowPx = 56f, size = 4))
        assertEquals(0, dropIndex(from = 3, offsetPx = -4000f, rowPx = 56f, size = 4))
    }

    @Test
    fun `a drag on an unmeasured list stays where it is`() {
        assertEquals(2, dropIndex(from = 2, offsetPx = 500f, rowPx = 0f, size = 4))
        assertEquals(2, dropIndex(from = 2, offsetPx = 500f, rowPx = 56f, size = 0))
    }

    @Test
    fun `the page scrolls only while the row is against an edge`() {
        // §3 edge case 1: within 48.dp of either viewport edge, at 6.dp per frame.
        assertEquals(-6f, autoScrollStep(100f, 80f, 900f, 48f, 6f), 0f)
        assertEquals(6f, autoScrollStep(880f, 80f, 900f, 48f, 6f), 0f)
        assertEquals(0f, autoScrollStep(500f, 80f, 900f, 48f, 6f), 0f)
    }

    // ─── Warnings ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a clash line is keyed to the station that starts tired`() {
        val warnings = stationWarnings(
            draft = draft(listOf(station("push_a"), station("push_b"), station("squat"))),
            pattern = { id -> if (id.startsWith("push")) Pattern.H_PUSH else Pattern.SQUAT },
            name = { id -> id },
            StringsJa,
        )

        assertEquals(mapOf(1 to "push_a と push_b は続けて置かない方がよい"), warnings)
    }

    @Test
    fun `the round-wrap clash is keyed to the first row`() {
        // `adjacentPatternClashes` returns the wrap pair as (last, 0) in loop order, and §3 insets the
        // line under the second of the pair — which for the wrap is the station the routine comes back
        // around to. Three stations and two rounds, so the list genuinely repeats.
        val warnings = stationWarnings(
            draft = draft(listOf(station("push_a"), station("squat"), station("push_b")), rounds = 2),
            pattern = { id -> if (id.startsWith("push")) Pattern.H_PUSH else Pattern.SQUAT },
            name = { id -> id },
            StringsJa,
        )

        assertEquals(mapOf(0 to "push_b と push_a は続けて置かない方がよい"), warnings)
    }

    @Test
    fun `a reorder announces the move and the clash it created, in one node`() {
        // §3 requires a clash to announce *itself* when the user reorders into one. `WarningLine` is
        // composed only when the map has an entry, so on every reorder it is *born* carrying its text
        // — and `MoveAnnouncer`'s own KDoc states the rule: Compose emits nothing for a semantics node
        // that did not exist on the previous pass. The always-alive announcer carries it instead,
        // joined with `stationSemantics`' own 、 so no new copy is invented.
        assertEquals(
            "二番目に移動しました、腕立て伏せ と ディップス は続けて置かない方がよい",
            moveAnnouncementWith(1, "腕立て伏せ と ディップス は続けて置かない方がよい", StringsJa),
        )
        // A reorder into clean order says only that it moved.
        assertEquals("三番目に移動しました", moveAnnouncementWith(2, null, StringsJa))
    }

    @Test
    fun `the clash a move creates is read off the order the move produced`() {
        // The announcer speaks once per gesture and must speak about the order the user just made, not
        // the one they left. Dragging the second push up beside the first is the exact gesture §3 has
        // in mind, and the line is keyed to the second of the pair — the station that starts tired.
        val before = draft(listOf(station("push_a"), station("squat"), station("push_b")))
        val pattern: (String) -> Pattern? = { id -> if (id.startsWith("push")) Pattern.H_PUSH else Pattern.SQUAT }

        // Nothing to say about the order as it stands.
        assertTrue(stationWarnings(before, pattern, { it }, StringsJa).isEmpty())

        val target = 1
        val after = before.copy(stations = io.eddiegulay.tempo.gym.moveItem(before.stations, 2, target))
        val clash = stationWarnings(after, pattern, { it }, StringsJa)[target]

        assertEquals("push_a と push_b は続けて置かない方がよい", clash)
        assertEquals(
            "二番目に移動しました、push_a と push_b は続けて置かない方がよい",
            moveAnnouncementWith(target, clash, StringsJa),
        )
    }

    @Test
    fun `a movement the catalogue cannot name never clashes`() {
        val warnings = stationWarnings(
            draft = draft(listOf(station("gone_a"), station("gone_b"))),
            pattern = { null },
            name = { unknownExercise(StringsJa) },
            StringsJa,
        )

        assertTrue(warnings.isEmpty())
    }

    // ─── The wheels ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a wheel opened on a value outside its range still contains that value`() {
        // `DECISIONS.md` §Q21. `TempoValueWheel` resolves an absent value with
        // `values.indexOf(selected).coerceAtLeast(0)` — it silently seeds row 0 — so a wheel that
        // cannot show what it is editing rewrites it the moment the row unfolds. All three numbers
        // below are shipped seed data, not hypotheticals.

        // チェルシー is 30 rounds; 巡数 offers 1..20.
        val rounds = mergedWheelOptions(roundOptions(StringsJa), 30) { roundWheelValueLabel(it, StringsJa) }
        assertEquals(WheelOption(30, "三十巡"), rounds.last())
        assertEquals(roundOptions(StringsJa).size + 1, rounds.size)

        // タバタ rests 10 between rounds; 巡の間の休息 steps by 15, so 10 lands between なし and 十五秒.
        val roundRest = mergedWheelOptions(restOptions(RestSlot.BETWEEN_ROUNDS, StringsJa), 10) {
            restWheelValueLabel(it, StringsJa)
        }
        assertEquals(listOf(WheelOption(0, "なし"), WheelOption(10, "十秒"), WheelOption(15, "十五秒")), roundRest.take(3))

        // マーフ has stations at 200 and 300 reps; 回数 offers 1..100.
        val reps = mergedWheelOptions(repOptions(StringsJa), 300) { repWheelValueLabel(it, StringsJa) }
        assertEquals(WheelOption(300, "三百回"), reps.last())

        // And the point of all three: the wheel can now seed itself on the value it was opened with.
        for (merged in listOf(rounds to 30, roundRest to 10, reps to 300)) {
            assertTrue(merged.first.map { it.value }.indexOf(merged.second) > 0)
        }
    }

    @Test
    fun `a wheel that already holds its value is left exactly as it was`() {
        // The merge is a repair, not a transform: widening every range to cover every seedable number
        // would give every user a three-hundred-row 回数 wheel to pay for three catalogue stations.
        assertEquals(
            roundOptions(StringsJa),
            mergedWheelOptions(roundOptions(StringsJa), 12) { roundWheelValueLabel(it, StringsJa) },
        )
        assertEquals(
            repOptions(StringsJa),
            mergedWheelOptions(repOptions(StringsJa), 20) { repWheelValueLabel(it, StringsJa) },
        )
        assertEquals(
            restOptions(RestSlot.BETWEEN_STATIONS, StringsJa),
            mergedWheelOptions(restOptions(RestSlot.BETWEEN_STATIONS, StringsJa), 0) {
                restWheelValueLabel(it, StringsJa)
            },
        )
    }

    @Test
    fun `the merged row is labelled in its own wheel's words`() {
        // §Q10: a duration the user chose renders as the value they chose, なし at zero. A merged row
        // that reached for `durationKanji` would print 一分 into a column of 六十秒.
        assertEquals("なし", restWheelValueLabel(0, StringsJa))
        assertEquals("六十秒", restWheelValueLabel(60, StringsJa))
        assertEquals("九十秒", secondWheelValueLabel(90, StringsJa))
        assertEquals("二百回", repWheelValueLabel(200, StringsJa))
        assertEquals("三十巡", roundWheelValueLabel(30, StringsJa))
    }

    // ─── The row dispatch ───────────────────────────────────────────────────────────────────────

    @Test
    fun `every dialable row resolves to its wheel in both languages`() {
        // **The test the old code could not have carried.** The dispatch used to be
        // `when (row.label)` against three Japanese `private const val`s, and it was correct only
        // because `engineRows` happened to spell the same three words. In English every arm would have
        // missed and fallen through to the read-only branch: the station rest, the round rest and 巡数
        // silently undialable, with no compile error, no exception and nothing on screen to say the
        // page was broken rather than simply that shape. Asserting in one language cannot see that, so
        // this asserts in both.
        for (strings in listOf<Strings>(StringsJa, StringsEn)) {
            val rows = engineRows(
                engine = Engine.INTERVAL_CIRCUIT,
                rounds = 3,
                timeCapSeconds = null,
                restBetweenStations = 15,
                restBetweenRounds = 60,
                strings = strings,
            )

            assertEquals(
                "${strings.lang}: 巡回 draws the two rests and 巡数, and all three must be wheels",
                mapOf(
                    EngineRowKind.REST_BETWEEN_STATIONS to BuilderRow.StationRest,
                    EngineRowKind.REST_BETWEEN_ROUNDS to BuilderRow.RoundRest,
                    EngineRowKind.ROUNDS to BuilderRow.Rounds,
                ),
                rows.associate { it.kind to builderRowControl(it.kind, Engine.INTERVAL_CIRCUIT) },
            )
        }
    }

    @Test
    fun `the row labels differ by language, which is why they cannot be the dispatch`() {
        // The other half of the argument, and the half that keeps the test above honest: if the labels
        // were the same in both languages the assertion above would pass under a string match too.
        val ja = engineRows(Engine.INTERVAL_CIRCUIT, 3, null, 15, 60, StringsJa).map { it.label }
        val en = engineRows(Engine.INTERVAL_CIRCUIT, 3, null, 15, 60, StringsEn).map { it.label }

        assertEquals(listOf("種目の間の休息", "巡の間の休息", "巡数"), ja)
        assertNotEquals(ja, en)
    }

    @Test
    fun `時間内 states its rounds and its cap rather than dialling them`() {
        // 時間内で is not a value on a wheel, and §3 edge case 11 gives no range for a 制限時間 — so
        // both rows are read-only, in either language, for the same reason and not by falling through.
        assertNull(builderRowControl(EngineRowKind.ROUNDS, Engine.AMRAP))
        assertNull(builderRowControl(EngineRowKind.TIME_CAP, Engine.AMRAP))
        // …and 巡数 is a wheel everywhere else it appears.
        for (engine in Engine.entries.filter { it != Engine.AMRAP }) {
            assertEquals(engine.name, BuilderRow.Rounds, builderRowControl(EngineRowKind.ROUNDS, engine))
        }
    }

    // ─── Opening and landing ────────────────────────────────────────────────────────────────────

    @Test
    fun `a new routine opens on the user's own defaults`() {
        // `setDefaultStationRest`'s contract is "a default for routines authored from now on"; reading
        // the preference here is what makes that sentence true.
        val fresh = newDraft(prefs)

        assertNull(fresh.routineId)
        assertEquals("", fresh.name)
        assertEquals(Engine.INTERVAL_CIRCUIT, fresh.engine)
        assertEquals(1, fresh.rounds)
        assertEquals(20, fresh.restBetweenStations)
        assertEquals(90, fresh.restBetweenRounds)
        assertEquals(7, fresh.prepareSeconds)
        assertTrue(fresh.stations.isEmpty())
    }

    @Test
    fun `saving adopts the id the store handed back`() {
        // Editing a built-in is a copy-on-write into a new user routine (`02-data.md` §A.0.3), so the
        // detail underneath is showing the routine the user did *not* just edit. Landing on it would
        // make their work look lost, and their next save would write to the original again.
        assertEquals(
            GymRoute.RoutineDetail("r_copy"),
            builderLanding(GymRoute.RoutineDetail("r_sevenmin"), savedRoutineId = "r_copy"),
        )
    }

    @Test
    fun `an unchanged id and an index below are left to the ordinary pop`() {
        assertNull(builderLanding(GymRoute.RoutineDetail("r_user"), savedRoutineId = "r_user"))
        assertNull(builderLanding(GymRoute.Library, savedRoutineId = "r_user"))
        assertNull(builderLanding(GymRoute.Home, savedRoutineId = "r_user"))
        assertNull(builderLanding(null, savedRoutineId = "r_user"))
    }

    @Test
    fun `the draft survives the picker and nothing else`() {
        // The clearing rule, in one predicate: pushing the picker disposes the builder and must not
        // drop the draft; 保存, やめる, a confirmed discard and a HOME press all leave neither page.
        assertTrue(builderDraftSurvives(listOf(GymRoute.Library, GymRoute.Builder("r_a"))))
        assertTrue(
            builderDraftSurvives(
                listOf(GymRoute.Library, GymRoute.Builder("r_a"), GymRoute.StationPicker(2)),
            ),
        )
        assertFalse(builderDraftSurvives(listOf(GymRoute.Library, GymRoute.RoutineDetail("r_a"))))
        assertFalse(builderDraftSurvives(listOf(GymRoute.Home)))
    }

    @Test
    fun `every engine the builder offers has a label, and バーバラ's is among them`() {
        // §3's mock draws six chips; §6 :1117 gives `FOR_TIME_WITH_REST` a label and Phase 2 seeds
        // バーバラ with it. Omitting it would make a shipped built-in unbuildable in the builder — the
        // argument `allowedMeasures` already makes for keeping 限界まで on 完走.
        assertEquals(Engine.entries.toSet(), ENGINE_CHOICES.toSet())
        assertEquals(Engine.INTERVAL_CIRCUIT, ENGINE_CHOICES.first())
        assertTrue(ENGINE_CHOICES.indexOf(Engine.FOR_TIME) < ENGINE_CHOICES.indexOf(Engine.FOR_TIME_WITH_REST))
    }

    private fun summary(id: String, name: String) = io.eddiegulay.tempo.gym.RoutineSummary(
        routineId = id,
        versionId = 1L,
        name = name,
        engine = Engine.INTERVAL_CIRCUIT,
        builtIn = false,
        tier = null,
        origin = null,
        favourite = false,
        sortOrder = 0,
        rounds = 1,
        timeCapSeconds = null,
        intervalSeconds = null,
        restBetweenStations = 15,
        restBetweenRounds = 60,
        stationCount = 3,
        estimatedDurationSeconds = 300,
        estimatedTotalReps = 60,
        primaryMetric = io.eddiegulay.tempo.gym.BestMetric.MOST_VOLUME,
        timesDone = 0,
        lastStartedAt = null,
        lastActiveMs = null,
        best = null,
        archivedAt = null,
    )
}
