package io.eddiegulay.tempo.ui.gym

import io.eddiegulay.tempo.i18n.StringsJa
import io.eddiegulay.tempo.calendar.Loadable
import io.eddiegulay.tempo.data.GymFault
import io.eddiegulay.tempo.gym.AdvanceRule
import io.eddiegulay.tempo.gym.BestMetric
import io.eddiegulay.tempo.gym.BestTile
import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.Exercise
import io.eddiegulay.tempo.gym.GymRoute
import io.eddiegulay.tempo.gym.Measure
import io.eddiegulay.tempo.gym.Pattern
import io.eddiegulay.tempo.gym.ProgressionSet
import io.eddiegulay.tempo.gym.ProgressionState
import io.eddiegulay.tempo.gym.ProgressionStep
import io.eddiegulay.tempo.gym.RoutineBest
import io.eddiegulay.tempo.gym.RoutineDetail
import io.eddiegulay.tempo.gym.RoutineSnapshot
import io.eddiegulay.tempo.gym.RoutineStation
import io.eddiegulay.tempo.gym.RoutineSummary
import io.eddiegulay.tempo.gym.SessionSummary
import io.eddiegulay.tempo.gym.StartBlock
import io.eddiegulay.tempo.gym.StepShape
import io.eddiegulay.tempo.gym.StepUnit
import io.eddiegulay.tempo.gym.Tier
import io.eddiegulay.tempo.gym.estimateLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * What `GYM.LIBRARY.DETAIL` *says*, pinned away from the composables that draw it.
 *
 * The page is read rather than tapped, so its failure mode is a sentence that is subtly wrong — a
 * rest echoed back in a unit the user never chose, a delete confirm that promises the records survive
 * when they are about to be purged, a 〇巡 on a circuit that counts no rounds. None of those show up
 * in a screenshot, and every one of them is a plain function here.
 */
class LibraryDetailCopyTest {

    // ─── 削除, which branches on what is at stake ────────────────────────────────────────────────

    @Test
    fun `deleting a routine with history promises the records survive`() {
        // `04-library-records.md` §1 rule 2: this is an archive. The sentence has to say so, because
        // the button says 削除 in both branches and the user cannot see the blast radius.
        val copy = detailDeleteCopy("シンディ", Loadable.Ready(6)) as DetailDeleteState.Confirm
        assertEquals("「シンディ」を削除しますか", copy.title)
        assertEquals("これまでの六回の記録は残ります。型だけが一覧から消えます。", copy.body)
        assertEquals("削除", copy.confirm)
        assertEquals("やめる", copy.cancel)
        assertFalse(copy.purge)
    }

    @Test
    fun `deleting a never-performed routine says it goes completely`() {
        // §1 rule 4: 完全に削除 is offered only at zero, where nothing readable is destroyed.
        val copy = detailDeleteCopy("朝の型", Loadable.Ready(0)) as DetailDeleteState.Confirm
        assertEquals("やった記録はありません。完全に消えます。", copy.body)
        assertEquals("完全に削除", copy.confirm)
        assertTrue(copy.purge)
    }

    @Test
    fun `the count in the confirm is kanji past ninety-nine`() {
        // `kanjiExtended`, not `kanji` — a routine done a hundred and twelve times must not read 〇回
        // or fall back to arabic mid-sentence.
        val copy = detailDeleteCopy("七分間", Loadable.Ready(112)) as DetailDeleteState.Confirm
        assertTrue(copy.body.startsWith("これまでの百十二回"))
    }

    @Test
    fun `an unread count is never the ありません branch`() {
        // THE blocker. A zero-seeded `Int` made 「やった記録はありません。完全に消えます。」 the first
        // frame of every confirm and the permanent one over an unreadable store — an
        // ありません-as-emptiness claim on the only irreversible dialog in the feature
        // (`00-plan.md` §4.1 rule 1, `DECISIONS.md` §Q6).
        val waiting = detailDeleteCopy("シンディ", Loadable.Loading)
        val unreadable = detailDeleteCopy("シンディ", Loadable.Failed(GymFault.StoreCorrupt))
        listOf(waiting, unreadable).forEach { state ->
            assertFalse(state is DetailDeleteState.Confirm)
            assertEquals("「シンディ」を削除しますか", state.title)
            assertEquals("やめる", state.cancel)
        }
    }

