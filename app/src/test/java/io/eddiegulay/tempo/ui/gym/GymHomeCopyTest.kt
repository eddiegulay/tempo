package io.eddiegulay.tempo.ui.gym

import io.eddiegulay.tempo.calendar.Loadable
import io.eddiegulay.tempo.data.GymFault
import io.eddiegulay.tempo.gym.BestMetric
import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.GymHomeFeed
import io.eddiegulay.tempo.gym.GymTab
import io.eddiegulay.tempo.gym.LastResult
import io.eddiegulay.tempo.gym.Measure
import io.eddiegulay.tempo.gym.PersistedClock
import io.eddiegulay.tempo.gym.Phase
import io.eddiegulay.tempo.gym.ResumableSession
import io.eddiegulay.tempo.gym.Resumability
import io.eddiegulay.tempo.gym.RoutineBest
import io.eddiegulay.tempo.gym.RoutineCard
import io.eddiegulay.tempo.gym.SegmentResult
import io.eddiegulay.tempo.gym.data.resumabilityOf
import io.eddiegulay.tempo.gym.label
import io.eddiegulay.tempo.i18n.StringsEn
import io.eddiegulay.tempo.i18n.StringsJa
import io.eddiegulay.tempo.ui.faultCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * `GYM.HOME`'s copy tables, asserted as tables.
 *
 * Every string these functions can produce is quoted from `01-shell.md` §B, `03-player.md` §A,
 * `04-library-records.md` §6 or `exercise-design.md` §12, so the tests below are mostly literal: they
 * exist to catch the day someone "tidies" 前回 六分五十秒 ・ 三日前 into something more natural, or
 * decides 〇回 is a reasonable thing to render at zero.
 *
 * Three of them are about *silence* rather than wording — [bestLine] on a `HIGHEST_STEP` record,
 * [timesDoneLabel] at zero, and [progressLine] before the first station — because omitting a line is
 * this page's answer whenever a case has no documented copy, and a regression there is a sentence
 * nobody wrote appearing on screen.
 *
 * **The Japanese assertions are unchanged by the migration, on purpose.** They now read through
 * [StringsJa] rather than through literals in the page's own file, and they still assert the same
 * characters: moving the copy into a table had to be behaviour-neutral for the language the app
 * already ships, and a test that was relaxed to accommodate the move would have verified the move
 * instead of the behaviour. The English assertions are the new half, and they are deliberately few —
 * they pin the two places where English is a *decision* rather than a translation: [stalenessLabel]'s
 * buckets, which must not restate an hour count they cannot stand behind, and the composed lines
 * whose separators and counters differ.
 */
class GymHomeCopyTest {

    private val ja = StringsJa
    private val en = StringsEn

    private val zone: ZoneId = ZoneId.of("Asia/Tokyo")
    private val today: LocalDate = LocalDate.of(2026, 6, 17)

    private fun card(
        id: String = "seven",
        name: String = "七分間",
        stationCount: Int = 12,
        workSeconds: Int? = 30,
        restBetweenStations: Int = 10,
        estimatedDurationSeconds: Int = 7 * 60,
        timesDone: Int = 14,
        lastResult: LastResult? = null,
        best: RoutineBest? = null,
        builtIn: Boolean = true,
        origin: String? = null,
    ) = RoutineCard(
        id, name, stationCount, workSeconds, restBetweenStations,
        estimatedDurationSeconds, timesDone, lastResult, best, builtIn, origin,
    )

    private fun lastResult(
        activeMs: Long = 410_000L,
        rounds: Int = 0,
        engine: Engine = Engine.INTERVAL_CIRCUIT,
        day: LocalDate = LocalDate.of(2026, 6, 14),
    ) = LastResult(
        sessionId = 1L,
        startedAt = day.atTime(7, 0).atZone(zone).toInstant().toEpochMilli(),
        activeMs = activeMs,
        roundsCompleted = rounds,
        complete = true,
        engine = engine,
    )