    @Test
    fun `an unread count offers no destructive button at all, not even the archive one`() {
        // Not merely "no 完全に削除": 削除 with an unread count would still have to claim
        // 「これまでの〇回の記録は残ります」, and there is no count to put in it.
        val waiting = detailDeleteCopy("シンディ", Loadable.Loading) as DetailDeleteState.Waiting
        assertEquals("読み込み中", waiting.body)
    }

    @Test
    fun `an unreadable count carries its fault so the dialog can say 記録を読めません`() {
        // The strip renders `faultCopy`, which is 記録を読めません ・ もう一度 — a sentence that cannot be
        // misread as 記録はありません (`DECISIONS.md` §Q6). Silence here would be the same lie as zero.
        val state = detailDeleteCopy("シンディ", Loadable.Failed(GymFault.StoreCorrupt))
        assertEquals(GymFault.StoreCorrupt, (state as DetailDeleteState.Unreadable).fault)
    }

    @Test
    fun `the write follows the words that were shown, not a second reading of the count`() {
        // `purge` travels with the copy so a count resolving between the sentence and the tap cannot
        // turn 「記録は残ります」 into a purge.
        assertTrue((detailDeleteCopy("x", Loadable.Ready(0)) as DetailDeleteState.Confirm).purge)
        assertFalse((detailDeleteCopy("x", Loadable.Ready(1)) as DetailDeleteState.Confirm).purge)
    }

    // ─── A prescription is a value somebody chose ───────────────────────────────────────────────

    @Test
    fun `a rest-free seconds prescription renders as the seconds it was set to`() {
        // `DECISIONS.md` §Q10: sixty seconds set on a wheel reading 六十秒 must not read back 一分.
        assertEquals("六十秒", prescriptionLabel(station(Measure.DURATION, seconds = 60)))
        assertEquals("三十秒", prescriptionLabel(station(Measure.DURATION, seconds = 30)))
    }

    @Test
    fun `reps and max effort each say what the station asks for`() {
        assertEquals("十五回", prescriptionLabel(station(Measure.REPS, reps = 15)))
        assertEquals("限界まで", prescriptionLabel(station(Measure.MAX_EFFORT)))
    }

    @Test
    fun `a prescription with no value is a dash, never a zero`() {
        // The CHECK constraint should have refused this row. 〇回 would be a claim about the routine;
        // a dash is what a missing value looks like everywhere else in 鍛錬.
        assertEquals("—", prescriptionLabel(station(Measure.REPS, reps = null)))
    }

    @Test
    fun `a station announces its position first`() {
        assertEquals("一番目、懸垂、五回", stationRowSemantics(1, "懸垂", "五回"))
        assertEquals("十二番目、プランク、三十秒", stationRowSemantics(12, "プランク", "三十秒"))
    }

    // ─── Why 始める cannot be pressed ────────────────────────────────────────────────────────────

    @Test
    fun `an unknown exercise explains itself under the button`() {
        assertEquals("種目が見つからないため 始められません", startBlockCopy(StartBlock.UnknownExercise))
    }

    @Test
    fun `an archived routine says nothing twice`() {
        // The 削除済み chip beside the title has already said it. Repeating it under the button would
        // be the page telling the user the same fact in two places.
        assertNull(startBlockCopy(StartBlock.Archived))
        assertNull(startBlockCopy(StartBlock.None))
    }