    private fun best(
        metric: BestMetric = BestMetric.MOST_ROUNDS,
        value: Double = 17.0,
        on: LocalDate = LocalDate.of(2026, 6, 2),
    ) = RoutineBest(
        routineId = "seven",
        routineName = "七分間",
        engine = Engine.AMRAP,
        metric = metric,
        value = value,
        tiebreak = 0.0,
        sessionId = 9L,
        achievedAt = 0L,
        localDate = on,
        timesDone = 14,
        routineArchived = false,
        structureChanged = false,
    )

    private fun clock(
        started: Long = 1_000_000L,
        pausedAccumulated: Long = 0L,
        pausedAt: Long? = null,
    ) = PersistedClock(
        startedAtElapsedMs = started,
        pausedAccumulatedMs = pausedAccumulated,
        pausedAtElapsedMs = pausedAt,
        pausedAtWallMs = null,
        bootAnchorMs = 0L,
        anchorWallMs = 0L,
    )

    private fun segment(ordinal: Int, phase: Phase, skipped: Boolean = false, closedElapsed: Long = 0L) =
        SegmentResult(
            ordinal = ordinal,
            phase = phase,
            roundIndex = 0,
            stationOrder = ordinal,
            exerciseId = "pushup",
            measure = Measure.DURATION,
            prescribedReps = null,
            prescribedSeconds = 30,
            actualReps = null,
            actualMs = 30_000L,
            addedMs = 0L,
            skipped = skipped,
            difficultyCoefficient = 1.0,
            volumeUnits = 3.0,
            closedAtWallMs = 0L,
            closedAtElapsedMs = closedElapsed,
        )

    private fun session(
        resumability: Resumability = Resumability.RESUMABLE,
        results: List<SegmentResult> = emptyList(),
        lastWriteWallMs: Long = 0L,
        clock: PersistedClock = clock(),
    ) = ResumableSession(
        sessionId = 4L,
        routineId = "seven",
        routineVersionId = 2L,
        routineName = "七分間",
        routineExists = true,
        tier = null,
        engine = Engine.INTERVAL_CIRCUIT,
        startedAtWallMs = 0L,
        lastWriteWallMs = lastWriteWallMs,
        stationsPlanned = 12,
        roundsPlanned = 1,
        clock = clock,
        results = results,
        resumability = resumability,
    )

    // ─── Sections ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a first run has no よく使う section, because there is no history to rank`() {
        val feed = GymHomeFeed(
            recent = emptyList(),
            builtIn = List(6) { card(id = "b$it") },
            builtInTotal = 6,
            userRoutines = emptyList(),
            everTrained = false,
        )
        val kinds = homeSections(feed, ja).map { it.kind }
        assertEquals(listOf(HomeSectionKind.BuiltIn, HomeSectionKind.User), kinds)
    }

    @Test
    fun `自分の型 keeps its section when it is empty, because that is where 型はまだありません lives`() {
        val feed = GymHomeFeed(
            recent = listOf(card()),
            builtIn = listOf(card(id = "b1")),
            builtInTotal = 1,
            userRoutines = emptyList(),
            everTrained = true,
        )
        val user = homeSections(feed, ja).single { it.kind == HomeSectionKind.User }
        assertTrue(user.cards.isEmpty())
        assertEquals("自分の型", user.label)
    }

    @Test
    fun `すべて見る appears only when the preview is short of the total`() {
        val four = List(4) { card(id = "b$it") }
        val exactly = GymHomeFeed(listOf(card()), four, builtInTotal = 4, userRoutines = emptyList(), everTrained = true)
        val more = GymHomeFeed(listOf(card()), four, builtInTotal = 9, userRoutines = emptyList(), everTrained = true)
        assertFalse(homeSections(exactly, ja).single { it.kind == HomeSectionKind.BuiltIn }.seeAll)
        assertTrue(homeSections(more, ja).single { it.kind == HomeSectionKind.BuiltIn }.seeAll)
    }

    @Test
    fun `one routine in two sections gets two different list keys`() {
        val feed = GymHomeFeed(
            recent = listOf(card(id = "seven")),
            builtIn = listOf(card(id = "seven")),
            builtInTotal = 1,
            userRoutines = emptyList(),
            everTrained = true,
        )
        val keys = homeSections(feed, ja).flatMap { s -> s.cards.map { "${s.keyPrefix}:${it.routineId}" } }
        assertEquals(listOf("frequent:seven", "builtin:seven"), keys)
    }

    @Test
    fun `the tab bar's three words come from the table and not from the enum`() {
        // §L3: the label was a constructor argument, which is resolved at class-init and cannot
        // follow the picker. Asserted through the enum's extension so a tab added later cannot be
        // given a word in one language only — `label` is exhaustive and would not compile.
        assertEquals(listOf("鍛錬", "型", "記録"), GymTab.entries.map { it.label(ja) })
        assertEquals(listOf("Train", "Routines", "Records"), GymTab.entries.map { it.label(en) })
    }

    // ─── A card's lines ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `a circuit reports its duration and a plain relative day`() {
        val line = lastResultLine(lastResult(), today, zone, ja)
        assertEquals("前回 六分五十秒 ・ 三日前", line)
    }

    @Test
    fun `an AMRAP reports rounds instead, because a fixed cap is not a result`() {
        val line = lastResultLine(
            lastResult(activeMs = 1_200_000L, rounds = 15, engine = Engine.AMRAP, day = LocalDate.of(2026, 6, 11)),
            today,
            zone,
            ja,
        )
        assertEquals("前回 十五巡 ・ 六日前", line)
    }

    @Test
    fun `an AMRAP that completed no round falls back to its duration rather than printing 〇巡`() {
        val line = lastResultLine(lastResult(activeMs = 410_000L, rounds = 0, engine = Engine.AMRAP), today, zone, ja)
        assertEquals("前回 六分五十秒 ・ 三日前", line)
    }

    @Test
    fun `bestLine gives every metric its own documented word`() {
        assertEquals("最高 十七巡", bestLine(best(BestMetric.MOST_ROUNDS, 17.0), ja))
        assertEquals("最高 三百二十回", bestLine(best(BestMetric.MOST_REPS, 320.0), ja))
        assertEquals("最速 六分十四秒", bestLine(best(BestMetric.BEST_TIME, 374_000.0), ja))
        assertEquals("最高負荷 八十四", bestLine(best(BestMetric.MOST_VOLUME, 84.0), ja))
    }

    @Test
    fun `a HIGHEST_STEP record has no line, because no Japanese label for it exists`() {
        // `DECISIONS.md` §Q9: nothing seeds it in v1 and none is to be invented. The step a ladder has
        // reached is rendered as 第九段 on the routine's own page, which is where a ladder belongs.
        assertNull(bestLine(best(BestMetric.HIGHEST_STEP, 9.0), ja))
    }

    @Test
    fun `a record of zero is no record`() {
        assertNull(bestLine(best(BestMetric.MOST_ROUNDS, 0.0), ja))
        assertNull(bestLine(null, ja))
    }

    @Test
    fun `最高 is this month's only within this month, and never for a date in the future`() {
        assertTrue(isPrThisMonth(best(on = LocalDate.of(2026, 6, 2)), today))
        assertFalse(isPrThisMonth(best(on = LocalDate.of(2026, 5, 30)), today))
        assertFalse(isPrThisMonth(best(on = LocalDate.of(2026, 6, 30)), today))
        assertFalse(isPrThisMonth(null, today))
    }

    @Test
    fun `a routine never done has no count line`() {
        assertNull(timesDoneLabel(0, ja))
        assertEquals("十四回", timesDoneLabel(14, ja))
    }

    @Test
    fun `relative days read きょう, きのう and a kanji count`() {
        // `relativeDayJa` is gone: it was this page's own copy of a formatter, and the behaviour now
        // lives on `fmt.relativeDay` for both languages (§L8). The Japanese it produces is unchanged,
        // which is what this still asserts — the hiragana vocabulary that looks *backwards*, distinct
        // from `dayToken`'s kanji 今日 / 明日, which looks forwards.
        assertEquals("きょう", ja.fmt.relativeDay(today, today))
        assertEquals("きのう", ja.fmt.relativeDay(today.minusDays(1), today))
        assertEquals("三日前", ja.fmt.relativeDay(today.minusDays(3), today))
        // A clock wound backwards is the only way to get here, and 負一日前 would report our confusion
        // as a fact about their training.
        assertEquals("きょう", ja.fmt.relativeDay(today.plusDays(2), today))

        // §L7: English has one word where Japanese has two vocabularies, and that collapse is a
        // documented deletion rather than a translation.
        assertEquals("Today", en.fmt.relativeDay(today, today))
        assertEquals("Today", en.fmt.dayToken(today, today))
    }

    @Test
    fun `a card is one TalkBack node, punctuated as the spec writes it`() {
        val subject = card(lastResult = lastResult(), best = best())
        val description = routineCardDescription(
            subject,
            lastResultLine(subject.lastResult, today, zone, ja),
            bestLine(subject.best, ja),
            ja,
        )
        assertEquals(
            "七分間、十二種目 ・ 三十秒 / 十秒 ・ 約 七分、前回 六分五十秒、三日前、十四回、最高 十七巡",
            description,
        )
    }

    @Test
    fun `the same card in English keeps its shape and changes its counters and its punctuation`() {
        val subject = card(lastResult = lastResult(), best = best())
        val last = lastResultLine(subject.lastResult, today, zone, en)
        assertEquals("Last 6m 50s · 3 days ago", last)
        assertEquals("Best 17 rounds", bestLine(subject.best, en))
        assertEquals("14 times", timesDoneLabel(14, en))

        // The node re-punctuates the visible line's separator into the list one — ` · ` → `, ` here
        // and ` ・ ` → 「、」 above — which only works because the line was built through `fmt`.
        // The name and the summary stay as the row stores them: "seven" is not a seed id, so
        // `displayName` falls through to what was written, which is §L10's rule for user content.
        assertEquals(
            "七分間, 12 stations · 30s / 10s · ~7m, Last 6m 50s, 3 days ago, 14 times, Best 17 rounds",
            routineCardDescription(subject, last, bestLine(subject.best, en), en),
        )
    }

    // ─── The banner ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the banner is one node and names 続ける only when it is there`() {
        assertEquals(
            "つづき、七分間、六分十四秒 経過、八種目まで進んだ、続ける",
            resumeBannerDescription("七分間", "六分十四秒", "八種目まで進んだ", resumable = true, strings = ja),
        )
        assertEquals(
            "つづき、七分間",
            resumeBannerDescription("七分間", null, null, resumable = false, strings = ja),
        )
    }

    @Test
    fun `progress counts the stations a session passed, skipped ones included, rests excluded`() {
        val results = listOf(
            segment(0, Phase.WORK),
            segment(1, Phase.REST),
            segment(2, Phase.REPS, skipped = true),
            segment(3, Phase.REST),
        )
        assertEquals(2, stationsProgressed(results))
        assertEquals("二種目まで進んだ", progressLine(stationsProgressed(results), ja))
        assertNull(progressLine(0, ja))

        // まで進んだ follows its count and "Reached" precedes it, which is why the whole sentence is
        // a table member rather than a suffix glued on at the call site.
        assertEquals("Reached 2 stations", progressLine(2, en))
        assertNull(progressLine(0, en))
    }

    @Test
    fun `a live session's elapsed climbs with the monotonic clock and freezes when it is paused`() {
        val running = clock(started = 1_000_000L, pausedAccumulated = 20_000L)
        assertEquals(280_000L, liveElapsedMs(running, nowElapsedMs = 1_300_000L))

        val paused = clock(started = 1_000_000L, pausedAccumulated = 20_000L, pausedAt = 1_300_000L)
        assertEquals(280_000L, liveElapsedMs(paused, nowElapsedMs = 9_999_999L))
    }

    @Test
    fun `elapsed is never negative, whatever the anchors say`() {
        assertEquals(0L, liveElapsedMs(clock(started = 5_000_000L), nowElapsedMs = 1_000L))
    }

    @Test
    fun `an interrupted session is credited only to its last persisted transition`() {
        // The gap between the last write and now is unknowable — it may have been in a pocket — so it
        // is never credited (`03-player.md` §A edge case 6).
        val results = listOf(
            segment(0, Phase.WORK, closedElapsed = 1_200_000L),
            segment(1, Phase.REST, closedElapsed = 1_230_000L),
        )
        assertEquals(230_000L, recoveredElapsedMs(session(results = results, clock = clock(started = 1_000_000L))))
        assertEquals(0L, recoveredElapsedMs(session(results = emptyList())))
    }

    // ─── The resume prompt ──────────────────────────────────────────────────────────────────────

    /** A fixed morning in the class's fixed zone, so "the same day" is a fact, not a property of today. */
    private val morning: Long =
        LocalDate.of(2026, 6, 10).atTime(8, 0).atZone(zone).toInstant().toEpochMilli()

    private fun staleness(sinceMorningMs: Long) =
        stalenessLabel(morning, morning + sinceMorningMs, zone, ja)

    @Test
    fun `staleness is one of the documented words and never a number`() {
        assertEquals("さっき", staleness(60_000L))
        assertEquals("一時間前", staleness(90 * 60_000L))
        assertEquals("二時間前", staleness(3 * 60 * 60_000L))
        assertEquals("三日前", staleness(3 * 24 * 60 * 60_000L))
        assertTrue(staleness(137 * 60_000L).none { it.isDigit() })
    }

    @Test
    fun `a session abandoned this morning is きょう at ten at night, not 二時間前 and not 昨日`() {
        // The bug this pins out, and the gap that fixing it exposed. The 2h–24h bucket first returned
        // 二時間前, so 08:00 revisited at 22:00 was announced as "two hours ago" — out by a factor of
        // seven. The first fix made it 昨日, which is false in the other direction: it is still the
        // same day. §A edge case 4's five words cannot cover 4h–24h, so past the horizon the label
        // delegates to relativeDayJa, whose きょう is documented at 01-shell.md:642. DECISIONS.md §Q13.
        assertEquals("きょう", staleness(5 * 60 * 60_000L))   // 13:00
        assertEquals("きょう", staleness(14 * 60 * 60_000L))  // 22:00
    }

    @Test
    fun `past the horizon the day words count calendar days, not elapsed hours`() {
        // The ordering here is the whole argument for taking instants and a zone rather than a delta:
        // FIVE hours reads きのう while FOURTEEN reads きょう. No hour count can produce that, because
        // it is midnight that separates them, not duration.
        val lateNight = LocalDate.of(2026, 6, 10).atTime(22, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals("きのう", stalenessLabel(lateNight, lateNight + 5 * 60 * 60_000L, zone, ja)) // 03:00
        assertEquals("きょう", staleness(14 * 60 * 60_000L))                                  // 08:00 → 22:00
    }

    @Test
    fun `below the horizon elapsed time wins, even across midnight`() {
        // Twenty minutes after midnight, きのう would be true and useless — the fact the user wants is
        // that they stopped a moment ago. The day words take over only once the elapsed number has
        // stopped being the more informative of the two.
        val lateNight = LocalDate.of(2026, 6, 10).atTime(23, 50).atZone(zone).toInstant().toEpochMilli()
        assertEquals("一時間前", stalenessLabel(lateNight, lateNight + 20 * 60_000L, zone, ja))
    }

    @Test
    fun `the 二時間前 bucket ends exactly where a session stops being resumable`() {
        // The 4h edge is not a new number: it is `resumabilityOf`'s own STALE_AFTER_MS. Pinned against
        // that function rather than against a literal so the two cannot drift apart.
        val fourHours = 4 * 60 * 60_000L
        val anchored = clock(started = 0L)
        assertEquals(
            Resumability.RESUMABLE,
            resumabilityOf(anchored, lastWriteWallMs = 0L, hasResults = true, nowWall = fourHours - 1, nowElapsed = fourHours - 1),
        )
        assertEquals(
            Resumability.STALE,
            resumabilityOf(anchored, lastWriteWallMs = 0L, hasResults = true, nowWall = fourHours, nowElapsed = fourHours),
        )
        assertEquals("二時間前", staleness(fourHours - 1))
        assertEquals("きょう", staleness(fourHours))
    }

    @Test
    fun `the English buckets round, because they cannot restate an hour count they span`() {
        // §A edge case 4's words are buckets that look like measurements: 一時間前 covers ten minutes
        // to two hours and 二時間前 covers two hours to four. The Japanese is the spec's and ships as
        // it is; an authored English "2 hours ago" over a three-and-a-half-hour-old session would be
        // the false *statement* the bucket edges were moved to remove, not the coarse-but-true one.
        // Every word below is true across the whole of its bucket.
        assertEquals("Just now", stalenessLabel(morning, morning + 60_000L, zone, en))
        assertEquals("A little while ago", stalenessLabel(morning, morning + 11 * 60_000L, zone, en))
        assertEquals("A little while ago", stalenessLabel(morning, morning + 119 * 60_000L, zone, en))
        assertEquals("A few hours ago", stalenessLabel(morning, morning + 3 * 60 * 60_000L, zone, en))

        // And past the horizon English delegates to the same formatter Japanese does.
        assertEquals("Today", stalenessLabel(morning, morning + 5 * 60 * 60_000L, zone, en))
    }

    @Test
    fun `a resumable session is asked about in the present tense`() {
        val results = listOf(segment(0, Phase.WORK, closedElapsed = 1_374_000L))
        val copy = resumePromptCopy(
            session(results = results, clock = clock(started = 1_000_000L), lastWriteWallMs = 0L),
            nowWallMs = 60_000L,
            strings = ja,
        )
        assertEquals("途中の 鍛錬があります", copy.title)
        assertEquals("七分間 ・ 六分十四秒 ・ 一種目", copy.detail)
        assertEquals("さっき", copy.staleness)
        assertNull(copy.note)
    }

    @Test
    fun `a rebooted session says so and drops the promise of continuing`() {
        val copy = resumePromptCopy(session(resumability = Resumability.REBOOTED), nowWallMs = 0L, strings = ja)
        assertEquals("途中の 鍛錬が 残っています", copy.title)
        assertEquals("続きからは できません", copy.note)

        val english = resumePromptCopy(
            session(resumability = Resumability.REBOOTED),
            nowWallMs = 0L,
            strings = en,
        )
        assertEquals("An unfinished workout is left", english.title)
        assertEquals("It can't be continued", english.note)
    }

    @Test
    fun `a session with nothing to save keeps the ordinary title and omits its empty numbers`() {
        // It lost 記録する, not 続ける's premise — and 〇秒 ・ 〇種目 would read as a score.
        val copy = resumePromptCopy(session(resumability = Resumability.NOTHING_TO_SAVE), nowWallMs = 0L, strings = ja)
        assertEquals("途中の 鍛錬があります", copy.title)
        assertEquals("七分間", copy.detail)
        assertNull(copy.note)
    }

    // ─── 削除 ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `削除 says which of its two meanings it has`() {
        val kept = deleteRoutineCopy("シンディ", timesDone = 6, strings = ja)
        assertEquals("「シンディ」を削除しますか", kept.title)
        assertEquals("これまでの六回の記録は残ります。型だけが一覧から消えます。", kept.body)
        assertEquals("削除", kept.confirm)

        val gone = deleteRoutineCopy("シンディ", timesDone = 0, strings = ja)
        assertEquals("やった記録はありません。完全に消えます。", gone.body)
        assertEquals("完全に削除", gone.confirm)
    }

    @Test
    fun `削除's dialog is the library's one dialog, not a third copy of it`() {
        // Three surfaces raise this confirm and all three landed §6's branch independently. The
        // library's two pages converged on one key set; this asserts that GYM.HOME reads the same
        // members rather than a parallel table that can drift a word at a time.
        for (strings in listOf(ja, en)) {
            val library = strings.gymLibrary
            val kept = deleteRoutineCopy("シンディ", timesDone = 6, strings = strings)
            assertEquals(library.deleteTitle("シンディ"), kept.title)
            assertEquals(library.deleteBodyArchive(strings.fmt.times(6)), kept.body)
            assertEquals(library.deleteConfirmArchive, kept.confirm)

            val gone = deleteRoutineCopy("シンディ", timesDone = 0, strings = strings)
            assertEquals(library.deleteBodyPurge, gone.body)
            assertEquals(library.deleteConfirmPurge, gone.confirm)
        }
    }

    @Test
    fun `the routine an open session belongs to never reaches the irreversible branch`() {
        // `timesDone` counts finished sessions only, so a routine whose one session is the interrupted
        // one counts zero — and the dialog promised 完全に消えます over a purge that `GymStore`'s own
        // guard then refuses, because that guard counts *all* sessions.
        val fresh = card(id = "seven", timesDone = 0)
        assertEquals(0, deleteRoutineCount(fresh, openRoutineId = null))
        assertEquals(0, deleteRoutineCount(fresh, openRoutineId = "other"))
        assertEquals(1, deleteRoutineCount(fresh, openRoutineId = "seven"))

        assertEquals("完全に削除", deleteRoutineCopy(fresh.name, deleteRoutineCount(fresh, null), ja).confirm)
        assertEquals("削除", deleteRoutineCopy(fresh.name, deleteRoutineCount(fresh, "seven"), ja).confirm)

        // A real history is never lowered by it.
        assertEquals(14, deleteRoutineCount(card(id = "seven", timesDone = 14), openRoutineId = "seven"))
    }

    // ─── Faults ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a failed read of the open-session row is words, never silence`() {
        // The defect: `resumable` is a real Loadable, but the page unwrapped it and `homeResume` maps
        // Loading and Failed alike to `None` — so an unreadable open session drew exactly the pixels
        // of a clean store. Loading stays silent (the row arrives in milliseconds); Failed must not.
        assertEquals(GymFault.StoreCorrupt, resumeFault(Loadable.Failed(GymFault.StoreCorrupt)))
        assertEquals(
            StringsJa.fault.gym.storeUnreadable,
            faultCopy(resumeFault(Loadable.Failed(GymFault.StoreCorrupt))!!, StringsJa).message,
        )
        assertNull(resumeFault(Loadable.Loading))
        assertNull(resumeFault(Loadable.Ready(null)))
        assertNull(resumeFault(Loadable.Ready(session())))
    }

    @Test
    fun `a write fault with no action word needs a dismiss of its own`() {
        // `DECISIONS.md` §Q6 leaves these four with nothing to press, and `FaultStrip` draws its tap
        // target only when there is something — so on HOME they pinned an unremovable strip to the top
        // of the feed for the rest of the visit.
        assertTrue(writeFaultStuck(GymFault.RoutineGone))
        assertTrue(writeFaultStuck(GymFault.SessionGone))
        assertTrue(writeFaultStuck(GymFault.Rejected))
        assertTrue(writeFaultStuck(GymFault.StoreFull))

        // The retryable ones dismiss themselves on もう一度 and must not grow a second word.
        assertFalse(writeFaultStuck(GymFault.StoreCorrupt))
        assertFalse(writeFaultStuck(GymFault.Unknown(cause = null)))
    }
}