    // ─── これまで ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `an attempt row carries the month, because this list has no month header`() {
        // Deliberately not `sessionRowLines`, which prints 十七日 under a 六月 header the detail page
        // does not draw.
        val line = attemptLine(summary(rounds = 17, activeMs = 20 * 60_000L))
        assertEquals("六月十七日", line.date)
        assertEquals("十七巡", line.score)
        assertEquals("二十分", line.duration)
    }

    @Test
    fun `a circuit that counts no rounds omits its score rather than printing zero`() {
        val line = attemptLine(summary(rounds = 0, reps = 0))
        assertNull(line.score)
    }

    @Test
    fun `a session that counted reps but not rounds reports the reps`() {
        assertEquals("三百四回", attemptLine(summary(rounds = 0, reps = 304)).score)
    }

    @Test
    fun `a partial attempt carries 途中まで and never a record chip`() {
        // `00-plan.md` §4.1 rule 2, and §4 edge case 1: a PR requires complete = 1. Even a row the
        // store marked personalBest must not wear the crown when it ended early.
        val line = attemptLine(summary(complete = false, personalBest = true, stationsPlanned = 20, stationsDone = 8))
        assertTrue(line.partial)
        assertEquals("途中まで ・ 二十種目中 八", line.chip)
    }

    @Test
    fun `a complete record-holding attempt wears the standing chip`() {
        val line = attemptLine(summary(personalBest = true))
        assertFalse(line.partial)
        assertEquals("自己最高", line.chip)
    }

    // ─── The estimate line ──────────────────────────────────────────────────────────────────────

    @Test
    fun `the estimate comes from the stored projection, not from the stations`() {
        // §3 edge case 9. The station list here is deliberately inconsistent with the stored numbers:
        // a version is immutable, so the writer's figure is the one that stands.
        val estimate = snapshotEstimate(snapshot(durationSeconds = 475, totalReps = 0))
        assertEquals(475, estimate.durationSeconds)
        assertEquals("約 八分", estimateLabel(Engine.INTERVAL_CIRCUIT, estimate))
    }

    @Test
    fun `a max-effort station makes the estimate a 目安`() {
        val estimate = snapshotEstimate(
            snapshot(durationSeconds = 600, totalReps = 100, stations = listOf(station(Measure.MAX_EFFORT))),
        )
        assertTrue(estimate.approximate)
        assertTrue(estimateLabel(Engine.INTERVAL_CIRCUIT, estimate).endsWith("目安"))
    }

    @Test
    fun `an unresolvable exercise makes the estimate a 目安 too`() {
        val orphan = station(Measure.REPS, reps = 10).copy(exercise = null)
        assertTrue(snapshotEstimate(snapshot(stations = listOf(orphan))).approximate)
    }

    @Test
    fun `an arithmetic estimate says nothing about being approximate`() {
        val estimate = snapshotEstimate(snapshot(durationSeconds = 1200, totalReps = 600))
        assertFalse(estimate.approximate)
    }

    // ─── 最高 tiles ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a record set this month accents and an older one does not`() {
        // §3 edge case 7. Matched by label rather than by re-deriving the engine's metric, so this is
        // also the test that would fail if `bestMetricLabel` ever stopped being injective.
        val today = LocalDate.of(2026, 6, 17)
        val fresh = best(BestMetric.MOST_ROUNDS, LocalDate.of(2026, 6, 2))
        val stale = best(BestMetric.MOST_REPS, LocalDate.of(2026, 5, 30))
        assertTrue(bestTileIsThisMonth(BestTile("最高巡数", "十七巡"), listOf(fresh, stale), today))
        assertFalse(bestTileIsThisMonth(BestTile("最高反復", "三百四回"), listOf(fresh, stale), today))
    }

    @Test
    fun `the same month a year earlier is not this month`() {
        val today = LocalDate.of(2026, 6, 17)
        val lastYear = best(BestMetric.MOST_ROUNDS, LocalDate.of(2025, 6, 2))
        assertFalse(bestTileIsThisMonth(BestTile("最高巡数", "十七巡"), listOf(lastYear), today))
    }

    @Test
    fun `やった回数 never accents, because a lifetime count did not happen this month`() {
        val today = LocalDate.of(2026, 6, 17)
        val fresh = best(BestMetric.MOST_VOLUME, today)
        assertFalse(bestTileIsThisMonth(BestTile("やった回数", "六回"), listOf(fresh), today))
    }

    // ─── The header and the foot ────────────────────────────────────────────────────────────────

    @Test
    fun `the subtitle is the engine and the tier`() {
        assertEquals("時間内 ・ 中級", detailSubtitle(Engine.AMRAP, Tier.INTERMEDIATE, StringsJa))
    }

    @Test
    fun `a user routine with no tier subtitles with the engine alone`() {
        // `derivedTier` has not guessed one yet, and 「巡回 ・ 」 would be a dangling separator.
        assertEquals("巡回", detailSubtitle(Engine.INTERVAL_CIRCUIT, null, StringsJa))
    }

    @Test
    fun `the favourite action states what the tap will do`() {
        assertEquals("よく使うに入れる", detailFavouriteLabel(favourite = false))
        assertEquals("よく使うから外す", detailFavouriteLabel(favourite = true))
    }

    @Test
    fun `AMRAP is the one engine the page glosses`() {
        // §6 :1118 attaches the gloss to 時間内 and marks it "on the detail page". No other engine has
        // a documented one, and none may be invented.
        assertEquals("決めた時間で何巡できるか", detailEngineGloss(Engine.AMRAP))
        assertNull(detailEngineGloss(Engine.INTERVAL_CIRCUIT))
        assertNull(detailEngineGloss(Engine.FIXED_SETS))
    }

    @Test
    fun `the header borrows a name from the library only until the detail lands`() {
        // §3's Loading state: "title from the nav arg to avoid a flash". The nav arg is an id, so the
        // only name available early is the library's.
        val library = Loadable.Ready(listOf(summaryOf("r", "シンディ")))
        assertEquals("シンディ", detailHeaderTitle(Loadable.Loading, library, "r"))
        assertEquals("七分間", detailHeaderTitle(Loadable.Ready(detail()), library, "r"))
    }

    @Test
    fun `an unread library invents no title`() {
        // Not the id, not a placeholder: a name is the one fact this page exists to report.
        assertNull(detailHeaderTitle(Loadable.Loading, Loadable.Loading, "r"))
        assertNull(detailHeaderTitle(Loadable.Loading, Loadable.Failed(GymFault.StoreCorrupt), "r"))
        assertNull(detailHeaderTitle(Loadable.Loading, Loadable.Ready(emptyList()), "r"))
    }

    // ─── The ladder, and the read that failed ───────────────────────────────────────────────────

    @Test
    fun `a failed progression read says so instead of drawing the nothing a ladderless routine draws`() {
        // The MAJOR. `stepFor(progression.valueOrNull())` made Loading and Failed identical pixels, so
        // リーコン・ロン with an unreadable `progression_state` looked exactly like a routine with no
        // ladder — while 第七段 ・ 十八段のうち is that routine's central content (§3 edge case 5).
        val fault = GymFault.Unknown("routine gone: p_recon_ron")
        val block = progressionBlock(Engine.FIXED_SETS, "p_recon_ron", Loadable.Failed(fault))
        assertEquals(ProgressionBlock.Unreadable(fault), block)
    }

    @Test
    fun `a progression still loading stays silent`() {
        // Loading may be silent: the read is one query from answering, and a strip that flashes on
        // every entry would cry wolf. Only Failed must speak.
        assertEquals(
            ProgressionBlock.Silent,
            progressionBlock(Engine.FIXED_SETS, "p_recon_ron", Loadable.Loading),
        )
    }

    @Test
    fun `a read ladder renders the rung the user is on`() {
        val block = progressionBlock(Engine.FIXED_SETS, "p_recon_ron", Loadable.Ready(progression()))
        assertEquals(ProgressionBlock.Step("第七段 ・ 七 六 五 四 四", "十八段のうち"), block)
    }

    @Test
    fun `a routine with no ladder is silent, whatever the progression flow is doing`() {
        val fault = Loadable.Failed(GymFault.StoreCorrupt)
        assertEquals(ProgressionBlock.Silent, progressionBlock(Engine.INTERVAL_CIRCUIT, null, fault))
        assertEquals(ProgressionBlock.Silent, progressionBlock(Engine.FIXED_SETS, null, fault))
    }

    // ─── 始める's node ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `始める says 始める in every face it has`() {
        // §6 :1121 documents one description and its verb is 始める. Substituting the visible label
        // produced 「シンディ」を支度, which no table carries and which is not a sentence.
        assertEquals("「シンディ」を始める", startButtonDescription("シンディ", starting = false, StartBlock.None))
        assertTrue(
            startButtonDescription("シンディ", starting = true, StartBlock.None)
                .startsWith("「シンディ」を始める"),
        )
        assertFalse(
            startButtonDescription("シンディ", starting = true, StartBlock.None).contains("」を支度"),
        )
    }

    @Test
    fun `a blocked 始める explains itself to TalkBack, which cannot see the line under it`() {
        assertEquals(
            "「七分間」を始める、種目が見つからないため 始められません",
            startButtonDescription("七分間", starting = false, StartBlock.UnknownExercise),
        )
        // Archived appends nothing: the 削除済み chip is inside the header's own node already.
        assertEquals(
            "「七分間」を始める",
            startButtonDescription("七分間", starting = false, StartBlock.Archived),
        )
    }

    // ─── The foot: archived greys, built-in omits ───────────────────────────────────────────────

    @Test
    fun `an archived routine greys 編集 and よく使う rather than dropping them`() {
        // §1 rule 3 and §3's Archived state both say *disabled*. Absent-not-greyed is the BuiltIn
        // rule, and applying it here hid the fact that 元に戻す makes both available again.
        val actions = detailActions(builtIn = false, archived = true, favourite = false)
        assertEquals(
            listOf("写して作る", "よく使うに入れる", "編集", "元に戻す"),
            actions.map { it.label },
        )
        assertTrue(actions.single { it.kind == DetailActionKind.Duplicate }.enabled)
        assertFalse(actions.single { it.kind == DetailActionKind.Favourite }.enabled)
        assertFalse(actions.single { it.kind == DetailActionKind.Edit }.enabled)
        assertTrue(actions.single { it.kind == DetailActionKind.Restore }.enabled)
    }

    @Test
    fun `a built-in omits 編集 and 削除 entirely`() {
        val actions = detailActions(builtIn = true, archived = false, favourite = true)
        assertEquals(listOf("写して作る", "よく使うから外す"), actions.map { it.label })
    }

    @Test
    fun `a live user routine offers all four, all live`() {
        val actions = detailActions(builtIn = false, archived = false, favourite = false)
        assertEquals(listOf("写して作る", "よく使うに入れる", "編集", "削除"), actions.map { it.label })
        assertTrue(actions.all { it.enabled })
    }

    // ─── One resume prompt, not two ─────────────────────────────────────────────────────────────

    @Test
    fun `the prompt is drawn only by the page the user is looking at`() {
        // `GymShell`'s `AnimatedContent` keeps the outgoing and the incoming page composed together, so
        // an unguarded mount here and HOME's stacked two AlertDialogs — two scrims — over every pop.
        val top = GymRoute.RoutineDetail("r")
        assertTrue(detailOwnsResumePrompt(listOf(GymRoute.Home, GymRoute.Library, top), "r"))
        assertFalse(detailOwnsResumePrompt(listOf(GymRoute.Home), "r"))
        assertFalse(detailOwnsResumePrompt(listOf(GymRoute.Home, GymRoute.RoutineDetail("other")), "r"))
        assertFalse(detailOwnsResumePrompt(emptyList(), "r"))
    }

    // ─── fixtures ───────────────────────────────────────────────────────────────────────────────

    private fun station(measure: Measure, reps: Int? = null, seconds: Int? = null) = RoutineStation(
        position = 0,
        exerciseId = "pushup",
        exercise = Exercise(
            id = "pushup",
            nameJa = "腕立て伏せ",
            nameEn = "Push-up",
            pattern = Pattern.H_PUSH,
            secondsPerRep = 2.0,
            difficulty = 1.0,
            isIsometric = false,
            cue = null,
            ladderId = "push",
            catalogVersion = 1,
            archived = false,
        ),
        measure = measure,
        prescribedReps = reps,
        prescribedSeconds = seconds,
        note = null,
    )

    private fun snapshot(
        durationSeconds: Int = 475,
        totalReps: Int = 0,
        stations: List<RoutineStation> = listOf(station(Measure.REPS, reps = 10)),
    ) = RoutineSnapshot(
        versionId = 1L,
        routineId = "r",
        versionNumber = 1,
        name = "七分間",
        engine = Engine.INTERVAL_CIRCUIT,
        rounds = 1,
        timeCapSeconds = null,
        intervalSeconds = null,
        restBetweenStations = 10,
        restBetweenRounds = 0,
        prepareSeconds = 5,
        progressionProgramId = null,
        primaryMetric = BestMetric.MOST_VOLUME,
        stationCount = stations.size,
        estimatedDurationSeconds = durationSeconds,
        estimatedTotalReps = totalReps,
        structuralHash = 0,
        createdAt = 0L,
        stations = stations,
    )

    private fun summary(
        rounds: Int = 3,
        reps: Int = 120,
        activeMs: Long = 6L * 60_000L,
        complete: Boolean = true,
        personalBest: Boolean = false,
        stationsPlanned: Int = 12,
        stationsDone: Int = 12,
    ) = SessionSummary(
        sessionId = 1L,
        routineId = "r",
        routineVersionId = 1L,
        routineName = "七分間",
        engine = Engine.INTERVAL_CIRCUIT,
        tier = null,
        startedAt = 0L,
        finishedAt = 1L,
        localDate = LocalDate.of(2026, 6, 17),
        activeMs = activeMs,
        wallMs = activeMs,
        complete = complete,
        reachedTimeCap = false,
        roundsPlanned = 1,
        roundsCompleted = rounds,
        stationsPlanned = stationsPlanned,
        stationsCompleted = stationsDone,
        totalReps = reps,
        volumeUnits = 0.0,
        rating = null,
        personalBest = personalBest,
    )

    private fun detail() = RoutineDetail(
        routineId = "r",
        builtIn = false,
        tier = null,
        origin = null,
        favourite = false,
        derivedFromRoutineId = null,
        scaledFromRoutineId = null,
        createdAt = 0L,
        archivedAt = null,
        snapshot = snapshot(),
        bests = emptyList(),
        recentAttempts = emptyList(),
        timesDone = 0,
    )

    private fun summaryOf(routineId: String, name: String) = RoutineSummary(
        routineId = routineId,
        versionId = 1L,
        name = name,
        engine = Engine.AMRAP,
        builtIn = true,
        tier = null,
        origin = null,
        favourite = false,
        sortOrder = 0,
        rounds = null,
        timeCapSeconds = 1200,
        intervalSeconds = null,
        restBetweenStations = 0,
        restBetweenRounds = 0,
        stationCount = 3,
        estimatedDurationSeconds = 1200,
        estimatedTotalReps = 600,
        primaryMetric = BestMetric.MOST_ROUNDS,
        timesDone = 6,
        lastStartedAt = null,
        lastActiveMs = null,
        best = null,
        archivedAt = null,
    )

    /** リーコン・ロン's seventh rung — `02-data.md` §F.2 row 7, whose five sets total thirty-eight. */
    private fun progression() = ProgressionState(
        programId = "p_recon_ron",
        nameJa = "リーコン・ロン",
        stepUnit = StepUnit.STEP,
        stepCount = 18,
        advanceRule = AdvanceRule.MANUAL,
        advanceParam = null,
        cycleDays = null,
        origin = "Pasieka, 1981",
        noteJa = null,
        currentStepIndex = 7,
        currentStep = ProgressionStep(
            stepIndex = 7,
            labelJa = null,
            shape = StepShape.FIXED,
            totalReps = 38,
            restSeconds = 90,
            noteJa = null,
            sets = listOf(7, 6, 5, 4, 4).mapIndexed { index, reps ->
                ProgressionSet(setIndex = index, reps = reps, variant = null)
            },
        ),
        sessionsAtStep = 0,
        stepEnteredAt = 0L,
        lastSessionId = null,
        cycleDay = 0,
    )

    private fun best(metric: BestMetric, on: LocalDate) = RoutineBest(
        routineId = "r",
        routineName = "シンディ",
        engine = Engine.AMRAP,
        metric = metric,
        value = 17.0,
        tiebreak = 0.0,
        sessionId = 1L,
        achievedAt = 0L,
        localDate = on,
        timesDone = 6,
        routineArchived = false,
        structureChanged = false,
    )
}
